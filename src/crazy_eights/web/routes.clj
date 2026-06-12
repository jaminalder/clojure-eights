(ns crazy_eights.web.routes
  (:require [clojure.data.json :as json]
            [crazy_eights.app.core :as app]
            [crazy_eights.app.simulation :as simulation]
            [crazy_eights.domain.model :as model]
            [crazy_eights.web.commands :as web-commands]
            [crazy_eights.web.page :as page]
            [crazy_eights.web.sse :as sse]
            [crazy_eights.web.view_model :as view-model]
            [crazy_eights.web.views :as views]
            [reitit.ring :as ring]
            [ring.middleware.cookies :as cookies]
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

(defn- html-response [status body]
  (response status body "text/html; charset=utf-8"))

(defn- see-other [location]
  {:status 303 :headers {"Location" location} :body ""})

(defn- cookie-name [game-id]
  (str "ce-" game-id))

(defn- viewer-id
  "Player id of the requesting browser, or nil for strangers and spectators."
  [store game-id request]
  (let [claimed (get-in request [:cookies (cookie-name game-id) :value])]
    (when (get-in (app/get-game store game-id) [:players claimed])
      claimed)))

(defn- render-fragments [store game-id viewer]
  (let [view (view-model/game-view (app/get-game store game-id) viewer)]
    {:status (views/status-html view)
     :game-board (views/board-html view)
     :player-hand (views/hand-html view)}))

(defn- status-response
  "An action result becomes the status fragment: errors privately for the
  actor, fresh state otherwise (full fragments travel over SSE)."
  [store game-id viewer result]
  (log-response
   (html-response 200
                  (if (:error result)
                    (views/error-status-html (:error result))
                    (views/status-html (view-model/game-view
                                        (app/get-game store game-id) viewer))))))

(defn- run-use-case [store {:keys [error game-id player-id card declared-suit] :as command}]
  (if error
    {:error error}
    (case (:type command)
      :join-game (app/join-game! store game-id (:name command))
      :start-game (app/start-game! store game-id player-id {})
      :play-card (app/play-card! store game-id player-id card declared-suit)
      :draw-card (app/draw-card! store game-id player-id)
      :pass-turn (app/pass-turn! store game-id player-id))))

(defn- game-action-handler [store command-fn]
  (fn [request]
    (log-request request)
    (let [game-id (get-in request [:path-params :id])
          viewer (viewer-id store game-id request)
          command (command-fn game-id viewer (:params request))]
      (status-response store game-id viewer (run-use-case store command)))))

(defn- game-routes [store]
  [["/"
    {:get (fn [request]
            (log-request request)
            (log-response (html-response 200 (views/start-page))))}]
   ["/games"
    {:post (fn [request]
             (log-request request)
             (let [command (web-commands/create-game-command (:params request))
                   {:keys [game-id]} (app/create-game! store)
                   {:keys [player-id]} (app/join-game! store game-id (:name command))]
               (log-response
                (assoc (see-other (str "/games/" game-id))
                       :cookies {(cookie-name game-id) {:value player-id :path "/"}}))))}]
   ["/games/:id"
    {:get (fn [request]
            (log-request request)
            (let [game-id (get-in request [:path-params :id])
                  game (app/get-game store game-id)]
              (log-response
               (if game
                 (html-response 200 (views/game-page
                                     (view-model/game-view game (viewer-id store game-id request))))
                 (html-response 404 (views/not-found-page))))))}]
   ["/games/:id/join"
    {:post (fn [request]
             (log-request request)
             (let [game-id (get-in request [:path-params :id])
                   command (web-commands/join-game-command game-id (:params request))
                   result (when (app/get-game store game-id)
                            (run-use-case store command))]
               (log-response
                (cond
                  (nil? result) (html-response 404 (views/not-found-page))
                  (:player-id result) (assoc (see-other (str "/games/" game-id))
                                             :cookies {(cookie-name game-id)
                                                       {:value (:player-id result) :path "/"}})
                  :else (see-other (str "/games/" game-id))))))}]
   ["/games/:id/start"
    {:post (game-action-handler store
                                (fn [game-id viewer _params]
                                  (web-commands/start-game-command game-id viewer)))}]
   ["/games/:id/play"
    {:post (game-action-handler store web-commands/play-card-command)}]
   ["/games/:id/draw"
    {:post (game-action-handler store
                                (fn [game-id viewer _params]
                                  (web-commands/draw-card-command game-id viewer)))}]
   ["/games/:id/pass"
    {:post (game-action-handler store
                                (fn [game-id viewer _params]
                                  (web-commands/pass-turn-command game-id viewer)))}]
   ["/games/:id/hand"
    {:get (fn [request]
            (log-request request)
            (let [game-id (get-in request [:path-params :id])
                  game (app/get-game store game-id)
                  view (view-model/game-view game (viewer-id store game-id request))]
              (log-response
               (if game
                 (html-response 200 (views/hand-html view {:declare-code
                                                           (get-in request [:params "declare"])}))
                 (html-response 404 (views/not-found-page))))))}]
   ["/games/:id/events"
    {:get (fn [request]
            (log-request request)
            (let [game-id (get-in request [:path-params :id])
                  viewer (viewer-id store game-id request)]
              (if (app/get-game store game-id)
                (sse/game-events-response store game-id
                                          #(render-fragments store game-id viewer)
                                          request)
                (log-response (json-response 404 {:error "unknown game"})))))}]])

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
                             (log-response (json-response 404 {:error "unknown simulation"})))))]
    [["/observer"
      {:get (fn [request]
              (log-request request)
              (log-response
               (html-response 200 (page/observer-page))))}]
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
              (sse-response (get-in request [:path-params :id]) request))}]]))

(defn app [{:keys [store] :as config}]
  (let [store (or store (app/create-store))]
    (params/wrap-params
     (cookies/wrap-cookies
      (ring/ring-handler
       (ring/router
        (into (game-routes store) (simulation-routes config)))
       (ring/routes
        (ring/create-resource-handler {:path "/assets" :root "public"})
        (ring/create-default-handler
         {:not-found (fn [_] (html-response 404 (views/not-found-page)))})))))))
