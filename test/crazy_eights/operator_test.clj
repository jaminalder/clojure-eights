(ns crazy_eights.operator-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [crazy_eights.app.core :as app]
            [crazy_eights.domain.model :as model]
            [crazy_eights.operator :as op]
            [crazy_eights.runtime :as runtime]))

(defn clean-runtime [test-fn]
  (op/unobserve-all!)
  (runtime/reset-store!)
  (test-fn)
  (op/unobserve-all!)
  (runtime/reset-store!))

(use-fixtures :each clean-runtime)

(def fixed-deck
  (vec (concat [(model/card :queen :clubs)
                (model/card :two :clubs)
                (model/card :six :diamonds)
                (model/card :king :diamonds)
                (model/card :ace :diamonds)
                (model/card :eight :diamonds)
                (model/card :two :hearts)
                (model/card :ace :clubs)
                (model/card :king :spades)
                (model/card :jack :clubs)
                (model/card :queen :spades)
                (model/card :four :clubs)]
               (drop 12 model/full-deck))))

(deftest games-returns-live-tables-grouped-by-status
  (let [{waiting-id :game-id} (app/create-game! runtime/store)
        _waiting-host (app/join-game! runtime/store waiting-id "waiting-host")
        {started-id :game-id} (app/create-game! runtime/store)
        started-host (app/join-game! runtime/store started-id "started-host")
        _started-guest (app/join-game! runtime/store started-id "started-guest")]
    (app/start-game! runtime/store started-id (:player-id started-host) {:deck fixed-deck})
    (let [summary (op/games)]
      (is (= [waiting-id] (mapv :game-id (:waiting summary))))
      (is (= [started-id] (mapv :game-id (:in-progress summary))))
      (is (= (str "/games/" waiting-id "/observer/"
                  (:observer-id (app/get-game runtime/store waiting-id)))
             (get-in summary [:waiting 0 :observer-path])))
      (is (= "waiting-host" (get-in summary [:waiting 0 :players 0 :name])))
      (is (= 2 (get-in summary [:in-progress 0 :player-count]))))))

(deftest game-returns-full-operator-state
  (let [{:keys [game-id]} (app/create-game! runtime/store)
        p1 (app/join-game! runtime/store game-id "anna")
        _p2 (app/join-game! runtime/store game-id "ben")]
    (app/start-game! runtime/store game-id (:player-id p1) {:deck fixed-deck})
    (let [game (op/game game-id)]
      (is (= game-id (:game-id game)))
      (is (= "anna" (get-in game [:players (:player-id p1) :name])))
      (is (seq (get-in game [:state :players 0 :hand])))
      (is (seq (get-in game [:state :draw-pile]))))))

(deftest game-reports-unknown-game
  (is (= {:error :unknown-game}
         (op/game "missing"))))

(deftest observe-subscribes-and-prints-readable-event-summaries
  (let [{:keys [game-id]} (app/create-game! runtime/store)
        result (op/observe! game-id)]
    (is (= game-id (:game-id result)))
    (is (string? (:observer-id result)))
    (is (= [result] (op/observers)))
    (let [printed (with-out-str
                    (app/join-game! runtime/store game-id "anna"))]
      (is (re-find #"player joined" printed))
      (is (re-find #"anna|game-0-player-0" printed)))))

(deftest unobserve-stops-event-printing
  (let [{:keys [game-id]} (app/create-game! runtime/store)
        {:keys [observer-id]} (op/observe! game-id)]
    (is (= :stopped (:status (op/unobserve! observer-id))))
    (is (= [] (op/observers)))
    (let [printed (with-out-str
                    (app/join-game! runtime/store game-id "anna"))]
      (is (= "" printed)))))

(deftest observe-reports-unknown-game
  (is (= {:error :unknown-game}
         (op/observe! "missing"))))

(deftest unobserve-reports-unknown-observer
  (is (= {:error :unknown-observer}
         (op/unobserve! "missing"))))
