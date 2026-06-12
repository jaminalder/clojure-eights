(ns crazy_eights.web.sse
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [crazy_eights.app.core :as app]
            [crazy_eights.app.simulation :as simulation]
            [org.httpkit.server :as http]))

(def ^:private open-response
  {:status 200
   :headers {"Content-Type" "text/event-stream"
             "Cache-Control" "no-cache"
             "Connection" "keep-alive"}
   :body "event: open\ndata: {\"status\":\"connected\"}\n\n"})

;; simulation observer stream

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

;; game fragment stream

(defn fragment-frames
  "One SSE message per fragment: {:status html ...} becomes
  'event: status\\ndata: <html>\\n\\n...'. Data is forced onto a single line."
  [fragments]
  (apply str
         (map (fn [[fragment-name html]]
                (str "event: " (name fragment-name) "\n"
                     "data: " (str/replace html "\n" "") "\n\n"))
              fragments)))

(defn game-on-open
  "Subscribes the channel to app events before anything can move, then sends
  the current fragments so no transition between page render and connect is lost."
  [store game-id render-fragments channel]
  (http/send! channel open-response false)
  (app/subscribe! store game-id channel
                  (fn [_event]
                    (http/send! channel (fragment-frames (render-fragments)) false)))
  (http/send! channel (fragment-frames (render-fragments)) false))

(defn game-events-response [store game-id render-fragments request]
  (http/as-channel request
                   {:on-open (partial game-on-open store game-id render-fragments)
                    :on-close (fn [channel _status]
                                (app/unsubscribe! store game-id channel))}))
