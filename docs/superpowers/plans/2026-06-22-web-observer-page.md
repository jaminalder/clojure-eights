# Web Observer Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a UUID-protected web observer page that shows all player hands face-up and updates live.

**Architecture:** Keep the domain unchanged. Store a stable observer UUID in the app game map, expose the path through `op/games`, and add separate observer view-model, view, fragments, and routes so normal player views keep hiding opponent hands.

**Tech Stack:** Clojure 1.12, existing in-memory app layer, Reitit Ring routes, Hiccup, htmx SSE, http-kit.

---

## File Structure

- Modify `src/crazy_eights/app/core.clj`: add `:observer-id` to new games.
- Modify `src/crazy_eights/web/paths.clj`: add observer page and event paths.
- Modify `src/crazy_eights/operator.clj`: include `:observer-path` in game summaries.
- Modify `src/crazy_eights/web/view_model.clj`: add observer view projection with all player hands.
- Modify `src/crazy_eights/web/fragments.clj`: add observer fragment map.
- Modify `src/crazy_eights/web/views.clj`: add observer page and observer table rendering.
- Modify `src/crazy_eights/web/routes.clj`: add observer page and observer SSE routes with UUID validation.
- Modify `resources/public/style.css`: add compact observer layout styles.
- Modify focused tests under `test/crazy_eights/app`, `test/crazy_eights/operator_test.clj`, and `test/crazy_eights/web`.

## Tasks

### Task 1: App Observer Token And Operator Path

- [ ] Add failing app/operator tests for `:observer-id` and `:observer-path`.
- [ ] Run focused tests and verify failure.
- [ ] Add observer UUID to `create-game!` game maps.
- [ ] Add `paths/observer` and include observer path in `op/games` summaries.
- [ ] Run focused tests and verify pass.

### Task 2: Observer View Model And Fragments

- [ ] Add failing view-model and fragments tests proving observer views include all hands and normal views still hide hands.
- [ ] Run focused tests and verify failure.
- [ ] Implement `observer-view` and `observer-fragments`.
- [ ] Run focused tests and verify pass.

### Task 3: Observer Views And Styles

- [ ] Add failing view tests for observer page SSE wiring and face-up cards.
- [ ] Run focused tests and verify failure.
- [ ] Implement observer Hiccup rendering and compact CSS.
- [ ] Run focused tests and verify pass.

### Task 4: Observer Routes

- [ ] Add failing route tests for correct UUID, wrong UUID, and observer SSE route.
- [ ] Run focused tests and verify failure.
- [ ] Implement route validation and observer route handlers.
- [ ] Run focused tests and verify pass.

### Task 5: Verification And Commit

- [ ] Run `clojure -M:test`.
- [ ] Run `clojure -M:lint`.
- [ ] Run `./scripts/verify-web`.
- [ ] Commit all intended changes.
