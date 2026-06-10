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

(defn run-scenario [scenario]
  (let [state (get-in scenario [:given :state])
        command (get-in scenario [:when :command])
        result (commands/decide state command)]
    (if (= :domain-error (:type result))
      {:pass? (= (get-in scenario [:then :error])
                 (select-keys result [:reason]))}
      (let [next-state (events/apply-events state result)]
        {:pass? (and (= (get-in scenario [:then :events]) result)
                     (subset-match? (get-in scenario [:then :state-matches]) next-state))}))))

(defn run-scenario-resource [path]
  (run-scenario (load-scenario-resource path)))
