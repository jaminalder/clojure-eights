# crazy-eights

Pure Clojure Crazy Eights domain model.

## Purpose

This repository starts with the game domain only. The goal is to model Crazy Eights as immutable data and pure functions before adding application or web concerns.

## Current Scope

The current implementation includes:

- domain data and constructors
- command decisions for `:start-game` and `:play-card`
- event application
- invariant checks
- executable EDN scenarios
- property and unit tests

## How To Read The Project

The code is the primary specification.

- domain behavior lives in `src/crazy_eights/domain/`
- tests under `test/crazy_eights/domain/` are the executable spec
- scenario EDN under `resources/domain/scenarios/` is kept only because it is executed by tests

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

Run lint:

```bash
clojure -M:lint
```

Run one namespace:

```bash
clojure -M:test --focus crazy_eights.domain.commands-test
```
