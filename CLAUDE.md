# CLAUDE.md

@AGENTS.md

## Commands

```bash
clojure -M:test                                              # full test suite
clojure -M:test --focus crazy_eights.domain.commands-test    # one namespace
clojure -M:lint                                              # clj-kondo (must stay clean)
clojure -M:sim-log                                           # domain simulation with move log
clojure -M:app-sim-log                                       # app-layer simulation with log
clojure -M:run-web                                           # multiplayer game web UI on http://localhost:8080
./scripts/verify-web                                         # live HTTP + Playwright web smoke
./scripts/verify-web --no-browser                            # live HTTP smoke without browser
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
- `src/crazy_eights/web/` — transport only, no game logic. Request pipeline:
  request → `commands` (parse params into a web command) → app use case → domain
  events/error → `view_model/game-view` (per-viewer, never exposes other hands) →
  `views` (hiccup fragments: status, game-board, player-hand). `routes` wires
  reitit + cookies (per-game `ce-<game-id>` cookie identifies the player) with
  injectable seams for tests; `sse` pushes re-rendered fragments to every
  connected viewer on each app event (htmx sse extension swaps them in);
  `cards` maps cards to codes/SVG paths; `server` is the entry point. Static
  assets live in `resources/public` (52 public-domain card SVGs + htmx vendored).

State lives in atoms at the app/web boundary only; everything below is values in,
values out.

Design balance: DDD defines the language the software should speak; bottom-up
Clojure design defines how that language grows inside the program. The domain
layer should read as a small executable Crazy Eights language, not as CRUD
operations or framework ceremony. Prefer small pure functions such as
`play-card`, `draw-card`, `valid-play?`, `advance-turn`, and `game-over?`, then
compose larger workflows from those transformations.

Keep these languages distinct:

- domain: cards, hands, turns, legal moves, winners
- command: start game, play card, draw card, pass turn
- event/result: card played, card drawn, turn advanced, game ended, invalid move
- view: playable cards, available actions, current player, game status
- HTTP: routes, params, form posts, responses

The web layer is an adapter. HTTP handlers translate requests into commands;
the app layer orchestrates command handling; the domain enforces rules and
returns state/results; view-model functions prepare UI data; hiccup views render
prepared view models with minimal logic. Domain behavior must remain testable
without HTTP, HTML, sessions, routes, or a running server.

## Workflow

- Test-first: extend or add the failing test before changing behavior. Tests are the
  executable specification.
- When adding behavior, name the domain concept first, implement the smallest
  pure functions that express it, compose larger behavior from those functions,
  then expose it through app handlers and web routes.
- Do not put domain rules in HTTP handlers or hiccup views.
- Do not introduce macros, protocols, multimethods, rule engines, abstract state
  machines, or framework-like abstractions unless repeated concrete code proves
  the need.
- After every change run `clojure -M:test` and `clojure -M:lint`; both must be green.
- For any live web change, follow the `web-verification` skill before claiming it
  works. Prefer `./scripts/verify-web` because it handles server startup, curl
  checks, Playwright smoke, logs, and cleanup in one command.
- When editing Clojure files, follow the `clojure-craft` skill.
