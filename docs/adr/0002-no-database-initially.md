# ADR 0002: No Database Initially

## Status

Accepted

## Context

The initial scope is a small multiplayer card game with no requirement for durable history or long-lived records.

## Decision

Do not introduce a database in the initial architecture. Future application state will be temporary and in-memory outside the pure domain layer.

## Consequences

- no persistence complexity in the first slice
- domain model stays independent of storage concerns
- infrastructure decisions can be deferred until a real need appears
