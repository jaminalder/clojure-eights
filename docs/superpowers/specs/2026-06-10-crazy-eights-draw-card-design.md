# Crazy Eights Draw Card Design

## Goal

Add the next domain slice for Crazy Eights by modeling a strict `:draw-card` command that fits the current command/event/state approach without widening the domain unnecessarily.

## Why This Slice

The current domain supports `:start-game` and `:play-card`, but not the next obvious turn action when a player cannot legally play. That leaves the domain structurally clean but behaviorally incomplete.

The next useful step is not “complete Crazy Eights.” It is a narrow, well-specified draw behavior that keeps the code small and the rules explicit.

## Chosen Rule Set

This slice uses the strictest and smallest draw rule:

- a player may draw only on their turn
- a player may draw only when they have no playable card in hand
- drawing takes exactly one card from the draw pile
- drawing ends the turn immediately
- an empty draw pile causes a domain error

This slice intentionally excludes:

- reshuffling the discard pile
- drawing until a playable card appears
- immediate play after drawing
- pass commands
- scoring and rounds

## Domain Behavior

### New Command

Add a new command shape:

```clojure
{:type :draw-card
 :player 0}
```

### Command Validity

The `:draw-card` command is valid only when all of the following are true:

- the game state is in progress
- the requesting player is the current player
- the player has no playable card in hand
- the draw pile is not empty

If any rule is violated, the command returns a domain error map rather than events.

### Error Reasons

This slice should use these reasons:

- `:not-current-player`
- `:must-play-before-drawing`
- `:draw-pile-empty`

### New Event

On success, emit a new event:

```clojure
{:type :card-drawn
 :player 0
 :card {:rank :two :suit :clubs}}
```

After the draw event, emit the existing turn advancement event.

### Event Effects

Applying `:card-drawn` should:

- remove the top card from `:draw-pile`
- append the drawn card to the player’s `:hand`

The existing `:turn-advanced` event continues to move the turn to the next player.

## File Responsibilities

### `src/crazy_eights/domain/commands.clj`

- add `:draw-card` decision logic
- keep draw legality beside the other command decisions
- reuse the current event-oriented command style

### `src/crazy_eights/domain/events.clj`

- add `:card-drawn` event application

### `src/crazy_eights/domain/invariants.clj`

- keep unchanged unless the draw flow reveals a missing invariant

### `test/crazy_eights/domain/commands_test.clj`

- add direct tests for legal and illegal draw cases

### `test/crazy_eights/domain/invariants_test.clj`

- add one invariant-preservation test after a legal draw

### `resources/domain/scenarios/`

- add at least one happy-path draw scenario EDN file because it is executable and clarifies the rule
- optionally add one invalid draw scenario only if it buys clarity beyond unit tests

### `src/crazy_eights/domain/scenarios.clj`

- likely unchanged, unless the new scenario files expose a runner gap

## Tests

### Direct Command Tests

The command test layer should cover:

- legal draw when no playable card exists
- illegal draw when a playable card exists
- illegal draw when the draw pile is empty
- illegal draw by a non-current player

### Event And State Tests

The resulting state from a legal draw should prove that:

- the drawn card leaves the draw pile
- the drawn card appears in the player hand
- the turn advances

### Invariant Test

The state after a successful draw should still satisfy `invariants/check`.

### Scenario Coverage

One happy-path scenario is enough for this slice. An invalid scenario is optional, not required.

## Design Constraints

- keep the current domain architecture intact
- do not add a new namespace for draw behavior
- keep the domain pure and deterministic
- prefer code and executable tests over additional descriptive docs
- avoid speculative support for reshuffle or follow-up play in this slice

## Success Criteria

This slice is complete when:

- `:draw-card` is implemented as a legal command
- successful draws emit domain events
- illegal draws return domain errors
- tests cover the new success and failure cases
- at least one draw scenario runs through the existing scenario test flow
- the resulting code remains small, direct, and idiomatic
