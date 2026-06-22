(ns crazy_eights.web.server
  (:require [crazy_eights.runtime :as runtime]))

(defn start!
  ([] (start! {}))
  ([opts]
   (let [result (runtime/start-web! opts)]
     (when (= :started (:status result))
       (println "server started on" (str "http://localhost:" (:port result))))
     result)))

(defn stop! []
  (runtime/stop-web!))

(defn -main [& _args]
  (start!)
  @(promise))
