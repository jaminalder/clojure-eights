(ns crazy_eights.domain.commands
  (:require [crazy_eights.domain.events :as events]
            [crazy_eights.domain.model :as model]))

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
    [(events/game-started
      {:players (deal-hands player-count deck)
       :draw-pile draw-pile
       :discard-pile [discard]
       :active-suit (:suit discard)
       :current-player 0
       :status :in-progress
       :winner nil
       :passes-in-row 0})]))

(defn- valid-player-count? [player-count]
  (and (pos-int? player-count)
       (<= 2 player-count model/max-player-count)))

(defn- enough-cards-to-start? [player-count deck]
  (< (* player-count model/cards-per-player)
     (count deck)))

(defn- opening-card [player-count deck]
  (first (remaining-deck player-count deck)))

(defn- valid-opening-card? [player-count deck]
  (not= :eight (:rank (opening-card player-count deck))))

(defn- valid-start-game? [state {:keys [player-count deck]}]
  (and (nil? state)
       (valid-player-count? player-count)
       (every? model/card? deck)
       (enough-cards-to-start? player-count deck)
       (valid-opening-card? player-count deck)))

(defn- reject-if-finished [state]
  (when (model/game-over? state)
    (domain-error :game-already-finished)))

(defn- draw-card-events [state {:keys [player]}]
  (if-let [error (reject-if-finished state)]
    error
    (let [hand (model/current-hand state player)
          drawn-card (first (:draw-pile state))]
      (cond
        (not (model/current-player? state player))
        (domain-error :not-current-player)

        (model/playable-hand? state hand)
        (domain-error :must-play-before-drawing)

        (nil? drawn-card)
        (domain-error :draw-pile-empty)

        :else
        [(events/card-drawn player drawn-card)]))))

(defn- reshuffle-draw-pile-events [state {:keys [cards]}]
  (if-let [error (reject-if-finished state)]
    error
    (cond
      (not (model/reshuffleable? state))
      (domain-error :reshuffle-not-allowed)

      (not= (vec cards) (model/reshuffle-cards (:discard-pile state)))
      (domain-error :invalid-reshuffle-cards)

      :else
      [(events/draw-pile-reshuffled cards
                                    (model/top-discard (:discard-pile state)))])))

(defn- pass-turn-events [state {:keys [player]}]
  (if-let [error (reject-if-finished state)]
    error
    (let [hand (model/current-hand state player)
          next-player-index (model/next-player state)
          passes-after (inc (:passes-in-row state))]
      (cond
        (not (model/current-player? state player))
        (domain-error :not-current-player)

        (model/playable-hand? state hand)
        (domain-error :cannot-pass-while-playable)

        (seq (:draw-pile state))
        (domain-error :must-play-before-drawing)

        (model/reshuffleable? state)
        (domain-error :must-reshuffle-before-passing)

        :else
        (cond-> [(events/turn-passed next-player-index)]
          (= passes-after (count (:players state)))
          (conj (events/game-blocked)))))))

(defn- play-card-events [state {:keys [player card declared-suit]}]
  (if-let [error (reject-if-finished state)]
    error
    (let [hand (model/current-hand state player)
          remaining-hand (model/remove-card hand card)]
      (cond
        (not (model/current-player? state player))
        (domain-error :not-current-player)

        (not (model/card-in-hand? hand card))
        (domain-error :card-not-in-hand)

        (not (model/playable-card? state card))
        (domain-error :card-not-playable)

        (and (model/requires-declared-suit? card)
             (not (model/valid-declared-suit? card declared-suit)))
        (domain-error :declared-suit-required)

        :else
        (cond-> [(events/card-played player card)]
          (model/requires-declared-suit? card)
          (conj (events/suit-declared declared-suit))

          (empty? remaining-hand)
          (conj (events/game-won player))

          (seq remaining-hand)
          (conj (events/turn-advanced (model/next-player state))))))))

(defn decide [state command]
  (case (:type command)
    :start-game (if (valid-start-game? state command)
                  (start-game-events command)
                  (domain-error :invalid-start-game))
    :play-card (play-card-events state command)
    :draw-card (draw-card-events state command)
    :reshuffle-draw-pile (reshuffle-draw-pile-events state command)
    :pass-turn (pass-turn-events state command)
    (domain-error :unknown-command)))
