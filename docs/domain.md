# Domain Architecture

The domain is intentionally small.

## Namespaces

- `crazy_eights.domain.model`: domain values, constructors, and rule predicates
- `crazy_eights.domain.commands`: command decision logic
- `crazy_eights.domain.events`: event application
- `crazy_eights.domain.invariants`: state consistency checks
- `crazy_eights.domain.scenarios`: EDN scenario execution support
- `crazy_eights.domain.generators`: test data generators

## Flow

1. A command is evaluated against immutable state.
2. The command returns either domain events or a domain error.
3. Events are applied in order to produce the next state.
4. Tests and scenarios describe the intended behavior.

## What Counts As Specification

- source code in `src/crazy_eights/domain/`
- unit and property tests in `test/crazy_eights/domain/`
- executable scenario data in `resources/domain/scenarios/`

Anything else should justify itself by affecting execution or preventing confusion.
