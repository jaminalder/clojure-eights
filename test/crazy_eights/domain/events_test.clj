(ns crazy_eights.domain.events-test
  (:require [clojure.test :refer [deftest is]]
            [crazy_eights.domain.events :as events]
            [crazy_eights.domain.model :as model]))

(deftest event-constructors-preserve-event-language
  (let [card (model/card :queen :clubs)]
    (is (= {:type :card-played
            :player 0
            :card card}
           (events/card-played 0 card)))
    (is (= {:type :card-drawn
            :player 0
            :card card}
           (events/card-drawn 0 card)))
    (is (= {:type :turn-advanced
            :player 1}
           (events/turn-advanced 1)))
    (is (= {:type :game-won
            :player 0}
           (events/game-won 0)))
    (is (= {:type :game-blocked}
           (events/game-blocked)))))

(deftest unknown-events-leave-state-unchanged
  (let [state {:status :in-progress}]
    (is (= state
           (events/apply-event state {:type :future-event})))))
