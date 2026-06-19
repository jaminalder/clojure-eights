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
- Prefer boring, explicit, testable functions over clever abstraction.
- Do not introduce macros, protocols, multimethods, rule engines, abstract state machines, or framework-like abstractions unless repeated concrete code demonstrates the need.
- Code is the primary specification. Tests are executable specification.
- Keep non-code artifacts only when they affect execution, reduce confusion, or constrain future edits.
- Prefer standard library functions and idiomatic Clojure forms over custom helpers.

## Project Architecture

- This repo is domain-first and bottom-up Clojure in implementation style.
- DDD defines what language the software should speak; Lisp/Clojure design defines how that language grows inside the program.
- Keep the domain pure: no HTTP, database, filesystem, logging, config, time, randomness, or runtime-mutable concerns in domain namespaces.
- Treat the domain layer as a small executable language for the problem domain, not as data structures plus CRUD operations.
- Preserve the domain/application/web separation: web translates requests, app orchestrates commands, domain enforces rules.
- Do not place domain rules directly in HTTP handlers or Hiccup views.
- Scenario EDN is kept only because tests execute it.

## Behavior Changes

- Start by naming the domain concept.
- Add or update executable examples, scenario tests, or focused tests before or alongside implementation.
- Implement the smallest pure domain functions that express the rule.
- Compose larger behavior from smaller transformations.
- Only then expose behavior through application handlers and web routes.

## Web Verification

- When working on live web behavior, browser interaction, SSE, or request/response flow in this repo, use the repo-local `web-verification` skill before claiming the web change works.
- Prefer `./scripts/verify-web` for live web checks. It starts the server, verifies HTTP routes, runs a Playwright browser smoke test, prints logs, and cleans up.
- In sandboxed agents, request approval for `./scripts/verify-web` as one scoped command rather than asking separately for server, curl, Playwright, and kill commands.
