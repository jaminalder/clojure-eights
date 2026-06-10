(ns crazy_eights.domain.commands
  (:require [crazy_eights.domain.model :as model]
            [crazy_eights.domain.rules :as rules]))

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

(defn- play-card-events [state {:keys [player card declared-suit]}]
  (let [current-hand (get-in state [:players player :hand])]
    (cond
      (not= player (:current-player state))
      (domain-error :not-current-player)

      (not (rules/card-in-hand? current-hand card))
      (domain-error :card-not-in-hand)

      (not (rules/playable-card? state card))
      (domain-error :card-not-playable)

      (and (rules/requires-declared-suit? card)
           (not (rules/valid-declared-suit? card declared-suit)))
      (domain-error :declared-suit-required)

      :else
      (cond-> [{:type :card-played
                :player player
                :card card}]
        (rules/requires-declared-suit? card)
        (conj {:type :suit-declared
               :suit declared-suit})

        (empty? (remove #(= % card) current-hand))
        (conj {:type :game-won
               :player player})

        (not (empty? (remove #(= % card) current-hand)))
        (conj {:type :turn-advanced
               :player (next-player state)})))))

(defn decide [state command]
  (case (:type command)
    :start-game (if (and (nil? state)
                         (pos-int? (:player-count command))
                         (every? rules/card? (:deck command))
                         (< (* (:player-count command) cards-per-player)
                            (count (:deck command))))
                  (start-game-events command)
                  (domain-error :invalid-start-game))
    :play-card (play-card-events state command)
    (domain-error :unknown-command)))
