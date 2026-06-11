(ns crazy_eights.app.pubsub)

(defn subscribe [subscribers subscriber-id handler]
  (assoc subscribers subscriber-id handler))

(defn unsubscribe [subscribers subscriber-id]
  (dissoc subscribers subscriber-id))

(defn publish [subscribers event]
  (doseq [[_ handler] subscribers]
    (handler event))
  subscribers)
