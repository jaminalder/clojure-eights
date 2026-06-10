(ns crazy_eights.domain.commands
  (:require [crazy_eights.domain.model :as model]))

(def cards-per-player 5)

(defn- domain-error [reason]
  {:type :domain-error
   :reason reason})

(defn- deal-hands [player-count deck]
  (mapv (fn [index]
          (model/player (take cards-per-player (drop (* index cards-per-player) deck))))
        (range player-count)))

(defn- remaining-deck [player-count deck]
  (drop (* player-count cards-per-player) deck))

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
      :winner nil}]))

(defn- next-player [state]
  (mod (inc (:current-player state))
       (count (:players state))))

(defn- current-hand [state player]
  (get-in state [:players player :hand]))

(defn- any-playable-card? [state hand]
  (some #(model/playable-card? state %) hand))

(defn- draw-card-events [state {:keys [player]}]
  (let [hand (current-hand state player)
        drawn-card (first (:draw-pile state))]
    (cond
      (not= player (:current-player state))
      (domain-error :not-current-player)

      (any-playable-card? state hand)
      (domain-error :must-play-before-drawing)

      (nil? drawn-card)
      (domain-error :draw-pile-empty)

      :else
      [{:type :card-drawn
        :player player
        :card drawn-card}
       {:type :turn-advanced
        :player (next-player state)}])))

(defn- play-card-events [state {:keys [player card declared-suit]}]
  (let [hand (current-hand state player)]
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

        (empty? (remove #(= % card) hand))
        (conj {:type :game-won
               :player player})

        (seq (remove #(= % card) hand))
        (conj {:type :turn-advanced
               :player (next-player state)})))))

(defn decide [state command]
  (case (:type command)
    :start-game (if (and (nil? state)
                         (pos-int? (:player-count command))
                         (every? model/card? (:deck command))
                         (< (* (:player-count command) cards-per-player)
                            (count (:deck command))))
                  (start-game-events command)
                  (domain-error :invalid-start-game))
    :play-card (play-card-events state command)
    :draw-card (draw-card-events state command)
    (domain-error :unknown-command)))
