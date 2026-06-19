(ns crazy_eights.web.view_model-test
  (:require [clojure.test :refer [deftest is testing]]
            [crazy_eights.domain.model :as model]
            [crazy_eights.web.view_model :as vm]))

(def waiting-game
  {:game-id "game-0"
   :state nil
   :players {"game-0-player-0" {:seat 0 :name "anna"}
             "game-0-player-1" {:seat 1 :name "ben"}}})

(def playing-state
  {:players [{:hand [(model/card :queen :clubs) (model/card :eight :diamonds)]}
             {:hand [(model/card :two :hearts)]}]
   :draw-pile [(model/card :four :clubs)]
   :discard-pile [(model/card :queen :spades)]
   :active-suit :spades
   :current-player 0
   :status :in-progress
   :winner nil
   :passes-in-row 0})

(def playing-game (assoc waiting-game :state playing-state))

(def between-games (assoc waiting-game :started-once? true))

(deftest waiting-view-for-host
  (let [view (vm/game-view waiting-game "game-0-player-0")]
    (is (= :waiting (:phase view)))
    (is (= ["anna" "ben"] (mapv :name (:players view))))
    (is (true? (:host? view)))
    (is (true? (:can-start? view)))
    (is (= "start game" (:start-label view)))
    (is (true? (:can-leave? view)))
    (is (= 0 (:viewer-seat view)))))

(deftest waiting-view-for-guest-and-spectator
  (let [guest (vm/game-view waiting-game "game-0-player-1")
        spectator (vm/game-view waiting-game nil)
        solo (vm/game-view (update waiting-game :players select-keys ["game-0-player-0"])
                           "game-0-player-0")]
    (is (false? (:can-start? guest)))
    (is (false? (:host? guest)))
    (is (true? (:can-leave? guest)))
    (is (nil? (:viewer-seat spectator)))
    (is (false? (:can-start? spectator)))
    (is (false? (:can-leave? spectator)))
    (testing "host alone cannot start"
      (is (false? (:can-start? solo))))))

(deftest between-games-view-keeps-table-actions-for-seated-players
  (let [host (vm/game-view between-games "game-0-player-0")
        guest (vm/game-view between-games "game-0-player-1")
        spectator (vm/game-view between-games nil)]
    (is (= :between-games (:phase host)))
    (is (true? (:can-start? host)))
    (is (= "start new game" (:start-label host)))
    (is (true? (:can-leave? host)))
    (is (false? (:can-start? guest)))
    (is (true? (:can-leave? guest)))
    (is (false? (:can-leave? spectator)))))

(deftest playing-view-for-current-player
  (let [view (vm/game-view playing-game "game-0-player-0")]
    (is (= :playing (:phase view)))
    (is (true? (:your-turn? view)))
    (is (= "QS" (:top-code view)))
    (is (= :spades (:active-suit view)))
    (is (= 1 (:draw-count view)))
    (is (= [{:code "QC" :playable? true :eight? false}
            {:code "8D" :playable? true :eight? true}]
           (mapv #(select-keys % [:code :playable? :eight?]) (:hand view))))
    (is (= [false true]
           (mapv :declarable? (:hand view))))
    (testing "playable hand means no draw and no pass"
      (is (false? (:can-draw? view)))
      (is (false? (:can-pass? view))))))

(deftest playing-view-hides-opponent-hands
  (let [view (vm/game-view playing-game "game-0-player-1")]
    (is (false? (:your-turn? view)))
    (is (= [{:name "anna" :card-count 2 :current? true :you? false}
            {:name "ben" :card-count 1 :current? false :you? true}]
           (mapv #(select-keys % [:name :card-count :current? :you?]) (:players view))))
    (is (= ["2H"] (mapv :code (:hand view))))
    (testing "nothing in the view exposes another player's cards"
      (let [printed (pr-str (dissoc view :top-card :top-code))]
        (doseq [leak ["\"QC\"" "\"8D\"" ":rank :queen" ":rank :eight" ":suit :diamonds"]]
          (is (not (re-find (re-pattern (java.util.regex.Pattern/quote leak)) printed))
              (str "leaked " leak)))))))

(deftest draw-and-pass-availability
  (let [no-play (assoc-in playing-state [:players 0 :hand] [(model/card :three :hearts)])
        drawable (assoc playing-game :state no-play)
        reshuffle (assoc playing-game :state (assoc no-play
                                                    :draw-pile []
                                                    :discard-pile [(model/card :ace :clubs)
                                                                   (model/card :queen :spades)]))
        stuck (assoc playing-game :state (assoc no-play :draw-pile []))]
    (testing "draw when pile has cards"
      (let [view (vm/game-view drawable "game-0-player-0")]
        (is (true? (:can-draw? view)))
        (is (false? (:can-pass? view)))))
    (testing "draw still offered when reshuffle can refill the pile"
      (is (true? (:can-draw? (vm/game-view reshuffle "game-0-player-0")))))
    (testing "pass only when no play, no draw, no reshuffle"
      (let [view (vm/game-view stuck "game-0-player-0")]
        (is (false? (:can-draw? view)))
        (is (true? (:can-pass? view)))))))

(deftest spectator-view-during-play
  (let [view (vm/game-view playing-game nil)]
    (is (= :playing (:phase view)))
    (is (nil? (:viewer-seat view)))
    (is (nil? (:hand view)))
    (is (= [2 1] (mapv :card-count (:players view))))))

(deftest finished-views
  (let [won (assoc playing-game :state (assoc playing-state
                                              :status :finished
                                              :winner 1
                                              :players [{:hand [(model/card :queen :clubs)]}
                                                        {:hand []}]))
        blocked (assoc playing-game :state (assoc playing-state
                                                  :status :finished
                                                  :winner nil))]
    (let [view (vm/game-view won "game-0-player-0")]
      (is (= :finished (:phase view)))
      (is (= "ben" (:winner-name view)))
      (is (false? (:blocked? view)))
      (is (false? (:your-turn? view)))
      (is (true? (:can-start? view)))
      (is (true? (:can-leave? view)))
      (is (= "start new game" (:start-label view))))
    (let [view (vm/game-view blocked nil)]
      (is (nil? (:winner-name view)))
      (is (true? (:blocked? view))))))
