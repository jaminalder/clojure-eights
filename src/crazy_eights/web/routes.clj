(ns crazy_eights.web.routes
  (:require [clojure.data.json :as json]
            [crazy_eights.app.simulation :as simulation]
            [crazy_eights.web.page :as page]
            [reitit.ring :as ring]
            [ring.middleware.params :as params]))

(defn response [status body content-type]
  {:status status
   :headers {"Content-Type" content-type}
   :body body})

(defn app [{:keys [simulation-service start! run-simulation! sse-response]}]
  (params/wrap-params
   (ring/ring-handler
    (ring/router
     [["/"
       {:get (fn [_]
               (response 200 (page/observer-page) "text/html; charset=utf-8"))}]
      ["/simulations"
        {:post (fn [request]
                 (let [player-count (some-> (get-in request [:params "player-count"]) parse-long)]
                   (if (and player-count (<= 2 player-count 10))
                    (let [start-fn (or start! #(simulation/start! simulation-service %))
                          run-fn (or run-simulation! #(simulation/run-to-completion! simulation-service %))
                          {:keys [simulation-id]} (start-fn player-count)]
                      (future (run-fn simulation-id))
                      (response 202
                                (json/write-str {:simulation-id simulation-id})
                                "application/json"))
                    (response 400
                              (json/write-str {:error "invalid player-count"})
                              "application/json"))))}]
      ["/simulations/:id/events"
        {:get (fn [request]
                ((or sse-response
                     (fn [simulation-id req]
                       (simulation/sse-response simulation-service simulation-id req)))
                 (get-in request [:path-params :id])
                 request))}]]))))
