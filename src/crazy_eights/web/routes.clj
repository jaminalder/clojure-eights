(ns crazy_eights.web.routes
  (:require [clojure.data.json :as json]
            [crazy_eights.app.core :as app]
            [crazy_eights.app.simulation :as simulation]
            [crazy_eights.domain.model :as model]
            [crazy_eights.web.commands :as web-commands]
            [crazy_eights.web.fragments :as fragments]
            [crazy_eights.web.page :as page]
            [crazy_eights.web.paths :as paths]
            [crazy_eights.web.sse :as sse]
            [crazy_eights.web.view_model :as view-model]
            [crazy_eights.web.views :as views]
            [reitit.ring :as ring]
            [ring.middleware.cookies :as cookies]
            [ring.middleware.params :as params]))

;; responses

(defn- response [status body content-type]
  {:status status
   :headers {"Content-Type" content-type}
   :body body})

(defn- json-response [status body]
  (response status (json/write-str body) "application/json"))

(defn- html-response [status body]
  (response status body "text/html; charset=utf-8"))

(defn- see-other
  ([location] {:status 303 :headers {"Location" location} :body ""})
  ([location cookies] (assoc (see-other location) :cookies cookies)))

(defn- not-found-html []
  (html-response 404 (views/not-found-page)))

;; logging — one request/response pair per matched route

(defn- wrap-logging [handler]
  (fn [request]
    (prn {:direction :incoming
          :method (:request-method request)
          :uri (:uri request)
          :params (:params request)
          :path-params (:path-params request)})
    (let [resp (handler request)]
      (prn {:direction :outgoing
            :status (:status resp)
            :content-type (get-in resp [:headers "Content-Type"])})
      resp)))

;; player session (per-game cookie)

(defn- cookie-name [game-id]
  (str "ce-" game-id))

(defn- viewer-id
  "Player id of the requesting browser, or nil for strangers and spectators."
  [store game-id request]
  (let [claimed (get-in request [:cookies (cookie-name game-id) :value])]
    (when (get-in (app/get-game store game-id) [:players claimed])
      claimed)))

(defn- player-cookie [game-id player-id]
  {(cookie-name game-id) {:value player-id :path "/"}})

;; use cases

(defn- run-use-case [store {:keys [error game-id player-id card declared-suit] :as command}]
  (if error
    {:error error}
    (case (:type command)
      :join-game (app/join-game! store game-id (:name command))
      :start-game (app/start-game! store game-id player-id {})
      :play-card (app/play-card! store game-id player-id card declared-suit)
      :draw-card (app/draw-card! store game-id player-id)
      :pass-turn (app/pass-turn! store game-id player-id))))

(defn- status-response
  "An action result becomes the status fragment: errors privately for the
  actor, fresh state otherwise (full fragments travel over SSE)."
  [store game-id viewer result]
  (html-response 200
                 (if (:error result)
                   (views/error-status-html (:error result))
                   (views/status-html (view-model/game-view
                                       (app/get-game store game-id) viewer)))))

;; handler combinators

(defn- game-id-of [request]
  (get-in request [:path-params :id]))

(defn- game-action-handler
  "Runs (command-fn game-id viewer params) through the domain and replies with
  the actor's status fragment."
  [store command-fn]
  (fn [request]
    (let [game-id (game-id-of request)
          viewer (viewer-id store game-id request)
          command (command-fn game-id viewer (:params request))]
      (status-response store game-id viewer (run-use-case store command)))))

(defn- game-query-handler
  "Resolves the game or replies 404; otherwise renders (render view request)."
  [store render]
  (fn [request]
    (let [game-id (game-id-of request)]
      (if-let [game (app/get-game store game-id)]
        (render (view-model/game-view game (viewer-id store game-id request)) request)
        (not-found-html)))))

(defn- game-routes [store]
  [["/"
    {:get (fn [_request]
            (html-response 200 (views/start-page)))}]
   ["/games"
    {:post (fn [request]
             (let [command (web-commands/create-game-command (:params request))
                   {:keys [game-id]} (app/create-game! store)
                   {:keys [player-id]} (app/join-game! store game-id (:name command))]
               (see-other (paths/game game-id) (player-cookie game-id player-id))))}]
   ["/games/:id"
    {:get (game-query-handler store
                              (fn [view _request]
                                (html-response 200 (views/game-page view))))}]
   ["/games/:id/join"
    {:post (fn [request]
             (let [game-id (game-id-of request)
                   command (web-commands/join-game-command game-id (:params request))
                   result (when (app/get-game store game-id)
                            (run-use-case store command))]
               (cond
                 (nil? result) (not-found-html)
                 (:player-id result) (see-other (paths/game game-id)
                                                 (player-cookie game-id (:player-id result)))
                 :else (see-other (paths/game game-id)))))}]
   ["/games/:id/start"
    {:post (game-action-handler store web-commands/start-game-command)}]
   ["/games/:id/play"
    {:post (game-action-handler store web-commands/play-card-command)}]
   ["/games/:id/draw"
    {:post (game-action-handler store web-commands/draw-card-command)}]
   ["/games/:id/pass"
    {:post (game-action-handler store web-commands/pass-turn-command)}]
   ["/games/:id/hand"
    {:get (game-query-handler store
                              (fn [view request]
                                (html-response 200 (views/hand-html
                                                    view
                                                    {:declare-code (get-in request [:params "declare"])}))))}]
   ["/games/:id/events"
    {:get (fn [request]
            (let [game-id (game-id-of request)
                  viewer (viewer-id store game-id request)]
              (if (app/get-game store game-id)
                (sse/game-events-response store game-id
                                          #(fragments/game-fragments store game-id viewer)
                                          request)
                (json-response 404 {:error "unknown game"}))))}]])

(defn- simulation-routes [{:keys [simulation-service start! run-simulation! sse-response]}]
  (let [start! (or start!
                   #(simulation/start! simulation-service %))
        run-simulation! (or run-simulation!
                            (fn [simulation-id]
                              (future (simulation/run-to-completion! simulation-service simulation-id))))
        sse-response (or sse-response
                         (fn [simulation-id request]
                           (if (simulation/get-simulation simulation-service simulation-id)
                             (sse/sse-response simulation-service simulation-id request run-simulation!)
                             (json-response 404 {:error "unknown simulation"}))))]
    [["/observer"
      {:get (fn [_request]
              (html-response 200 (page/observer-page)))}]
     ["/simulations"
      {:post (fn [request]
               (let [player-count (some-> (get-in request [:params "player-count"]) parse-long)]
                 (if (and player-count (<= 2 player-count model/max-player-count))
                   (let [{:keys [simulation-id]} (start! player-count)]
                     (json-response 202 {:simulation-id simulation-id}))
                   (json-response 400 {:error "invalid player-count"}))))}]
     ["/simulations/:id/events"
      {:get (fn [request]
              (sse-response (get-in request [:path-params :id]) request))}]]))

(defn app [{:keys [store] :as config}]
  (let [store (or store (app/create-store))]
    (params/wrap-params
     (cookies/wrap-cookies
      (ring/ring-handler
       (ring/router
        (into (game-routes store) (simulation-routes config))
        {:data {:middleware [wrap-logging]}})
       (ring/routes
        (ring/create-resource-handler {:path "/assets" :root "public"})
        (ring/create-default-handler
         {:not-found (fn [_] (not-found-html))})))))))
