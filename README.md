# crazy-eights

Pure Clojure domain-first Crazy Eights web application project.

## Purpose

This repository starts with the game domain only. The goal is to model Crazy Eights as immutable data and pure functions before adding any web or runtime concerns.

## Architecture Intent

- pure domain core first
- command -> events -> state transition flow
- documentation and executable scenarios drive intended behavior
- future layers will compose around the domain instead of leaking into it

## Current Scope

The current implementation only establishes:

- project shell
- executable tests

Planned next steps in this direction are:

- domain namespace structure
- domain resources

## Constraints

- no database
- no durable persistence
- no web server yet
- no UI yet
- no Docker yet
- no runtime state container in the domain

Temporary in-memory application state may exist later outside the domain layer.

## Future Direction

Later iterations may add:

- Ring / Reitit style HTTP architecture
- Hiccup / HTMX style server-rendered UI
- SSE or WebSocket support
- Dockerized deployment

## Domain Purity

Domain namespaces must not depend on HTTP, persistence, logging, config, filesystem, time, randomness, or mutable runtime primitives.
