# crazy-eights

Multiplayer Crazy Eights with a pure Clojure domain core.

## Purpose

This repository models Crazy Eights with two complementary design ideas:

- Domain-Driven Design defines the semantic model: the ubiquitous language, domain concepts, invariants, commands, events, state transitions, and separation between domain, application, infrastructure, and web concerns.
- Bottom-up Lisp/Clojure design defines the implementation style: immutable data, pure functions, small composable domain primitives, and workflows grown from simple transformations.

DDD answers "what language should the software speak?" The Lisp/SICP approach answers "how do we grow that language inside the program?"

The result should be a small executable language for Crazy Eights, not a CRUD model or a framework-shaped rule engine.

## Current Scope

The current implementation includes:

- domain data and constructors
- command decisions for `:start-game`, `:play-card`, `:draw-card`, `:reshuffle-draw-pile`, and `:pass-turn`
- in-memory application layer with game lifecycle and lightweight player identities
- optional application logging via app-event subscribers
- event application
- invariant checks
- executable EDN scenarios
- property, unit, app, web, and simulation tests
- a multiplayer web UI: host a game, share the link, friends join by name,
  the host deals, and the whole game is played in the browser (eights declare
  a suit via a picker; draws reshuffle automatically when needed)
- live updates over SSE: every move re-renders per-player hiccup fragments
  (status, game-board, player-hand) that htmx swaps into each open browser
- spectator view for anyone opening the link after the game started

## How To Read The Project

The code is the primary specification.

- domain behavior lives in `src/crazy_eights/domain/`
- application lifecycle lives in `src/crazy_eights/app/`
- web transport (routes, game views, SSE) lives in `src/crazy_eights/web/`
- tests under `test/crazy_eights/domain/` are the executable spec
- app tests under `test/crazy_eights/app/` exercise the in-memory shell around the domain
- web tests under `test/crazy_eights/web/` cover routes and SSE wiring
- scenario EDN under `resources/domain/scenarios/` is kept only because it is executed by tests

The domain now covers one complete playable game of Crazy Eights without scoring or multi-round match flow.

## Design Style

DDD grounds the model in the real domain language. Bottom-up Clojure design keeps the implementation small, composable, expressive, and executable.

The domain language includes cards, hands, turns, legal moves, winners, and game endings. Domain functions should read like domain statements: `play-card`, `draw-card`, `valid-play?`, `advance-turn`, and `game-over?`.

The command language includes starting a game, playing a card, drawing a card, and passing a turn. The event/result language includes cards played, cards drawn, turns advanced, games ended, and invalid moves.

Prefer simple functions and data transformations first. Let larger workflows emerge by composition. Do not introduce rule engines, overly abstract state machines, macro DSLs, protocols, multimethods, or generic framework-like abstractions unless repeated concrete code proves they are needed.

## Constraints

- no database
- no durable persistence
- no Docker yet
- no runtime state container in the domain

In-memory application and simulation state lives outside the domain layer.

## Domain Purity

Domain namespaces must not depend on HTTP, persistence, logging, config, filesystem, time, randomness, or mutable runtime primitives.

The same domain behavior must be testable without HTTP, HTML, sessions, routes, or a running server.

## Web Layer

The web layer is an adapter/detail, not the owner of game behavior.

- HTTP handlers translate requests into application commands.
- The application layer orchestrates command handling and in-memory lifecycle.
- The domain layer enforces rules and produces new state/results.
- View-model functions project domain state into UI-specific data such as playable cards, available actions, current player, and game status.
- Hiccup views render already-prepared view models with minimal logic.

The HTTP language is routes, params, form posts, and responses. It should not replace the domain, command, event/result, or view languages inside the system.

## Tests

Run all tests:

```bash
clojure -M:test
```

Run the shuffled-game simulation test with move logging:

```bash
clojure -M:sim-log
```

Run just the simulation test quietly:

```bash
clojure -M:test --focus crazy_eights.domain.simulation-test
```

Run the application-layer simulation test:

```bash
clojure -M:test --focus crazy_eights.app.simulation-test
```

Run the application-layer simulation test with logging to stdout:

```bash
clojure -M:app-sim-log
```

Run the web server:

```bash
clojure -M:run-web
```

Then open `http://localhost:8080` to host a multiplayer game. Game state is
in-memory only: a server restart forgets all games.

## Operator REPL

Normal web startup does not start a REPL:

```bash
clojure -M:run-web
```

For trusted operator access, start the web server and a local-only nREPL in the
same JVM:

```bash
clojure -M:operator-web
```

This starts the web app on `http://localhost:8080`, starts nREPL bound to
`127.0.0.1`, and writes `.nrepl-port`.

In Neovim with Conjure, open a Clojure file in the project and connect to the
port file with Conjure's `:CljConnectPortFile` command or the configured
localleader mapping.

Example operator forms:

```clojure
(require '[crazy_eights.operator :as op])

(op/games)
(op/game "game-0")
(op/observe! "game-0")
(op/observers)
(op/unobserve-all!)
```

The operator REPL is trusted live-process access. You can also call lower-level
app functions directly:

```clojure
(require '[crazy_eights.app.core :as app])
(require '[crazy_eights.runtime :as runtime])

(app/get-game runtime/store "game-0")
@runtime/store
```

For production-style shell access, SSH into the server host and connect to the
local nREPL port:

```bash
clojure -M:nrepl-client --port "$(cat .nrepl-port)"
```

Do not expose the nREPL port publicly. It is arbitrary code execution in the
running JVM.

The 52 card face SVGs under `resources/public/cards/` are Byron Knoll's
public-domain vector playing cards; htmx is vendored under
`resources/public/vendor/`.

For live web verification and debugging in this repo, follow the `web-verification` skill. It covers server startup with stored PID, readiness checks, direct HTTP verification, Playwright interaction, server log inspection, and explicit shutdown.

Application logging is implemented as an app-event subscriber in `crazy_eights.app.logging`, not inside the domain or app core.

Run lint:

```bash
clojure -M:lint
```

Run one namespace:

```bash
clojure -M:test --focus crazy_eights.domain.commands-test
```
