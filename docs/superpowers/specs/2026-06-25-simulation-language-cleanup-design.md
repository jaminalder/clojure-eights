# Simulation Language Cleanup Design

## Goal

Clean up the simulation client layer so future simulations, bot strategies, batch experiments, and operator controls can grow from small named concepts instead of ad hoc orchestration in `operator.clj`.

## Problem

`crazy_eights.operator/start-sim` currently knows too much about simulation internals. It creates a simulated game through `simulation/start-game!`, converts delay seconds through `simulation/delay-fn`, launches an untracked `future`, and calls `simulation/run-to-completion!` itself.

That splits one domain-facing use case, "start a background simulation", across operator and simulation namespaces. It also makes the simulation map little more than a game summary, with no explicit strategy, runner, outcome, budget, or background handle.

## Architecture

Keep simulation as a client of the app layer, not part of domain or app core.

Grow a small simulation language from plain functions and data:

- strategy: pure functions from domain state to app player action
- setup: create a normal app game with simulated players
- step: submit one chosen action for the current player
- run: repeat steps until finished, missing, or budget exhausted
- background launch: start a run in a future and return inspectable launch data
- outcome: plain map describing status, steps left, and game id

The operator remains a trusted REPL adapter. It validates REPL arguments, calls one simulation use case, and adds operator-facing observer path data. It must not assemble the simulation lifecycle itself.

## Public Simulation API

`crazy_eights.simulation.strategy` exposes a default strategy value:

```clojure
strategy/first-playable
```

The value is a plain function. It takes domain state and returns one app-level action map without player id:

```clojure
{:type :play-card :card card :declared-suit :spades}
{:type :draw-card}
{:type :pass-turn}
```

`crazy_eights.simulation.app` exposes:

```clojure
(start! store {:player-count 3
               :simulation-name "sim-test"})

(run-to-completion! store simulation {:delay-fn (fn [] nil)
                                      :step-budget 500
                                      :strategy strategy/first-playable})

(start-background! store {:player-count 3
                          :delay-seconds 0.3
                          :step-budget 500
                          :strategy strategy/first-playable})
```

`start!` creates and starts a normal app game. It returns simulation data including `:game-id`, `:player-count`, `:simulation-name`, and `:players`.

`run-to-completion!` runs an already-started simulation synchronously and returns outcome data. It marks budget exhaustion explicitly with `:status :step-budget-exhausted` instead of returning `:in-progress` with zero steps left.

`start-background!` is the single public use case for operator-style background launch. It creates the game, starts the future, and returns launch data including `:future` so callers can inspect or cancel it later if needed.

## Operator API

`(op/start-sim player-count delay-seconds)` keeps its current REPL shape and return data, but its implementation becomes shallow:

1. validate player count and delay seconds
2. call `simulation/start-background!`
3. add `:observer-path`
4. return plain data

The operator should not call `simulation/start!`, `simulation/delay-fn`, or `simulation/run-to-completion!` directly.

## Boundaries

Simulation may depend on `crazy_eights.app.core`, `crazy_eights.domain.model`, and `crazy_eights.simulation.strategy`.

Simulation should not depend on `crazy_eights.web.paths`. Observer URLs are operator/web presentation data.

The app layer should not depend on simulation. The domain layer remains pure and unaware of simulation.

## Testing

Tests cover:

- strategies remain pure functions and can be selected by the runner
- `start!` returns simulation data with players, not only a display summary
- `run-to-completion!` returns `:finished` for normal games
- `run-to-completion!` returns `:step-budget-exhausted` when budget reaches zero before finish
- `start-background!` returns immediately with game id, future, player count, delay seconds, and simulation name
- `op/start-sim` returns observer path data while delegating lifecycle orchestration to simulation

Verification commands:

- `clojure -M:test --focus crazy_eights.simulation.app-test`
- `clojure -M:test --focus crazy_eights.simulation.strategy-test`
- `clojure -M:test --focus crazy_eights.operator-test`
- `clojure -M:test`
- `clojure -M:lint`
