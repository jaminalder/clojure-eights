# Crazy Eights Application Logging Design

## Goal

Add proper optional logging around the application layer without polluting the domain or entangling the application core with infrastructure concerns.

The logging layer should:

- observe app events
- turn them into structured log entries
- support stdout logging for development and simulation use
- remain completely outside the domain
- remain optional for the application layer

## Design Priorities

- preserve DDD boundaries
- keep domain pure
- keep app core free of direct logging calls
- make logging opt-in and subscriber-based
- keep the first implementation small and operationally useful

## Boundary

### Domain

The domain must not log.

It remains responsible only for:

- command legality
- event generation
- event application
- invariants

### Application Layer

The application layer remains responsible only for:

- game lifecycle
- player identity and seat mapping
- state mutation around the domain
- app-event publication

It should not call `println`, own a logger singleton, or depend on logging infrastructure.

### Logging Layer

Logging belongs outside the application core as an infrastructure subscriber.

That means:

- app events are emitted as they are now
- a logger subscribes to those events
- the logger formats and writes log entries

This keeps logging as a consumer of application behavior rather than a participant in application decisions.

## Logging Namespace

Add a new namespace:

`crazy_eights.app.logging`

It should contain:

- `event->log-entry`
- `stdout-subscriber`
- optionally a small helper to attach the subscriber to a game

## Log Entry Shape

The first version should use plain maps and avoid timestamps or external context unless supplied from the outside.

Target shape:

```clojure
{:level :info
 :event :move-made
 :game-id "game-1"
 :data {...}}
```

The goal is operational observability, not permanent audit/event storage.

## Mapping Rules

`event->log-entry` should preserve important information from app events, such as:

- event type
- game id
- player id or current player if present
- command if present
- winner if present

It should keep the original app event nested under `:data` or a similar field rather than inventing a second business model.

## Stdout Subscriber

`stdout-subscriber` should:

- take an app event
- convert it with `event->log-entry`
- print the resulting map to stdout in a readable EDN form

This is enough for development, CLI integration, and simulation logging.

## Integration Pattern

The application core should remain unchanged in responsibility.

Usage pattern:

1. create a store
2. create/join a game
3. subscribe the logging subscriber to that game
4. submit app actions
5. receive log output through the subscriber

This means logging can be attached or omitted freely without changing app behavior.

## Tests

### Unit Tests

Add tests for the logging namespace that verify:

- `event->log-entry` preserves key event data
- `stdout-subscriber` prints meaningful output for a representative event

### Integration Test

Add an app-layer integration test that:

- creates a game
- subscribes a logger-like sink
- runs create/join/start/act flow
- verifies logs are emitted in the same order as app events

### Simulation Test Integration

The existing app-layer simulation test should attach the stdout logger or a sink using the same logging namespace so simulation runs can demonstrate real application-event logging.

This is especially useful when running a dedicated logging command or a verbose simulation mode.

## What Not To Add

- no domain logging
- no direct logger dependency in `app.core`
- no file logging yet
- no timestamps unless injected externally
- no event-store abstraction
- no user-facing transcript formatting in the logging namespace

## Success Criteria

This is complete when:

- app logging exists as a separate namespace
- logs are produced by subscribing to app events
- app core behavior is unchanged when logging is absent
- tests cover both mapping and integration
- the app simulation can show logging through the same subscriber mechanism
