(ns crazy_eights.simulation.experiment
  (:require [crazy_eights.app.core :as app]
            [crazy_eights.simulation.app :as simulation]
            [crazy_eights.simulation.strategy :as strategy]))

(def default-player-count 2)

(defn rotate-lineup [lineup n]
  (let [lineup (vec lineup)
        size (count lineup)
        offset (if (pos? size) (mod n size) 0)]
    (vec (concat (subvec lineup offset)
                 (subvec lineup 0 offset)))))

(defn- strategy-ids [results]
  (->> results
       (mapcat :seat-strategies)
       set
       (remove nil?)
       sort
       vec))

(defn- seats-played [strategy-id results]
  (->> results
       (mapcat :seat-strategies)
       (filter #(= strategy-id %))
       count))

(defn- wins [strategy-id results]
  (count (filter #(= strategy-id (:winner-strategy %)) results)))

(defn- average [xs]
  (if (seq xs)
    (/ (reduce + xs) (double (count xs)))
    0))

(defn- games-with-winners [results]
  (count (filter :winner-strategy results)))

(defn- win-rate [win-count decisive-game-count]
  (if (pos? decisive-game-count)
    (/ win-count (double decisive-game-count))
    0.0))

(defn- occupied? [strategy-id result]
  (some #(= strategy-id %) (:seat-strategies result)))

(defn- strategy-summary [results decisive-games strategy-id]
  (let [strategy-results (filter #(occupied? strategy-id %) results)
        strategy-wins (wins strategy-id results)
        seats (seats-played strategy-id results)]
    {:seats-played seats
     :wins strategy-wins
     :win-rate (win-rate strategy-wins decisive-games)
     :avg-steps (average (map :steps strategy-results))
     :avg-draws (average (map :draws strategy-results))}))

(defn summarize [{:keys [player-count]} results]
  (let [decisive-games (games-with-winners results)]
    {:games (count results)
     :player-count player-count
     :finished (count (filter #(= :finished (:status %)) results))
     :blocked (count (filter #(and (= :finished (:status %))
                                    (nil? (:winner %)))
                              results))
     :budget-exhausted (count (filter #(= :step-budget-exhausted (:status %)) results))
     :strategies (into {}
                       (map (fn [strategy-id]
                              [strategy-id (strategy-summary results decisive-games strategy-id)]))
                       (strategy-ids results))}))

(defn run-game [{:keys [player-count strategies step-budget]} run-index]
  (let [store (app/create-store)
        started (simulation/start! store {:player-count player-count})]
    (simulation/run-to-completion! store started
                                   (cond-> {:delay-fn (fn [] nil)
                                            :strategies (rotate-lineup strategies run-index)}
                                     step-budget (assoc :step-budget step-budget)))))

(defn run [{:keys [games player-count strategies]
            :or {games 1
                 player-count default-player-count
                 strategies [strategy/careful strategy/first-playable]}
            :as opts}]
  (let [opts (assoc opts
                    :games games
                    :player-count player-count
                    :strategies strategies)
        results (mapv #(run-game opts %) (range games))]
    (summarize opts results)))
