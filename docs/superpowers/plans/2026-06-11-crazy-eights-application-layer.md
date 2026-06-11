# Crazy Eights Application Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a thin in-memory application layer with lightweight player identities, seat assignment, action submission, pub/sub updates, and one full-game simulation against the app layer.

**Architecture:** Keep the domain pure and build only a small in-memory shell around it. Start with `app.core` for registry/lifecycle/action flow and `app.pubsub` for subscriber management. Application events remain plain maps, and simulation logic stays entirely in tests.

**Tech Stack:** Clojure, clojure.test, Kaocha

---

## File Map

- Create: `src/crazy_eights/app/pubsub.clj`
- Create: `src/crazy_eights/app/core.clj`
- Create: `test/crazy_eights/app/core_test.clj`
- Create: `test/crazy_eights/app/simulation_test.clj`
- Modify: `README.md`
- Modify: `docs/domain.md`

### Task 1: Add Pub/Sub Primitive And Failing App Lifecycle Tests

**Files:**
- Create: `test/crazy_eights/app/core_test.clj`
- Create: `src/crazy_eights/app/pubsub.clj`

- [ ] **Step 1: Write the failing app-layer tests**

Create `test/crazy_eights/app/core_test.clj` with:

```clojure
(ns crazy_eights.app.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [crazy_eights.app.core :as app]
            [crazy_eights.domain.model :as model]))

(deftest create-and-join-game
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        p1 (app/join-game! store game-id)
        p2 (app/join-game! store game-id)
        game (app/get-game store game-id)]
    (is (string? game-id))
    (is (string? (:player-id p1)))
    (is (= 0 (:seat p1)))
    (is (= 1 (:seat p2)))
    (is (= #{(:player-id p1) (:player-id p2)}
           (set (keys (:players game)))))))

(deftest submit-action-starts-game-and-stores-state
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        _p1 (app/join-game! store game-id)
        _p2 (app/join-game! store game-id)
        deck model/full-deck
        result (app/submit-action! store game-id {:type :start-game
                                                  :player-count 2
                                                  :deck deck})
        game (app/get-game store game-id)]
    (is (vector? (:events result)))
    (is (= :in-progress (get-in game [:state :status])))))

(deftest player-action-translates-player-id-to-seat
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        p1 (app/join-game! store game-id)
        _p2 (app/join-game! store game-id)
        deck (vec (concat [(model/card :queen :clubs)
                           (model/card :ace :diamonds)
                           (model/card :queen :spades)
                           (model/card :queen :diamonds)
                           (model/card :eight :diamonds)
                           (model/card :two :hearts)
                           (model/card :ace :clubs)
                           (model/card :king :spades)
                           (model/card :queen :hearts)
                           (model/card :jack :clubs)
                           (model/card :three :clubs)
                           (model/card :four :clubs)]
                          (drop 12 model/full-deck)))]
    (app/submit-action! store game-id {:type :start-game
                                       :player-count 2
                                       :deck deck})
    (let [result (app/submit-player-action! store game-id (:player-id p1)
                                            {:type :play-card
                                             :card (model/card :queen :clubs)})]
      (is (= :card-played (:type (first (:events result))))))))

(deftest subscribers-receive-app-events-in-order
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        _p1 (app/join-game! store game-id)
        _p2 (app/join-game! store game-id)
        sink (atom [])]
    (app/subscribe! store game-id :test-subscriber #(swap! sink conj %))
    (app/submit-action! store game-id {:type :start-game
                                       :player-count 2
                                       :deck model/full-deck})
    (is (= [:game-created :player-joined :player-joined :game-started]
           (map :type @sink)))))
```

- [ ] **Step 2: Run the focused app test to verify it fails**

Run: `clojure -M:test --focus crazy_eights.app.core-test`
Expected: FAIL because `app.core` and `app.pubsub` do not exist yet.

- [ ] **Step 3: Create the minimal pub/sub namespace**

Create `src/crazy_eights/app/pubsub.clj` with:

```clojure
(ns crazy_eights.app.pubsub)

(defn subscribe [subscribers subscriber-id handler]
  (assoc subscribers subscriber-id handler))

(defn unsubscribe [subscribers subscriber-id]
  (dissoc subscribers subscriber-id))

(defn publish [subscribers event]
  (doseq [[_ handler] subscribers]
    (handler event))
  subscribers)
```

- [ ] **Step 4: Commit the red tests and pubsub groundwork**

```bash
git add test/crazy_eights/app/core_test.clj src/crazy_eights/app/pubsub.clj
git commit -m "test: add application layer coverage"
```

### Task 2: Implement The In-Memory Application Core

**Files:**
- Create: `src/crazy_eights/app/core.clj`
- Modify: `test/crazy_eights/app/core_test.clj`

- [ ] **Step 1: Implement the minimal app core**

Create `src/crazy_eights/app/core.clj` with:

```clojure
(ns crazy_eights.app.core
  (:require [crazy_eights.app.pubsub :as pubsub]
            [crazy_eights.domain.commands :as commands]
            [crazy_eights.domain.events :as events]))

(defn create-store []
  (atom {:next-game-id 0
         :games {}}))

(defn- next-game-id [store-state]
  (str "game-" (:next-game-id store-state)))

(defn- next-player-id [game]
  (str (:game-id game) "-player-" (count (:players game))))

(defn- emit! [store game-id event]
  (let [subscribers (get-in @store [:games game-id :subscribers] {})]
    (pubsub/publish subscribers event)))

(defn create-game! [store]
  (let [created (atom nil)]
    (swap! store
           (fn [state]
             (let [game-id (next-game-id state)
                   game {:game-id game-id
                         :state nil
                         :players {}
                         :subscribers {}}]
               (reset! created {:game-id game-id})
               (-> state
                   (update :next-game-id inc)
                   (assoc-in [:games game-id] game)))))
    (emit! store (:game-id @created) {:type :game-created
                                      :game-id (:game-id @created)})
    @created))

(defn join-game! [store game-id]
  (let [joined (atom nil)]
    (swap! store
           (fn [state]
             (let [game (get-in state [:games game-id])
                   seat (count (:players game))
                   player-id (next-player-id game)]
               (reset! joined {:player-id player-id :seat seat})
               (assoc-in state [:games game-id :players player-id] {:seat seat}))))
    (emit! store game-id {:type :player-joined
                          :game-id game-id
                          :player-id (:player-id @joined)
                          :seat (:seat @joined)})
    @joined))

(defn get-game [store game-id]
  (get-in @store [:games game-id]))

(defn subscribe! [store game-id subscriber-id handler]
  (swap! store update-in [:games game-id :subscribers]
         #(pubsub/subscribe (or % {}) subscriber-id handler))
  subscriber-id)

(defn unsubscribe! [store game-id subscriber-id]
  (swap! store update-in [:games game-id :subscribers]
         #(pubsub/unsubscribe (or % {}) subscriber-id))
  subscriber-id)

(defn submit-action! [store game-id command]
  (let [result (atom nil)]
    (swap! store
           (fn [state]
             (let [game (get-in state [:games game-id])
                   current-state (:state game)
                   decision (commands/decide current-state command)]
               (reset! result decision)
               (if (= :domain-error (:type decision))
                 state
                 (assoc-in state
                           [:games game-id :state]
                           (events/apply-events current-state decision))))))
    (when (vector? @result)
      (let [next-state (get-in @store [:games game-id :state])]
        (emit! store game-id {:type (if (= :start-game (:type command))
                                      :game-started
                                      :move-made)
                            :game-id game-id
                            :command command
                            :events @result
                            :state next-state})
        (emit! store game-id {:type :turn-changed
                              :game-id game-id
                              :current-player (:current-player next-state)
                              :state next-state})
        (when (= :finished (:status next-state))
          (emit! store game-id {:type :game-finished
                                :game-id game-id
                                :winner (:winner next-state)
                                :state next-state}))))
    {:events @result})

(defn submit-player-action! [store game-id player-id action]
  (let [seat (get-in @store [:games game-id :players player-id :seat])]
    (submit-action! store game-id (assoc action :player seat))))
```

- [ ] **Step 2: Run the focused app tests to verify they pass**

Run: `clojure -M:test --focus crazy_eights.app.core-test`
Expected: PASS for lifecycle, join, translation, and subscription behavior.

- [ ] **Step 3: Commit the application core**

```bash
git add src/crazy_eights/app/core.clj test/crazy_eights/app/core_test.clj
git commit -m "feat: add in-memory application layer"
```

### Task 3: Add Full-Game Simulation Against The Application Layer

**Files:**
- Create: `test/crazy_eights/app/simulation_test.clj`

- [ ] **Step 1: Write the failing app simulation test**

Create `test/crazy_eights/app/simulation_test.clj` with:

```clojure
(ns crazy_eights.app.simulation-test
  (:require [clojure.test :refer [deftest is]]
            [crazy_eights.app.core :as app]
            [crazy_eights.domain.model :as model]))

(defn shuffle-deck [deck]
  (vec (shuffle deck)))

(defn valid-start-deck [player-count]
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
          deck)))))

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

(defn run-app-simulated-game [player-count]
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        players (vec (repeatedly player-count #(app/join-game! store game-id)))
        _start (app/submit-action! store game-id {:type :start-game
                                                  :player-count player-count
                                                  :deck (valid-start-deck player-count)})
        event-log (atom [])]
    (app/subscribe! store game-id :simulation #(swap! event-log conj %))
    (loop [steps-left 500]
      (let [state (get-in (app/get-game store game-id) [:state])]
        (if (or (= :finished (:status state)) (zero? steps-left))
          {:state state :events @event-log :steps-left steps-left :players players}
          (let [player-index (:current-player state)
                player-id (:player-id (nth players player-index))
                action (choose-player-action state)]
            (app/submit-player-action! store game-id player-id action)
            (recur (dec steps-left))))))))

(deftest app-layer-simulated-games-finish
  (doseq [player-count [2 3 4]]
    (let [{:keys [state events steps-left]} (run-app-simulated-game player-count)
          event-log (with-out-str (doseq [event events] (prn event)))]
      (is (= :finished (:status state))
          (str "app simulation did not finish for " player-count " players\n" event-log))
      (is (pos? steps-left)
          (str "app simulation exhausted step budget for " player-count " players\n" event-log)))))
```

- [ ] **Step 2: Run the focused app simulation test to verify it fails**

Run: `clojure -M:test --focus crazy_eights.app.simulation-test`
Expected: FAIL until the app shell and simulation assumptions line up.

- [ ] **Step 3: Run app-focused and full verification**

Run:

```bash
clojure -M:test --focus crazy_eights.app.core-test --focus crazy_eights.app.simulation-test
clojure -M:test
clojure -M:lint
```

Expected: PASS for app-layer tests, full suite, and lint.

- [ ] **Step 4: Commit the simulation coverage**

```bash
git add test/crazy_eights/app/simulation_test.clj
git commit -m "test: add application layer simulation"
```

### Task 4: Update Docs For The Application Layer

**Files:**
- Modify: `README.md`
- Modify: `docs/domain.md`

- [ ] **Step 1: Update the README current scope and test commands**

Adjust `README.md` so `## Current Scope` also mentions:

- in-memory application layer
- lightweight player identities
- app-level simulation tests

And under `## Tests` add one app simulation example command:

```bash
clojure -M:test --focus crazy_eights.app.simulation-test
```

- [ ] **Step 2: Update `docs/domain.md` to mention the boundary**

Add a short section stating that:

- the domain remains pure
- the application layer is the in-memory stateful shell around it
- messaging transport stays outside the application layer

- [ ] **Step 3: Commit the docs alignment**

```bash
git add README.md docs/domain.md
git commit -m "docs: describe application layer boundary"
```

## Plan Review Notes

- Spec coverage: covers app registry, player identities, action submission, pub/sub events, app-layer simulation, and boundary documentation.
- Placeholder scan: no placeholders remain; all files, tests, commands, and snippets are explicit.
- Type consistency: the app layer uses `game-id`, `player-id`, domain seat mapping, and app events consistently, while leaving transport concerns outside the application namespaces.
