# Composable Simulation Strategies Design

## Goal

Add composable Crazy Eights strategies and a synchronous experiment runner so strategies can play against each other and produce useful win-rate plus efficiency statistics.

## Scope

This is a Crazy Eights slice. It should keep the strategy API future-friendly by using plain functions and data, but it must not introduce a generic card-game framework.

Included:

- current-player observations that do not expose opponent hands
- legal action candidates for the current player
- scoring rules that can be added together into a strategy
- a stronger built-in strategy based on simple Crazy Eights rules
- per-seat strategy lineups in simulation runs
- repeated experiment runs with aggregate strategy statistics
- a small operator REPL entry point for strategy comparison

Excluded for this slice:

- seeded randomness and deterministic deck streams
- persistent experiment jobs or registries
- generic multi-game abstractions
- protocols, multimethods, macros, or rule engines
- learned strategies, search trees, probability models, or opponent memory

## Strategy Model

Strategies remain pure. A strategy receives an observation for the current player and returns one legal app action without player id.

The observation is a plain map built from domain state:

```clojure
{:player 0
 :hand [...]
 :top-card {:rank :nine :suit :diamonds}
 :active-suit :diamonds
 :draw-count 21
 :discard-count 4
 :other-card-counts [5 3]
 :passes-in-row 0
 :status :in-progress}
```

It must not expose other players' hands. This keeps simulated strategies closer to what a real bot/player could know.

Legal action candidates are also plain maps:

```clojure
{:type :play-card
 :card {:rank :queen :suit :diamonds}
 :resulting-hand [...]
 :resulting-active-suit :diamonds}

{:type :play-card
 :card {:rank :eight :suit :clubs}
 :declared-suit :spades
 :resulting-hand [...]
 :resulting-active-suit :spades}

{:type :draw-card}
{:type :pass-turn}
```

For eights, candidates include one action per valid declared suit. The chosen action submitted to the app layer is the candidate without candidate-only keys such as `:resulting-hand`.

## Rules And Composition

A scoring rule is a pure function:

```clojure
(rule observation candidate) => number
```

`from-rules` builds a strategy from named rules by summing scores for each legal candidate and choosing the highest-scoring candidate. Ties are deterministic by candidate order.

This is intentionally not a rule engine. There is no agenda, inference, mutation, or conflict-resolution framework. Rules are just functions that return numbers.

First built-in scoring rules:

- `win-now`: heavily prefer playing the final card.
- `avoid-eight`: penalize eights when a non-eight play exists.
- `declare-most-held-suit`: when playing an eight, prefer declaring the suit most represented in the remaining hand.
- `prefer-rank-switch-to-held-suit`: prefer same-rank plays that switch the active suit toward suits still held in hand.
- `prefer-strong-suit`: prefer plays that leave active suit matching many remaining cards.
- `prefer-play-over-draw`: prefer legal plays over drawing or passing.

Built-in strategies:

- `first-playable`: current baseline behavior, rebuilt on legal candidates.
- `careful`: a composed strategy using the rules above.

Strategy values are plain maps:

```clojure
{:id :careful
 :choose choose-fn}
```

The simulation runner should also accept a plain function as a strategy for compatibility, treating it as `:anonymous` when statistics need an id.

## Simulation Runner Changes

`crazy_eights.simulation.app/run-to-completion!` should support:

```clojure
{:strategy strategy/careful}
{:strategies [strategy/careful strategy/first-playable]}
```

`:strategy` applies one strategy to every player. `:strategies` assigns one strategy per seat and cycles through the vector if fewer strategies than players are provided.

Each turn chooses the strategy for the current seat. The runner records lightweight metrics while running:

```clojure
{:steps 87
 :draws 12
 :plays 75
 :passes 0
 :winner 1
 :winner-strategy :careful
 :seat-strategies [:first-playable :careful]}
```

The existing background operator simulation can keep using the default strategy unless an explicit strategy is supplied later.

## Experiments

Add `crazy_eights.simulation.experiment` as a synchronous app-client language for repeated strategy comparisons.

Public API:

```clojure
(experiment/run {:games 1000
                 :player-count 4
                 :strategies [strategy/careful strategy/first-playable]})
```

The runner creates a fresh store per game, runs with no delay, and rotates the strategy lineup by run index to reduce first-seat bias.

Randomness remains ad hoc for this slice: the app layer continues to use random shuffled valid start decks.

Result shape:

```clojure
{:games 1000
 :player-count 4
 :finished 992
 :blocked 8
 :budget-exhausted 0
 :strategies
 {:careful {:seats-played 2000
            :wins 640
            :win-rate 0.32
            :avg-steps 84.1
            :avg-draws 6.4}
  :first-playable {:seats-played 2000
                   :wins 352
                   :win-rate 0.176
                   :avg-steps 91.7
                   :avg-draws 8.2}}}
```

Wins are counted by winner strategy. `:seats-played` counts how many seats a strategy occupied across all games. `:avg-steps` and `:avg-draws` average across games where that strategy occupied at least one seat.

Blocked games and budget exhaustion are not wins. They are counted separately.

## Operator API

Add a shallow REPL adapter:

```clojure
(op/compare-strategies 1000 [strategy/careful strategy/first-playable])
```

It should call the experiment namespace directly and not assemble simulation lifecycles itself.

## Testing

Tests cover:

- observation hides opponent hands and exposes card counts
- legal candidates include playable non-eights, one declared-suit candidate per eight, draw, and pass as appropriate
- `first-playable` preserves current baseline behavior
- `careful` avoids eights when a non-eight play is available
- `careful` declares the suit most represented in the remaining hand
- `careful` prefers same-rank suit switches toward suits held in hand
- simulation runs can assign different strategies per seat
- run outcomes include steps, draws, winner strategy, and seat strategies
- experiments rotate lineups and aggregate wins, seats played, win rate, average steps, and average draws
- operator strategy comparison delegates to the experiment namespace

Verification commands:

- `clojure -M:test --focus crazy_eights.simulation.strategy-test`
- `clojure -M:test --focus crazy_eights.simulation.app-test`
- `clojure -M:test --focus crazy_eights.simulation.experiment-test`
- `clojure -M:test --focus crazy_eights.operator-test`
- `clojure -M:test`
- `clojure -M:lint`
