# CLAUDE.md

@AGENTS.md

## Commands

```bash
clojure -M:test                                              # full test suite
clojure -M:test --focus crazy_eights.domain.commands-test    # one namespace
clojure -M:lint                                              # clj-kondo (must stay clean)
clojure -M:sim-log                                           # domain simulation with move log
clojure -M:app-sim-log                                       # app-layer simulation with log
clojure -M:run-web                                           # web observer on http://localhost:8080
```

There is no REPL alias; evaluate forms via `clojure -M -e` or add tests instead.

## Architecture

Three layers, dependency direction strictly `web → app → domain`:

- `src/crazy_eights/domain/` — pure functions over immutable data, no side effects.
  Command flow: `commands/decide` takes `(state, command)` and returns a vector of
  events or `{:type :domain-error :reason ...}`; `events/apply-events` folds events
  into the next state. `model` holds values and rule predicates, `invariants/check`
  returns violation reasons, `scenarios` executes the EDN under
  `resources/domain/scenarios/`, `generators` feeds property tests.
- `src/crazy_eights/app/` — in-memory shell: `core` (game registry in an atom, seat
  mapping, action submission, app-event emission), `pubsub` (plain-map subscribers),
  `logging` (app-event subscriber, never inside domain/core), `simulation` (runtime
  simulation service with injected delay-fn). No HTTP/JSON here.
- `src/crazy_eights/web/` — transport only: `routes` (reitit, with injectable seams
  for tests), `page` (hiccup observer page), `sse` (http-kit SSE channel wiring),
  `server` (entry point). No game logic here.

State lives in atoms at the app/web boundary only; everything below is values in,
values out.

## Workflow

- Test-first: extend or add the failing test before changing behavior. Tests are the
  executable specification.
- After every change run `clojure -M:test` and `clojure -M:lint`; both must be green.
- For any live web change, follow the `web-verification` skill before claiming it
  works (curl first, then browser, then clean shutdown).
- When editing Clojure files, follow the `clojure-craft` skill.
