(ns crazy_eights.app.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [crazy_eights.app.core :as app]
            [crazy_eights.domain.model :as model]))

(deftest create-and-join-game
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        p1 (app/join-game! store game-id "anna")
        p2 (app/join-game! store game-id)
        game (app/get-game store game-id)]
    (is (string? game-id))
    (is (string? (:player-id p1)))
    (is (= 0 (:seat p1)))
    (is (= 1 (:seat p2)))
    (is (= "anna" (get-in game [:players (:player-id p1) :name])))
    (is (= "player 2" (get-in game [:players (:player-id p2) :name])))
    (is (= 2 (:next-player-number game)))
    (is (= #{(:player-id p1) (:player-id p2)}
           (set (keys (:players game)))))))

(deftest create-game-assigns-observer-id
  (let [store (app/create-store)
        {first-id :game-id} (app/create-game! store)
        {second-id :game-id} (app/create-game! store)
        first-observer (:observer-id (app/get-game store first-id))
        second-observer (:observer-id (app/get-game store second-id))]
    (is (string? first-observer))
    (is (uuid? (parse-uuid first-observer)))
    (is (not= first-observer second-observer))))

(deftest host-game-creates-game-and-seats-host
  (let [store (app/create-store)
        result (app/host-game! store "anna")
        game (app/get-game store (:game-id result))]
    (is (= "game-0" (:game-id result)))
    (is (= "game-0-player-0" (:player-id result)))
    (is (= 0 (:seat result)))
    (is (= "anna" (get-in game [:players (:player-id result) :name])))))

(deftest host-is-first-seat
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        p1 (app/join-game! store game-id "anna")
        p2 (app/join-game! store game-id "ben")
        game (app/get-game store game-id)]
    (is (app/host? game (:player-id p1)))
    (is (not (app/host? game (:player-id p2))))
    (is (not (app/host? game "nobody")))))

(deftest join-game-emits-player-joined
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        sink (atom [])]
    (app/subscribe! store game-id :test #(swap! sink conj %))
    (let [p1 (app/join-game! store game-id "anna")]
      (is (= [{:type :player-joined
               :game-id game-id
               :player-id (:player-id p1)
               :seat 0
               :name "anna"}]
             @sink)))))

(deftest subscribing-to-missing-game-does-not-create-table
  (let [store (app/create-store)]
    (is (= :test (app/subscribe! store "missing" :test identity)))
    (is (empty? (:games @store)))))

(deftest join-game-rejects-more-than-max-players
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)]
    (dotimes [_ model/max-player-count]
      (app/join-game! store game-id))
    (is (= {:error :game-full}
           (app/join-game! store game-id)))))

(deftest join-game-rejects-unknown-game
  (let [store (app/create-store)]
    (is (= {:error :unknown-game}
           (app/join-game! store "missing" "anna")))
    (is (empty? (:games @store)))))

(deftest join-game-rejects-after-start
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        p1 (app/join-game! store game-id "anna")
        _p2 (app/join-game! store game-id "ben")]
    (app/start-game! store game-id (:player-id p1) {})
    (is (= {:error :game-already-started}
           (app/join-game! store game-id "late")))))

(deftest non-host-can-leave-before-start
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        p1 (app/join-game! store game-id "anna")
        p2 (app/join-game! store game-id "ben")
        p3 (app/join-game! store game-id "cody")]
    (is (= {:left? true :player-id (:player-id p2)}
           (app/leave-table! store game-id (:player-id p2))))
    (let [game (app/get-game store game-id)]
      (is (= [0 1] (mapv :seat (sort-by :seat (vals (:players game))))))
      (is (= 1 (get-in game [:players (:player-id p3) :seat])))
      (is (= 3 (:next-player-number game))))
    (let [p4 (app/join-game! store game-id "drew")]
      (is (= "game-0-player-3" (:player-id p4)))
      (is (= 2 (:seat p4)))
      (is (= 0 (get-in (app/get-game store game-id) [:players (:player-id p1) :seat]))))))

(deftest host-leaving-ends-the-table
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        p1 (app/join-game! store game-id "anna")
        _p2 (app/join-game! store game-id "ben")
        sink (atom [])]
    (app/subscribe! store game-id :test #(swap! sink conj %))
    (is (= {:ended? true}
           (app/leave-table! store game-id (:player-id p1))))
    (is (nil? (app/get-game store game-id)))
    (is (= [{:type :table-ended :game-id game-id}] @sink))
    (is (= :test (app/unsubscribe! store game-id :test)))))

(deftest start-game-use-case
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        p1 (app/join-game! store game-id "anna")
        p2 (app/join-game! store game-id "ben")]
    (testing "only the host can start"
      (is (= {:error :not-host}
             (app/start-game! store game-id (:player-id p2) {}))))
    (testing "host starts with a deterministic deck"
      (let [result (app/start-game! store game-id (:player-id p1)
                                    {:deck (app/valid-start-deck 2)})]
        (is (vector? (:events result)))
        (is (true? (:started-once? (app/get-game store game-id))))
        (is (= :in-progress
               (get-in (app/get-game store game-id) [:state :status])))))
    (testing "starting twice is rejected"
      (is (= {:error :invalid-start-game}
             (app/start-game! store game-id (:player-id p1) {}))))))

(deftest start-game-requires-two-players
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        p1 (app/join-game! store game-id "solo")]
    (is (= {:error :not-enough-players}
           (app/start-game! store game-id (:player-id p1) {})))))

(deftest start-game-rejects-unknown-game
  (let [store (app/create-store)]
    (is (= {:error :unknown-game}
           (app/start-game! store "missing" "nobody" {})))))

(deftest valid-start-deck-rejects-invalid-player-count
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"invalid player-count"
                        (app/valid-start-deck 11))))

(deftest submit-action-starts-game-and-stores-state
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        _p1 (app/join-game! store game-id)
        _p2 (app/join-game! store game-id)
        deck model/full-deck
        result (app/submit-action! store game-id {:type :start-game
                                                  :player-count 2
                                                  :deck deck})
        game (app/get-game store game-id)]
    (is (vector? (:events result)))
    (is (= :in-progress (get-in game [:state :status])))))

(def fixed-deck
  "Deterministic deck: seat 0 gets cards 0-4, seat 1 cards 5-9, card 10 is the
  first discard (queen of spades). Seat 0 can open with the queen of clubs;
  seat 1 holds the eight of diamonds."
  (vec (concat [(model/card :queen :clubs)
                (model/card :ace :diamonds)
                (model/card :two :clubs)
                (model/card :six :diamonds)
                (model/card :king :diamonds)
                (model/card :eight :diamonds)
                (model/card :two :hearts)
                (model/card :ace :clubs)
                (model/card :king :spades)
                (model/card :jack :clubs)
                (model/card :queen :spades)
                (model/card :four :clubs)]
               (drop 12 model/full-deck))))

(defn- started-pair
  "Store with a started two-player game using fixed-deck. Returns [store game-id p1 p2]."
  []
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        p1 (app/join-game! store game-id "anna")
        p2 (app/join-game! store game-id "ben")]
    (app/start-game! store game-id (:player-id p1) {:deck fixed-deck})
    [store game-id p1 p2]))

(deftest finished-table-can-start-a-new-game
  (let [[store game-id p1 p2] (started-pair)
        players-before (:players (app/get-game store game-id))
        finished-state (assoc (:state (app/get-game store game-id))
                              :status :finished
                              :winner 0)]
    (swap! store assoc-in [:games game-id :state] finished-state)
    (let [result (app/start-game! store game-id (:player-id p1) {:deck fixed-deck})
          game (app/get-game store game-id)]
      (is (vector? (:events result)))
      (is (= players-before (:players game)))
      (is (= :in-progress (get-in game [:state :status])))
      (is (= [(:player-id p1) (:player-id p2)]
             (sort (keys (:players game))))))))

(deftest non-host-leaving-after-finish-keeps-table-between-games
  (let [[store game-id _p1 p2] (started-pair)
        finished-state (assoc (:state (app/get-game store game-id))
                              :status :finished
                              :winner 0)]
    (swap! store assoc-in [:games game-id :state] finished-state)
    (is (= {:left? true :player-id (:player-id p2)}
           (app/leave-table! store game-id (:player-id p2))))
    (let [game (app/get-game store game-id)]
      (is (nil? (:state game)))
      (is (true? (:started-once? game)))
      (is (= 1 (count (:players game)))))))

(deftest players-cannot-leave-during-active-game
  (let [[store game-id _p1 p2] (started-pair)]
    (is (= {:error :game-in-progress}
           (app/leave-table! store game-id (:player-id p2))))
    (is (= 2 (count (:players (app/get-game store game-id)))))))

(deftest play-card-use-case
  (let [[store game-id p1 p2] (started-pair)]
    (testing "out-of-turn play is a normalized error"
      (is (= {:error :not-current-player}
             (app/play-card! store game-id (:player-id p2)
                             (model/card :king :spades) nil))))
    (testing "current player plays a matching card"
      (let [result (app/play-card! store game-id (:player-id p1)
                                   (model/card :queen :clubs) nil)]
        (is (= :card-played (:type (first (:events result)))))))))

(deftest play-eight-with-declared-suit
  (let [[store game-id p1 p2] (started-pair)]
    (app/play-card! store game-id (:player-id p1) (model/card :queen :clubs) nil)
    (let [result (app/play-card! store game-id (:player-id p2)
                                 (model/card :eight :diamonds) :hearts)]
      (is (= [:card-played :suit-declared :turn-advanced]
             (mapv :type (:events result))))
      (is (= :hearts (get-in (app/get-game store game-id) [:state :active-suit]))))))

(deftest draw-card-use-case
  (let [[store game-id p1 _p2] (started-pair)]
    (testing "drawing while a play is available is an error"
      (is (= {:error :must-play-before-drawing}
             (app/draw-card! store game-id (:player-id p1)))))))

(deftest draw-card-reshuffles-automatically
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        p1 (app/join-game! store game-id "anna")
        _p2 (app/join-game! store game-id "ben")
        ;; seat 0 holds an unplayable card, draw pile empty, reshuffle possible
        state {:players [{:hand [(model/card :three :clubs)]}
                         {:hand [(model/card :five :spades)]}]
               :draw-pile []
               :discard-pile [(model/card :queen :hearts) (model/card :nine :diamonds)]
               :active-suit :diamonds
               :current-player 0
               :status :in-progress
               :winner nil
               :passes-in-row 0}]
    (swap! store assoc-in [:games game-id :state] state)
    (let [result (app/draw-card! store game-id (:player-id p1))
          next-state (:state (app/get-game store game-id))]
      (is (= :card-drawn (:type (first (:events result)))))
      (is (= [(model/card :three :clubs) (model/card :queen :hearts)]
             (get-in next-state [:players 0 :hand])))
      (is (empty? (:draw-pile next-state)))
      (is (= [(model/card :nine :diamonds)] (:discard-pile next-state))))))

(deftest pass-turn-use-case
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        p1 (app/join-game! store game-id "anna")
        _p2 (app/join-game! store game-id "ben")
        ;; no playable card, no draw pile, no reshuffle: pass is the only move
        state {:players [{:hand [(model/card :three :clubs)]}
                         {:hand [(model/card :five :spades)]}]
               :draw-pile []
               :discard-pile [(model/card :nine :diamonds)]
               :active-suit :diamonds
               :current-player 0
               :status :in-progress
               :winner nil
               :passes-in-row 0}]
    (swap! store assoc-in [:games game-id :state] state)
    (let [result (app/pass-turn! store game-id (:player-id p1))]
      (is (= :turn-passed (:type (first (:events result)))))
      (is (= 1 (get-in (app/get-game store game-id) [:state :current-player]))))))

(deftest player-action-translates-player-id-to-seat
  (let [[store game-id p1 _p2] (started-pair)
        result (app/submit-player-action! store game-id (:player-id p1)
                                          {:type :play-card
                                           :card (model/card :queen :clubs)})]
    (is (= :card-played (:type (first (:events result)))))))

(deftest subscribers-receive-app-events-in-order
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        _p1 (app/join-game! store game-id)
        _p2 (app/join-game! store game-id)
        sink (atom [])]
    (app/subscribe! store game-id :test-subscriber #(swap! sink conj %))
    (app/submit-action! store game-id {:type :start-game
                                       :player-count 2
                                       :deck model/full-deck})
    (is (= [:game-started :turn-changed]
           (map :type @sink)))))

(deftest swap-result!-commits-state-and-returns-result
  (let [a (atom {:n 0})
        result (app/swap-result! a (fn [state]
                                     [(update state :n inc) {:was (:n state)}]))]
    (is (= {:n 1} @a))
    (is (= {:was 0} result))))

(deftest action-events-derives-the-event-stream
  (let [action-events #'app/action-events]
    (testing "start-game opens with :game-started then :turn-changed"
      (is (= [:game-started :turn-changed]
             (map :type (action-events {:type :start-game}
                                       {:status :in-progress :current-player 0}
                                       [{:type :game-started}])))))
    (testing "other commands report :move-made"
      (is (= [:move-made :turn-changed]
             (map :type (action-events {:type :play-card}
                                       {:status :in-progress :current-player 1}
                                       [{:type :card-played}])))))
    (testing "a finished state appends :game-finished with the winner"
      (let [evs (action-events {:type :play-card}
                               {:status :finished :winner 2 :current-player 2}
                               [{:type :card-played}])]
        (is (= [:move-made :turn-changed :game-finished] (map :type evs)))
        (is (= 2 (:winner (last evs))))))))
