# Simulation Client Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move simulation into a separate client layer and expose `(op/start-sim player-count delay-seconds)` for background simulations against the live app store.

**Architecture:** Delete simulation from app and domain areas. Add `crazy_eights.simulation.strategy` for action choice and `crazy_eights.simulation.app` for running a normal app game. Operator starts a background future and returns the normal game id plus observer path; there is no simulation registry.

**Tech Stack:** Clojure 1.12, existing in-memory app layer, operator REPL, standard futures, current observer page.

---

## File Structure

- Create `src/crazy_eights/simulation/strategy.clj`: choose the next simple action from domain state.
- Create `src/crazy_eights/simulation/app.clj`: create and run simulated app games in a supplied store.
- Modify `src/crazy_eights/operator.clj`: add `start-sim` and argument validation.
- Delete `src/crazy_eights/app/simulation.clj`.
- Delete `test/crazy_eights/app/simulation_test.clj`.
- Delete `test/crazy_eights/app/simulation_runtime_test.clj`.
- Delete `test/crazy_eights/app/simulation_runner.clj`.
- Delete `test/crazy_eights/domain/simulation_test.clj`.
- Delete `test/crazy_eights/domain/simulation_runner.clj`.
- Create tests under `test/crazy_eights/simulation/`.
- Modify `test/crazy_eights/operator_test.clj`.
- Modify `deps.edn`, `README.md`, `CLAUDE.md`, and relevant docs references.

## Tasks

### Task 1: Strategy Namespace

- [ ] Write failing tests for `crazy_eights.simulation.strategy/choose-action` covering play, draw, and pass.
- [ ] Run focused tests and verify failure because the namespace is missing.
- [ ] Implement `playable-card` and `choose-action` using plain data and domain predicates.
- [ ] Run focused strategy tests and verify pass.

### Task 2: Simulation App Client

- [ ] Write failing tests for `crazy_eights.simulation.app/start-game!` and `run-to-completion!`.
- [ ] Run focused tests and verify failure because the namespace is missing.
- [ ] Implement creation of a normal app game with simulated player names and observer path.
- [ ] Implement the move loop against app functions with injected delay.
- [ ] Run focused simulation app tests and verify pass.

### Task 3: Operator Entry Point

- [ ] Write failing operator tests for `(op/start-sim 3 0)` return value, `op/games` visibility, and invalid arguments.
- [ ] Run focused operator tests and verify failure.
- [ ] Add `start-sim` to `crazy_eights.operator`, launching the simulation in a future.
- [ ] Run focused operator tests and verify pass.

### Task 4: Remove Old Simulation Locations

- [ ] Delete app/domain simulation source, tests, and runners.
- [ ] Remove `:sim-log` and `:app-sim-log` aliases.
- [ ] Update README, CLAUDE, and current docs references so simulation is no longer described as app/domain functionality.
- [ ] Search for stale `app.simulation`, `domain.simulation`, `sim-log`, and `app-sim-log` references.

### Task 5: Verification And Commit

- [ ] Run `clojure -M:test`.
- [ ] Run `clojure -M:lint`.
- [ ] Run `./scripts/verify-web`.
- [ ] Commit all intended changes.
