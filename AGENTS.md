# Project Instructions

## Git

- Work on the current branch in this repository.
- Do not create or use git worktrees.
- Commit whenever it is useful for progress.
- Never push.

## Clojure / Lisp Rules

- When editing `*.clj`, `*.cljc`, `deps.edn`, `.clj-kondo/**`, `README.md`, `docs/domain.md`, or `docs/adr/**`, use the repo-local `clojure-craft` skill before implementation.
- Prefer data and transformation over object modeling.
- Prefer simple values, plain maps, vectors, sets, and sequences over custom types unless a custom type buys something concrete.
- Keep functions small, direct, and REPL-friendly.
- Avoid Java-shaped naming, unnecessary wrappers, speculative abstraction, and framework-style indirection.
- Code is the primary specification. Tests are executable specification.
- Keep non-code artifacts only when they affect execution, reduce confusion, or constrain future edits.
- Prefer standard library functions and idiomatic Clojure forms over custom helpers.

## Project Architecture

- This repo is domain-first.
- Keep the domain pure: no HTTP, database, filesystem, logging, config, time, randomness, or runtime-mutable concerns in domain namespaces.
- Scenario EDN is kept only because tests execute it.
