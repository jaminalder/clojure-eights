(ns crazy_eights.domain.events)

(defmulti apply-event (fn [_state event] (:type event)))

(defmethod apply-event :game-started [_state event]
  (dissoc event :type))

(defmethod apply-event :default [state _event]
  state)

(defn apply-events [state events]
  (reduce apply-event state events))
