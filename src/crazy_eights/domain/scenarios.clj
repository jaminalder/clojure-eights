(ns crazy_eights.domain.scenarios
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [crazy_eights.domain.commands :as commands]
            [crazy_eights.domain.events :as events]))

(defn load-scenario-resource [path]
  (-> path io/resource slurp edn/read-string))

(defn- subset-match? [expected actual]
  (every? (fn [[k v]]
            (let [actual-value (get actual k)]
              (if (map? v)
                (subset-match? v actual-value)
                (= v actual-value))))
          expected))

(defn- run-step [state {:keys [command then]}]
  (let [result (commands/decide state command)]
    (if (= :domain-error (:type result))
      {:pass? (= (:error then)
                 (select-keys result [:reason]))
       :state state}
      (let [next-state (events/apply-events state result)]
        {:pass? (and (= (:events then) result)
                     (or (nil? (:state-matches then))
                         (subset-match? (:state-matches then) next-state)))
         :state next-state}))))

(defn run-scenario [scenario]
  (let [initial-state (get-in scenario [:given :state])]
    (if-let [steps (:steps scenario)]
      (let [result (reduce (fn [{:keys [pass? state] :as acc} step]
                             (if-not pass?
                               acc
                               (run-step state step)))
                           {:pass? true :state initial-state}
                           steps)]
        {:pass? (and (:pass? result)
                     (or (nil? (get-in scenario [:then :state-matches]))
                         (subset-match? (get-in scenario [:then :state-matches])
                                        (:state result))))})
      (run-step initial-state {:command (get-in scenario [:when :command])
                               :then (:then scenario)}))))

(defn run-scenario-resource [path]
  (run-scenario (load-scenario-resource path)))
