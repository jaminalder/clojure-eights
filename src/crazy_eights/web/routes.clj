(ns crazy_eights.web.routes
  (:require [clojure.data.json :as json]
            [crazy_eights.app.core :as app]
            [crazy_eights.web.commands :as web-commands]
            [crazy_eights.web.fragments :as fragments]
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

(defn- expired-player-cookie [game-id]
  {(cookie-name game-id) {:value "" :path "/" :max-age 0}})

;; use cases

(defn- run-use-case [store {:keys [error game-id player-id card declared-suit] :as command}]
  (if error
    {:error error}
    (case (:type command)
      :create-game (app/host-game! store (:name command))
      :join-game (app/join-game! store game-id (:name command))
      :start-game (app/start-game! store game-id player-id {})
      :play-card (app/play-card! store game-id player-id card declared-suit)
      :draw-card (app/draw-card! store game-id player-id)
      :pass-turn (app/pass-turn! store game-id player-id)
      :leave-table (app/leave-table! store game-id player-id)
      {:error :unknown-command})))

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

(defn- observer-id-of [request]
  (get-in request [:path-params :observer-id]))

(defn- observer-game [store request]
  (let [game-id (game-id-of request)
        observer-id (observer-id-of request)
        game (app/get-game store game-id)]
    (when (= observer-id (:observer-id game))
      game)))

(defn- game-action-handler
  "Runs (command-fn game-id viewer params) through the domain and replies with
  the actor's status fragment."
  [store command-fn]
  (fn [request]
    (let [game-id (game-id-of request)
          viewer (viewer-id store game-id request)
          command (command-fn game-id viewer (:params request))]
      (status-response store game-id viewer (run-use-case store command)))))

(defn- create-game-handler [store]
  (fn [request]
    (let [{:keys [game-id player-id]} (run-use-case
                                       store
                                       (web-commands/create-game-command (:params request)))]
      (see-other (paths/game game-id) (player-cookie game-id player-id)))))

(defn- join-game-response [game-id result]
  (cond
    (= :unknown-game (:error result))
    (not-found-html)

    (:player-id result)
    (see-other (paths/game game-id)
               (player-cookie game-id (:player-id result)))

    :else
    (see-other (paths/game game-id))))

(defn- join-game-handler [store]
  (fn [request]
    (let [game-id (game-id-of request)
          command (web-commands/join-game-command game-id (:params request))]
      (join-game-response game-id (run-use-case store command)))))

(defn- start-page-handler [_request]
  (html-response 200 (views/start-page)))

(defn- game-page-response [view _request]
  (html-response 200 (views/game-page view)))

(defn- hand-fragment-response [view request]
  (html-response 200 (views/hand-html
                      view
                      {:declare-code (get-in request [:params "declare"])})))

(defn- game-events-handler [store]
  (fn [request]
    (let [game-id (game-id-of request)
          viewer (viewer-id store game-id request)]
      (if (app/get-game store game-id)
        (sse/game-events-response store game-id
                                  #(fragments/game-fragments store game-id viewer)
                                  request)
         (json-response 404 {:error "unknown game"})))))

(defn- observer-page-handler [store]
  (fn [request]
    (if-let [game (observer-game store request)]
      (html-response 200
                     (views/observer-page (view-model/observer-view game)
                                          (observer-id-of request)))
      (not-found-html))))

(defn- observer-events-handler [store]
  (fn [request]
    (let [game-id (game-id-of request)]
      (if (observer-game store request)
        (sse/game-events-response store game-id
                                  #(fragments/observer-fragments store game-id)
                                  request)
        (not-found-html)))))

(defn- leave-table-handler [store]
  (fn [request]
    (let [game-id (game-id-of request)
          viewer (viewer-id store game-id request)
          command (web-commands/leave-table-command game-id viewer (:params request))
          result (run-use-case store command)]
      (if (or (:left? result) (:ended? result))
        (see-other "/" (expired-player-cookie game-id))
        (status-response store game-id viewer result)))))

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
    {:get start-page-handler}]
   ["/games"
    {:post (create-game-handler store)}]
   ["/games/:id"
    {:get (game-query-handler store game-page-response)}]
   ["/games/:id/join"
    {:post (join-game-handler store)}]
   ["/games/:id/start"
    {:post (game-action-handler store web-commands/start-game-command)}]
   ["/games/:id/play"
    {:post (game-action-handler store web-commands/play-card-command)}]
   ["/games/:id/draw"
    {:post (game-action-handler store web-commands/draw-card-command)}]
   ["/games/:id/pass"
    {:post (game-action-handler store web-commands/pass-turn-command)}]
   ["/games/:id/leave"
    {:post (leave-table-handler store)}]
   ["/games/:id/hand"
     {:get (game-query-handler store hand-fragment-response)}]
   ["/games/:id/events"
    {:get (game-events-handler store)}]
   ["/games/:id/observer/:observer-id"
    {:get (observer-page-handler store)}]
   ["/games/:id/observer/:observer-id/events"
    {:get (observer-events-handler store)}]])

(defn app [{:keys [store]}]
  (let [store (or store (app/create-store))]
    (params/wrap-params
     (cookies/wrap-cookies
      (ring/ring-handler
       (ring/router
         (game-routes store)
         {:data {:middleware [wrap-logging]}})
       (ring/routes
        (ring/create-resource-handler {:path "/assets" :root "public"})
        (ring/create-default-handler
         {:not-found (fn [_] (not-found-html))})))))))
