# Crazy Eights Domain Bootstrap Design

## Goal

Create the initial shell of a Clojure web application project for multiplayer Crazy Eights, but implement only the pure domain layer, its documentation, its domain specifications, and an immediately runnable test suite.

## Scope

This first slice includes:

- project metadata and dependencies
- README and architecture documentation
- domain namespace structure under `src/crazy_eights/domain/`
- domain specification resources under `resources/domain/`
- executable tests under `test/crazy_eights/domain/`
- a minimal domain lifecycle built around `:start-game` and `:play-card`

This first slice explicitly excludes:

- HTTP or Ring/Reitit handlers
- Hiccup, HTMX, or any UI layer
- WebSocket or SSE support
- application service orchestration beyond pure domain functions
- infrastructure concerns
- persistence and database integration
- Docker and deployment setup
- runtime state containers such as atoms, refs, or agents inside the domain

## Recommended Approach

Use a tiny but complete domain loop with only two commands:

- `:start-game`
- `:play-card`

This is the smallest useful slice because it supports a real game lifecycle without introducing draw behavior or networking concerns. It keeps the domain coherent while still allowing scenarios to be expressed entirely through domain commands and events.

## Architecture

The project starts as a pure domain core plus documentation and tests. The domain model uses plain immutable maps and vectors. Command functions inspect a state and a command, then return either a vector of domain events or a domain error map. State changes occur only by applying events to state.

The first supported lifecycle is:

`start-game -> repeated play-card -> optional winner state`

The implementation must not introduce web, transport, persistence, deployment, or runtime-container concerns into the domain.

## Domain Model

The initial state shape stays intentionally small:

```clojure
{:players [{:hand [...]} ...]
 :draw-pile [...]
 :discard-pile [...]
 :active-suit :hearts
 :current-player 0
 :status :in-progress
 :winner nil}
```

Cards are simple maps:

```clojure
{:rank :eight
 :suit :clubs}
```

Commands are immutable data:

```clojure
{:type :start-game
 :player-count 2
 :deck [...]} 

{:type :play-card
 :player 0
 :card {:rank :eight :suit :clubs}
 :declared-suit :spades}
```

Events are immutable data and represent all successful state changes:

```clojure
{:type :game-started ...}
{:type :card-played ...}
{:type :suit-declared ...}
{:type :turn-advanced ...}
{:type :game-won :player 0}
```

Errors are immutable maps such as:

```clojure
{:type :domain-error
 :reason :card-not-playable}
```

## Determinism

The domain must remain free of randomness. The `:start-game` command therefore accepts a full deck in command data. Dealing and initial discard setup are derived from that provided deck. This keeps setup deterministic and keeps randomization outside the domain.

## Namespace Responsibilities

### `crazy_eights.domain.model`

- define ranks and suits
- provide card and player constructors
- provide simple validation helpers for basic values
- avoid Malli dependency unless strictly necessary

### `crazy_eights.domain.schema`

- define Malli schemas for rank, suit, card, player, game state, commands, events, and domain errors
- keep schemas descriptive and aligned with the plain data model

### `crazy_eights.domain.rules`

- define pure predicates such as `valid-suit?`, `valid-rank?`, `card?`, `card-in-hand?`, `playable-card?`, `requires-declared-suit?`, and `valid-declared-suit?`

### `crazy_eights.domain.commands`

- decide whether `:start-game` and `:play-card` are legal
- return either a vector of events or a domain error map
- never mutate state directly

### `crazy_eights.domain.events`

- apply a single event or a sequence of events to state
- encode all successful state transitions through events

### `crazy_eights.domain.invariants`

- check consistency rules for cards, active suit, player structures, current player bounds, discard presence, and winner consistency
- provide a small API for checking whether a state is valid

### `crazy_eights.domain.scenarios`

- load EDN scenarios from `resources/domain/scenarios`
- execute command, event application, and assertions
- keep semantics intentionally minimal

### `crazy_eights.domain.generators`

- define `test.check` generators for valid suits, ranks, cards, hands, and small valid state fragments
- only generate domain-valid data

## Command and Event Flow

For a valid command:

1. command function inspects the current state and command data
2. command function returns a vector of events
3. events are applied in order to produce a new state
4. invariants are checked on the resulting state

For an invalid command:

1. command function returns a domain error map
2. no state transition occurs

This keeps decision logic and state transition logic separate and testable.

## Initial Rules

The first domain rules are the explicit Crazy Eights rules in the project brief:

- standard 52-card deck
- four suits and thirteen ranks
- eights are wild
- a play is legal if the card matches active suit, matches top discard rank, or is an eight
- when an eight is played, the next active suit must be declared
- a player cannot play a card they do not hold
- a player cannot play an illegal card
- the first player with an empty hand wins
- scoring is out of scope
- draw-pile reshuffle edge cases are out of scope unless needed for a narrow invariant

## Scenario Format

Scenarios are EDN data with this shape:

```edn
{:name "play matching rank"
 :given {:state ...}
 :when {:command ...}
 :then {:events [...]
        :state-matches {...}}}
```

Invalid command scenarios use:

```edn
{:name "cannot play invalid card"
 :given {:state ...}
 :when {:command ...}
 :then {:error {:reason :card-not-playable}}}
```

The runner only needs to support:

- loading the scenario EDN
- deciding the command
- applying resulting events when present
- asserting either expected error or expected events plus partial final state match

## Invariants

The first invariant set should include:

- all cards in the state have valid suit and rank
- all players have vector hands
- `:active-suit` is valid when the game is in progress
- `:current-player` is within player bounds when the game is in progress
- discard pile is non-empty for an in-progress game
- winner state is consistent with at least one empty hand
- in-progress state has no declared winner

The invariant layer should remain small and explicit rather than attempting exhaustive formal modeling.

## Testing Strategy

The project uses:

- `clojure.test`
- a dedicated runner alias
- `test.check`
- Malli as the schema library

The first passing suite covers:

- rule predicate tests
- command decision tests for `:start-game` and `:play-card`
- invariant tests for valid and invalid states
- EDN-backed scenario tests
- at least one property test that generated valid cards satisfy the card predicate

## Documentation

The project root README should explain:

- project purpose
- domain-first architecture intent
- future web stack direction
- absence of database and durable persistence
- temporary in-memory state to be handled outside the domain later
- purity constraints for the domain layer

Additional docs should include:

- `docs/domain.md` for detailed domain architecture and constraints
- ADR documenting the pure domain-first start
- ADR documenting the no-database initial architecture

## Out of Scope Guardrails

The following must not enter the domain layer:

- HTTP or routing namespaces
- UI concerns
- transport formats
- databases or persistence APIs
- timestamps, UUIDs, user IDs, session IDs, or auth concepts
- logging, environment, config, filesystem, or process concerns
- randomness or time as hidden dependencies
- mutable coordination primitives

## Success Criteria

The first implementation is complete when:

- the repository has the requested project structure
- the domain namespaces exist and contain minimal useful code
- the domain remains pure and infrastructure-free
- the documentation clearly describes the constraints and intended architecture
- EDN model, rules, and scenario resources exist
- the initial test suite runs and passes immediately
- no web server, database, UI, runtime state container, or Docker setup has been introduced
