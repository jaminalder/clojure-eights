# Composable Simulation Strategies Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build composable Crazy Eights strategy rules and a synchronous experiment runner that compares strategy performance.

**Architecture:** Keep strategies pure and data-oriented: observations, legal candidates, scoring rules, and composed chooser functions. Keep experiments as simulation app clients that run normal app games with per-seat strategies and aggregate plain data statistics. Operator remains a REPL adapter over one experiment use case.

**Tech Stack:** Clojure 1.12, existing pure domain model, in-memory app store, simulation client layer, clojure.test, clj-kondo.

---

## File Structure

- Modify `src/crazy_eights/simulation/strategy.clj`: observation, legal candidates, scoring rules, composed strategy maps, action cleanup.
- Modify `test/crazy_eights/simulation/strategy_test.clj`: executable strategy/rule examples.
- Modify `src/crazy_eights/simulation/app.clj`: per-seat strategy selection and run metrics.
- Modify `test/crazy_eights/simulation/app_test.clj`: per-seat strategy and outcome metric tests.
- Create `src/crazy_eights/simulation/experiment.clj`: synchronous repeated run and aggregate statistics.
- Create `test/crazy_eights/simulation/experiment_test.clj`: experiment runner tests.
- Modify `src/crazy_eights/operator.clj`: add `compare-strategies` adapter.
- Modify `test/crazy_eights/operator_test.clj`: delegation test.

## Tasks

### Task 1: Strategy Observation And Candidates

**Files:**
- Modify: `src/crazy_eights/simulation/strategy.clj`
- Modify: `test/crazy_eights/simulation/strategy_test.clj`

- [ ] **Step 1: Write failing tests for observation and legal candidates**

Add tests showing `observation` hides opponent hands, `legal-actions` emits playable cards, eights with declared suits, draw, and pass.

- [ ] **Step 2: Run focused strategy tests and verify failure**

Run: `clojure -M:test --focus crazy_eights.simulation.strategy-test`
Expected: FAIL because `observation` and `legal-actions` are missing.

- [ ] **Step 3: Implement observation and legal candidates**

Add pure functions in `strategy.clj`:

```clojure
(defn observation [state] ...)
(defn legal-actions [observation] ...)
(defn action [candidate] ...)
```

- [ ] **Step 4: Run focused strategy tests and verify pass**

Run: `clojure -M:test --focus crazy_eights.simulation.strategy-test`
Expected: PASS.

### Task 2: Scoring Rules And Composed Strategies

**Files:**
- Modify: `src/crazy_eights/simulation/strategy.clj`
- Modify: `test/crazy_eights/simulation/strategy_test.clj`

- [ ] **Step 1: Write failing tests for rule behavior**

Add tests that `careful` avoids eights when a non-eight is available, declares the most-held suit after an eight, and prefers same-rank suit switches toward held suits.

- [ ] **Step 2: Run focused strategy tests and verify failure**

Run: `clojure -M:test --focus crazy_eights.simulation.strategy-test`
Expected: FAIL because `from-rules`, rules, and `careful` are missing.

- [ ] **Step 3: Implement scoring and built-in strategies**

Add pure scoring rules and maps:

```clojure
(defn from-rules [id rules] ...)
(def first-playable {:id :first-playable :choose ...})
(def careful {:id :careful :choose ...})
```

Keep `choose-action` as a compatibility function that delegates to `first-playable`.

- [ ] **Step 4: Run focused strategy tests and verify pass**

Run: `clojure -M:test --focus crazy_eights.simulation.strategy-test`
Expected: PASS.

### Task 3: Per-Seat Strategies And Run Metrics

**Files:**
- Modify: `src/crazy_eights/simulation/app.clj`
- Modify: `test/crazy_eights/simulation/app_test.clj`

- [ ] **Step 1: Write failing simulation runner tests**

Add tests showing `run-to-completion!` accepts `:strategies`, records `:seat-strategies`, counts draws/plays/passes/steps, and returns `:winner-strategy` when finished with a winner.

- [ ] **Step 2: Run focused app simulation tests and verify failure**

Run: `clojure -M:test --focus crazy_eights.simulation.app-test`
Expected: FAIL because per-seat strategy selection and metrics are missing.

- [ ] **Step 3: Implement per-seat strategy selection and metrics**

Update `run-to-completion!` so a strategy can be a map with `:choose`, a plain function, or a vector under `:strategies`. Track metrics in the loop.

- [ ] **Step 4: Run focused app simulation tests and verify pass**

Run: `clojure -M:test --focus crazy_eights.simulation.app-test`
Expected: PASS.

### Task 4: Experiment Runner And Statistics

**Files:**
- Create: `src/crazy_eights/simulation/experiment.clj`
- Create: `test/crazy_eights/simulation/experiment_test.clj`

- [ ] **Step 1: Write failing experiment tests**

Add tests for lineup rotation and aggregation over supplied fake outcomes or small real runs. Verify `:games`, `:player-count`, `:finished`, `:blocked`, `:budget-exhausted`, `:seats-played`, `:wins`, `:win-rate`, `:avg-steps`, and `:avg-draws`.

- [ ] **Step 2: Run focused experiment tests and verify failure**

Run: `clojure -M:test --focus crazy_eights.simulation.experiment-test`
Expected: FAIL because namespace is missing.

- [ ] **Step 3: Implement experiment namespace**

Implement `rotate-lineup`, `run-game`, `summarize`, and `run` as plain functions. `run` creates a fresh app store per game and uses no delay.

- [ ] **Step 4: Run focused experiment tests and verify pass**

Run: `clojure -M:test --focus crazy_eights.simulation.experiment-test`
Expected: PASS.

### Task 5: Operator Adapter

**Files:**
- Modify: `src/crazy_eights/operator.clj`
- Modify: `test/crazy_eights/operator_test.clj`

- [ ] **Step 1: Write failing operator delegation test**

Add a test using `with-redefs` to prove `(op/compare-strategies games strategies)` calls `experiment/run` with `{:games games :strategies strategies}`.

- [ ] **Step 2: Run focused operator tests and verify failure**

Run: `clojure -M:test --focus crazy_eights.operator-test`
Expected: FAIL because `compare-strategies` is missing.

- [ ] **Step 3: Implement operator adapter**

Require `crazy_eights.simulation.experiment` and add:

```clojure
(defn compare-strategies [games strategies]
  (experiment/run {:games games
                   :strategies strategies}))
```

- [ ] **Step 4: Run focused operator tests and verify pass**

Run: `clojure -M:test --focus crazy_eights.operator-test`
Expected: PASS.

### Task 6: Final Verification And Commit

**Files:**
- All files touched above

- [ ] **Step 1: Run focused simulation tests**

Run: `clojure -M:test --focus crazy_eights.simulation.strategy-test --focus crazy_eights.simulation.app-test --focus crazy_eights.simulation.experiment-test --focus crazy_eights.operator-test`
Expected: PASS.

- [ ] **Step 2: Run full verification**

Run: `clojure -M:test`
Expected: PASS.

Run: `clojure -M:lint`
Expected: 0 errors, 0 warnings.

- [ ] **Step 3: Review diff and commit intended files**

Run: `git status --short`, `git diff`, and `git log --oneline -10`.

Commit intended files only, leaving unrelated existing changes untouched:

```bash
git add docs/superpowers/specs/2026-06-25-composable-simulation-strategies-design.md docs/superpowers/plans/2026-06-25-composable-simulation-strategies.md src/crazy_eights/simulation/strategy.clj src/crazy_eights/simulation/app.clj src/crazy_eights/simulation/experiment.clj src/crazy_eights/operator.clj test/crazy_eights/simulation/strategy_test.clj test/crazy_eights/simulation/app_test.clj test/crazy_eights/simulation/experiment_test.clj test/crazy_eights/operator_test.clj
git commit -m "feat: compare composed simulation strategies"
```

## Self-Review

- Spec coverage: tasks cover observations, candidates, scoring rules, careful strategy, per-seat strategies, metrics, experiments, and operator adapter.
- Placeholder scan: no placeholders remain.
- Type consistency: strategy values use `{:id ... :choose ...}`; plain functions remain accepted as compatibility strategy values; experiment stats refer to strategy ids.
