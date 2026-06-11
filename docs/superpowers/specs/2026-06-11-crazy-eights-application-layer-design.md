# Crazy Eights Application Layer Design

## Goal

Add a clean application layer around the completed single-game Crazy Eights domain.

The application layer should:

- manage game lifecycle in memory
- assign lightweight player identities and seats
- translate client actions into domain commands
- apply domain events to stored state
- emit application events through an in-memory pub/sub interface

It should stay transport-agnostic so web, CLI, and simulation clients can all use it.

## Scope

This is the first application shell, not a web backend.

It includes:

- in-memory game registry
- lightweight per-game player identities
- join-game flow
- action submission
- subscription to app events
- application-layer tests
- one complete simulation test that plays through a full game against the app layer

It excludes:

- HTTP
- WebSocket or SSE delivery
- persistence
- auth/session/cookies
- user accounts
- durable logs

## Design Priorities

- keep the domain pure and untouched by transport concerns
- keep the application layer thin
- keep state management explicit
- support multiple client types
- emit neutral app events rather than transport-specific messages

## Architecture

### Layer Split

#### Domain

The domain remains responsible for:

- pure game rules
- command legality
- event generation
- event application
- invariants

#### Application Layer

The application layer becomes responsible for:

- creating and storing games
- managing player identities and seats
- turning client intents into domain commands
- storing updated state
- publishing app events to subscribers

#### Client / Transport Layer

Future clients remain responsible for delivery and UX:

- web: HTTP, SSE, WebSocket
- CLI: terminal interaction and printing
- simulation: automated players and tracing

This keeps the application layer semantic but not transport-shaped.

## Application Model

### Game Registry

The application layer should maintain an in-memory registry:

```clojure
{game-id {:state ...
          :players {player-id {:seat 0}}
          :subscribers {subscriber-id callback}
          :status ...}}
```

The exact map shape can stay small and internal, but it should at least capture:

- domain state
- mapping from `player-id` to domain seat index
- active subscriptions for app events

### Player Identities

The application layer should use lightweight stable player ids within each game.

Clients act as `player-id`, not raw seat index.

That means:

- the domain still uses seat indexes
- the application translates `player-id -> seat`
- clients do not need to know seat numbers directly

### In-Memory Mutation Boundary

The application layer may use mutable runtime state because it is outside the pure domain.

The simplest correct choice is an atom-managed registry with serialized updates through pure update functions.

That keeps mutation localized and testable.

## Public Application Operations

The first application layer should support these operations:

- `create-game`
- `join-game`
- `get-game`
- `submit-action`
- `subscribe`
- `unsubscribe`

### `create-game`

Creates a new application game entry.

Responsibilities:

- allocate a `game-id`
- create empty player registry
- create empty subscriber registry
- create empty or pending pre-start status

### `join-game`

Registers a player in a game and assigns the next available seat.

Responsibilities:

- allocate a `player-id`
- assign a seat index deterministically
- reject joins once game has started if that is the chosen lifecycle rule

### `get-game`

Returns the current application view of the game.

This may include:

- game id
- player identities and seat mapping summary
- domain state

### `submit-action`

Takes a client-originating action and applies it to the stored game.

Responsibilities:

- validate that the player belongs to the game
- translate app action into domain command
- call domain `decide`
- if success, apply events and store next state
- emit corresponding app events
- if failure, return domain error without mutating state

### `subscribe` / `unsubscribe`

Manage app-event listeners for a game.

The pub/sub API should stay simple: subscribers receive app events for that game in order.

## App Events

The application layer should publish app-level events after successful lifecycle or game changes.

Likely event set:

- `:game-created`
- `:player-joined`
- `:game-started`
- `:move-made`
- `:turn-changed`
- `:game-finished`

These are not transport payloads. They are neutral application events that future web/CLI/simulation layers can consume.

For now they can stay plain maps.

## Namespace Structure

Start with two namespaces only:

- `crazy_eights.app.core`
- `crazy_eights.app.pubsub`

### `crazy_eights.app.core`

Responsible for:

- game registry
- ids
- joins
- action submission
- emitting app events through pubsub

### `crazy_eights.app.pubsub`

Responsible for:

- subscriber registration
- subscriber removal
- publishing events to current subscribers

This is smaller and cleaner than prematurely splitting into many tiny namespaces.

If `app.core` later grows too much, it can be split based on real pressure.

## Testing Strategy

### Application Unit Tests

Tests should cover:

- create-game allocates a game id and initial app state
- join-game assigns player ids and seats deterministically
- submit-action rejects unknown game ids or player ids
- successful actions mutate stored game state
- failed actions do not mutate stored game state
- subscribers receive app events in order
- unsubscribe stops future delivery

### Application Integration Tests

Tests should cover:

- create game
- join multiple players
- start game through app layer
- play several turns through app layer
- observe state and emitted app events together

### Full Simulation Test Against App Layer

Add one test-only simulation client that:

- creates an app game
- joins N players
- starts the game with a shuffled valid deck
- repeatedly chooses legal actions through the app layer
- records app-level events and command outcomes
- stops when the game finishes
- fails if a step limit is exceeded

This is the application-layer analogue to the domain simulation test and proves that the shell around the domain is sufficient for a full game lifecycle.

### Logging

The simulation client should record a transcript of commands, app events, and summary state.

Like the domain simulation logging, full output should be available on demand or on failure.

## Lifecycle Choices

This first application layer should make one simplifying choice:

- players join before the game is started

That avoids mid-game join complexity and keeps lifecycle rules obvious.

## What Not To Add

- no auth/session model
- no transport protocol shaping
- no persistence abstraction yet
- no event bus framework
- no service/controller layering
- no DTO/view-model explosion

## Success Criteria

The application layer is complete when:

- multiple players can create and join a game through app functions
- a game can be started through the app layer
- client actions can be submitted through the app layer without exposing raw seat indexes
- successful state changes publish app events
- subscribers receive updates in order
- one full game can be simulated to completion against the application layer
- the domain remains pure and unchanged by transport concerns
