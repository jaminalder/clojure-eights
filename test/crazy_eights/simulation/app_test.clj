(ns crazy_eights.simulation.app-test
  (:require [clojure.test :refer [deftest is]]
            [crazy_eights.app.core :as app]
            [crazy_eights.simulation.app :as simulation]
            [crazy_eights.simulation.strategy :as strategy]))

(deftest start-is-the-simulation-setup-use-case
  (let [store (app/create-store)
        result (simulation/start! store {:player-count 3
                                         :simulation-name "sim-test"})
        game (app/get-game store (:game-id result))]
    (is (= (:game-id game) (:game-id result)))
    (is (= (:observer-id game) (:observer-id result)))
    (is (= "sim-test" (:simulation-name result)))
    (is (= 3 (:player-count result)))
    (is (= 3 (count (:players result))))
    (is (= :in-progress (get-in game [:state :status])))
    (is (= ["sim-test-p1" "sim-test-p2" "sim-test-p3"]
           (mapv :name (sort-by :seat (vals (:players game))))))))

(deftest run-to-completion-finishes-a-normal-app-game
  (let [store (app/create-store)
        started (simulation/start! store {:player-count 2
                                          :simulation-name "sim-test"})
        result (simulation/run-to-completion! store started {:delay-fn (fn [] nil)})]
    (is (= :finished (:status result)))
    (is (pos? (:steps-left result)))
    (is (= :finished (get-in (app/get-game store (:game-id started)) [:state :status])))))

(deftest run-to-completion-reports-step-budget-exhaustion
  (let [store (app/create-store)
        started (simulation/start! store {:player-count 2
                                          :simulation-name "sim-test"})
        result (simulation/run-to-completion! store started {:delay-fn (fn [] nil)
                                                            :step-budget 0})]
    (is (= :step-budget-exhausted (:status result)))
    (is (= 0 (:steps-left result)))
    (is (= :in-progress (get-in (app/get-game store (:game-id started))
                                [:state :status])))))

(deftest run-to-completion-uses-supplied-strategy
  (let [store (app/create-store)
        started (simulation/start! store {:player-count 2
                                          :simulation-name "sim-test"})
        calls (atom 0)
        strategy (fn [state]
                   (swap! calls inc)
                   (strategy/choose-action state))]
    (simulation/run-to-completion! store started {:delay-fn (fn [] nil)
                                                 :step-budget 1
                                                 :strategy strategy})
    (is (= 1 @calls))))

(deftest start-background-launches-a-running-simulation
  (let [store (app/create-store)
        result (simulation/start-background! store {:player-count 2
                                                    :simulation-name "sim-test"
                                                    :delay-seconds 0
                                                    :delay-fn (fn [] nil)})]
    (is (= "sim-test" (:simulation-name result)))
    (is (= 2 (:player-count result)))
    (is (= 0 (:delay-seconds result)))
    (is (future? (:future result)))
    (is (= :finished (:status @(:future result))))))

(deftest delay-seconds-becomes-delay-function
  (let [calls (atom 0)
        delay-fn (simulation/delay-fn 0.001 #(swap! calls inc))]
    (delay-fn)
    (is (= 1 @calls))))
