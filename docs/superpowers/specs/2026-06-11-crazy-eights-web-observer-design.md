# Crazy Eights Web Observer Design

## Goal

Add the first minimal web client for the project: a simulation observer page.

From a user perspective, the page should:

- load as a plain minimal HTML page
- allow entering a player count
- start a backend simulation
- receive pushed move logs in real time
- show those logs in the browser as the simulation progresses

The first version should optimize for technical correctness and clean boundaries, not design polish.

## Design Priorities

- keep the domain pure
- keep the application layer transport-agnostic
- use the smallest web stack that supports real push updates
- reuse the completed app layer and app-event logging architecture
- avoid frontend build tooling for the first page

## Chosen Stack

Use:

- Ring
- Reitit Ring
- Hiccup
- http-kit
- SSE for one-way server-to-browser updates

Why:

- Reitit gives small, explicit routing
- Hiccup gives server-rendered HTML with no JS build pipeline
- SSE is a better fit than WebSocket for one-way simulation logs
- http-kit is a small practical runtime choice for an initial web shell

## Architecture

### Layer Boundaries

#### Domain

Still owns only:

- game rules
- command decisions
- domain events
- invariant checks

#### Application Layer

Still owns only:

- game registry
- player identities and seat mapping
- app event publication
- action submission

#### Runtime Simulation Layer

Add a new runtime namespace under `src/` for long-running simulation processes.

It should:

- create and join app players
- start a game with a valid shuffled deck
- choose legal actions repeatedly
- wait between moves via injected delay function
- publish simulation log messages to subscribers

This is runtime logic, not test-only logic.

#### Web Layer

The web layer should:

- serve the observer page
- accept a start-simulation request
- expose an SSE endpoint for log delivery
- not contain game logic

### Proposed Namespaces

- `crazy_eights.app.simulation`
- `crazy_eights.web.page`
- `crazy_eights.web.routes`
- optionally `crazy_eights.web.server`

## Runtime Simulation Layer

### Responsibilities

`crazy_eights.app.simulation` should:

- create a simulation id
- maintain in-memory simulation registry
- attach logging subscriber(s)
- start background simulation work
- track current status

### Simulation Model

A simulation entry can stay small, for example:

```clojure
{:simulation-id "sim-1"
 :game-id "game-0"
 :status :running
 :player-count 4
 :subscribers {...}}
```

The simulation registry is separate from the app game registry.

### Action Selection

The runtime simulation should use the same legal-action strategy already proven in tests:

- play if possible
- else draw if possible
- else reshuffle if possible
- else pass

### Delay

The simulation runtime must not hardcode `Thread/sleep` directly in a way that makes testing difficult.

Use an injected delay function.

- production: `(fn [] (Thread/sleep 500))`
- tests: `(fn [] nil)`

## Logging And Browser Events

The project already uses app-event subscribers for logging. The web observer should reuse that idea.

The runtime simulation should produce browser-facing log messages by subscribing to app events and converting them into simple readable text.

Example messages:

- `player 0 played queen of hearts`
- `player 1 drew five of clubs`
- `player 2 passed`
- `game finished, winner: player 3`

The browser-facing event payload can stay small:

```clojure
{:message "player 0 played queen of hearts"}
```

SSE can use a single event type like `log`.

## Web Layer

### Routes

The first web layer should support:

- `GET /` -> observer page
- `POST /simulations` -> start a simulation
- `GET /simulations/:id/events` -> SSE stream

### Page Behavior

The page should be intentionally plain.

Minimal behavior:

- a number input for player count
- a start button
- an empty log container
- tiny JavaScript that:
  - posts player count to `/simulations`
  - receives a simulation id
  - opens `EventSource` to `/simulations/:id/events`
  - appends incoming messages to the page

No styling work is required beyond making it readable.

## Tests

### Runtime Simulation Tests

Add tests for `crazy_eights.app.simulation` that verify:

- a simulation can be started
- it emits log messages in order
- it reaches an end state using a no-op delay in tests

### Route Tests

Add tests that verify:

- `GET /` returns HTML
- `POST /simulations` accepts a valid player count and returns a simulation id
- invalid player counts return an error response

### Integration Tests

Add one in-memory integration test that:

- starts a simulation through the route handler or simulation service
- captures emitted log messages
- confirms several messages are produced and the simulation finishes

The first iteration does not need real browser automation.

## What Not To Add

- no React or frontend build tool
- no WebSocket layer yet
- no persistence
- no auth/session model
- no CSS/design system
- no domain logging
- no direct coupling of web handlers to domain command logic

## Success Criteria

This feature is complete when:

- the project can serve a minimal observer page
- a user can choose player count and start a backend simulation
- move logs stream to the page over SSE
- runtime simulation logic stays outside the domain and app core
- route and simulation tests pass
- the whole suite and lint stay green
