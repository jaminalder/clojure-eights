(ns crazy_eights.domain.commands-test
  (:require [clojure.test :refer [deftest is testing]]
            [crazy_eights.domain.commands :as commands]
            [crazy_eights.domain.events :as events]
            [crazy_eights.domain.model :as model]))

(def ordered-deck
  [(model/card :ace :clubs)
   (model/card :two :clubs)
   (model/card :three :clubs)
   (model/card :four :clubs)
   (model/card :five :clubs)
   (model/card :six :clubs)
   (model/card :seven :clubs)
   (model/card :eight :clubs)
   (model/card :nine :clubs)
   (model/card :ten :clubs)
   (model/card :jack :clubs)
   (model/card :queen :clubs)
   (model/card :king :clubs)
   (model/card :ace :diamonds)
   (model/card :two :diamonds)])

(deftest start-game-emits-initial-state-event
  (let [events (commands/decide nil {:type :start-game
                                     :player-count 2
                                     :deck ordered-deck})
        state (events/apply-events nil events)]
    (testing "event shape"
      (is (= :game-started (:type (first events)))))
    (testing "resulting state"
      (is (= :in-progress (:status state)))
      (is (= 2 (count (:players state))))
      (is (= :clubs (:active-suit state))))))
