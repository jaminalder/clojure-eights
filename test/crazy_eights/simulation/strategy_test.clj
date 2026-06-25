(ns crazy_eights.simulation.strategy-test
  (:require [clojure.test :refer [deftest is testing]]
            [crazy_eights.domain.model :as model]
            [crazy_eights.simulation.strategy :as strategy]))

(defn- state-with-hand [hand]
  {:players [{:hand hand}
              {:hand [(model/card :five :spades)]}]
   :draw-pile [(model/card :king :hearts)]
   :discard-pile [(model/card :nine :diamonds)]
   :active-suit :diamonds
   :current-player 0
   :status :in-progress
    :winner nil
    :passes-in-row 0})

(deftest observation-hides-opponent-hands
  (let [state (assoc (state-with-hand [(model/card :three :clubs)
                                       (model/card :nine :hearts)])
                     :players [{:hand [(model/card :three :clubs)
                                       (model/card :nine :hearts)]}
                               {:hand [(model/card :ace :spades)
                                       (model/card :king :spades)
                                       (model/card :queen :spades)]}])]
    (is (= {:player 0
            :hand [(model/card :three :clubs)
                   (model/card :nine :hearts)]
            :top-card (model/card :nine :diamonds)
            :active-suit :diamonds
            :draw-count 1
            :discard-count 1
            :other-card-counts [3]
            :passes-in-row 0
            :status :in-progress}
           (strategy/observation state)))))

(deftest legal-actions-include-playable-cards-and-each-eight-declaration
  (let [obs (strategy/observation
             (state-with-hand [(model/card :nine :hearts)
                               (model/card :eight :clubs)
                               (model/card :three :clubs)]))]
    (is (= [{:type :play-card
             :card (model/card :nine :hearts)
             :resulting-hand [(model/card :eight :clubs)
                              (model/card :three :clubs)]
             :resulting-active-suit :hearts}
            {:type :play-card
             :card (model/card :eight :clubs)
             :declared-suit :clubs
             :resulting-hand [(model/card :nine :hearts)
                              (model/card :three :clubs)]
             :resulting-active-suit :clubs}
            {:type :play-card
             :card (model/card :eight :clubs)
             :declared-suit :diamonds
             :resulting-hand [(model/card :nine :hearts)
                              (model/card :three :clubs)]
             :resulting-active-suit :diamonds}
            {:type :play-card
             :card (model/card :eight :clubs)
             :declared-suit :hearts
             :resulting-hand [(model/card :nine :hearts)
                              (model/card :three :clubs)]
             :resulting-active-suit :hearts}
            {:type :play-card
             :card (model/card :eight :clubs)
             :declared-suit :spades
             :resulting-hand [(model/card :nine :hearts)
                              (model/card :three :clubs)]
             :resulting-active-suit :spades}]
           (strategy/legal-actions obs)))))

(deftest legal-actions-draw-or-pass-when-no-card-is-playable
  (let [drawable (strategy/observation (state-with-hand [(model/card :three :clubs)]))
        blocked (strategy/observation (assoc (state-with-hand [(model/card :three :clubs)])
                                             :draw-pile []
                                             :discard-pile [(model/card :nine :diamonds)]))]
    (is (= [{:type :draw-card}]
           (strategy/legal-actions drawable)))
    (is (= [{:type :pass-turn}]
           (strategy/legal-actions blocked)))))

(deftest action-removes-candidate-only-keys
  (is (= {:type :play-card
          :card (model/card :eight :clubs)
          :declared-suit :spades}
         (strategy/action {:type :play-card
                           :card (model/card :eight :clubs)
                           :declared-suit :spades
                           :resulting-hand []
                           :resulting-active-suit :spades}))))

(deftest choose-action-plays-first-playable-card
  (let [state (state-with-hand [(model/card :three :clubs)
                                 (model/card :nine :hearts)
                                 (model/card :eight :clubs)])]
    (is (= {:type :play-card
            :card (model/card :nine :hearts)}
           (strategy/choose-action state)))))

(deftest first-playable-is-the-default-strategy-function
  (let [state (state-with-hand [(model/card :nine :hearts)])]
    (is (= (strategy/choose-action state)
           ((:choose strategy/first-playable) (strategy/observation state))))))

(deftest composed-strategy-chooses-highest-scored-action
  (let [choose (:choose (strategy/from-rules :test [(fn [_ candidate]
                                                     (if (= (model/card :nine :hearts)
                                                            (:card candidate))
                                                       10
                                                       0))]))
        obs (strategy/observation
             (state-with-hand [(model/card :nine :clubs)
                               (model/card :nine :hearts)]))]
    (is (= {:type :play-card
            :card (model/card :nine :hearts)}
           (choose obs)))))

(deftest careful-avoids-eights-when-non-eight-play-is-available
  (let [obs (strategy/observation
             (state-with-hand [(model/card :eight :clubs)
                               (model/card :nine :hearts)]))]
    (is (= {:type :play-card
            :card (model/card :nine :hearts)}
           ((:choose strategy/careful) obs)))))

(deftest careful-declares-the-suit-most-held-after-playing-eight
  (let [obs (strategy/observation
             (state-with-hand [(model/card :eight :clubs)
                               (model/card :three :spades)
                               (model/card :four :spades)
                               (model/card :king :hearts)]))]
    (is (= {:type :play-card
            :card (model/card :eight :clubs)
            :declared-suit :spades}
           ((:choose strategy/careful) obs)))))

(deftest careful-prefers-rank-switch-to-a-suit-held-in-hand
  (let [obs (strategy/observation
             (state-with-hand [(model/card :nine :clubs)
                               (model/card :queen :diamonds)
                               (model/card :three :clubs)]))]
    (is (= {:type :play-card
            :card (model/card :nine :clubs)}
           ((:choose strategy/careful) obs)))))

(deftest choose-action-declares-spades-for-eights
  (let [state (state-with-hand [(model/card :eight :clubs)])]
    (is (= {:type :play-card
            :card (model/card :eight :clubs)
            :declared-suit :spades}
           (strategy/choose-action state)))))

(deftest choose-action-draws-when-no-card-is-playable
  (let [state (state-with-hand [(model/card :three :clubs)])]
    (is (= {:type :draw-card}
           (strategy/choose-action state)))))

(deftest choose-action-passes-when-no-card-and-no-draw-is-available
  (let [state (assoc (state-with-hand [(model/card :three :clubs)])
                     :draw-pile []
                     :discard-pile [(model/card :nine :diamonds)])]
    (testing "single discard means no reshuffle is possible"
      (is (= {:type :pass-turn}
             (strategy/choose-action state))))))
