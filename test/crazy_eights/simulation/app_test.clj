(ns crazy_eights.simulation.app-test
  (:require [clojure.test :refer [deftest is]]
            [crazy_eights.app.core :as app]
            [crazy_eights.domain.model :as model]
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
        strategy (fn [obs]
                   (swap! calls inc)
                   ((:choose strategy/first-playable) obs))]
    (simulation/run-to-completion! store started {:delay-fn (fn [] nil)
                                                 :step-budget 1
                                                 :strategy strategy})
    (is (= 1 @calls))))

(deftest run-to-completion-uses-strategy-for-current-seat
  (let [store (app/create-store)
        started (simulation/start! store {:player-count 2
                                          :simulation-name "sim-test"})
        deterministic-state {:players [{:hand [(model/card :nine :hearts)
                                               (model/card :three :clubs)]}
                                       {:hand [(model/card :five :hearts)
                                               (model/card :queen :clubs)]}]
                             :draw-pile [(model/card :king :spades)]
                             :discard-pile [(model/card :nine :diamonds)]
                             :active-suit :diamonds
                             :current-player 0
                             :status :in-progress
                             :winner nil
                             :passes-in-row 0}
        calls (atom [])
        p0 {:id :p0
            :choose (fn [obs]
                      (swap! calls conj [:p0 (:player obs)])
                      ((:choose strategy/first-playable) obs))}
        p1 {:id :p1
            :choose (fn [obs]
                      (swap! calls conj [:p1 (:player obs)])
                      ((:choose strategy/first-playable) obs))}
        _ (swap! store assoc-in [:games (:game-id started) :state] deterministic-state)
        result (simulation/run-to-completion! store started {:delay-fn (fn [] nil)
                                                            :step-budget 2
                                                            :strategies [p0 p1]})]
    (is (= [:p0 :p1] (:seat-strategies result)))
    (is (= [[:p0 0] [:p1 1]] @calls))))

(deftest run-to-completion-reports_run_metrics
  (let [store (app/create-store)
        started (simulation/start! store {:player-count 2
                                          :simulation-name "sim-test"})
        result (simulation/run-to-completion! store started {:delay-fn (fn [] nil)})]
    (is (pos? (:steps result)))
    (is (nat-int? (:draws result)))
    (is (nat-int? (:plays result)))
    (is (nat-int? (:passes result)))
    (is (= :finished (:status result)))
    (is (contains? #{:first-playable nil} (:winner-strategy result)))
    (is (= [:first-playable :first-playable] (:seat-strategies result)))))

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
