(ns crazy_eights.web.routes
  (:require [clojure.data.json :as json]
            [crazy_eights.app.simulation :as simulation]
            [crazy_eights.domain.model :as model]
            [crazy_eights.web.page :as page]
            [reitit.ring :as ring]
            [ring.middleware.params :as params]))

(defn- log-request [request]
  (prn {:direction :incoming
        :method (:request-method request)
        :uri (:uri request)
        :params (:params request)
        :path-params (:path-params request)}))

(defn- log-response [response]
  (prn {:direction :outgoing
        :status (:status response)
        :content-type (get-in response [:headers "Content-Type"])})
  response)

(defn response [status body content-type]
  {:status status
   :headers {"Content-Type" content-type}
   :body body})

(defn app [{:keys [simulation-service start! sse-response]}]
  (params/wrap-params
   (ring/ring-handler
    (ring/router
     [["/"
        {:get (fn [_]
                (log-response
                 (response 200 (page/observer-page) "text/html; charset=utf-8")))}]
      ["/simulations"
        {:post (fn [request]
                 (log-request request)
                 (let [player-count (some-> (get-in request [:params "player-count"]) parse-long)]
                    (if (and player-count (<= 2 player-count model/max-player-count))
                     (let [start-fn (or start! #(simulation/start! simulation-service %))
                          {:keys [simulation-id]} (start-fn player-count)]
                       (log-response
                        (response 202
                                 (json/write-str {:simulation-id simulation-id})
                                 "application/json")))
                    (log-response
                     (response 400
                               (json/write-str {:error "invalid player-count"})
                               "application/json")))))}]
      ["/simulations/:id/events"
        {:get (fn [request]
                (log-request request)
                ((or sse-response
                     (fn [simulation-id req]
                       (future (simulation/run-to-completion! simulation-service simulation-id))
                       (simulation/sse-response simulation-service simulation-id req)))
                 (get-in request [:path-params :id])
                 request))}]]))))
