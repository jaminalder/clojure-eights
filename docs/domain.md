# Domain Architecture

The domain is intentionally small.

## Namespaces

- `crazy_eights.domain.model`: domain values, constructors, and rule predicates
- `crazy_eights.domain.commands`: command decision logic
- `crazy_eights.domain.events`: event application
- `crazy_eights.domain.invariants`: state consistency checks
- `crazy_eights.domain.scenarios`: EDN scenario execution support
- `crazy_eights.domain.generators`: test data generators

## Flow

1. A command is evaluated against immutable state.
2. The command returns either domain events or a domain error.
3. Events are applied in order to produce the next state.
4. Tests and scenarios describe the intended behavior.

## Single-Game Scope

The current domain models one complete Crazy Eights game:

- start game from a supplied deck
- play cards, including declared-suit eights
- draw one card at a time when no play is available
- reshuffle the draw pile explicitly and deterministically
- pass only when no play and no draw are possible
- finish by win or blocked-game ending

Scoring and multi-round match flow are intentionally out of scope.

## Application Boundary

Outside the pure domain, the project now has a small in-memory application layer.

It is responsible for:

- game lifecycle and registry
- lightweight player identities and seat assignment
- translating player actions into domain commands
- publishing app-level events through in-memory pub/sub

It is not responsible for HTTP, WebSocket, SSE, CLI I/O, auth, or persistence.

Those concerns belong in future clients and transport layers around the application shell.

## What Counts As Specification

- source code in `src/crazy_eights/domain/`
- unit and property tests in `test/crazy_eights/domain/`
- executable scenario data in `resources/domain/scenarios/`

The simulation test is additional confidence coverage for shuffled games, but the domain itself remains deterministic because every deck order is passed in explicitly.

The application layer has its own full-game simulation coverage as well, proving the shell around the domain can manage a game end-to-end.

Anything else should justify itself by affecting execution or preventing confusion.
