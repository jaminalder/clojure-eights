(ns crazy_eights.domain.invariants-test
  (:require [clojure.test :refer [deftest is]]
            [crazy_eights.domain.commands :as commands]
            [crazy_eights.domain.events :as events]
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

(deftest played-card-state-preserves-invariants
  (let [state {:players [(model/player [(model/card :queen :clubs)
                                        (model/card :ace :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile [(model/card :two :diamonds)]
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil}
        events (commands/decide state {:type :play-card
                                       :player 0
                                       :card (model/card :queen :clubs)})
        next-state (events/apply-events state events)]
    (is (= :card-played (:type (first events))))
    (is (empty? (invariants/check next-state)))))

(deftest drawn-card-state-preserves-invariants
  (let [state {:players [(model/player [(model/card :ace :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile [(model/card :two :diamonds)
                           (model/card :three :diamonds)]
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil}
        events (commands/decide state {:type :draw-card
                                       :player 0})
        next-state (events/apply-events state events)]
    (is (= :card-drawn (:type (first events))))
    (is (empty? (invariants/check next-state)))))

(deftest passed-turn-state-preserves-invariants
  (let [state {:players [(model/player [(model/card :ace :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile []
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil
               :passes-in-row 0}
        events (commands/decide state {:type :pass-turn
                                       :player 0})
        next-state (events/apply-events state events)]
    (is (= :turn-passed (:type (first events))))
    (is (empty? (invariants/check next-state)))))

(deftest blocked-game-state-preserves-invariants
  (let [state {:players [(model/player [(model/card :ace :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile []
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 1
               :status :in-progress
               :winner nil
               :passes-in-row 1}
        events (commands/decide state {:type :pass-turn
                                       :player 1})
        next-state (events/apply-events state events)]
    (is (= :game-blocked (:type (last events))))
    (is (empty? (invariants/check next-state)))))
