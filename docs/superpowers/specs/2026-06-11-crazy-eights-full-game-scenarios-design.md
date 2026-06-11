# Crazy Eights Full Game Scenario And Simulation Design

## Goal

Improve the completed single-game domain coverage in two ways:

- replace the current midgame-based "full game" scenario with a real deterministic transcript that starts from a complete deck and a `:start-game` command
- add a separate shuffled-deck auto-play test that repeatedly chooses legal actions until the game ends

The domain itself should remain pure and deterministic.

## Problem

The current `full_single_game_to_win.edn` includes a full deck in `:given`, but the actual game state is already preconstructed. That means the scenario is not really testing the initial deal or the actual game start lifecycle.

Also, there is no broader simulation coverage that exercises the completed single-game rules across shuffled decks and multiple player counts.

## Design Principles

- keep randomness out of production code
- keep scenarios executable and realistic
- keep simulation helpers in tests, not the domain
- preserve command/event purity
- maximize confidence without adding framework-like machinery

## Deterministic Full Game Scenario

### Desired Shape

The deterministic full-game scenario should start with no state and a complete deck, then use the real `:start-game` command as the first step.

Target shape:

```edn
{:name "full single game to win"
 :given {:state nil}
 :steps [{:command {:type :start-game
                    :player-count 4
                    :deck [...]}
          :then {:events [...]}}
         {:command ...
          :then {:events [...]}}
         ...]
 :then {:state-matches {...}}}
```

This is better because:

- the deck is no longer decorative
- the scenario proves the actual start-game lifecycle
- the initial deal and first discard are part of the executable spec

### Scenario Runner Requirement

The scenario runner already supports multi-step scenarios. It should also cleanly support `:given {:state nil}` as the initial state for a transcript.

That means no special scenario-only setup path is needed. The first step simply runs `:start-game` against `nil`.

## Realistic Full Game Transcript

The deterministic scenario should still exercise a genuinely complete game flow.

The chosen transcript should include, within one game:

- start game from full deck
- normal play turns
- at least one draw
- at least one redraw on the same turn
- at least one explicit reshuffle
- at least one explicit pass
- eventual win

The transcript does not need to include a blocked ending, because blocked behavior is already covered by dedicated scenarios.

## Auto-Play Simulation Test

### Purpose

Add one non-deterministic test that starts real games from shuffled decks and automatically plays them to completion.

This test exists to increase confidence that the completed command set is sufficient to always reach an end state under a simple legal strategy.

### Location

Keep the simulation logic in the test layer, not in production namespaces.

Preferred file:

`test/crazy_eights/domain/simulation_test.clj`

### Behavior

The test helper should:

1. create or shuffle a complete 52-card deck in test code
2. call `:start-game`
3. repeatedly inspect the current state and choose the next legal action:
   - if the current player has a playable card, play one
   - else if the draw pile is non-empty, draw one
   - else if reshuffle is legal, reshuffle using a deterministic order derived from current discard contents
   - else pass
4. apply resulting events to advance state
5. stop when `:status` becomes `:finished`
6. fail if a hard step limit is exceeded

### Logging

The simulation should record a transcript of:

- chosen command
- produced events
- optionally small state summaries such as current player, draw size, passes-in-row, winner, status

The transcript should be surfaced in assertion failures so debugging a bad simulation run is easy.

Printing every run is unnecessary; logging only on failure is enough.

### Determinism Boundary

The simulation test itself may use random shuffling because it is test code, not domain code.

The domain remains deterministic because the shuffled deck is still passed in as explicit command data.

## Deck Support In Tests

To make this pleasant, test code needs an easy way to build a complete deck.

The cleanest option is to add a small helper in `model.clj`:

```clojure
(def full-deck
  (vec (for [suit suits
             rank ranks]
         (card rank suit))))
```

This is still plain domain data, not test-only machinery, and it is useful for both deterministic scenarios and simulation tests.

## File Responsibilities

### `src/crazy_eights/domain/model.clj`

- may expose `full-deck` if that simplifies scenario and test construction

### `src/crazy_eights/domain/scenarios.clj`

- should work with transcript scenarios starting from `nil`
- no scenario-only domain shortcuts

### `resources/domain/scenarios/full_single_game_to_win.edn`

- replace current midgame setup with a true full-deck start-game transcript

### `test/crazy_eights/domain/scenarios_test.clj`

- continue running the deterministic scenario resources

### `test/crazy_eights/domain/simulation_test.clj`

- add the shuffled-deck auto-play coverage

## What Not To Add

- no production auto-play command
- no hidden randomness in the domain
- no special scenario-only deal helper
- no heavy simulation framework

## Success Criteria

This improvement is complete when:

- the full-game scenario begins with `:start-game` from a complete deck
- that scenario still reaches a real end state through a transcript of legal commands
- there is a shuffled-deck auto-play test that reaches finished states under a simple legal strategy
- the domain stays pure and deterministic
- tests remain readable and the simulation transcript is debuggable on failure
