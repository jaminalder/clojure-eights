# Simulation Client Layer Design

## Goal

Move simulation out of the domain and app layers into a separate simulation client layer that talks to the app layer.

The operator REPL should be able to start a simple background simulation with:

```clojure
(op/start-sim 3 0.3)
```

The simulated game is a normal app game and is visible through `op/games`, including the observer URL.

## Architecture

The domain remains pure game rules only. The app layer remains game lifecycle and command orchestration only. Simulation becomes a separate client layer under `crazy_eights.simulation`, depending on `crazy_eights.app.core` the same way web and operator clients do.

There is no simulation registry or simulation datastore. Starting a simulation creates a normal game in the supplied app store, seats simulated players, starts the game, and launches a background move loop. The game itself is the durable in-memory thing operators inspect.

## Namespaces

`crazy_eights.simulation.strategy` chooses simple legal player actions from domain state:

- play the first playable card
- declare `:spades` when playing an eight
- draw when no card is playable and drawing or app-level reshuffle is possible
- pass otherwise

`crazy_eights.simulation.app` runs the strategy against the app layer:

- create a normal app game in a supplied store
- join simulated players named `sim-<random-id>-p1`, `sim-<random-id>-p2`, etc.
- start the game through `app/start-game!`
- move in a background-safe loop until the game finishes or a step budget is exhausted
- sleep a supplied delay between moves

`crazy_eights.operator` exposes `start-sim` as the REPL entry point.

## Operator API

`(op/start-sim player-count delay-seconds)` returns immediately:

```clojure
{:game-id "game-4"
 :observer-path "/games/game-4/observer/<uuid>"
 :player-count 3
 :delay-seconds 0.3
 :simulation-name "sim-a1b2c3"}
```

Invalid arguments return plain data:

```clojure
{:error :invalid-player-count}
{:error :invalid-delay-seconds}
```

There is no `op/simulations` or simulation lookup. Use `op/games` and `op/game` to inspect simulated games.

## Removal

Remove existing simulation code from app and domain areas:

- `src/crazy_eights/app/simulation.clj`
- app simulation tests and runner
- domain simulation tests and runner
- `:sim-log` and `:app-sim-log` aliases

Update README and guidance so simulation is described as a separate client layer, not app or domain functionality.

## Testing

Tests cover:

- strategy chooses play, draw, and pass from concrete states
- simulation app client creates a normal app game with named simulated players and observer path
- simulation app client can run a game to completion with no delay
- `op/start-sim` returns immediately with game id and observer path, and the game appears in `op/games`
- invalid `op/start-sim` arguments return errors
- removed app/domain simulation namespaces are no longer referenced by tests or aliases

Verification commands:

- `clojure -M:test`
- `clojure -M:lint`
- `./scripts/verify-web`
