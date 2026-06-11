# crazy-eights

Pure Clojure Crazy Eights domain model.

## Purpose

This repository starts with the game domain only. The goal is to model Crazy Eights as immutable data and pure functions before adding application or web concerns.

## Current Scope

The current implementation includes:

- domain data and constructors
- command decisions for `:start-game`, `:play-card`, `:draw-card`, `:reshuffle-draw-pile`, and `:pass-turn`
- in-memory application layer with game lifecycle and lightweight player identities
- optional application logging via app-event subscribers
- event application
- invariant checks
- executable EDN scenarios
- property, unit, app, and simulation tests

## How To Read The Project

The code is the primary specification.

- domain behavior lives in `src/crazy_eights/domain/`
- application lifecycle lives in `src/crazy_eights/app/`
- tests under `test/crazy_eights/domain/` are the executable spec
- app tests under `test/crazy_eights/app/` exercise the in-memory shell around the domain
- scenario EDN under `resources/domain/scenarios/` is kept only because it is executed by tests

The domain now covers one complete playable game of Crazy Eights without scoring or multi-round match flow.

## Constraints

- no database
- no durable persistence
- no web server yet
- no UI yet
- no Docker yet
- no runtime state container in the domain

Temporary in-memory application state may exist later outside the domain layer.

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

Application logging is implemented as an app-event subscriber in `crazy_eights.app.logging`, not inside the domain or app core.

Run lint:

```bash
clojure -M:lint
```

Run one namespace:

```bash
clojure -M:test --focus crazy_eights.domain.commands-test
```
