(ns crazy_eights.app.logging)

(defn event->log-entry [event]
  {:level :info
   :event (:type event)
   :game-id (:game-id event)
   :data event})

(defn stdout-subscriber []
  (fn [event]
    (prn (event->log-entry event))))
