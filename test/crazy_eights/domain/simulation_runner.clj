(ns crazy_eights.domain.simulation-runner
  (:require [crazy_eights.domain.simulation-test :as simulation]
            [kaocha.runner :as runner]))

(defn -main [& _args]
  (binding [simulation/*log-simulation* true]
    (runner/-main "--focus"
                  "crazy_eights.domain.simulation-test"
                  "--no-capture-output")))
