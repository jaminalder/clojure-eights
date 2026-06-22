# REPL Operator Interface Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `subagent-driven-development` recommended, or `executing-plans`, to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Add a trusted same-JVM REPL operator interface for inspecting and observing live Crazy Eights games.

**Architecture:** Keep domain and app layers unchanged in responsibility. Add a small runtime namespace that owns the process-local app store, web server handle, and explicit local-only nREPL lifecycle. Add an operator namespace as a REPL-friendly facade over `crazy_eights.app.core`, while still allowing direct access to `runtime/store`.

**Tech Stack:** Clojure 1.12, http-kit, current in-memory app store, nREPL for Conjure and shell access.

---

## File Structure

- Create `src/crazy_eights/runtime.clj`: shared app store, web lifecycle, local-only nREPL lifecycle, operator runtime main.
- Create `src/crazy_eights/operator.clj`: trusted REPL facade for `games`, `game`, `observe!`, `unobserve!`, `unobserve-all!`, `observers`.
- Modify `src/crazy_eights/web/server.clj`: delegate normal web startup to runtime web lifecycle, without starting nREPL.
- Modify `deps.edn`: add explicit `:operator-web` and `:nrepl-client` aliases.
- Create `test/crazy_eights/runtime_test.clj`: verify shared store and normal web startup does not start nREPL.
- Create `test/crazy_eights/operator_test.clj`: verify grouped game summaries, full game state lookup, observer lifecycle.
- Modify `README.md`: document Neovim/Conjure and SSH shell connection workflows.

## Tasks

### Task 1: Runtime Store And Web Lifecycle

- [ ] Write failing runtime tests in `test/crazy_eights/runtime_test.clj`.
- [ ] Run `clojure -M:test --focus crazy_eights.runtime-test` and verify failure because `crazy_eights.runtime` is missing.
- [ ] Create `src/crazy_eights/runtime.clj` with shared `store`, `services`, `start-web!`, `stop-web!`, `start-nrepl!`, `stop-nrepl!`, `start-operator!`, `stop!`, and `-main`.
- [ ] Replace `src/crazy_eights/web/server.clj` so normal web startup delegates to `runtime/start-web!` and does not start nREPL.
- [ ] Run focused runtime and web route tests.
- [ ] Commit with `feat: add shared runtime store`.

### Task 2: Operator Inspection And Observation

- [ ] Write failing operator tests in `test/crazy_eights/operator_test.clj` for `games`, `game`, `observe!`, `unobserve!`, `unobserve-all!`, and `observers`.
- [ ] Run `clojure -M:test --focus crazy_eights.operator-test` and verify failure because `crazy_eights.operator` is missing.
- [ ] Create `src/crazy_eights/operator.clj` with REPL-friendly wrappers over `app.core` and runtime store.
- [ ] Run focused operator tests.
- [ ] Commit with `feat: add repl operator interface`.

### Task 3: nREPL Operator Aliases

- [ ] Add `:operator-web` alias with `nrepl/nrepl` extra deps and main opts for `crazy_eights.runtime`.
- [ ] Add `:nrepl-client` alias with `nrepl/nrepl` and main opts for `nrepl.cmdline` connecting to `127.0.0.1`.
- [ ] Smoke-check `clojure -M:operator-web` starts web and nREPL and writes `.nrepl-port`.
- [ ] Smoke-check `clojure -M:nrepl-client --port "$(cat .nrepl-port)"` can evaluate `(require '[crazy_eights.operator :as op])` and `(op/games)`.
- [ ] Verify `clojure -M:run-web` still starts only the web server.
- [ ] Commit with `feat: add operator nrepl aliases`.

### Task 4: Documentation And Verification

- [ ] Add README documentation for normal web startup, operator startup, Conjure connection, operator forms, direct app access, and SSH shell access.
- [ ] Commit with `doc: explain operator repl workflow`.
- [ ] Run `clojure -M:test`.
- [ ] Run `clojure -M:lint`.
- [ ] Run `./scripts/verify-web`.
- [ ] Run final manual operator runtime smoke check.
