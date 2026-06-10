# Crazy Eights Domain Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the initial pure Clojure Crazy Eights domain project shell, documentation, domain resources, and passing executable tests for the `:start-game` and `:play-card` slice.

**Architecture:** The implementation stays domain-first and pure. Commands emit events or domain errors, events apply immutable state transitions, and invariants validate resulting state. Documentation and EDN resources define the intended model and rules while tests drive the first working implementation.

**Tech Stack:** Clojure, Malli, test.check, clojure.test, Kaocha

---

## File Map

- Create: `deps.edn` - project dependencies and test alias
- Create: `README.md` - project overview and constraints
- Create: `docs/domain.md` - domain architecture guide
- Create: `docs/adr/0001-domain-first-clojure.md` - ADR for pure domain-first start
- Create: `docs/adr/0002-no-database-initially.md` - ADR for no database / temporary state
- Create: `src/crazy_eights/domain/model.clj` - ranks, suits, constructors, basic value helpers
- Create: `src/crazy_eights/domain/schema.clj` - Malli schemas for domain data
- Create: `src/crazy_eights/domain/rules.clj` - core rule predicates
- Create: `src/crazy_eights/domain/commands.clj` - `:start-game` and `:play-card` decision logic
- Create: `src/crazy_eights/domain/events.clj` - event application functions
- Create: `src/crazy_eights/domain/invariants.clj` - state consistency checks
- Create: `src/crazy_eights/domain/scenarios.clj` - EDN scenario loader and runner helpers
- Create: `src/crazy_eights/domain/generators.clj` - test.check generators for valid domain data
- Create: `resources/domain/model.edn` - domain model description
- Create: `resources/domain/rules.edn` - explicit rules description
- Create: `resources/domain/scenarios/play_matching_rank.edn` - happy-path scenario
- Create: `resources/domain/scenarios/play_eight_declares_suit.edn` - eight play scenario
- Create: `resources/domain/scenarios/cannot_play_invalid_card.edn` - invalid play scenario
- Create: `tests.edn` - Kaocha suite configuration
- Create: `test/crazy_eights/domain/rules_test.clj` - rule predicate tests
- Create: `test/crazy_eights/domain/commands_test.clj` - command behavior tests
- Create: `test/crazy_eights/domain/invariants_test.clj` - invariant tests
- Create: `test/crazy_eights/domain/scenario_test.clj` - EDN scenario tests
- Create: `test/crazy_eights/domain/property_test.clj` - generator-backed property tests

### Task 1: Project Shell And Documentation

**Files:**
- Create: `deps.edn`
- Create: `README.md`
- Create: `docs/domain.md`
- Create: `docs/adr/0001-domain-first-clojure.md`
- Create: `docs/adr/0002-no-database-initially.md`
- Create: `tests.edn`
- Create: `test/crazy_eights/domain/project_shell_test.clj`

- [ ] **Step 1: Write the failing documentation and dependency smoke test**

Create `test/crazy_eights/domain/project_shell_test.clj` with:

```clojure
(ns crazy_eights.domain.project-shell-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]))

(defn resource-exists? [path]
  (boolean (io/resource path)))

(deftest project-shell-files-exist
  (testing "documentation files exist"
    (is (.exists (io/file "README.md")))
    (is (.exists (io/file "docs/domain.md")))
    (is (.exists (io/file "docs/adr/0001-domain-first-clojure.md")))
    (is (.exists (io/file "docs/adr/0002-no-database-initially.md")))))
```

- [ ] **Step 2: Run the smoke test to verify it fails**

Run: `clojure -M:test --focus crazy_eights.domain.project-shell-test`
Expected: FAIL because the test harness is not configured yet and the referenced files do not exist yet.

- [ ] **Step 3: Write the minimal project metadata and docs**

Create `deps.edn` with:

```clojure
{:paths ["src" "resources"]
 :deps {org.clojure/clojure {:mvn/version "1.12.0"}
        metosin/malli {:mvn/version "0.16.4"}
        org.clojure/test.check {:mvn/version "1.1.1"}}
 :aliases
 {:test {:extra-paths ["test"]
         :extra-deps {lambdaisland/kaocha {:mvn/version "1.91.1392"}}
         :main-opts ["-m" "kaocha.runner"]}}}
```

Create `tests.edn` with:

```clojure
#kaocha/v1
{:tests [{:id :unit
          :test-paths ["test"]
          :source-paths ["src"]}]}
```

Create `README.md` with:

```markdown
# crazy-eights

Pure Clojure domain-first Crazy Eights web application project.

## Purpose

This repository starts with the game domain only. The goal is to model Crazy Eights as immutable data and pure functions before adding any web or runtime concerns.

## Architecture Intent

- pure domain core first
- command -> events -> state transition flow
- documentation and executable scenarios drive intended behavior
- future layers will compose around the domain instead of leaking into it

## Current Scope

The current implementation only establishes:

- project shell
- domain namespace structure
- domain resources
- executable tests

## Constraints

- no database
- no durable persistence
- no web server yet
- no UI yet
- no Docker yet
- no runtime state container in the domain

Temporary in-memory application state may exist later outside the domain layer.

## Future Direction

Later iterations may add:

- Ring / Reitit style HTTP architecture
- Hiccup / HTMX style server-rendered UI
- SSE or WebSocket support
- Dockerized deployment

## Domain Purity

Domain namespaces must not depend on HTTP, persistence, logging, config, filesystem, time, randomness, or mutable runtime primitives.
```

Create `docs/domain.md` with:

```markdown
# Domain Architecture

## Namespace Responsibilities

- `crazy_eights.domain.model`: core values and constructors
- `crazy_eights.domain.schema`: Malli schemas for domain data
- `crazy_eights.domain.rules`: pure rule predicates
- `crazy_eights.domain.commands`: command decision logic
- `crazy_eights.domain.events`: event application
- `crazy_eights.domain.invariants`: state consistency checks
- `crazy_eights.domain.scenarios`: EDN scenario loading and execution support
- `crazy_eights.domain.generators`: valid test data generators

## Command / Event Flow

1. A command is evaluated against immutable state.
2. The command handler returns either domain events or a domain error.
3. Events are applied in order to produce the next state.
4. Invariants are checked on successful state transitions.

## Scenario Format

```edn
{:name "play matching rank"
 :given {:state ...}
 :when {:command ...}
 :then {:events [...]
        :state-matches {...}}}
```

Invalid commands use `:then {:error {...}}`.

## Invariants

Successful commands must preserve core state consistency:

- valid cards only
- valid active suit
- valid current player index
- valid player hand structure
- non-empty discard pile while in progress
- winner consistent with an empty hand

## Exclusions

Do not import HTTP, Ring, routing, databases, WebSockets, UI, time, randomness, logging, config, environment, filesystem, or mutable runtime primitives into the domain.
```

Create `docs/adr/0001-domain-first-clojure.md` with:

```markdown
# ADR 0001: Start With A Pure Clojure Domain Core

## Status

Accepted

## Context

The project will grow into a web application, but the early risk is unclear game behavior rather than delivery mechanics.

## Decision

Start with a pure Clojure domain core modeled as immutable data and pure functions.

## Consequences

- game rules can be tested without infrastructure
- scenarios can define intended behavior directly
- future web and runtime layers compose around the domain instead of shaping it prematurely
```

Create `docs/adr/0002-no-database-initially.md` with:

```markdown
# ADR 0002: No Database Initially

## Status

Accepted

## Context

The initial scope is a small multiplayer card game with no requirement for durable history or long-lived records.

## Decision

Do not introduce a database in the initial architecture. Future application state will be temporary and in-memory outside the pure domain layer.

## Consequences

- no persistence complexity in the first slice
- domain model stays independent of storage concerns
- infrastructure decisions can be deferred until a real need appears
```

- [ ] **Step 4: Run the smoke test to verify it passes**

Run: `clojure -M:test --focus crazy_eights.domain.project-shell-test`
Expected: PASS for the new project shell test.

- [ ] **Step 5: Commit the project shell**

```bash
git add deps.edn tests.edn README.md docs/domain.md docs/adr/0001-domain-first-clojure.md docs/adr/0002-no-database-initially.md test/crazy_eights/domain/project_shell_test.clj
git commit -m "chore: bootstrap project docs and test runner"
```

### Task 2: Core Model, Schemas, Rules, And Domain Resources

**Files:**
- Create: `src/crazy_eights/domain/model.clj`
- Create: `src/crazy_eights/domain/schema.clj`
- Create: `src/crazy_eights/domain/rules.clj`
- Create: `resources/domain/model.edn`
- Create: `resources/domain/rules.edn`
- Create: `test/crazy_eights/domain/rules_test.clj`

- [ ] **Step 1: Write the failing rules test**

Create `test/crazy_eights/domain/rules_test.clj` with:

```clojure
(ns crazy_eights.domain.rules-test
  (:require [clojure.test :refer [deftest is testing]]
            [crazy_eights.domain.model :as model]
            [crazy_eights.domain.rules :as rules]))

(deftest card-and-playability-rules
  (let [top-card (model/card :queen :hearts)
        hand [(model/card :queen :clubs)
              (model/card :eight :spades)]
        matching-rank (model/card :queen :clubs)
        wild-eight (model/card :eight :spades)
        wrong-card (model/card :ace :clubs)]
    (testing "basic value predicates"
      (is (rules/valid-suit? :hearts))
      (is (rules/valid-rank? :queen))
      (is (rules/card? top-card))
      (is (rules/card-in-hand? hand matching-rank)))
    (testing "playability"
      (is (rules/playable-card? {:active-suit :hearts
                                 :discard-pile [top-card]}
                                matching-rank))
      (is (rules/playable-card? {:active-suit :clubs
                                 :discard-pile [top-card]}
                                wild-eight))
      (is (not (rules/playable-card? {:active-suit :hearts
                                      :discard-pile [top-card]}
                                     wrong-card))))
    (testing "declared suit requirements"
      (is (rules/requires-declared-suit? wild-eight))
      (is (not (rules/requires-declared-suit? matching-rank)))
      (is (rules/valid-declared-suit? wild-eight :spades))
      (is (not (rules/valid-declared-suit? wild-eight nil))))))
```

- [ ] **Step 2: Run the rules test to verify it fails**

Run: `clojure -M:test --focus crazy_eights.domain.rules-test`
Expected: FAIL because the domain namespaces do not exist yet.

- [ ] **Step 3: Write the minimal model, schema, rules, and resource files**

Create `src/crazy_eights/domain/model.clj` with:

```clojure
(ns crazy_eights.domain.model)

(def suits [:clubs :diamonds :hearts :spades])

(def ranks [:ace :two :three :four :five :six :seven :eight :nine :ten :jack :queen :king])

(defn valid-suit? [suit]
  (contains? (set suits) suit))

(defn valid-rank? [rank]
  (contains? (set ranks) rank))

(defn card [rank suit]
  {:rank rank
   :suit suit})

(defn player [hand]
  {:hand (vec hand)})

(defn card? [value]
  (and (map? value)
       (valid-rank? (:rank value))
       (valid-suit? (:suit value))))
```

Create `src/crazy_eights/domain/schema.clj` with:

```clojure
(ns crazy_eights.domain.schema
  (:require [malli.core :as m]
            [crazy_eights.domain.model :as model]))

(def suit-schema
  (into [:enum {:error/message "valid suit required"}] model/suits))

(def rank-schema
  (into [:enum {:error/message "valid rank required"}] model/ranks))

(def card-schema
  [:map
   [:rank rank-schema]
   [:suit suit-schema]])

(def player-schema
  [:map
   [:hand [:vector card-schema]]])

(def game-state-schema
  [:map
   [:players [:vector player-schema]]
   [:draw-pile [:vector card-schema]]
   [:discard-pile [:vector card-schema]]
   [:active-suit [:maybe suit-schema]]
   [:current-player [:maybe :int]]
   [:status [:enum :waiting-for-start :in-progress :finished]]
   [:winner [:maybe :int]]])

(def command-schema
  [:multi {:dispatch :type}
   [:start-game [:map [:type [:= :start-game]] [:player-count pos-int?] [:deck [:vector card-schema]]]]
   [:play-card [:map [:type [:= :play-card]] [:player :int] [:card card-schema] [:declared-suit {:optional true} [:maybe suit-schema]]]]])

(def event-schema
  [:multi {:dispatch :type}
   [:game-started [:map [:type [:= :game-started]] [:players [:vector player-schema]] [:draw-pile [:vector card-schema]] [:discard-pile [:vector card-schema]] [:active-suit suit-schema] [:current-player :int] [:status [:= :in-progress]] [:winner [:= nil]]]]
   [:card-played [:map [:type [:= :card-played]] [:player :int] [:card card-schema]]]
   [:suit-declared [:map [:type [:= :suit-declared]] [:suit suit-schema]]]
   [:turn-advanced [:map [:type [:= :turn-advanced]] [:player :int]]]
   [:game-won [:map [:type [:= :game-won]] [:player :int]]]])

(def domain-error-schema
  [:map [:type [:= :domain-error]] [:reason keyword?]])

(defn valid? [schema value]
  (m/validate schema value))
```

Create `src/crazy_eights/domain/rules.clj` with:

```clojure
(ns crazy_eights.domain.rules
  (:require [crazy_eights.domain.model :as model]))

(def valid-suit? model/valid-suit?)

(def valid-rank? model/valid-rank?)

(def card? model/card?)

(defn card-in-hand? [hand card]
  (some #(= card %) hand))

(defn requires-declared-suit? [card]
  (= :eight (:rank card)))

(defn valid-declared-suit? [card declared-suit]
  (if (requires-declared-suit? card)
    (valid-suit? declared-suit)
    (nil? declared-suit)))

(defn playable-card? [{:keys [active-suit discard-pile]} card]
  (let [top-card (peek (vec discard-pile))]
    (or (= :eight (:rank card))
        (= active-suit (:suit card))
        (= (:rank top-card) (:rank card)))))
```

Create `resources/domain/model.edn` with:

```edn
{:card {:shape {:rank :keyword
                :suit :keyword}}
 :player {:shape {:hand [:vector :card]}}
 :game-state {:shape {:players [:vector :player]
                      :draw-pile [:vector :card]
                      :discard-pile [:vector :card]
                      :active-suit :keyword
                      :current-player :int
                      :status :keyword
                      :winner [:maybe :int]}}}
```

Create `resources/domain/rules.edn` with:

```edn
{:deck {:suits [:clubs :diamonds :hearts :spades]
        :ranks [:ace :two :three :four :five :six :seven :eight :nine :ten :jack :queen :king]}
 :rules [{:name :matching-suit-or-rank
          :description "A card may be played if it matches the active suit or the top discard rank."}
         {:name :eight-is-wild
          :description "An eight may always be played and requires a declared next suit."}
         {:name :must-hold-card
          :description "A player may not play a card that is not in their hand."}
         {:name :first-empty-hand-wins
          :description "The first player with an empty hand wins."}]}
```

- [ ] **Step 4: Run the rules test to verify it passes**

Run: `clojure -M:test --focus crazy_eights.domain.rules-test`
Expected: PASS for the rule predicate test.

- [ ] **Step 5: Commit the core rule layer**

```bash
git add src/crazy_eights/domain/model.clj src/crazy_eights/domain/schema.clj src/crazy_eights/domain/rules.clj resources/domain/model.edn resources/domain/rules.edn test/crazy_eights/domain/rules_test.clj
git commit -m "feat: add core domain model and rules"
```

### Task 3: Invariants And Start-Game Flow

**Files:**
- Create: `src/crazy_eights/domain/events.clj`
- Create: `src/crazy_eights/domain/invariants.clj`
- Create: `src/crazy_eights/domain/commands.clj`
- Create: `test/crazy_eights/domain/invariants_test.clj`
- Create: `test/crazy_eights/domain/commands_test.clj`

- [ ] **Step 1: Write the failing invariants and start-game tests**

Create `test/crazy_eights/domain/invariants_test.clj` with:

```clojure
(ns crazy_eights.domain.invariants-test
  (:require [clojure.test :refer [deftest is]]
            [crazy_eights.domain.model :as model]
            [crazy_eights.domain.invariants :as invariants]))

(deftest valid-state-passes-invariants
  (let [state {:players [(model/player [(model/card :ace :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile [(model/card :three :diamonds)]
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil}]
    (is (empty? (invariants/check state)))))

(deftest winner-must-have-empty-hand
  (let [state {:players [(model/player [(model/card :ace :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile []
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 1
               :status :finished
               :winner 0}]
    (is (= [{:reason :winner-hand-not-empty}]
           (invariants/check state)))))
```

Create `test/crazy_eights/domain/commands_test.clj` with:

```clojure
(ns crazy_eights.domain.commands-test
  (:require [clojure.test :refer [deftest is testing]]
            [crazy_eights.domain.commands :as commands]
            [crazy_eights.domain.events :as events]
            [crazy_eights.domain.model :as model]))

(def ordered-deck
  [(model/card :ace :clubs)
   (model/card :two :clubs)
   (model/card :three :clubs)
   (model/card :four :clubs)
   (model/card :five :clubs)
   (model/card :six :clubs)
   (model/card :seven :clubs)
   (model/card :eight :clubs)
   (model/card :nine :clubs)
   (model/card :ten :clubs)
   (model/card :jack :clubs)
   (model/card :queen :clubs)
   (model/card :king :clubs)
   (model/card :ace :diamonds)
   (model/card :two :diamonds)])

(deftest start-game-emits-initial-state-event
  (let [events (commands/decide nil {:type :start-game
                                     :player-count 2
                                     :deck ordered-deck})
        state (events/apply-events nil events)]
    (testing "event shape"
      (is (= :game-started (:type (first events)))))
    (testing "resulting state"
      (is (= :in-progress (:status state)))
      (is (= 2 (count (:players state))))
      (is (= :clubs (:active-suit state))))))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `clojure -M:test --focus crazy_eights.domain.invariants-test --focus crazy_eights.domain.commands-test`
Expected: FAIL because the namespaces and functions do not exist yet.

- [ ] **Step 3: Write the minimal invariants, events, and start-game command flow**

Create `src/crazy_eights/domain/invariants.clj` with:

```clojure
(ns crazy_eights.domain.invariants
  (:require [crazy_eights.domain.rules :as rules]))

(defn- valid-card-collection? [cards]
  (every? rules/card? cards))

(defn- winner-empty-hand? [{:keys [players winner]}]
  (or (nil? winner)
      (empty? (get-in players [winner :hand]))))

(defn check [{:keys [players draw-pile discard-pile active-suit current-player status winner] :as state}]
  (cond-> []
    (not (vector? players))
    (conj {:reason :players-not-vector})

    (some #(not (vector? (:hand %))) players)
    (conj {:reason :player-hand-not-vector})

    (not (valid-card-collection? (mapcat :hand players)))
    (conj {:reason :invalid-player-card})

    (not (valid-card-collection? draw-pile))
    (conj {:reason :invalid-draw-pile-card})

    (not (valid-card-collection? discard-pile))
    (conj {:reason :invalid-discard-pile-card})

    (and (= :in-progress status)
         (not (rules/valid-suit? active-suit)))
    (conj {:reason :invalid-active-suit})

    (and (= :in-progress status)
         (or (neg? current-player)
             (<= (count players) current-player)))
    (conj {:reason :invalid-current-player})

    (and (= :in-progress status)
         (empty? discard-pile))
    (conj {:reason :empty-discard-pile})

    (and (= :in-progress status)
         winner)
    (conj {:reason :winner-present-while-in-progress})

    (not (winner-empty-hand? state))
    (conj {:reason :winner-hand-not-empty})))
```

Create `src/crazy_eights/domain/events.clj` with:

```clojure
(ns crazy_eights.domain.events)

(defmulti apply-event (fn [_state event] (:type event)))

(defmethod apply-event :game-started [_state event]
  (dissoc event :type))

(defmethod apply-event :default [state _event]
  state)

(defn apply-events [state events]
  (reduce apply-event state events))
```

Create `src/crazy_eights/domain/commands.clj` with:

```clojure
(ns crazy_eights.domain.commands
  (:require [crazy_eights.domain.model :as model]
            [crazy_eights.domain.rules :as rules]))

(def cards-per-player 5)

(defn- domain-error [reason]
  {:type :domain-error
   :reason reason})

(defn- deal-hands [player-count deck]
  (mapv (fn [index]
          (model/player (take cards-per-player (drop (* index cards-per-player) deck))))
        (range player-count)))

(defn- remaining-deck [player-count deck]
  (drop (* player-count cards-per-player) deck))

(defn- start-game-events [{:keys [player-count deck]}]
  (let [remaining (vec (remaining-deck player-count deck))
        discard (first remaining)
        draw-pile (vec (rest remaining))]
    [{:type :game-started
      :players (deal-hands player-count deck)
      :draw-pile draw-pile
      :discard-pile [discard]
      :active-suit (:suit discard)
      :current-player 0
      :status :in-progress
      :winner nil}]))

(defn decide [state command]
  (case (:type command)
    :start-game (if (and (nil? state)
                         (pos-int? (:player-count command))
                         (every? rules/card? (:deck command))
                         (< (* (:player-count command) cards-per-player)
                            (count (:deck command))))
                  (start-game-events command)
                  (domain-error :invalid-start-game))
    (domain-error :unknown-command)))
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `clojure -M:test --focus crazy_eights.domain.invariants-test --focus crazy_eights.domain.commands-test`
Expected: PASS for both tests.

- [ ] **Step 5: Commit the invariant and start-game slice**

```bash
git add src/crazy_eights/domain/invariants.clj src/crazy_eights/domain/events.clj src/crazy_eights/domain/commands.clj test/crazy_eights/domain/invariants_test.clj test/crazy_eights/domain/commands_test.clj
git commit -m "feat: add start-game command flow"
```

### Task 4: Play-Card Events, Scenarios, And Command Decisions

**Files:**
- Modify: `src/crazy_eights/domain/commands.clj`
- Modify: `src/crazy_eights/domain/events.clj`
- Create: `src/crazy_eights/domain/scenarios.clj`
- Create: `resources/domain/scenarios/play_matching_rank.edn`
- Create: `resources/domain/scenarios/play_eight_declares_suit.edn`
- Create: `resources/domain/scenarios/cannot_play_invalid_card.edn`
- Modify: `test/crazy_eights/domain/commands_test.clj`
- Create: `test/crazy_eights/domain/scenario_test.clj`

- [ ] **Step 1: Write the failing play-card and scenario tests**

Append to `test/crazy_eights/domain/commands_test.clj`:

```clojure
(deftest play-card-emits-card-events
  (let [state {:players [(model/player [(model/card :queen :clubs)
                                        (model/card :eight :spades)])
                        (model/player [(model/card :ace :hearts)])]
               :draw-pile [(model/card :two :diamonds)]
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil}
        events (commands/decide state {:type :play-card
                                       :player 0
                                       :card (model/card :queen :clubs)})]
    (is (= [{:type :card-played
             :player 0
             :card (model/card :queen :clubs)}
            {:type :turn-advanced
             :player 1}]
           events))))

(deftest playing-an-eight-requires-declared-suit
  (let [state {:players [(model/player [(model/card :eight :clubs)])
                        (model/player [(model/card :ace :hearts)])]
               :draw-pile []
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil}]
    (is (= {:type :domain-error :reason :declared-suit-required}
           (commands/decide state {:type :play-card
                                   :player 0
                                   :card (model/card :eight :clubs)})))))
```

Create `test/crazy_eights/domain/scenario_test.clj` with:

```clojure
(ns crazy_eights.domain.scenario-test
  (:require [clojure.test :refer [deftest is testing]]
            [crazy_eights.domain.scenarios :as scenarios]))

(deftest bundled-scenarios-pass
  (doseq [path ["domain/scenarios/play_matching_rank.edn"
                "domain/scenarios/play_eight_declares_suit.edn"
                "domain/scenarios/cannot_play_invalid_card.edn"]]
    (testing path
      (is (true? (:pass? (scenarios/run-scenario-resource path)))))))
```

- [ ] **Step 2: Run the play-card and scenario tests to verify they fail**

Run: `clojure -M:test --focus crazy_eights.domain.commands-test --focus crazy_eights.domain.scenario-test`
Expected: FAIL because `:play-card` logic, scenario support, and resources do not exist yet.

- [ ] **Step 3: Write the minimal play-card flow, event handlers, and scenario resources**

Update `src/crazy_eights/domain/events.clj` to:

```clojure
(ns crazy_eights.domain.events)

(defmulti apply-event (fn [_state event] (:type event)))

(defmethod apply-event :game-started [_state event]
  (dissoc event :type))

(defmethod apply-event :card-played [state {:keys [player card]}]
  (-> state
      (update-in [:players player :hand]
                 (fn [hand]
                   (vec (remove #(= % card) hand))))
      (update :discard-pile conj card)
      (assoc :active-suit (:suit card))))

(defmethod apply-event :suit-declared [state {:keys [suit]}]
  (assoc state :active-suit suit))

(defmethod apply-event :turn-advanced [state {:keys [player]}]
  (assoc state :current-player player))

(defmethod apply-event :game-won [state {:keys [player]}]
  (assoc state :status :finished
               :winner player))

(defmethod apply-event :default [state _event]
  state)

(defn apply-events [state events]
  (reduce apply-event state events))
```

Update `src/crazy_eights/domain/commands.clj` to:

```clojure
(ns crazy_eights.domain.commands
  (:require [crazy_eights.domain.model :as model]
            [crazy_eights.domain.rules :as rules]))

(def cards-per-player 5)

(defn- domain-error [reason]
  {:type :domain-error
   :reason reason})

(defn- deal-hands [player-count deck]
  (mapv (fn [index]
          (model/player (take cards-per-player (drop (* index cards-per-player) deck))))
        (range player-count)))

(defn- remaining-deck [player-count deck]
  (drop (* player-count cards-per-player) deck))

(defn- start-game-events [{:keys [player-count deck]}]
  (let [remaining (vec (remaining-deck player-count deck))
        discard (first remaining)
        draw-pile (vec (rest remaining))]
    [{:type :game-started
      :players (deal-hands player-count deck)
      :draw-pile draw-pile
      :discard-pile [discard]
      :active-suit (:suit discard)
      :current-player 0
      :status :in-progress
      :winner nil}]))

(defn- next-player [state]
  (mod (inc (:current-player state))
       (count (:players state))))

(defn- play-card-events [state {:keys [player card declared-suit]}]
  (let [current-hand (get-in state [:players player :hand])]
    (cond
      (not= player (:current-player state))
      (domain-error :not-current-player)

      (not (rules/card-in-hand? current-hand card))
      (domain-error :card-not-in-hand)

      (not (rules/playable-card? state card))
      (domain-error :card-not-playable)

      (and (rules/requires-declared-suit? card)
           (not (rules/valid-declared-suit? card declared-suit)))
      (domain-error :declared-suit-required)

      :else
      (cond-> [{:type :card-played
                :player player
                :card card}]
        (rules/requires-declared-suit? card)
        (conj {:type :suit-declared
               :suit declared-suit})

        (empty? (remove #(= % card) current-hand))
        (conj {:type :game-won
               :player player})

        (not (empty? (remove #(= % card) current-hand)))
        (conj {:type :turn-advanced
               :player (next-player state)})))))

(defn decide [state command]
  (case (:type command)
    :start-game (if (and (nil? state)
                         (pos-int? (:player-count command))
                         (every? rules/card? (:deck command))
                         (< (* (:player-count command) cards-per-player)
                            (count (:deck command))))
                  (start-game-events command)
                  (domain-error :invalid-start-game))
    :play-card (play-card-events state command)
    (domain-error :unknown-command)))
```

Create `src/crazy_eights/domain/scenarios.clj` with:

```clojure
(ns crazy_eights.domain.scenarios
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [crazy_eights.domain.commands :as commands]
            [crazy_eights.domain.events :as events]))

(defn load-scenario-resource [path]
  (-> path io/resource slurp edn/read-string))

(defn- subset-match? [expected actual]
  (every? (fn [[k v]]
            (let [actual-value (get actual k)]
              (if (map? v)
                (subset-match? v actual-value)
                (= v actual-value))))
          expected))

(defn run-scenario [scenario]
  (let [state (get-in scenario [:given :state])
        command (get-in scenario [:when :command])
        result (commands/decide state command)]
    (if (= :domain-error (:type result))
      {:pass? (= (get-in scenario [:then :error])
                 (select-keys result [:reason]))}
      (let [next-state (events/apply-events state result)]
        {:pass? (and (= (get-in scenario [:then :events]) result)
                     (subset-match? (get-in scenario [:then :state-matches]) next-state))}))))

(defn run-scenario-resource [path]
  (run-scenario (load-scenario-resource path)))
```

Create `resources/domain/scenarios/play_matching_rank.edn` with:

```edn
{:name "play matching rank"
 :given {:state {:players [{:hand [{:rank :queen :suit :clubs}]}
                           {:hand [{:rank :ace :suit :spades}]}]
                 :draw-pile [{:rank :two :suit :diamonds}]
                 :discard-pile [{:rank :queen :suit :hearts}]
                 :active-suit :hearts
                 :current-player 0
                 :status :in-progress
                 :winner nil}}
 :when {:command {:type :play-card
                  :player 0
                  :card {:rank :queen :suit :clubs}}}
 :then {:events [{:type :card-played
                  :player 0
                  :card {:rank :queen :suit :clubs}}
                 {:type :game-won
                  :player 0}]
        :state-matches {:status :finished
                        :winner 0
                        :active-suit :clubs}}}
```

Create `resources/domain/scenarios/play_eight_declares_suit.edn` with:

```edn
{:name "play eight declares suit"
 :given {:state {:players [{:hand [{:rank :eight :suit :clubs}
                                    {:rank :queen :suit :clubs}]}
                           {:hand [{:rank :ace :suit :spades}]}]
                 :draw-pile [{:rank :two :suit :diamonds}]
                 :discard-pile [{:rank :king :suit :hearts}]
                 :active-suit :hearts
                 :current-player 0
                 :status :in-progress
                 :winner nil}}
 :when {:command {:type :play-card
                  :player 0
                  :card {:rank :eight :suit :clubs}
                  :declared-suit :spades}}
 :then {:events [{:type :card-played
                  :player 0
                  :card {:rank :eight :suit :clubs}}
                 {:type :suit-declared
                  :suit :spades}
                 {:type :turn-advanced
                  :player 1}]
        :state-matches {:active-suit :spades
                        :current-player 1
                        :status :in-progress}}}
```

Create `resources/domain/scenarios/cannot_play_invalid_card.edn` with:

```edn
{:name "cannot play invalid card"
 :given {:state {:players [{:hand [{:rank :ace :suit :clubs}]}
                           {:hand [{:rank :king :suit :spades}]}]
                 :draw-pile [{:rank :two :suit :diamonds}]
                 :discard-pile [{:rank :queen :suit :hearts}]
                 :active-suit :hearts
                 :current-player 0
                 :status :in-progress
                 :winner nil}}
 :when {:command {:type :play-card
                  :player 0
                  :card {:rank :ace :suit :clubs}}}
 :then {:error {:reason :card-not-playable}}}
```

- [ ] **Step 4: Run the play-card and scenario tests to verify they pass**

Run: `clojure -M:test --focus crazy_eights.domain.commands-test --focus crazy_eights.domain.scenario-test`
Expected: PASS for command behavior and bundled scenarios.

- [ ] **Step 5: Commit the play-card slice**

```bash
git add src/crazy_eights/domain/commands.clj src/crazy_eights/domain/events.clj src/crazy_eights/domain/scenarios.clj resources/domain/scenarios/play_matching_rank.edn resources/domain/scenarios/play_eight_declares_suit.edn resources/domain/scenarios/cannot_play_invalid_card.edn test/crazy_eights/domain/commands_test.clj test/crazy_eights/domain/scenario_test.clj
git commit -m "feat: add play-card command scenarios"
```

### Task 5: Generators, Property Test, And Full Verification

**Files:**
- Create: `src/crazy_eights/domain/generators.clj`
- Create: `test/crazy_eights/domain/property_test.clj`
- Modify: `test/crazy_eights/domain/invariants_test.clj`

- [ ] **Step 1: Write the failing property and invariant preservation tests**

Create `test/crazy_eights/domain/property_test.clj` with:

```clojure
(ns crazy_eights.domain.property-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [crazy_eights.domain.generators :as generators]
            [crazy_eights.domain.rules :as rules]))

(defspec generated-cards-are-valid 50
  (prop/for-all [card generators/card-gen]
    (rules/card? card)))

(deftest generated-hands-contain-only-valid-cards
  (let [hand (first (clojure.test.check.generators/sample generators/hand-gen 1))]
    (is (every? rules/card? hand))))
```

Append to `test/crazy_eights/domain/invariants_test.clj`:

```clojure
(deftest played-card-state-preserves-invariants
  (let [state {:players [(model/player [(model/card :queen :clubs)
                                        (model/card :ace :clubs)])
                        (model/player [(model/card :king :spades)])]
               :draw-pile [(model/card :two :diamonds)]
               :discard-pile [(model/card :queen :hearts)]
               :active-suit :hearts
               :current-player 0
               :status :in-progress
               :winner nil}
        events (commands/decide state {:type :play-card
                                       :player 0
                                       :card (model/card :queen :clubs)})
        next-state (events/apply-events state events)]
    (is (vector? events))
    (is (empty? (invariants/check next-state)))))
```

Update the namespace requires in `test/crazy_eights/domain/invariants_test.clj` to include:

```clojure
[crazy_eights.domain.commands :as commands]
[crazy_eights.domain.events :as events]
```

- [ ] **Step 2: Run the property and invariant tests to verify they fail**

Run: `clojure -M:test --focus crazy_eights.domain.property-test --focus crazy_eights.domain.invariants-test`
Expected: FAIL because generators do not exist yet.

- [ ] **Step 3: Write the minimal generators**

Create `src/crazy_eights/domain/generators.clj` with:

```clojure
(ns crazy_eights.domain.generators
  (:require [clojure.test.check.generators :as gen]
            [crazy_eights.domain.model :as model]))

(def suit-gen
  (gen/elements model/suits))

(def rank-gen
  (gen/elements model/ranks))

(def card-gen
  (gen/fmap (fn [[rank suit]]
              (model/card rank suit))
            (gen/tuple rank-gen suit-gen)))

(def hand-gen
  (gen/vector card-gen 0 5))

(def player-gen
  (gen/fmap model/player hand-gen))

(def game-state-gen
  (gen/let [players (gen/vector player-gen 2 4)
            top-card card-gen
            draw-pile (gen/vector card-gen 0 10)]
    {:players players
     :draw-pile draw-pile
     :discard-pile [top-card]
     :active-suit (:suit top-card)
     :current-player 0
     :status :in-progress
     :winner nil}))
```

- [ ] **Step 4: Run the full test suite to verify everything passes**

Run: `clojure -M:test`
Expected: PASS for all unit, scenario, and property tests.

- [ ] **Step 5: Commit the generator and verification slice**

```bash
git add src/crazy_eights/domain/generators.clj test/crazy_eights/domain/property_test.clj test/crazy_eights/domain/invariants_test.clj
git commit -m "test: add domain property coverage"
```

### Task 6: Final Resource And Namespace Coverage Check

**Files:**
- Modify: `src/crazy_eights/domain/schema.clj`
- Modify: `test/crazy_eights/domain/commands_test.clj`

- [ ] **Step 1: Write the failing schema coverage test**

Append to `test/crazy_eights/domain/commands_test.clj`:

```clojure
(deftest command-and-event-shapes-match-schema
  (let [start-events (commands/decide nil {:type :start-game
                                           :player-count 2
                                           :deck ordered-deck})]
    (is (schema/valid? schema/command-schema {:type :start-game
                                              :player-count 2
                                              :deck ordered-deck}))
    (is (every? #(schema/valid? schema/event-schema %) start-events))))
```

Update the namespace requires in `test/crazy_eights/domain/commands_test.clj` to include:

```clojure
[crazy_eights.domain.schema :as schema]
```

- [ ] **Step 2: Run the command test to verify it fails if schema coverage is missing**

Run: `clojure -M:test --focus crazy_eights.domain.commands-test`
Expected: FAIL if schema helpers or event shapes drift from implementation.

- [ ] **Step 3: Adjust schema definitions if needed to match produced data exactly**

If any mismatch appears, keep `src/crazy_eights/domain/schema.clj` aligned with the actual command and event maps. The target shapes are:

```clojure
[:map [:type [:= :start-game]] [:player-count pos-int?] [:deck [:vector card-schema]]]

[:map [:type [:= :game-started]]
      [:players [:vector player-schema]]
      [:draw-pile [:vector card-schema]]
      [:discard-pile [:vector card-schema]]
      [:active-suit suit-schema]
      [:current-player :int]
      [:status [:= :in-progress]]
      [:winner [:= nil]]]
```

- [ ] **Step 4: Run the full suite again**

Run: `clojure -M:test`
Expected: PASS with schema coverage included.

- [ ] **Step 5: Commit the final alignment**

```bash
git add src/crazy_eights/domain/schema.clj test/crazy_eights/domain/commands_test.clj
git commit -m "test: align domain schema coverage"
```

## Plan Review Notes

- Spec coverage: covered project shell, docs, domain namespaces, EDN resources, start-game and play-card commands, invariants, scenarios, generators, and passing tests.
- Placeholder scan: no temporary markers remain in code or command steps.
- Type consistency: commands, events, state shape, schema names, and namespace naming are consistent across tasks.
