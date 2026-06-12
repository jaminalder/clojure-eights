(ns crazy_eights.domain.commands
  (:require [crazy_eights.domain.model :as model]))

(defn- domain-error [reason]
  {:type :domain-error
   :reason reason})

(defn- deal-hands [player-count deck]
  (mapv (fn [index]
          (model/player (take model/cards-per-player
                              (drop (* index model/cards-per-player) deck))))
        (range player-count)))

(defn- remaining-deck [player-count deck]
  (drop (* player-count model/cards-per-player) deck))

(defn- start-game-events [{:keys [player-count deck]}]
  (let [remaining (vec (remaining-deck player-count deck))
        discard (first remaining)
        draw-pile (vec (rest remaining))]
    [{:type :game-started
      :players (deal-hands player-count deck)
      :draw-pile draw-pile
      :discard-pile [discard]
      :active-suit (:suit discard)
      :current-player 0
      :status :in-progress
      :winner nil
      :passes-in-row 0}]))

(defn- game-finished? [state]
  (= :finished (:status state)))

(defn- reject-if-finished [state]
  (when (game-finished? state)
    (domain-error :game-already-finished)))

(defn- next-player [state]
  (mod (inc (:current-player state))
       (count (:players state))))

(defn- current-hand [state player]
  (get-in state [:players player :hand]))

(defn- draw-card-events [state {:keys [player]}]
  (if-let [error (reject-if-finished state)]
    error
    (let [hand (current-hand state player)
          drawn-card (first (:draw-pile state))]
      (cond
        (not= player (:current-player state))
        (domain-error :not-current-player)

        (model/playable-hand? state hand)
        (domain-error :must-play-before-drawing)

        (nil? drawn-card)
        (domain-error :draw-pile-empty)

        :else
        [{:type :card-drawn
          :player player
          :card drawn-card}]))))

(defn- reshuffle-draw-pile-events [state {:keys [cards]}]
  (if-let [error (reject-if-finished state)]
    error
    (cond
      (not (model/reshuffleable? state))
      (domain-error :reshuffle-not-allowed)

      (not= (vec cards) (model/reshuffle-cards (:discard-pile state)))
      (domain-error :invalid-reshuffle-cards)

      :else
      [{:type :draw-pile-reshuffled
        :cards (vec cards)
        :top-card (model/top-discard (:discard-pile state))}])))

(defn- pass-turn-events [state {:keys [player]}]
  (if-let [error (reject-if-finished state)]
    error
    (let [hand (current-hand state player)
          next-player-index (next-player state)
          passes-after (inc (:passes-in-row state))]
      (cond
        (not= player (:current-player state))
        (domain-error :not-current-player)

        (model/playable-hand? state hand)
        (domain-error :cannot-pass-while-playable)

        (seq (:draw-pile state))
        (domain-error :must-play-before-drawing)

        (model/reshuffleable? state)
        (domain-error :must-reshuffle-before-passing)

        :else
        (cond-> [{:type :turn-passed
                  :player next-player-index}]
          (= passes-after (count (:players state)))
          (conj {:type :game-blocked}))))))

(defn- play-card-events [state {:keys [player card declared-suit]}]
  (if-let [error (reject-if-finished state)]
    error
    (let [hand (current-hand state player)
          remaining-hand (model/remove-card hand card)]
      (cond
        (not= player (:current-player state))
        (domain-error :not-current-player)

        (not (model/card-in-hand? hand card))
        (domain-error :card-not-in-hand)

        (not (model/playable-card? state card))
        (domain-error :card-not-playable)

        (and (model/requires-declared-suit? card)
             (not (model/valid-declared-suit? card declared-suit)))
        (domain-error :declared-suit-required)

        :else
        (cond-> [{:type :card-played
                  :player player
                  :card card}]
          (model/requires-declared-suit? card)
          (conj {:type :suit-declared
                 :suit declared-suit})

          (empty? remaining-hand)
          (conj {:type :game-won
                 :player player})

          (seq remaining-hand)
          (conj {:type :turn-advanced
                 :player (next-player state)}))))))

(defn decide [state command]
  (case (:type command)
    :start-game (if (and (nil? state)
                         (<= 2 (:player-count command) model/max-player-count)
                         (pos-int? (:player-count command))
                         (every? model/card? (:deck command))
                         (not= :eight (:rank (first (vec (remaining-deck (:player-count command)
                                                                       (:deck command))))))
                         (< (* (:player-count command) model/cards-per-player)
                            (count (:deck command))))
                  (start-game-events command)
                  (domain-error :invalid-start-game))
    :play-card (play-card-events state command)
    :draw-card (draw-card-events state command)
    :reshuffle-draw-pile (reshuffle-draw-pile-events state command)
    :pass-turn (pass-turn-events state command)
    (domain-error :unknown-command)))
