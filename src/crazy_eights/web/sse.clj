(ns crazy_eights.web.sse
  (:require [clojure.string :as str]
            [crazy_eights.app.core :as app]
            [org.httpkit.server :as http]))

(def ^:private open-response
  {:status 200
   :headers {"Content-Type" "text/event-stream"
             "Cache-Control" "no-cache"
             "Connection" "keep-alive"}
   :body "event: open\ndata: {\"status\":\"connected\"}\n\n"})

(defn fragment-frames
  "One SSE message per fragment: {:status html ...} becomes
  'event: status\\ndata: <html>\\n\\n...'. Data is forced onto a single line."
  [fragments]
  (apply str
         (map (fn [[fragment-name html]]
                (str "event: " (name fragment-name) "\n"
                     "data: " (str/replace html "\n" "") "\n\n"))
              fragments)))

(defn table-ended-frame []
  (str "event: table-ended\n"
       "data: <script>window.location.href='/'</script>\n\n"))

(defn- game-event-frame [render-fragments event]
  (if (= :table-ended (:type event))
    (table-ended-frame)
    (fragment-frames (render-fragments))))

(defn game-on-open
  "Subscribes the channel to app events before anything can move, then sends
  the current fragments so no transition between page render and connect is lost."
  [store game-id render-fragments channel]
  (http/send! channel open-response false)
  (app/subscribe! store game-id channel
                  (fn [event]
                    (http/send! channel (game-event-frame render-fragments event) false)))
  (http/send! channel (fragment-frames (render-fragments)) false))

(defn game-events-response [store game-id render-fragments request]
  (http/as-channel request
                   {:on-open (partial game-on-open store game-id render-fragments)
                    :on-close (fn [channel _status]
                                (app/unsubscribe! store game-id channel))}))
