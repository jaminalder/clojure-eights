# Domain Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove duplicate domain specification artifacts and simplify namespace boundaries so the code and executable tests are the primary source of truth.

**Architecture:** Keep only executable domain artifacts. Scenario EDN stays because tests run it. Remove descriptive-only EDN and schema layers because they duplicate intent already expressed in code and tests. Simplify namespace boundaries where one namespace mostly re-exports another.

**Tech Stack:** Clojure, clojure.test, test.check, Kaocha

---

## File Map

- Modify: `README.md`
- Modify: `docs/domain.md`
- Modify: `src/crazy_eights/domain/model.clj`
- Delete: `src/crazy_eights/domain/rules.clj`
- Delete: `src/crazy_eights/domain/schema.clj`
- Modify: `src/crazy_eights/domain/commands.clj`
- Modify: `src/crazy_eights/domain/invariants.clj`
- Modify: `src/crazy_eights/domain/generators.clj`
- Delete: `resources/domain/model.edn`
- Delete: `resources/domain/rules.edn`
- Modify: `test/crazy_eights/domain/rules_test.clj`
- Modify: `test/crazy_eights/domain/property_test.clj`
- Modify: `test/crazy_eights/domain/commands_test.clj`

### Task 1: Remove Schema And Descriptive Resource Duplication

**Files:**
- Modify: `test/crazy_eights/domain/commands_test.clj`
- Delete: `src/crazy_eights/domain/schema.clj`
- Delete: `resources/domain/model.edn`
- Delete: `resources/domain/rules.edn`

- [ ] **Step 1: Write the failing cleanup test change**

Update `test/crazy_eights/domain/commands_test.clj` by removing the schema require and the schema-specific test:

```clojure
(ns crazy_eights.domain.commands-test
  (:require [clojure.test :refer [deftest is testing]]
            [crazy_eights.domain.commands :as commands]
            [crazy_eights.domain.events :as events]
            [crazy_eights.domain.model :as model]))
```

Delete this test block entirely:

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

- [ ] **Step 2: Run the focused test suite to verify the remaining tests still pass without schema coverage**

Run: `clojure -M:test --focus crazy_eights.domain.commands-test`
Expected: PASS with the schema test removed.

- [ ] **Step 3: Remove the duplicate non-executable artifacts**

Delete these files:

```text
src/crazy_eights/domain/schema.clj
resources/domain/model.edn
resources/domain/rules.edn
```

- [ ] **Step 4: Run the full test suite**

Run: `clojure -M:test`
Expected: PASS with no code depending on deleted schema/resource files.

### Task 2: Collapse The Thin Rules Namespace Into Model

**Files:**
- Modify: `src/crazy_eights/domain/model.clj`
- Delete: `src/crazy_eights/domain/rules.clj`
- Modify: `src/crazy_eights/domain/commands.clj`
- Modify: `src/crazy_eights/domain/invariants.clj`
- Modify: `test/crazy_eights/domain/rules_test.clj`
- Modify: `test/crazy_eights/domain/property_test.clj`

- [ ] **Step 1: Write the failing namespace-boundary test change**

Update `test/crazy_eights/domain/rules_test.clj` to require only `crazy_eights.domain.model` and call the behavior from there:

```clojure
(ns crazy_eights.domain.rules-test
  (:require [clojure.test :refer [deftest is testing]]
            [crazy_eights.domain.model :as model]))
```

Change every `rules/...` call to `model/...`, including:

```clojure
(model/valid-suit? :hearts)
(model/valid-rank? :queen)
(model/card? top-card)
(model/card-in-hand? hand matching-rank)
(model/playable-card? {...} matching-rank)
(model/requires-declared-suit? wild-eight)
(model/valid-declared-suit? wild-eight :spades)
```

Update `test/crazy_eights/domain/property_test.clj` to require only `crazy_eights.domain.model` and replace `rules/card?` with `model/card?`.

- [ ] **Step 2: Run the focused tests to verify they fail because model does not expose the full rule surface yet**

Run: `clojure -M:test --focus crazy_eights.domain.rules-test --focus crazy_eights.domain.property-test`
Expected: FAIL with missing vars like `model/card-in-hand?` or `model/playable-card?`.

- [ ] **Step 3: Move the rule functions into model and update callers**

Update `src/crazy_eights/domain/model.clj` to:

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

(defn card-in-hand? [hand card]
  (boolean (some #(= card %) hand)))

(defn requires-declared-suit? [card]
  (= :eight (:rank card)))

(defn valid-declared-suit? [card declared-suit]
  (if (requires-declared-suit? card)
    (valid-suit? declared-suit)
    (nil? declared-suit)))

(defn playable-card? [{:keys [active-suit discard-pile]} card]
  (let [top-card (peek discard-pile)]
    (or (= :eight (:rank card))
        (= active-suit (:suit card))
        (= (:rank top-card) (:rank card)))))
```

Update `src/crazy_eights/domain/commands.clj` to require only model and replace every `rules/...` reference with `model/...`.

Update `src/crazy_eights/domain/invariants.clj` to require only model and replace:

```clojure
rules/card? -> model/card?
rules/valid-suit? -> model/valid-suit?
```

Delete `src/crazy_eights/domain/rules.clj`.

Update `test/crazy_eights/domain/property_test.clj` to:

```clojure
(ns crazy_eights.domain.property-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [crazy_eights.domain.generators :as generators]
            [crazy_eights.domain.model :as model]))
```

- [ ] **Step 4: Run the focused tests to verify the simplified boundary passes**

Run: `clojure -M:test --focus crazy_eights.domain.rules-test --focus crazy_eights.domain.property-test --focus crazy_eights.domain.commands-test --focus crazy_eights.domain.invariants-test`
Expected: PASS with no remaining dependency on `crazy_eights.domain.rules`.

### Task 3: Tighten Documentation Around Code-As-Spec

**Files:**
- Modify: `README.md`
- Modify: `docs/domain.md`

- [ ] **Step 1: Update the README to match the actual project and remove stale “planned next steps” language**

Replace `README.md` contents with:

```markdown
# crazy-eights

Pure Clojure Crazy Eights domain model.

## Purpose

This repository starts with the game domain only. The goal is to model Crazy Eights as immutable data and pure functions before adding application or web concerns.

## Current Scope

The current implementation includes:

- domain data and constructors
- command decisions for `:start-game` and `:play-card`
- event application
- invariant checks
- executable EDN scenarios
- property and unit tests

## How To Read The Project

The code is the primary specification.

- domain behavior lives in `src/crazy_eights/domain/`
- tests under `test/crazy_eights/domain/` are the executable spec
- scenario EDN under `resources/domain/scenarios/` is kept only because it is executed by tests

## Constraints

- no database
- no durable persistence
- no web server yet
- no UI yet
- no Docker yet
- no runtime state container in the domain

Temporary in-memory application state may exist later outside the domain layer.

## Domain Purity

Domain namespaces must not depend on HTTP, persistence, logging, config, filesystem, time, randomness, or mutable runtime primitives.

## Tests

Run all tests:

```bash
clojure -M:test
```

Run one namespace:

```bash
clojure -M:test --focus crazy_eights.domain.commands-test
```
```

- [ ] **Step 2: Update the domain doc so it explains only the real moving parts**

Replace `docs/domain.md` contents with:

```markdown
# Domain Architecture

The domain is intentionally small.

## Namespaces

- `crazy_eights.domain.model`: domain values, constructors, and rule predicates
- `crazy_eights.domain.commands`: command decision logic
- `crazy_eights.domain.events`: event application
- `crazy_eights.domain.invariants`: state consistency checks
- `crazy_eights.domain.scenarios`: EDN scenario execution support
- `crazy_eights.domain.generators`: test data generators

## Flow

1. A command is evaluated against immutable state.
2. The command returns either domain events or a domain error.
3. Events are applied in order to produce the next state.
4. Tests and scenarios describe the intended behavior.

## What Counts As Specification

- source code in `src/crazy_eights/domain/`
- unit and property tests in `test/crazy_eights/domain/`
- executable scenario data in `resources/domain/scenarios/`

Anything else should justify itself by affecting execution or preventing confusion.
```

- [ ] **Step 3: Run the full test suite again**

Run: `clojure -M:test`
Expected: PASS with documentation aligned and no behavior changes.

## Plan Review Notes

- Spec coverage: removes non-executable duplicate artifacts, simplifies the model/rules boundary, and aligns docs to code-as-spec.
- Placeholder scan: no placeholders remain.
- Type consistency: all former `rules` call sites move to `model`; no remaining references to schema or descriptive EDN should remain.
