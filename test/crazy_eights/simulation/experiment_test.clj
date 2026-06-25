(ns crazy_eights.simulation.experiment-test
  (:require [clojure.test :refer [deftest is]]
            [crazy_eights.simulation.experiment :as experiment]
            [crazy_eights.simulation.strategy :as strategy]))

(deftest rotate-lineup-moves-strategies-through-seats
  (is (= [:a :b :c]
         (experiment/rotate-lineup [:a :b :c] 0)))
  (is (= [:b :c :a]
         (experiment/rotate-lineup [:a :b :c] 1)))
  (is (= [:c :a :b]
         (experiment/rotate-lineup [:a :b :c] 2))))

(deftest summarize-aggregates-strategy-outcomes
  (let [results [{:status :finished
                  :winner 0
                  :winner-strategy :careful
                  :seat-strategies [:careful :first-playable]
                  :steps 10
                  :draws 2}
                 {:status :finished
                  :winner 1
                  :winner-strategy :careful
                  :seat-strategies [:first-playable :careful]
                  :steps 20
                  :draws 4}
                 {:status :step-budget-exhausted
                  :winner nil
                  :seat-strategies [:careful :first-playable]
                  :steps 500
                  :draws 100}]]
    (is (= {:games 3
            :player-count 2
            :finished 2
            :blocked 0
            :budget-exhausted 1
            :strategies {:careful {:seats-played 3
                                   :wins 2
                                   :win-rate 1.0
                                   :avg-steps 176.66666666666666
                                   :avg-draws 35.333333333333336}
                         :first-playable {:seats-played 3
                                          :wins 0
                                          :win-rate 0.0
                                          :avg-steps 176.66666666666666
                                          :avg-draws 35.333333333333336}}}
           (experiment/summarize {:player-count 2} results)))))

(deftest summarize-win-rates-are-decimal-share-of-games-with-winners
  (let [results [{:status :finished
                  :winner 0
                  :winner-strategy :careful
                  :seat-strategies [:careful :first-playable]
                  :steps 10
                  :draws 2}
                 {:status :finished
                  :winner 1
                  :winner-strategy :first-playable
                  :seat-strategies [:careful :first-playable]
                  :steps 12
                  :draws 3}
                 {:status :finished
                  :winner 0
                  :winner-strategy :careful
                  :seat-strategies [:careful :first-playable]
                  :steps 14
                  :draws 4}
                 {:status :step-budget-exhausted
                  :winner nil
                  :seat-strategies [:careful :first-playable]
                  :steps 500
                  :draws 90}]
        summary (experiment/summarize {:player-count 2} results)
        careful-rate (get-in summary [:strategies :careful :win-rate])
        first-playable-rate (get-in summary [:strategies :first-playable :win-rate])]
    (is (= 2 (:wins (get-in summary [:strategies :careful]))))
    (is (= 1 (:wins (get-in summary [:strategies :first-playable]))))
    (is (= 0.6666666666666666 careful-rate))
    (is (= 0.3333333333333333 first-playable-rate))
    (is (double? careful-rate))
    (is (= 1.0 (+ careful-rate first-playable-rate)))))

(deftest summarize-win-rates-are-zero-when-no-game-has-a-winner
  (let [summary (experiment/summarize
                 {:player-count 2}
                 [{:status :step-budget-exhausted
                   :winner nil
                   :seat-strategies [:careful :first-playable]
                   :steps 500
                   :draws 90}])]
    (is (= 0.0 (get-in summary [:strategies :careful :win-rate])))
    (is (= 0.0 (get-in summary [:strategies :first-playable :win-rate])))))

(deftest run-compares-strategies-over-normal-games
  (let [result (experiment/run {:games 2
                                :player-count 2
                                :strategies [strategy/careful strategy/first-playable]})]
    (is (= 2 (:games result)))
    (is (= 2 (:player-count result)))
    (is (= #{:careful :first-playable}
           (set (keys (:strategies result)))))
    (is (= 2 (get-in result [:strategies :careful :seats-played])))
    (is (= 2 (get-in result [:strategies :first-playable :seats-played])))
    (is (number? (get-in result [:strategies :careful :win-rate])))
    (is (number? (get-in result [:strategies :careful :avg-steps])))
    (is (number? (get-in result [:strategies :careful :avg-draws])))))
