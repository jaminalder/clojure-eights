# Crazy Eights Web Observer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a minimal web observer page that can start a backend simulation and stream move logs to the browser over SSE.

**Architecture:** Build a small Ring/Reitit/Hiccup web shell around the existing app layer. Keep simulation runtime in a separate application namespace under `src/`, and have the web layer only handle request routing, page rendering, and SSE delivery.

**Tech Stack:** Clojure, Ring, Reitit, Hiccup, http-kit, clojure.test, Kaocha

---

## File Map

- Modify: `deps.edn`
- Create: `src/crazy_eights/app/simulation.clj`
- Create: `src/crazy_eights/web/page.clj`
- Create: `src/crazy_eights/web/routes.clj`
- Create: `src/crazy_eights/web/server.clj`
- Create: `test/crazy_eights/app/simulation_runtime_test.clj`
- Create: `test/crazy_eights/web/routes_test.clj`
- Modify: `README.md`

### Task 1: Add Web Dependencies And Failing Web Route Tests

**Files:**
- Modify: `deps.edn`
- Create: `test/crazy_eights/web/routes_test.clj`

- [ ] **Step 1: Write the failing web route tests**

Create `test/crazy_eights/web/routes_test.clj` with:

```clojure
(ns crazy_eights.web.routes-test
  (:require [clojure.test :refer [deftest is]]
            [crazy_eights.web.routes :as routes]))

(deftest observer-page-renders-html
  (let [handler (routes/app {:simulation-service nil})
        response (handler {:request-method :get
                           :uri "/"})]
    (is (= 200 (:status response)))
    (is (= "text/html; charset=utf-8"
           (get-in response [:headers "Content-Type"])))
    (is (.contains (:body response) "start simulation"))))

(deftest start-simulation-validates-player-count
  (let [handler (routes/app {:simulation-service {:start! (fn [_] {:simulation-id "sim-1"})}})
        invalid (handler {:request-method :post
                          :uri "/simulations"
                          :params {"player-count" "1"}})
        valid (handler {:request-method :post
                        :uri "/simulations"
                        :params {"player-count" "4"}})]
    (is (= 400 (:status invalid)))
    (is (= 202 (:status valid)))))
```

- [ ] **Step 2: Run the focused web route test to verify it fails**

Run: `clojure -M:test --focus crazy_eights.web.routes-test`
Expected: FAIL because the web namespaces and dependencies do not exist yet.

- [ ] **Step 3: Add the minimal web dependencies and aliases**

Update `deps.edn` to:

```clojure
{:paths ["src" "resources"]
 :mvn/repos {"artifactory.swisscom.com" {:url "https://repo.maven.apache.org/maven2/"}
             "artifactory-oce" {:url "https://repo.clojars.org/"}}
 :deps {org.clojure/clojure {:mvn/version "1.12.0"}
        org.clojure/test.check {:mvn/version "1.1.1"}
        ring/ring-core {:mvn/version "1.13.0"}
        metosin/reitit-ring {:mvn/version "0.7.2"}
        hiccup/hiccup {:mvn/version "2.0.0-RC3"}
        http-kit/http-kit {:mvn/version "2.8.0"}}
 :aliases
 {:test {:extra-paths ["test"]
         :extra-deps {lambdaisland/kaocha {:mvn/version "1.91.1392"}}
         :main-opts ["-m" "kaocha.runner"]}
  :sim-log {:extra-paths ["test"]
            :extra-deps {lambdaisland/kaocha {:mvn/version "1.91.1392"}}
            :main-opts ["-m" "crazy_eights.domain.simulation-runner"]}
  :app-sim-log {:extra-paths ["test"]
                :extra-deps {lambdaisland/kaocha {:mvn/version "1.91.1392"}}
                :main-opts ["-m" "crazy_eights.app.simulation-runner"]}
  :lint {:extra-deps {clj-kondo/clj-kondo {:mvn/version "2024.08.29"}}
         :main-opts ["-m" "clj-kondo.main" "--lint" "src" "test"]}
  :run-web {:main-opts ["-m" "crazy_eights.web.server"]}}}
```

- [ ] **Step 4: Commit the dependency and red-test setup**

```bash
git add deps.edn test/crazy_eights/web/routes_test.clj
git commit -m "test: add web observer route coverage"
```

### Task 2: Add Runtime Simulation Service

**Files:**
- Create: `src/crazy_eights/app/simulation.clj`
- Create: `test/crazy_eights/app/simulation_runtime_test.clj`

- [ ] **Step 1: Write the failing runtime simulation tests**

Create `test/crazy_eights/app/simulation_runtime_test.clj` with:

```clojure
(ns crazy_eights.app.simulation-runtime-test
  (:require [clojure.test :refer [deftest is]]
            [crazy_eights.app.simulation :as simulation]))

(deftest start-simulation-creates-entry
  (let [service (simulation/create-service {:delay-fn (fn [] nil)})
        {:keys [simulation-id]} (simulation/start! service 3)
        sim (simulation/get-simulation service simulation-id)]
    (is (string? simulation-id))
    (is (= 3 (:player-count sim)))))

(deftest simulation-publishes-log-events
  (let [service (simulation/create-service {:delay-fn (fn [] nil)})
        sink (atom [])
        {:keys [simulation-id]} (simulation/start! service 2)]
    (simulation/subscribe! service simulation-id :test #(swap! sink conj %))
    (simulation/run-to-completion! service simulation-id)
    (is (seq @sink))
    (is (some #(= :log (:type %)) @sink))))
```

- [ ] **Step 2: Run the focused runtime simulation test to verify it fails**

Run: `clojure -M:test --focus crazy_eights.app.simulation-runtime-test`
Expected: FAIL because the simulation runtime namespace does not exist yet.

- [ ] **Step 3: Implement the minimal runtime simulation service**

Create `src/crazy_eights/app/simulation.clj` with:

```clojure
(ns crazy_eights.app.simulation
  (:require [crazy_eights.app.core :as app]
            [crazy_eights.app.logging :as logging]
            [crazy_eights.app.pubsub :as pubsub]
            [crazy_eights.domain.model :as model]))

(defn create-service [{:keys [delay-fn]}]
  (atom {:next-simulation-id 0
         :delay-fn (or delay-fn (fn [] (Thread/sleep 500)))
         :simulations {}}))

(defn- next-simulation-id [service-state]
  (str "sim-" (:next-simulation-id service-state)))

(defn get-simulation [service simulation-id]
  (get-in @service [:simulations simulation-id]))

(defn subscribe! [service simulation-id subscriber-id handler]
  (swap! service update-in [:simulations simulation-id :subscribers]
         #(pubsub/subscribe (or % {}) subscriber-id handler))
  subscriber-id)

(defn- emit! [service simulation-id event]
  (let [subscribers (get-in @service [:simulations simulation-id :subscribers] {})]
    (pubsub/publish subscribers event)))

(defn shuffle-deck [deck]
  (vec (shuffle deck)))

(defn valid-start-deck [player-count]
  (if (<= 2 player-count model/max-player-count)
    (loop []
      (let [deck (shuffle-deck model/full-deck)
            store (app/create-store)
            {:keys [game-id]} (app/create-game! store)]
        (dotimes [_ player-count]
          (app/join-game! store game-id))
        (let [result (app/submit-action! store game-id {:type :start-game
                                                        :player-count player-count
                                                        :deck deck})]
          (if (= :domain-error (:type (:events result)))
            (recur)
            deck))))
    (throw (ex-info "invalid player-count for single deck"
                    {:player-count player-count
                     :max-player-count model/max-player-count}))))

(defn playable-card [state player]
  (first (filter #(model/playable-card? state %)
                 (get-in state [:players player :hand]))))

(defn choose-player-action [state]
  (let [player (:current-player state)]
    (cond
      (playable-card state player)
      (let [card (playable-card state player)]
        (cond-> {:type :play-card :card card}
          (model/requires-declared-suit? card)
          (assoc :declared-suit :spades)))

      (seq (:draw-pile state))
      {:type :draw-card}

      (model/reshuffleable? state)
      {:type :reshuffle-draw-pile
       :cards (model/reshuffle-cards (:discard-pile state))}

      :else
      {:type :pass-turn})))

(defn- log-event [service simulation-id app-event]
  (emit! service simulation-id {:type :log
                                :message (pr-str (logging/event->log-entry app-event))
                                :app-event app-event}))

(defn start! [service player-count]
  (let [simulation-id (atom nil)]
    (swap! service
           (fn [state]
             (let [id (next-simulation-id state)
                   store (app/create-store)
                   {:keys [game-id]} (app/create-game! store)
                   players (vec (repeatedly player-count #(app/join-game! store game-id)))
                   sim {:simulation-id id
                        :game-id game-id
                        :player-count player-count
                        :store store
                        :players players
                        :status :ready
                        :subscribers {}}]
               (reset! simulation-id id)
               (-> state
                   (update :next-simulation-id inc)
                   (assoc-in [:simulations id] sim)))))
    {:simulation-id @simulation-id})

(defn run-to-completion! [service simulation-id]
  (let [{:keys [store game-id player-count players]} (get-simulation service simulation-id)
        deck (valid-start-deck player-count)]
    (app/subscribe! store game-id :simulation-logger #(log-event service simulation-id %))
    (app/submit-action! store game-id {:type :start-game
                                       :player-count player-count
                                       :deck deck})
    (loop [steps-left 500]
      (let [state (:state (app/get-game store game-id))]
        (if (or (= :finished (:status state)) (zero? steps-left))
          (swap! service assoc-in [:simulations simulation-id :status] (:status state))
          (let [player-index (:current-player state)
                player-id (:player-id (nth players player-index))
                action (choose-player-action state)]
            ((:delay-fn @service))
            (app/submit-player-action! store game-id player-id action)
            (recur (dec steps-left))))))))
```

- [ ] **Step 4: Run the focused runtime simulation test to verify it passes**

Run: `clojure -M:test --focus crazy_eights.app.simulation-runtime-test`
Expected: PASS.

- [ ] **Step 5: Commit the runtime simulation service**

```bash
git add src/crazy_eights/app/simulation.clj test/crazy_eights/app/simulation_runtime_test.clj
git commit -m "feat: add runtime simulation service"
```

### Task 3: Add Web Page, Routes, And SSE Streaming

**Files:**
- Create: `src/crazy_eights/web/page.clj`
- Create: `src/crazy_eights/web/routes.clj`
- Create: `src/crazy_eights/web/server.clj`

- [ ] **Step 1: Implement the observer page**

Create `src/crazy_eights/web/page.clj` with:

```clojure
(ns crazy_eights.web.page
  (:require [hiccup2.core :as h]))

(defn observer-page []
  (str
   (h/html
    [:html
     [:head
      [:meta {:charset "utf-8"}]
      [:title "Crazy Eights Simulation Observer"]]
     [:body
      [:h1 "Crazy Eights Simulation Observer"]
      [:form {:id "simulation-form"}
       [:label {:for "player-count"} "Players"]
       [:input {:id "player-count"
                :name "player-count"
                :type "number"
                :min 2
                :max 10
                :value 4}]
       [:button {:type "submit"} "start simulation"]]
      [:pre {:id "log"}]
      [:script
       "const form = document.getElementById('simulation-form');\n"
       "const log = document.getElementById('log');\n"
       "form.addEventListener('submit', async (event) => {\n"
       "  event.preventDefault();\n"
       "  log.textContent = '';\n"
       "  const playerCount = document.getElementById('player-count').value;\n"
       "  const response = await fetch('/simulations', {method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body: new URLSearchParams({ 'player-count': playerCount })});\n"
       "  const data = await response.json();\n"
       "  const source = new EventSource(`/simulations/${data.simulation-id}/events`);\n"
       "  source.addEventListener('log', (e) => { const msg = JSON.parse(e.data); log.textContent += msg.message + '\\n'; });\n"
       "});"]]])))
```

- [ ] **Step 2: Implement the routes and SSE endpoint**

Create `src/crazy_eights/web/routes.clj` with:

```clojure
(ns crazy_eights.web.routes
  (:require [clojure.data.json :as json]
            [crazy_eights.web.page :as page]
            [reitit.ring :as ring]))

(defn response [status body content-type]
  {:status status
   :headers {"Content-Type" content-type}
   :body body})

(defn app [{:keys [simulation-service]}]
  (ring/ring-handler
   (ring/router
    [["/"
      {:get (fn [_]
              (response 200 (page/observer-page) "text/html; charset=utf-8"))}]
     ["/simulations"
      {:post (fn [request]
               (let [player-count (some-> (get-in request [:params "player-count"]) parse-long)]
                 (if (and player-count (<= 2 player-count 10))
                   (let [{:keys [simulation-id]} ((:start! simulation-service) player-count)]
                     (response 202
                               (json/write-str {:simulation-id simulation-id})
                               "application/json"))
                   (response 400
                             (json/write-str {:error "invalid player-count"})
                             "application/json"))))}]
     ["/simulations/:id/events"
      {:get (fn [request]
              ((:sse-response simulation-service) (get-in request [:path-params :id])))}]])))
```

- [ ] **Step 3: Implement the dev server entrypoint**

Create `src/crazy_eights/web/server.clj` with:

```clojure
(ns crazy_eights.web.server
  (:require [org.httpkit.server :as http]
            [crazy_eights.app.simulation :as simulation]
            [crazy_eights.web.routes :as routes]))

(defonce server (atom nil))

(defn -main [& _args]
  (let [service (simulation/create-service {:delay-fn (fn [] (Thread/sleep 500))})
        handler (routes/app {:simulation-service {:start! #(simulation/start! service %)
                                                  :sse-response #(simulation/sse-response service %)}})]
    (reset! server (http/run-server handler {:port 8080}))
    (println "server started on http://localhost:8080")))
```

- [ ] **Step 4: Run the focused route test to verify it still fails**

Run: `clojure -M:test --focus crazy_eights.web.routes-test`
Expected: FAIL until the simulation service exposes the SSE response function and all route dependencies are satisfied.

### Task 4: Add SSE Plumbing In The Runtime Simulation Service

**Files:**
- Modify: `src/crazy_eights/app/simulation.clj`
- Modify: `test/crazy_eights/app/simulation_runtime_test.clj`
- Modify: `test/crazy_eights/web/routes_test.clj`

- [ ] **Step 1: Add failing route/runtime expectations for SSE**

Extend `test/crazy_eights/app/simulation_runtime_test.clj` with:

```clojure
(deftest simulation-sse-subscriber-receives-log-events
  (let [service (simulation/create-service {:delay-fn (fn [] nil)})
        sink (atom [])
        {:keys [simulation-id]} (simulation/start! service 2)]
    (simulation/subscribe! service simulation-id :test #(swap! sink conj %))
    (simulation/run-to-completion! service simulation-id)
    (is (seq @sink))
    (is (every? #(= :log (:type %)) @sink))))
```

Replace `test/crazy_eights/web/routes_test.clj` with:

```clojure
(ns crazy_eights.web.routes-test
  (:require [clojure.test :refer [deftest is]]
            [crazy_eights.web.routes :as routes]))

(deftest observer-page-renders-html
  (let [handler (routes/app {:simulation-service nil})
        response (handler {:request-method :get
                           :uri "/"})]
    (is (= 200 (:status response)))
    (is (= "text/html; charset=utf-8"
           (get-in response [:headers "Content-Type"])))
    (is (.contains (:body response) "start simulation"))))

(deftest start-simulation-validates-player-count
  (let [handler (routes/app {:simulation-service {:start! (fn [_] {:simulation-id "sim-1"})
                                                  :sse-response (fn [_] {:status 200 :body "" :headers {}})}})
        invalid (handler {:request-method :post
                          :uri "/simulations"
                          :params {"player-count" "1"}})
        valid (handler {:request-method :post
                        :uri "/simulations"
                        :params {"player-count" "4"}})]
    (is (= 400 (:status invalid)))
    (is (= 202 (:status valid)))))

(deftest sse-endpoint-delegates-to-service
  (let [called (atom nil)
        handler (routes/app {:simulation-service {:start! (fn [_] {:simulation-id "sim-1"})
                                                  :sse-response (fn [simulation-id]
                                                                  (reset! called simulation-id)
                                                                  {:status 200
                                                                   :headers {"Content-Type" "text/event-stream"}
                                                                   :body ""})}})
        response (handler {:request-method :get
                           :uri "/simulations/sim-1/events"
                           :path-params {:id "sim-1"}})]
    (is (= "sim-1" @called))
    (is (= 200 (:status response)))))
```

- [ ] **Step 2: Run the focused runtime and route tests to verify they fail**

Run: `clojure -M:test --focus crazy_eights.app.simulation-runtime-test --focus crazy_eights.web.routes-test`
Expected: FAIL because the simulation service does not yet expose SSE integration.

- [ ] **Step 3: Extend the simulation runtime with subscribers and SSE output**

Update `src/crazy_eights/app/simulation.clj` to add:

```clojure
(defn subscribe! [service simulation-id subscriber-id handler]
  (swap! service update-in [:simulations simulation-id :subscribers]
         #(pubsub/subscribe (or % {}) subscriber-id handler))
  subscriber-id)

(defn unsubscribe! [service simulation-id subscriber-id]
  (swap! service update-in [:simulations simulation-id :subscribers]
         #(pubsub/unsubscribe (or % {}) subscriber-id))
  subscriber-id)

(defn sse-response [service simulation-id]
  {:status 200
   :headers {"Content-Type" "text/event-stream"
             "Cache-Control" "no-cache"
             "Connection" "keep-alive"}
   :body (fn [send!]
           (subscribe! service simulation-id send!
                       (fn [event]
                         (send! (str "event: log\n"
                                     "data: "
                                     (pr-str {:message (:message event)})
                                     "\n\n")))) )})
```

and make `emit!` publish to the per-simulation subscriber map.

The exact mechanics can stay simple as long as:

- simulation events become `:log` events
- subscribers receive them in order
- the route can delegate to `sse-response`

- [ ] **Step 4: Run focused web/runtime tests**

Run: `clojure -M:test --focus crazy_eights.app.simulation-runtime-test --focus crazy_eights.web.routes-test`
Expected: PASS.

### Task 5: Update Docs And Run Full Verification

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add web observer commands to the README**

Extend `README.md` `## Tests` or add a small runtime section with:

```bash
clojure -M:run-web
```

And a short note that the observer page is available at `http://localhost:8080` and streams simulation logs over SSE.

- [ ] **Step 2: Run the complete verification set**

Run:

```bash
clojure -M:test
clojure -M:lint
```

Expected: PASS / clean lint.

- [ ] **Step 3: Commit the web observer implementation**

```bash
git add src/crazy_eights/app/simulation.clj src/crazy_eights/web src/crazy_eights/app/pubsub.clj test/crazy_eights/app/simulation_runtime_test.clj test/crazy_eights/web/routes_test.clj README.md
git commit -m "feat: add web simulation observer"
```

## Plan Review Notes

- Spec coverage: covers runtime simulation service, web page, routes, SSE delivery, route/runtime tests, and startup docs.
- Placeholder scan: no placeholders remain; file paths, code, and commands are explicit.
- Type consistency: web talks only to routes, routes delegate to runtime simulation service, runtime simulation drives the app layer, and browser-visible logs stay simple `:log` events.
