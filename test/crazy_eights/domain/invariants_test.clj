(ns crazy_eights.domain.invariants-test
  (:require [clojure.test :refer [deftest is]]
            [crazy_eights.domain.invariants :as invariants]
            [crazy_eights.domain.model :as model]))

(deftest valid-state-passes-invariants
  (let [state {:players [(model/player [(model/card :ace :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile [(model/card :three :diamonds)]
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil}]
    (is (empty? (invariants/check state)))))

(deftest winner-must-have-empty-hand
  (let [state {:players [(model/player [(model/card :ace :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile []
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 1
               :status :finished
               :winner 0}]
    (is (= [{:reason :winner-hand-not-empty}]
           (invariants/check state)))))
