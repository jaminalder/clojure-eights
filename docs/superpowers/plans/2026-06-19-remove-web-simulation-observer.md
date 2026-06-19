# Remove Web Simulation Observer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the simulation observer from the web layer while keeping player web gameplay and app/domain simulations working.

**Architecture:** Delete the HTTP adapter for simulations and keep the application simulation service as a non-web utility. The web layer continues to expose only player game routes and game-fragment SSE.

**Tech Stack:** Clojure 1.12, Ring/Reitit, http-kit SSE, Hiccup, Kaocha, clj-kondo.

---

## File Structure

- Modify `test/crazy_eights/web/routes_test.clj`: replace observer route tests with explicit not-found checks for removed simulation routes.
- Modify `test/crazy_eights/web/sse_test.clj`: remove simulation SSE tests and keep game SSE tests.
- Modify `test/crazy_eights/web/game_routes_test.clj`: stop passing `:simulation-service nil` to the web app constructor.
- Modify `src/crazy_eights/web/routes.clj`: remove simulation route helpers, simulation dependencies, and simulation route registration.
- Modify `src/crazy_eights/web/sse.clj`: remove simulation SSE functions and simulation dependency.
- Modify `src/crazy_eights/web/server.clj`: stop constructing a simulation service for web startup.
- Delete `src/crazy_eights/web/page.clj`: remove the observer page.
- Modify `README.md` and `CLAUDE.md`: remove web observer documentation while keeping simulation commands.
- Keep `src/crazy_eights/app/simulation.clj`, simulation tests, and simulation aliases unchanged.

### Task 1: Specify Removed Web Routes

**Files:**
- Modify: `test/crazy_eights/web/routes_test.clj`

- [ ] **Step 1: Replace observer tests with not-found expectations**

Use this complete file content:

```clojure
(ns crazy_eights.web.routes-test
  (:require [clojure.test :refer [deftest is testing]]
            [crazy_eights.web.routes :as routes]))

(defn- response-for [method uri]
  ((routes/app {}) {:request-method method :uri uri}))

(deftest simulation-observer-routes-are-not-served
  (testing "observer page"
    (is (= 404 (:status (response-for :get "/observer")))))
  (testing "simulation start endpoint"
    (is (= 404 (:status (response-for :post "/simulations")))))
  (testing "simulation event stream endpoint"
    (is (= 404 (:status (response-for :get "/simulations/sim-1/events"))))))

(deftest unknown-route-returns-404
  (is (= 404 (:status (response-for :get "/favicon.ico")))))
```

- [ ] **Step 2: Run focused route test and confirm failure before implementation**

Run: `clojure -M:test --focus crazy_eights.web.routes-test`

Expected: FAIL because current web routes still serve `/observer` and `/simulations`.

### Task 2: Remove Simulation SSE Web Tests

**Files:**
- Modify: `test/crazy_eights/web/sse_test.clj`

- [ ] **Step 1: Remove simulation-specific SSE tests and dependency**

Use this complete file content:

```clojure
(ns crazy_eights.web.sse-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [crazy_eights.app.core :as app]
            [crazy_eights.web.sse :as sse]
            [org.httpkit.server :as http]))

(defn- recording-channel [sent]
  (reify http/Channel
    (open? [_] true)
    (close [_] true)
    (websocket? [_] false)
    (send! [_ data] (swap! sent conj data) true)
    (send! [_ data _close-after-send?] (swap! sent conj data) true)))

(deftest game-on-open-sends-fragments-now-and-on-app-events
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        _p1 (app/join-game! store game-id "anna")
        sent (atom [])
        channel (recording-channel sent)
        render (fn [] {:status "<p>s</p>"
                       :game-board "<div>b</div>"
                       :player-hand "<div>h</div>"})]
    (sse/game-on-open store game-id render channel)
    (testing "open response plus an immediate fragment frame"
      (let [[open frame] @sent]
        (is (= 200 (:status open)))
        (is (str/includes? frame "event: status\ndata: <p>s</p>"))
        (is (str/includes? frame "event: game-board\ndata: <div>b</div>"))
        (is (str/includes? frame "event: player-hand\ndata: <div>h</div>"))))
    (testing "app events push fresh fragments"
      (let [frames-before (count @sent)]
        (app/join-game! store game-id "ben")
        (is (= (inc frames-before) (count @sent)))))
    (testing "fragment frames never contain stray newlines in data"
      (is (str/includes?
           (sse/fragment-frames {:status "multi\nline"})
           "data: multiline")))))

(deftest table-ended-event-redirects-and-closes-game-stream
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        p1 (app/join-game! store game-id "anna")
        sent (atom [])
        channel (recording-channel sent)
        render (fn [] {:status "<p>s</p>"})]
    (sse/game-on-open store game-id render channel)
    (app/leave-table! store game-id (:player-id p1))
    (is (some #(and (string? %)
                    (str/includes? % "event: table-ended")
                    (str/includes? % "window.location.href='/'"))
              @sent))
    (is (str/includes? (sse/table-ended-frame) "event: table-ended"))))
```

- [ ] **Step 2: Run focused SSE test**

Run: `clojure -M:test --focus crazy_eights.web.sse-test`

Expected: PASS before implementation because game SSE still exists.

### Task 3: Remove Simulation Routes and Page

**Files:**
- Modify: `src/crazy_eights/web/routes.clj`
- Delete: `src/crazy_eights/web/page.clj`

- [ ] **Step 1: Remove simulation dependencies and route helpers**

In `src/crazy_eights/web/routes.clj`, remove these requires:

```clojure
[crazy_eights.app.simulation :as simulation]
[crazy_eights.domain.model :as model]
[crazy_eights.web.page :as page]
```

Delete the functions `simulation-response`, `simulation-sse-response`, `observer-handler`, `start-simulation-handler`, `simulation-events-handler`, and `simulation-routes`.

Change `app` to register only game routes:

```clojure
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
```

- [ ] **Step 2: Delete observer page namespace**

Delete `src/crazy_eights/web/page.clj`.

- [ ] **Step 3: Run route test**

Run: `clojure -M:test --focus crazy_eights.web.routes-test`

Expected: PASS.

### Task 4: Remove Simulation SSE Functions

**Files:**
- Modify: `src/crazy_eights/web/sse.clj`

- [ ] **Step 1: Remove simulation dependency and functions**

Use this complete file content:

```clojure
(ns crazy_eights.web.sse
  (:require [clojure.string :as str]
            [crazy_eights.app.core :as app]
            [org.httpkit.server :as http]))

(def ^:private open-response
  {:status 200
   :headers {"Content-Type" "text/event-stream"
             "Cache-Control" "no-cache"
             "Connection" "keep-alive"}
   :body "event: open\ndata: {\"status\":\"connected\"}\n\n"})

(defn fragment-frames
  "One SSE message per fragment: {:status html ...} becomes
  'event: status\\ndata: <html>\\n\\n...'. Data is forced onto a single line."
  [fragments]
  (apply str
         (map (fn [[fragment-name html]]
                (str "event: " (name fragment-name) "\n"
                     "data: " (str/replace html "\n" "") "\n\n"))
              fragments)))

(defn table-ended-frame []
  (str "event: table-ended\n"
       "data: <script>window.location.href='/'</script>\n\n"))

(defn- game-event-frame [render-fragments event]
  (if (= :table-ended (:type event))
    (table-ended-frame)
    (fragment-frames (render-fragments))))

(defn game-on-open
  "Subscribes the channel to app events before anything can move, then sends
  the current fragments so no transition between page render and connect is lost."
  [store game-id render-fragments channel]
  (http/send! channel open-response false)
  (app/subscribe! store game-id channel
                  (fn [event]
                    (http/send! channel (game-event-frame render-fragments event) false)))
  (http/send! channel (fragment-frames (render-fragments)) false))

(defn game-events-response [store game-id render-fragments request]
  (http/as-channel request
                   {:on-open (partial game-on-open store game-id render-fragments)
                    :on-close (fn [channel _status]
                                (app/unsubscribe! store game-id channel))}))
```

- [ ] **Step 2: Run focused SSE test**

Run: `clojure -M:test --focus crazy_eights.web.sse-test`

Expected: PASS.

### Task 5: Remove Web Startup Simulation Service

**Files:**
- Modify: `src/crazy_eights/web/server.clj`
- Modify: `test/crazy_eights/web/game_routes_test.clj`

- [ ] **Step 1: Simplify web server startup**

Use this complete file content for `src/crazy_eights/web/server.clj`:

```clojure
(ns crazy_eights.web.server
  (:require [crazy_eights.app.core :as app]
            [crazy_eights.web.routes :as routes]
            [org.httpkit.server :as http]))

(defonce server (atom nil))

(defn start! []
  (let [store (app/create-store)
        handler (routes/app {:store store})]
    (reset! server (http/run-server handler {:port 8080}))
    (println "server started on http://localhost:8080")))

(defn stop! []
  (when-let [stop-server @server]
    (stop-server)
    (reset! server nil)))

(defn -main [& _args]
  (start!)
  @(promise))
```

- [ ] **Step 2: Simplify game route test handler helper**

Change `test/crazy_eights/web/game_routes_test.clj` lines 8-9 to:

```clojure
(defn- handler [store]
  (routes/app {:store store}))
```

- [ ] **Step 3: Run focused game route tests**

Run: `clojure -M:test --focus crazy_eights.web.game-routes-test`

Expected: PASS.

### Task 6: Update Documentation

**Files:**
- Modify: `README.md`
- Modify: `CLAUDE.md`

- [ ] **Step 1: Update README web scope and run instructions**

In `README.md`, remove the bullet that says:

```markdown
- a minimal web observer page (`/observer`) that streams simulation logs over SSE
```

Change the web transport bullet to:

```markdown
- web transport (routes, game views, SSE) lives in `src/crazy_eights/web/`
```

Change the run-web text to:

```markdown
Then open `http://localhost:8080` to host a multiplayer game. Game state is
in-memory only: a server restart forgets all games.
```

- [ ] **Step 2: Update CLAUDE architecture notes**

Change the command comment to:

```bash
clojure -M:run-web                                           # multiplayer game web UI on http://localhost:8080
```

Change the web architecture paragraph so it says:

```markdown
connected viewer on each app event (htmx sse extension swaps them in);
`cards` maps cards to codes/SVG paths; `server` is the entry point. Static
assets live in `resources/public` (52 public-domain card SVGs + htmx vendored).
```

- [ ] **Step 3: Search for stale web observer documentation**

Run: `rg "observer|/simulations|simulation observer" README.md CLAUDE.md src test`

Expected: Only the negative web route test should mention the removed observer routes. Matches in app/domain simulation namespaces and tests are acceptable when they do not describe a web observer implementation.

### Task 7: Verify Simulations Still Work Outside Web

**Files:**
- No source edits expected.

- [ ] **Step 1: Run domain simulation test**

Run: `clojure -M:test --focus crazy_eights.domain.simulation-test`

Expected: PASS.

- [ ] **Step 2: Run application simulation tests**

Run: `clojure -M:test --focus crazy_eights.app.simulation-test`

Expected: PASS.

Run: `clojure -M:test --focus crazy_eights.app.simulation-runtime-test`

Expected: PASS.

### Task 8: Final Verification

**Files:**
- No source edits expected unless verification finds a defect.

- [ ] **Step 1: Run full tests**

Run: `clojure -M:test`

Expected: PASS.

- [ ] **Step 2: Run lint**

Run: `clojure -M:lint`

Expected: PASS with no findings.

- [ ] **Step 3: Run live web verification**

Run: `./scripts/verify-web`

Expected: PASS. This proves the player web game still works after observer removal.

- [ ] **Step 4: Confirm no web dependency on app simulation remains**

Run: `rg "crazy_eights.app.simulation|/observer|/simulations|simulation-service|run-simulation" src/crazy_eights/web`

Expected: No matches.

Run: `rg "/observer|/simulations|simulation observer" README.md CLAUDE.md`

Expected: No matches.

- [ ] **Step 5: Commit implementation**

Run:

```bash
git status --short
git diff
git add src/crazy_eights/web/routes.clj src/crazy_eights/web/sse.clj src/crazy_eights/web/server.clj test/crazy_eights/web/routes_test.clj test/crazy_eights/web/sse_test.clj test/crazy_eights/web/game_routes_test.clj README.md CLAUDE.md
git rm src/crazy_eights/web/page.clj
git commit -m "remove web simulation observer"
```

Expected: Commit succeeds and does not include unrelated files.
