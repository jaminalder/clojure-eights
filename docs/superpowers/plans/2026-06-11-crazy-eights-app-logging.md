# Crazy Eights Application Logging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add optional infrastructure-style logging around the application layer by subscribing to app events, and hook that logger into the app-layer simulation flow.

**Architecture:** Keep `app.core` free of direct logging calls. Add one logging namespace that converts app events to structured log entries and provides a stdout subscriber. Logging remains an optional subscriber on app events and is reused by the app simulation test.

**Tech Stack:** Clojure, clojure.test, Kaocha

---

## File Map

- Create: `src/crazy_eights/app/logging.clj`
- Create: `test/crazy_eights/app/logging_test.clj`
- Modify: `test/crazy_eights/app/simulation_test.clj`
- Modify: `README.md`

### Task 1: Add Logging Namespace And Unit Tests

**Files:**
- Create: `test/crazy_eights/app/logging_test.clj`
- Create: `src/crazy_eights/app/logging.clj`

- [ ] **Step 1: Write the failing logging tests**

Create `test/crazy_eights/app/logging_test.clj` with:

```clojure
(ns crazy_eights.app.logging-test
  (:require [clojure.test :refer [deftest is]]
            [crazy_eights.app.logging :as logging]))

(deftest event->log-entry-preserves-key-data
  (let [event {:type :move-made
               :game-id "game-1"
               :command {:type :play-card}
               :events [{:type :card-played}]}
        entry (logging/event->log-entry event)]
    (is (= :info (:level entry)))
    (is (= :move-made (:event entry)))
    (is (= "game-1" (:game-id entry)))
    (is (= event (:data entry)))))

(deftest stdout-subscriber-writes-log-entry
  (let [event {:type :game-started
               :game-id "game-2"}
        output (with-out-str
                 ((logging/stdout-subscriber) event))]
    (is (.contains output ":game-started"))
    (is (.contains output "game-2"))))
```

- [ ] **Step 2: Run the focused logging test to verify it fails**

Run: `clojure -M:test --focus crazy_eights.app.logging-test`
Expected: FAIL because `crazy_eights.app.logging` does not exist yet.

- [ ] **Step 3: Implement the minimal logging namespace**

Create `src/crazy_eights/app/logging.clj` with:

```clojure
(ns crazy_eights.app.logging)

(defn event->log-entry [event]
  {:level :info
   :event (:type event)
   :game-id (:game-id event)
   :data event})

(defn stdout-subscriber []
  (fn [event]
    (prn (event->log-entry event))))
```

- [ ] **Step 4: Run the focused logging tests to verify they pass**

Run: `clojure -M:test --focus crazy_eights.app.logging-test`
Expected: PASS.

- [ ] **Step 5: Commit the logging namespace**

```bash
git add src/crazy_eights/app/logging.clj test/crazy_eights/app/logging_test.clj
git commit -m "feat: add application logging subscriber"
```

### Task 2: Hook Logging Into The App Simulation Flow

**Files:**
- Modify: `test/crazy_eights/app/simulation_test.clj`

- [ ] **Step 1: Write the failing simulation logging test**

Append to `test/crazy_eights/app/simulation_test.clj`:

```clojure
(deftest app-simulation-logger-prints-events
  (let [output (with-out-str
                 (let [store (app/create-store)
                       {:keys [game-id]} (app/create-game! store)]
                   (app/subscribe! store game-id :stdout (logging/stdout-subscriber))
                   (app/join-game! store game-id)
                   (app/join-game! store game-id)
                   (app/submit-action! store game-id {:type :start-game
                                                      :player-count 2
                                                      :deck (valid-start-deck 2)})))]
    (is (.contains output ":game-started"))
    (is (.contains output ":turn-changed"))))
```

Update the namespace requires in `test/crazy_eights/app/simulation_test.clj` to include:

```clojure
[crazy_eights.app.logging :as logging]
```

- [ ] **Step 2: Run the focused app simulation test to verify it fails**

Run: `clojure -M:test --focus crazy_eights.app.simulation-test`
Expected: FAIL until the logging namespace is wired into the simulation test.

- [ ] **Step 3: Keep the app simulation and logging decoupled**

Do not add logging calls inside `app.core`. Only attach the logger by subscribing it in tests or runtime wiring.

- [ ] **Step 4: Run focused app tests and full verification**

Run:

```bash
clojure -M:test --focus crazy_eights.app.core-test --focus crazy_eights.app.logging-test --focus crazy_eights.app.simulation-test
clojure -M:test
clojure -M:lint
```

Expected: PASS for app-layer focused tests, full suite, and lint.

- [ ] **Step 5: Commit the app simulation logging hookup**

```bash
git add test/crazy_eights/app/simulation_test.clj
git commit -m "test: log app simulation events"
```

### Task 3: Document Application Logging Usage

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add one short README example**

Extend `README.md` with a short note under `## Tests` or a small new subsection showing that app logging is provided by a subscriber and that the app simulation test exercises it.

Add this command example:

```bash
clojure -M:test --focus crazy_eights.app.simulation-test
```

And one sentence explaining that application logging is implemented as an app-event subscriber, not inside the domain.

- [ ] **Step 2: Run final verification commands again**

Run:

```bash
clojure -M:test
clojure -M:lint
```

Expected: PASS / clean lint.

- [ ] **Step 3: Commit the docs update**

```bash
git add README.md
git commit -m "docs: describe application logging"
```

## Plan Review Notes

- Spec coverage: adds a dedicated logging namespace, unit coverage, app simulation logging integration, and a short usage note.
- Placeholder scan: no placeholders remain; every file path, test, and command is explicit.
- Type consistency: app events remain plain maps with `:type` and `:game-id`, and logging consumes those events without introducing a separate transport or storage layer.
