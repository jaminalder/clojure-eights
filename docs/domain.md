# Domain Architecture

## Namespace Responsibilities

- `crazy_eights.domain.model`: core values and constructors
- `crazy_eights.domain.schema`: Malli schemas for domain data
- `crazy_eights.domain.rules`: pure rule predicates
- `crazy_eights.domain.commands`: command decision logic
- `crazy_eights.domain.events`: event application
- `crazy_eights.domain.invariants`: state consistency checks
- `crazy_eights.domain.scenarios`: EDN scenario loading and execution support
- `crazy_eights.domain.generators`: valid test data generators

## Command / Event Flow

1. A command is evaluated against immutable state.
2. The command handler returns either domain events or a domain error.
3. Events are applied in order to produce the next state.
4. Invariants are checked on successful state transitions.

## Scenario Format

```edn
{:name "play matching rank"
 :given {:state ...}
 :when {:command ...}
 :then {:events [...]
        :state-matches {...}}}
```

Invalid commands use `:then {:error {...}}`.

## Invariants

Successful commands must preserve core state consistency:

- valid cards only
- valid active suit
- valid current player index
- valid player hand structure
- non-empty discard pile while in progress
- winner consistent with an empty hand

## Exclusions

Do not import HTTP, Ring, routing, databases, WebSockets, UI, time, randomness, logging, config, environment, filesystem, or mutable runtime primitives into the domain.
