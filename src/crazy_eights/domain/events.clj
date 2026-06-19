(ns crazy_eights.domain.events
  (:require [crazy_eights.domain.model :as model]))

(defn game-started [state]
  (assoc state :type :game-started))

(defn card-played [player card]
  {:type :card-played
   :player player
   :card card})

(defn suit-declared [suit]
  {:type :suit-declared
   :suit suit})

(defn card-drawn [player card]
  {:type :card-drawn
   :player player
   :card card})

(defn draw-pile-reshuffled [cards top-card]
  {:type :draw-pile-reshuffled
   :cards (vec cards)
   :top-card top-card})

(defn turn-advanced [player]
  {:type :turn-advanced
   :player player})

(defn turn-passed [player]
  {:type :turn-passed
   :player player})

(defn game-won [player]
  {:type :game-won
   :player player})

(defn game-blocked []
  {:type :game-blocked})

(defn- apply-game-started [_state event]
  (dissoc event :type))

(defn- apply-card-played [state {:keys [player card]}]
  (-> state
      (update-in [:players player :hand] model/remove-card card)
      (update :discard-pile conj card)
      (assoc :active-suit (:suit card)
             :passes-in-row 0)))

(defn- apply-suit-declared [state {:keys [suit]}]
  (assoc state :active-suit suit))

(defn- apply-card-drawn [state {:keys [player card]}]
  (-> state
      (update :draw-pile #(vec (rest %)))
      (update-in [:players player :hand] conj card)
      (assoc :passes-in-row 0)))

(defn- apply-draw-pile-reshuffled [state {:keys [cards top-card]}]
  (assoc state
         :draw-pile (vec cards)
         :discard-pile [top-card]))

(defn- apply-turn-advanced [state {:keys [player]}]
  (assoc state :current-player player))

(defn- apply-turn-passed [state {:keys [player]}]
  (-> state
      (update :passes-in-row inc)
      (assoc :current-player player)))

(defn- apply-game-won [state {:keys [player]}]
  (assoc state :status :finished
               :winner player))

(defn- apply-game-blocked [state]
  (assoc state :status :finished
               :winner nil))

(defn apply-event [state event]
  (case (:type event)
    :game-started (apply-game-started state event)
    :card-played (apply-card-played state event)
    :suit-declared (apply-suit-declared state event)
    :card-drawn (apply-card-drawn state event)
    :draw-pile-reshuffled (apply-draw-pile-reshuffled state event)
    :turn-advanced (apply-turn-advanced state event)
    :turn-passed (apply-turn-passed state event)
    :game-won (apply-game-won state event)
    :game-blocked (apply-game-blocked state)
    state))

(defn apply-events [state events]
  (reduce apply-event state events))
