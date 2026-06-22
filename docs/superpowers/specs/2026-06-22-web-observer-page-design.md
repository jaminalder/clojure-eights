# Web Observer Page Design

## Goal

Add a web observer page for each table that shows a complete game with all player hands face-up.

The observer page is access-token protected by a random UUID URL. Anyone with the URL can view the page. Observer links are exposed through the trusted REPL operator interface, not through the normal web UI.

## Architecture

The domain layer remains unchanged. The application layer stores a stable `:observer-id` on each game when the game is created. The web layer validates `game-id` plus `observer-id` before rendering an observer page or SSE stream. The operator layer includes the observer path in `op/games` table summaries.

Normal player and spectator views keep using `game-view`, which hides other players' hands. Observer views use a separate view-model projection that intentionally includes every player's hand.

## Routes

Observer routes are nested under the game:

- `GET /games/:id/observer/:observer-id`
- `GET /games/:id/observer/:observer-id/events`

The UUID is the access token. The game id alone does not grant observer access. Unknown games and incorrect observer ids return the existing 404 page.

## Operator Surface

`op/games` adds `:observer-path` to each table summary:

```clojure
{:game-id "game-0"
 :observer-path "/games/game-0/observer/<uuid>"}
```

The normal web UI does not show or copy observer links in this first slice.

## Observer Page

The observer page works for all table phases.

Waiting and between-games phases show seated player names and status. In-progress and finished phases show the middle stacks as normal play: draw pile back and count, top discard, and active suit. Player hands are rendered face-up in seat order using a simple responsive grid. Each player panel shows name, current-turn marker, card count, and compact hand.

The observer page has no play, join, start, or leave actions.

## Live Updates

Observer pages use SSE, reusing the existing app pub/sub and web SSE framing. Observer fragments are separate from player fragments so the normal privacy boundary remains explicit.

If the host leaves and the table ends, observer SSE can reuse the existing `table-ended` redirect behavior.

## Testing

Tests cover:

- `create-game!` assigns a stable `:observer-id`
- `op/games` includes observer paths
- observer view-model includes all player hand cards
- normal `game-view` still hides opponent cards
- observer page wires observer SSE and renders face-up opponent cards
- correct observer UUID returns 200
- incorrect observer UUID returns 404
- observer SSE route accepts correct UUID
- observer fragments are single-line and include all hands

Verification commands:

- `clojure -M:test`
- `clojure -M:lint`
- `./scripts/verify-web`
