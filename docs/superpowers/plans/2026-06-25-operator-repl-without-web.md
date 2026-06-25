# Operator REPL Without Web Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `clojure -M:operator-repl` for app-context nREPL without starting the web server.

**Architecture:** Reuse `crazy_eights.runtime/start-nrepl!`; add one named runtime use case for REPL-only startup and one CLI main. Keep web and REPL lifecycles separately testable.

**Tech Stack:** Clojure 1.12, nREPL, existing runtime namespace, clojure.test, clj-kondo.

---

## File Structure

- Modify `src/crazy_eights/runtime.clj`: add `start-operator-repl!` and `repl-main`.
- Modify `test/crazy_eights/runtime_test.clj`: test nREPL-only startup does not start web.
- Modify `deps.edn`: add `:operator-repl` alias.
- Modify `README.md`: document no-web operator REPL workflow.

## Tasks

### Task 1: Runtime REPL-Only Use Case

- [ ] Write failing test in `test/crazy_eights/runtime_test.clj` asserting `(runtime/start-operator-repl! {:nrepl {:port 0 :port-file temp-file}})` starts nREPL and leaves `(:web (runtime/status))` nil.
- [ ] Run `clojure -M:test --focus crazy_eights.runtime-test`; expect failure because `start-operator-repl!` is missing.
- [ ] Implement `start-operator-repl!` in `src/crazy_eights/runtime.clj` as `{:nrepl (start-nrepl! nrepl)}`.
- [ ] Run focused runtime tests; expect pass.

### Task 2: CLI Alias And Docs

- [ ] Add `repl-main` to `src/crazy_eights/runtime.clj`, printing nREPL bind/port/port-file and blocking on a promise.
- [ ] Add `:operator-repl` in `deps.edn` using nREPL dependency and `:main-opts ["-m" "crazy_eights.runtime" "repl"]`.
- [ ] Update `-main` to dispatch `"repl"` to `repl-main`, otherwise keep existing web+nREPL behavior.
- [ ] Update `README.md` with `clojure -M:operator-repl` for experiments and simulations without web.

### Task 3: Verification

- [ ] Run `clojure -M:test --focus crazy_eights.runtime-test`.
- [ ] Run `clojure -M:test`.
- [ ] Run `clojure -M:lint`.
- [ ] Smoke-check `clojure -M:operator-repl` starts nREPL and writes `.nrepl-port`, then stop it.
- [ ] Commit intended files only.

## Self-Review

- Spec coverage: plan covers runtime use case, alias, docs, tests, and smoke verification.
- Placeholder scan: no placeholders remain.
- Type consistency: runtime function names match tests and alias main dispatch.
