# Remove Web Simulation Observer Design

## Goal

Remove the simulation observer site from the web layer without removing simulation capability from the application or domain layers.

The multiplayer game web UI must remain fully functional for players. The root route `/` remains the game start page.

## Scope

Remove only the web-facing simulation observer surface:

- `GET /observer`
- `POST /simulations`
- `GET /simulations/:id/events`
- observer page Hiccup and JavaScript
- simulation-log SSE framing and subscription wiring in the web layer
- web server construction of a simulation service used only by observer routes
- tests and docs that describe the removed web observer

Keep simulation support outside the web layer:

- `crazy_eights.app.simulation`
- domain simulation tests
- application simulation tests
- `:sim-log` and `:app-sim-log` aliases

Keep the player game web surface:

- `/`
- `/games`
- `/games/:id`
- `/games/:id/join`
- `/games/:id/start`
- `/games/:id/play`
- `/games/:id/draw`
- `/games/:id/pass`
- `/games/:id/leave`
- `/games/:id/hand`
- `/games/:id/events`
- static card and htmx assets

## Architecture

The web layer should return to being only the multiplayer game adapter. HTTP routes translate browser requests into game commands, views render game state, and SSE streams game fragments.

The application simulation service remains an application-layer runtime utility. It can still be exercised by app tests and CLI aliases, but it is no longer exposed through HTTP.

The domain layer remains unchanged and pure.

## Components

`crazy_eights.web.routes` should no longer require `crazy_eights.app.simulation`, `crazy_eights.domain.model`, or `crazy_eights.web.page` for simulation observer behavior. It should register only game routes and resource/default handlers.

`crazy_eights.web.sse` should keep only game fragment SSE functions. Simulation-specific log frame conversion, `simulation/subscribe!`, and `simulation/unsubscribe!` belong outside the web layer once the observer is gone.

`crazy_eights.web.server` should create only an app store and pass it to `routes/app`. It should not create a simulation service for web startup.

`crazy_eights.web.page` should be deleted if it only renders the removed observer page.

## Error Handling

Removed simulation observer routes should fall through to the existing not-found handler. There is no compatibility route or redirect because the observer is intentionally removed.

Game route error handling remains unchanged.

## Testing

Update focused web tests so they no longer expect `/observer` or `/simulations` routes. Keep or add coverage proving the player-facing game routes still work, especially the start page and game SSE route wiring.

Run:

- `clojure -M:test`
- `clojure -M:lint`
- `./scripts/verify-web`

Also keep simulation verification available through existing tests and aliases:

- `clojure -M:test --focus crazy_eights.domain.simulation-test`
- `clojure -M:test --focus crazy_eights.app.simulation-test`
- `clojure -M:sim-log`
- `clojure -M:app-sim-log`

## Success Criteria

- `/observer` is no longer served.
- `/simulations` and `/simulations/:id/events` are no longer served.
- No web namespace depends on `crazy_eights.app.simulation`.
- Player game web routes remain functional.
- App and domain simulations still run without HTTP.
- Full tests, lint, and live web verification pass.
