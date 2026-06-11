# Crazy Eights Single Game Design

## Goal

Complete the Crazy Eights domain for one full playable game, without scoring or multi-round match flow.

The result should support the entire lifecycle of a single game:

- start the game
- play legal cards
- draw when unable to play
- reshuffle when the stock is exhausted
- pass when no draw is possible
- detect both win and blocked-game endings

## Design Priorities

This slice optimizes for:

- a complete and internally consistent single-game ruleset
- deterministic, pure domain logic
- explicit commands and events
- code and executable scenarios as the spec
- the smallest rule set that is still fully playable

## Chosen Ruleset

This design uses a minimal full-game Crazy Eights ruleset:

- a played card must match rank or active suit, unless it is an eight
- an eight may always be played and declares the next active suit
- a player must play if they have a playable card
- if a player has no playable card, they may draw one card at a time
- after each draw, the same player continues their turn
- if the drawn cards eventually make a legal play possible, the player must play and may not keep drawing or pass
- if the draw pile is empty, the discard pile except the top card may be reshuffled into a new draw pile
- because the domain is deterministic, reshuffle order is supplied explicitly by command data
- if a player has no playable card and no card can be drawn even after reshuffle, the player may pass
- if every player passes consecutively, the game ends blocked with no winner
- the first player to empty their hand wins immediately

This design intentionally excludes:

- scoring
- multiple rounds or match play
- special twos, queens, aces, or house-rule effects
- last-card announcement penalties

## Determinism And Reshuffling

The domain must stay free of randomness. That means reshuffling cannot happen implicitly inside the domain.

Instead, reshuffle is modeled as an explicit command:

```clojure
{:type :reshuffle-draw-pile
 :cards [...]} ; exact new stock order
```

The command is legal only when:

- the game is in progress
- the draw pile is empty
- the discard pile contains more than one card
- the supplied `:cards` are exactly the discard pile except for the top discard card

On success, the discard pile keeps its top card, and the supplied cards become the new draw pile in the provided order.

This keeps the domain pure while still supporting a complete playable game.

## Command Set

The completed single-game domain should support these commands:

- `:start-game`
- `:play-card`
- `:draw-card`
- `:reshuffle-draw-pile`
- `:pass-turn`

### `:play-card`

Still legal only when:

- the player is the current player
- the card is in that player’s hand
- the card is playable against the current active suit / discard top
- a declared suit is provided for eights and omitted otherwise

On success:

- remove the card from the player hand
- add it to the discard pile
- update active suit
- if the player is now empty-handed, end the game with that player as winner
- otherwise advance the turn
- reset any consecutive-pass counter

### `:draw-card`

Legal only when:

- the player is the current player
- the player has no playable card in hand
- the draw pile is non-empty

On success:

- draw the top card from the draw pile
- add it to the player hand
- keep the same player’s turn
- reset any consecutive-pass counter

The turn does not advance on draw. That is what allows redraws in the same turn.

### `:reshuffle-draw-pile`

Legal only when:

- the game is in progress
- the draw pile is empty
- the discard pile has more than one card
- the supplied cards are exactly the old discard pile except for the top card

On success:

- the top discard remains in the discard pile
- the supplied cards become the new draw pile
- the current player remains unchanged

### `:pass-turn`

Legal only when:

- the player is the current player
- the player has no playable card in hand
- the draw pile is empty
- the discard pile cannot be reshuffled because it has only the top card left

On success:

- the pass count is incremented
- if passes in a row reach player count, the game ends blocked with no winner
- otherwise the turn advances

## Error Reasons

The complete domain should use or add these domain error reasons:

- `:not-current-player`
- `:card-not-in-hand`
- `:card-not-playable`
- `:declared-suit-required`
- `:must-play-before-drawing`
- `:draw-pile-empty`
- `:reshuffle-not-allowed`
- `:invalid-reshuffle-cards`
- `:must-reshuffle-before-passing`
- `:cannot-pass-while-playable`

## Event Set

The completed single-game domain should emit these successful events:

- `:game-started`
- `:card-played`
- `:suit-declared`
- `:card-drawn`
- `:draw-pile-reshuffled`
- `:turn-advanced`
- `:turn-passed`
- `:game-won`
- `:game-blocked`

### Event Effects

#### `:card-drawn`

- removes the first card from draw pile
- appends the card to the player hand
- resets consecutive passes to zero

#### `:draw-pile-reshuffled`

- replaces the draw pile with the supplied cards
- leaves the top discard card untouched

#### `:turn-passed`

- increments consecutive passes
- advances to the next player

#### `:game-blocked`

- sets status to finished
- leaves winner as `nil`

## State Shape

The existing game state should be extended with:

```clojure
:passes-in-row 0
```

This value is:

- initialized to `0` on start
- reset to `0` by successful draw and play actions
- incremented by pass actions

This is the minimal state needed to detect a blocked game deterministically.

## File Responsibilities

### `src/crazy_eights/domain/model.clj`

- keep card/rule predicates
- add any minimal helper predicates needed for reshuffle validation or blocked-game logic if they are truly domain rules rather than command orchestration

### `src/crazy_eights/domain/commands.clj`

- implement all new command legality
- keep turn-cycle and error logic here

### `src/crazy_eights/domain/events.clj`

- implement the new successful transitions

### `src/crazy_eights/domain/invariants.clj`

- validate `:passes-in-row`
- ensure finished states remain consistent

### `src/crazy_eights/domain/generators.clj`

- update generated state shape so it includes the new field

### `src/crazy_eights/domain/scenarios.clj`

- extend the runner so scenarios can describe multiple commands in sequence
- keep backward compatibility with the existing single-command scenario shape if it remains simple to do so

### `resources/domain/scenarios/`

Add executable scenarios that cover the newly complete single-game behavior.

At minimum, the scenario set should include:

- redraw within a turn until a playable card appears
- reshuffle and continue play
- pass when no play and no draw are possible
- blocked game after a full cycle of passes
- one full playable game flow ending in a win

## Scenario Format Extension

The current scenario runner only supports one command. To describe a full game, it should support a sequence of steps.

Target shape:

```edn
{:name "full game"
 :given {:state ...}
 :steps [{:command ...
          :then {:events [...]}}
         {:command ...
          :then {:error {:reason ...}}}]
 :then {:state-matches {...}}}
```

This lets scenarios become executable game transcripts instead of isolated single-command examples.

## Testing Requirements

### Unit Tests

Direct command tests should cover:

- redraw behavior
- reshuffle legality and invalid reshuffle rejection
- pass legality
- blocked-game completion
- draw/play/pass interactions across the new complete turn model

### Invariant Tests

Add coverage showing that:

- reshuffle preserves invariants
- passing preserves invariants
- blocked finish states are valid

### Scenario Tests

Scenarios should prove that the game can now:

- continue through empty stock conditions
- recover via reshuffle
- end through either win or blocked completion

## Simplifying Choices

To keep the ruleset full but minimal, this design deliberately does not implement optional house-rule features such as:

- skip cards
- reverse cards
- draw-two stacks
- optional drawing when a play is available
- immediate play as a separate post-draw privilege rather than just continuing the same turn

## Success Criteria

This single-game domain is complete when:

- every turn has a legal path to continue or end
- stock exhaustion is handled deterministically
- passing is explicit and rule-bound
- blocked games terminate cleanly
- one full game can be expressed as executable scenario data
- tests and scenarios cover the finished single-game lifecycle
