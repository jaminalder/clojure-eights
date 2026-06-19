(ns crazy_eights.web.sse-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [crazy_eights.app.core :as app]
            [crazy_eights.web.sse :as sse]
            [org.httpkit.server :as http]))

(defn- recording-channel [sent]
  (reify http/Channel
    (open? [_] true)
    (close [_] true)
    (websocket? [_] false)
    (send! [_ data] (swap! sent conj data) true)
    (send! [_ data _close-after-send?] (swap! sent conj data) true)))

(deftest game-on-open-sends-fragments-now-and-on-app-events
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        _p1 (app/join-game! store game-id "anna")
        sent (atom [])
        channel (recording-channel sent)
        render (fn [] {:status "<p>s</p>"
                       :game-board "<div>b</div>"
                       :player-hand "<div>h</div>"})]
    (sse/game-on-open store game-id render channel)
    (testing "open response plus an immediate fragment frame"
      (let [[open frame] @sent]
        (is (= 200 (:status open)))
        (is (str/includes? frame "event: status\ndata: <p>s</p>"))
        (is (str/includes? frame "event: game-board\ndata: <div>b</div>"))
        (is (str/includes? frame "event: player-hand\ndata: <div>h</div>"))))
    (testing "app events push fresh fragments"
      (let [frames-before (count @sent)]
        (app/join-game! store game-id "ben")
        (is (= (inc frames-before) (count @sent)))))
    (testing "fragment frames never contain stray newlines in data"
      (is (str/includes?
           (sse/fragment-frames {:status "multi\nline"})
           "data: multiline")))))

(deftest table-ended-event-redirects-and-closes-game-stream
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        p1 (app/join-game! store game-id "anna")
        sent (atom [])
        channel (recording-channel sent)
        render (fn [] {:status "<p>s</p>"})]
    (sse/game-on-open store game-id render channel)
    (app/leave-table! store game-id (:player-id p1))
    (is (some #(and (string? %)
                    (str/includes? % "event: table-ended")
                    (str/includes? % "window.location.href='/'"))
              @sent))
    (is (str/includes? (sse/table-ended-frame) "event: table-ended"))))
