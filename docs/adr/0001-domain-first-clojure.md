# ADR 0001: Start With A Pure Clojure Domain Core

## Status

Accepted

## Context

The project will grow into a web application, but the early risk is unclear game behavior rather than delivery mechanics.

The project is domain-first, but it should not become ceremony-first. Domain-Driven Design provides the semantic model and boundary discipline. Bottom-up Lisp/Clojure design provides the implementation discipline: small functions over immutable data, composed into larger behavior.

## Decision

Start with a pure Clojure domain core modeled as immutable data and pure functions.

Use DDD to decide the language the software should speak: cards, hands, turns, legal moves, winners, commands, events, invariants, and state transitions.

Use bottom-up Clojure design to grow that language inside the program: small composable domain primitives, pure transformations, executable examples, and only the abstractions proven necessary by repeated concrete code.

## Consequences

- game rules can be tested without infrastructure
- scenarios can define intended behavior directly
- future web and runtime layers compose around the domain instead of shaping it prematurely
- the domain layer should read as a small executable language for Crazy Eights, not as CRUD wrappers or framework-style ceremony
- premature rule engines, abstract state machines, macro DSLs, protocols, and multimethods should be avoided until concrete repetition proves the need
