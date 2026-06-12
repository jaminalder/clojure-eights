(ns crazy_eights.web.server
  (:require [crazy_eights.app.core :as app]
            [crazy_eights.app.simulation :as simulation]
            [crazy_eights.web.routes :as routes]
            [org.httpkit.server :as http]))

(defonce server (atom nil))

(defn -main [& _args]
  (let [store (app/create-store)
        service (simulation/create-service {:delay-fn (fn [] (Thread/sleep 500))})
        handler (routes/app {:store store :simulation-service service})]
    (reset! server (http/run-server handler {:port 8080}))
    (println "server started on http://localhost:8080")))
