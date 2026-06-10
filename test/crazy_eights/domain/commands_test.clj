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

(deftest play-card-emits-card-events
  (let [state {:players [(model/player [(model/card :queen :clubs)
                                        (model/card :eight :spades)])
                        (model/player [(model/card :ace :hearts)])]
               :draw-pile [(model/card :two :diamonds)]
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil}
        events (commands/decide state {:type :play-card
                                       :player 0
                                       :card (model/card :queen :clubs)})]
    (is (= [{:type :card-played
             :player 0
             :card (model/card :queen :clubs)}
            {:type :turn-advanced
             :player 1}]
           events))))

(deftest playing-an-eight-requires-declared-suit
  (let [state {:players [(model/player [(model/card :eight :clubs)])
                        (model/player [(model/card :ace :hearts)])]
               :draw-pile []
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil}]
    (is (= {:type :domain-error :reason :declared-suit-required}
           (commands/decide state {:type :play-card
                                   :player 0
                                   :card (model/card :eight :clubs)})))))

(deftest draw-card-emits-draw-and-turn-events
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
                                       :player 0})]
    (is (= [{:type :card-drawn
             :player 0
             :card (model/card :two :diamonds)}
            {:type :turn-advanced
             :player 1}]
           events))))

(deftest must-play-before-drawing
  (let [state {:players [(model/player [(model/card :queen :clubs)])
                        (model/player [(model/card :ace :spades)])]
               :draw-pile [(model/card :two :diamonds)]
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil}]
    (is (= {:type :domain-error
            :reason :must-play-before-drawing}
           (commands/decide state {:type :draw-card
                                   :player 0})))))

(deftest draw-card-fails-when-pile-empty
  (let [state {:players [(model/player [(model/card :ace :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile []
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil}]
    (is (= {:type :domain-error
            :reason :draw-pile-empty}
           (commands/decide state {:type :draw-card
                                   :player 0})))))

(deftest draw-card-fails-for-non-current-player
  (let [state {:players [(model/player [(model/card :ace :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile [(model/card :two :diamonds)]
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil}]
    (is (= {:type :domain-error
            :reason :not-current-player}
           (commands/decide state {:type :draw-card
                                   :player 1})))))
