(ns crazy_eights.app.simulation-runtime-test
  (:require [clojure.test :refer [deftest is]]
            [crazy_eights.app.simulation :as simulation]))

(deftest start-simulation-creates-entry
  (let [service (simulation/create-service {:delay-fn (fn [] nil)})
        {:keys [simulation-id]} (simulation/start! service 3)
        sim (simulation/get-simulation service simulation-id)]
    (is (string? simulation-id))
    (is (= 3 (:player-count sim)))))

(deftest simulation-publishes-log-events
  (let [service (simulation/create-service {:delay-fn (fn [] nil)})
        sink (atom [])
        {:keys [simulation-id]} (simulation/start! service 2)]
    (simulation/subscribe! service simulation-id :test #(swap! sink conj %))
    (simulation/run-to-completion! service simulation-id)
    (is (seq @sink))
    (is (every? #(= :log (:type %)) @sink))
    (is (= :finished (:status (simulation/get-simulation service simulation-id))))))

(deftest run-to-completion-runs-only-once
  (let [service (simulation/create-service {:delay-fn (fn [] nil)})
        sink (atom [])
        {:keys [simulation-id]} (simulation/start! service 2)]
    (simulation/subscribe! service simulation-id :test #(swap! sink conj %))
    (simulation/run-to-completion! service simulation-id)
    (let [events-after-first-run (count @sink)]
      (simulation/run-to-completion! service simulation-id)
      (is (= events-after-first-run (count @sink))))))

(deftest run-to-completion-ignores-unknown-simulation
  (let [service (simulation/create-service {:delay-fn (fn [] nil)})]
    (is (nil? (simulation/run-to-completion! service "missing")))))
