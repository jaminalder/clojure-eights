# crazy-eights

Multiplayer Crazy Eights with a pure Clojure domain core.

## Purpose

This repository models Crazy Eights as immutable data and pure functions first, then wraps that domain in a thin in-memory application layer and a server-rendered multiplayer web UI (htmx + SSE).

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
- a minimal web observer page (`/observer`) that streams simulation logs over SSE

## How To Read The Project

The code is the primary specification.

- domain behavior lives in `src/crazy_eights/domain/`
- application lifecycle lives in `src/crazy_eights/app/`
- web transport (routes, observer page, SSE) lives in `src/crazy_eights/web/`
- tests under `test/crazy_eights/domain/` are the executable spec
- app tests under `test/crazy_eights/app/` exercise the in-memory shell around the domain
- web tests under `test/crazy_eights/web/` cover routes and SSE wiring
- scenario EDN under `resources/domain/scenarios/` is kept only because it is executed by tests

The domain now covers one complete playable game of Crazy Eights without scoring or multi-round match flow.

## Constraints

- no database
- no durable persistence
- no Docker yet
- no runtime state container in the domain

In-memory application and simulation state lives outside the domain layer.

## Domain Purity

Domain namespaces must not depend on HTTP, persistence, logging, config, filesystem, time, randomness, or mutable runtime primitives.

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

Then open `http://localhost:8080` to host a multiplayer game, or
`http://localhost:8080/observer` for the simulation observer. Game state is
in-memory only: a server restart forgets all games.

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
