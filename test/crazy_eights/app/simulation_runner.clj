(ns crazy_eights.app.simulation-runner
  (:require [crazy_eights.app.simulation-test :as simulation]
            [kaocha.runner :as runner]))

(defn -main [& _args]
  (binding [simulation/*log-app-simulation* true]
    (runner/-main "--focus"
                  "crazy_eights.app.simulation-test"
                  "--no-capture-output")))
