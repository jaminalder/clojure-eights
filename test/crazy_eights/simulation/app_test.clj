(ns crazy_eights.simulation.app-test
  (:require [clojure.test :refer [deftest is]]
            [crazy_eights.app.core :as app]
            [crazy_eights.simulation.app :as simulation]))

(deftest start-game-creates-normal-app-game
  (let [store (app/create-store)
        result (simulation/start-game! store 3 {:simulation-name "sim-test"})
        game (app/get-game store (:game-id result))]
    (is (= (:game-id game) (:game-id result)))
    (is (= (:observer-id game) (:observer-id result)))
    (is (= 3 (:player-count result)))
    (is (= "sim-test" (:simulation-name result)))
    (is (= :in-progress (get-in game [:state :status])))
    (is (= ["sim-test-p1" "sim-test-p2" "sim-test-p3"]
           (mapv :name (sort-by :seat (vals (:players game))))))))

(deftest run-to-completion-finishes-a-normal-app-game
  (let [store (app/create-store)
        started (simulation/start-game! store 2 {:simulation-name "sim-test"})
        result (simulation/run-to-completion! store started {:delay-fn (fn [] nil)})]
    (is (= :finished (:status result)))
    (is (pos? (:steps-left result)))
    (is (= :finished (get-in (app/get-game store (:game-id started)) [:state :status])))))

(deftest delay-seconds-becomes-delay-function
  (let [calls (atom 0)
        delay-fn (simulation/delay-fn 0.001 #(swap! calls inc))]
    (delay-fn)
    (is (= 1 @calls))))
