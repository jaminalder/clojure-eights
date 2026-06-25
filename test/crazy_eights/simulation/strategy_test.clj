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
           (strategy/first-playable state)))))

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
