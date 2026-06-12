(ns crazy_eights.web.sse
  (:require [clojure.data.json :as json]
            [crazy_eights.app.simulation :as simulation]
            [org.httpkit.server :as http]))

(def ^:private open-response
  {:status 200
   :headers {"Content-Type" "text/event-stream"
             "Cache-Control" "no-cache"
             "Connection" "keep-alive"}
   :body "event: open\ndata: {\"status\":\"connected\"}\n\n"})

(defn- log-event->frame [event]
  (str "event: log\n"
       "data: " (json/write-str {:message (:message event)})
       "\n\n"))

(defn on-open [service simulation-id run-simulation! channel]
  (http/send! channel open-response false)
  (simulation/subscribe! service simulation-id channel
                         (fn [event]
                           (http/send! channel (log-event->frame event) false)))
  (run-simulation! simulation-id))

(defn sse-response [service simulation-id request run-simulation!]
  (http/as-channel request
                   {:on-open (partial on-open service simulation-id run-simulation!)
                    :on-close (fn [channel _status]
                                (simulation/unsubscribe! service simulation-id channel))}))
