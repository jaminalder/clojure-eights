# ADR 0001: Start With A Pure Clojure Domain Core

## Status

Accepted

## Context

The project will grow into a web application, but the early risk is unclear game behavior rather than delivery mechanics.

## Decision

Start with a pure Clojure domain core modeled as immutable data and pure functions.

## Consequences

- game rules can be tested without infrastructure
- scenarios can define intended behavior directly
- future web and runtime layers compose around the domain instead of shaping it prematurely
