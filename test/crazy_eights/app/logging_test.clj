(ns crazy_eights.app.logging-test
  (:require [clojure.test :refer [deftest is]]
            [crazy_eights.app.logging :as logging]))

(deftest event->log-entry-preserves-key-data
  (let [event {:type :move-made
               :game-id "game-1"
               :command {:type :play-card}
               :events [{:type :card-played}]}
        entry (logging/event->log-entry event)]
    (is (= :info (:level entry)))
    (is (= :move-made (:event entry)))
    (is (= "game-1" (:game-id entry)))
    (is (= event (:data entry)))))

(deftest stdout-subscriber-writes-log-entry
  (let [event {:type :game-started
               :game-id "game-2"}
        output (with-out-str
                 ((logging/stdout-subscriber) event))]
    (is (.contains output ":game-started"))
    (is (.contains output "game-2"))))
