(ns crazy_eights.web.sse-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [crazy_eights.app.simulation :as simulation]
            [crazy_eights.web.sse :as sse]
            [org.httpkit.server :as http]))

(defn- recording-channel [sent]
  (reify http/Channel
    (open? [_] true)
    (close [_] true)
    (websocket? [_] false)
    (send! [_ data] (swap! sent conj data) true)
    (send! [_ data _close-after-send?] (swap! sent conj data) true)))

(deftest on-open-subscribes-channel-before-running-simulation
  (let [service (simulation/create-service {:delay-fn (fn [] nil)})
        {:keys [simulation-id]} (simulation/start! service 2)
        sent (atom [])
        channel (recording-channel sent)
        subscribers-at-run (atom nil)]
    (sse/on-open service simulation-id
                 (fn [id]
                   (reset! subscribers-at-run
                           (get-in @service [:simulations id :subscribers])))
                 channel)
    (is (contains? @subscribers-at-run channel)
        "simulation must start only after the SSE subscriber is attached")))

(deftest on-open-streams-open-event-and-simulation-logs
  (let [service (simulation/create-service {:delay-fn (fn [] nil)})
        {:keys [simulation-id]} (simulation/start! service 2)
        sent (atom [])
        channel (recording-channel sent)]
    (sse/on-open service simulation-id
                 #(simulation/run-to-completion! service %)
                 channel)
    (let [[open & log-frames] @sent]
      (is (= 200 (:status open)))
      (is (= "text/event-stream" (get-in open [:headers "Content-Type"])))
      (is (str/starts-with? (:body open) "event: open\n"))
      (is (seq log-frames))
      (is (every? #(str/starts-with? % "event: log\ndata: ") log-frames))
      (is (some #(str/includes? % "game finished") log-frames)))))
