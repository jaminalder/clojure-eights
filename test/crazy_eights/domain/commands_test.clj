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

(deftest draw-card-emits-draw-event
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
             :card (model/card :two :diamonds)}]
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

(deftest start-game-initializes-pass-counter
  (let [events (commands/decide nil {:type :start-game
                                     :player-count 2
                                     :deck ordered-deck})
        state (events/apply-events nil events)]
    (is (= 0 (:passes-in-row state)))))

(deftest draw-card-keeps-same-player-turn
  (let [state {:players [(model/player [(model/card :ace :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile [(model/card :two :diamonds)]
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil
               :passes-in-row 0}
        events (commands/decide state {:type :draw-card
                                       :player 0})
        state-after-events (events/apply-events state events)]
    (is (= 0 (:current-player state-after-events)))
    (is (= 0 (:passes-in-row state-after-events)))))

(deftest reshuffle-draw-pile-requires-exact-discard-tail
  (let [state {:players [(model/player [(model/card :ace :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile []
               :discard-pile [(model/card :queen :hearts)
                              (model/card :ace :diamonds)
                              (model/card :two :spades)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil
               :passes-in-row 0}]
    (is (= [{:type :draw-pile-reshuffled
             :cards [(model/card :queen :hearts)
                     (model/card :ace :diamonds)]
             :top-card (model/card :two :spades)}]
           (commands/decide state {:type :reshuffle-draw-pile
                                   :cards [(model/card :queen :hearts)
                                           (model/card :ace :diamonds)]})))
    (is (= {:type :domain-error
            :reason :invalid-reshuffle-cards}
           (commands/decide state {:type :reshuffle-draw-pile
                                   :cards [(model/card :two :spades)]})))) )

(deftest must-reshuffle-before-passing
  (let [state {:players [(model/player [(model/card :ace :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile []
               :discard-pile [(model/card :queen :hearts)
                              (model/card :two :diamonds)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil
               :passes-in-row 0}]
    (is (= {:type :domain-error
            :reason :must-reshuffle-before-passing}
           (commands/decide state {:type :pass-turn
                                   :player 0})))))

(deftest pass-turn-can-block-the-game
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
                                       :player 1})]
    (is (= [{:type :turn-passed
             :player 0}
            {:type :game-blocked}]
           events))))

(deftest finished-games-reject-further-commands
  (let [finished-state {:players [(model/player [(model/card :queen :clubs)])
                                (model/player [(model/card :king :spades)])]
                        :draw-pile [(model/card :two :diamonds)]
                        :discard-pile [(model/card :queen :hearts)]
                        :active-suit :hearts
                        :current-player 0
                        :status :finished
                        :winner 1
                        :passes-in-row 0}]
    (is (= {:type :domain-error
            :reason :game-already-finished}
           (commands/decide finished-state {:type :play-card
                                            :player 0
                                            :card (model/card :queen :clubs)})))
    (is (= {:type :domain-error
            :reason :game-already-finished}
           (commands/decide finished-state {:type :draw-card
                                            :player 0})))
    (is (= {:type :domain-error
            :reason :game-already-finished}
           (commands/decide finished-state {:type :pass-turn
                                            :player 0})))
    (is (= {:type :domain-error
            :reason :game-already-finished}
           (commands/decide finished-state {:type :reshuffle-draw-pile
                                            :cards []})))))

(deftest playing-one-card-removes-only-one-equal-card
  (let [state {:players [(model/player [(model/card :queen :clubs)
                                        (model/card :queen :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile []
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil
               :passes-in-row 0}
        events (commands/decide state {:type :play-card
                                       :player 0
                                       :card (model/card :queen :clubs)})
        next-state (events/apply-events state events)]
    (is (= [{:type :card-played
             :player 0
             :card (model/card :queen :clubs)}
            {:type :turn-advanced
             :player 1}]
           events))
    (is (= [(model/card :queen :clubs)]
           (get-in next-state [:players 0 :hand])))))

(deftest playing-card-not-first-in-hand-keeps-remaining-cards
  (let [state {:players [(model/player [(model/card :ace :diamonds)
                                        (model/card :queen :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile []
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil
               :passes-in-row 0}
        events (commands/decide state {:type :play-card
                                       :player 0
                                       :card (model/card :queen :clubs)})
        next-state (events/apply-events state events)]
    (is (= [{:type :card-played
             :player 0
             :card (model/card :queen :clubs)}
            {:type :turn-advanced
             :player 1}]
           events))
    (is (= [(model/card :ace :diamonds)]
           (get-in next-state [:players 0 :hand])))))

(deftest start-game-rejects-eight-as-initial-discard
  (let [deck [(model/card :ace :clubs)
              (model/card :two :clubs)
              (model/card :three :clubs)
              (model/card :four :clubs)
              (model/card :five :clubs)
              (model/card :six :clubs)
              (model/card :seven :clubs)
              (model/card :eight :clubs)
              (model/card :nine :clubs)
              (model/card :ten :clubs)
              (model/card :eight :diamonds)
              (model/card :queen :spades)]
        command {:type :start-game
                 :player-count 2
                 :deck deck}]
    (is (= {:type :domain-error
            :reason :invalid-start-game}
           (commands/decide nil command)))))

(deftest start-game-rejects-more-than-ten-players
  (is (= {:type :domain-error
          :reason :invalid-start-game}
         (commands/decide nil {:type :start-game
                               :player-count 11
                               :deck model/full-deck}))))
