(ns crazy_eights.domain.events)

(defmulti apply-event (fn [_state event] (:type event)))

(defmethod apply-event :game-started [_state event]
  (dissoc event :type))

(defmethod apply-event :card-played [state {:keys [player card]}]
  (-> state
      (update-in [:players player :hand]
                 (fn [hand]
                   (vec (remove #(= % card) hand))))
      (update :discard-pile conj card)
      (assoc :active-suit (:suit card)
             :passes-in-row 0)))

(defmethod apply-event :suit-declared [state {:keys [suit]}]
  (assoc state :active-suit suit))

(defmethod apply-event :card-drawn [state {:keys [player card]}]
  (-> state
      (update :draw-pile #(vec (rest %)))
      (update-in [:players player :hand] conj card)
      (assoc :passes-in-row 0)))

(defmethod apply-event :draw-pile-reshuffled [state {:keys [cards top-card]}]
  (assoc state
         :draw-pile (vec cards)
         :discard-pile [top-card]))

(defmethod apply-event :turn-advanced [state {:keys [player]}]
  (assoc state :current-player player))

(defmethod apply-event :turn-passed [state {:keys [player]}]
  (-> state
      (update :passes-in-row inc)
      (assoc :current-player player)))

(defmethod apply-event :game-won [state {:keys [player]}]
  (assoc state :status :finished
               :winner player))

(defmethod apply-event :game-blocked [state _event]
  (assoc state :status :finished
               :winner nil))

(defmethod apply-event :default [state _event]
  state)

(defn apply-events [state events]
  (reduce apply-event state events))
