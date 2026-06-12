(ns crazy_eights.web.routes
  (:require [clojure.data.json :as json]
            [crazy_eights.app.simulation :as simulation]
            [crazy_eights.domain.model :as model]
            [crazy_eights.web.page :as page]
            [crazy_eights.web.sse :as sse]
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

(defn- json-response [status body]
  (response status (json/write-str body) "application/json"))

(defn app [{:keys [simulation-service start! run-simulation! sse-response]}]
  (let [start! (or start!
                   #(simulation/start! simulation-service %))
        run-simulation! (or run-simulation!
                            (fn [simulation-id]
                              (future (simulation/run-to-completion! simulation-service simulation-id))))
        sse-response (or sse-response
                         (fn [simulation-id request]
                           (if (simulation/get-simulation simulation-service simulation-id)
                             (sse/sse-response simulation-service simulation-id request run-simulation!)
                             (log-response (json-response 404 {:error "unknown simulation"})))))]
    (params/wrap-params
     (ring/ring-handler
      (ring/router
       [["/"
         {:get (fn [request]
                 (log-request request)
                 (log-response
                  (response 200 (page/observer-page) "text/html; charset=utf-8")))}]
        ["/simulations"
         {:post (fn [request]
                  (log-request request)
                  (let [player-count (some-> (get-in request [:params "player-count"]) parse-long)]
                    (if (and player-count (<= 2 player-count model/max-player-count))
                      (let [{:keys [simulation-id]} (start! player-count)]
                        (log-response (json-response 202 {:simulation-id simulation-id})))
                      (log-response (json-response 400 {:error "invalid player-count"})))))}]
        ["/simulations/:id/events"
         {:get (fn [request]
                 (log-request request)
                 (sse-response (get-in request [:path-params :id]) request))}]])
      (ring/create-default-handler)))))
