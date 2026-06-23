# Domain Architecture

The domain is intentionally small, but it is not merely a collection of data structures or CRUD operations.

This project combines two complementary ideas:

- Domain-Driven Design defines the semantic model: ubiquitous language, bounded contexts where useful, domain concepts, invariants, commands, events, state transitions, and the separation of domain, application, infrastructure, and web concerns.
- Bottom-up Lisp/Clojure design defines the implementation style: immutable data plus pure functions, small composable primitives, and higher-level workflows that emerge from simple transformations.

DDD answers "what language should the software speak?" The Lisp/SICP approach answers "how do we grow that language inside the program?"

The domain layer should become a small executable language for Crazy Eights.

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

Workflows should be expressed by composing pure transformations where possible. Prefer simple functions first; let abstractions emerge from repeated domain code rather than from speculation.

Domain functions should read like domain statements, for example:

- `play-card`
- `draw-card`
- `valid-play?`
- `advance-turn`
- `game-over?`

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

Those concerns belong in clients and transport layers around the application shell.

## What Counts As Specification

- source code in `src/crazy_eights/domain/`
- unit and property tests in `test/crazy_eights/domain/`
- executable scenario data in `resources/domain/scenarios/`

Simulation coverage lives in `test/crazy_eights/simulation/` and exercises the app layer as a client. It is not domain or app-layer specification.

Anything else should justify itself by affecting execution or preventing confusion.

## System Languages

The system deliberately contains several small languages with clear boundaries:

- domain language: cards, hands, turns, legal moves, winners
- command language: start game, play card, draw card, pass turn
- event/result language: card played, card drawn, turn advanced, game ended, invalid move
- view language: playable cards, available actions, current player, game status
- HTTP language: routes, params, form posts, responses

The domain language is the center. Command, event/result, view, and HTTP language should translate around it without taking over rule ownership.

## Web Boundary

The web layer is an adapter/detail, not the owner of domain behavior.

- HTTP handlers translate requests into commands.
- The application layer orchestrates command handling.
- The domain layer enforces rules and produces new state/results.
- View-model functions project domain state into UI-specific data.
- Hiccup views render already-prepared view models and contain minimal logic.

The same domain behavior should be testable without HTTP, HTML, sessions, routes, or a running server.

## Development Guidance

DDD grounds the model in the real domain language. Bottom-up Clojure design keeps the implementation small, composable, expressive, and executable.

When adding behavior:

1. Start by naming the domain concept.
2. Add or update executable examples, scenario tests, or focused tests before or alongside implementation.
3. Implement the smallest pure domain functions that express the rule.
4. Compose larger behavior from smaller functions.
5. Only then expose the behavior through application handlers and web routes.

Do not place domain rules directly in HTTP handlers or Hiccup views. Do not introduce macros, protocols, multimethods, rule engines, overly abstract state machines, or framework-like abstractions unless there is a demonstrated need.

Prefer boring, explicit, testable functions over clever abstraction.
