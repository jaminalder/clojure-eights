# Clojure Agent Guidance Design

## Goal

Make this repository reliably produce idiomatic Lisp and Clojure code from AI agents by encoding language philosophy, style, tooling, review rules, and project-specific constraints in durable repo-local instructions.

## Why This Exists

The main risk is not lack of syntax knowledge. It is agents producing Java-shaped or generic FP code that technically works but is not idiomatic, composable, or clear in a Lisp/Clojure codebase.

This repo should bias agents toward:

- code as data
- simple values over object modeling
- explicit boundaries between data, state, and effects
- small, composable functions
- executable specification through code and tests
- REPL-friendly, direct development style

The guidance should be strong enough to shape future work, but small enough that it does not become another drifting documentation layer.

## Research Basis

The guidance is grounded in the following Clojure and Lisp sources:

- Clojure rationale: pragmatic Lisp, immutable data, simplicity, code-as-data, concurrency, and avoidance of mutable OO-heavy design
- Clojure syntax and REPL guides: expressions as the unit of development, reader/compiler separation, interactive development, and direct exploration
- community Clojure style guidance: naming, namespace hygiene, formatting, small functions, data-oriented idioms, and restraint around macros
- clj-kondo configuration guidance: project-local executable lint rules, namespace-local tuning, and repo-distributed lint configuration
- spec guide: useful as a source of data-oriented thinking and generators, but not mandatory for this repo unless it earns its keep as executable behavior

## Design Principles

### Lisp Principles

- Prefer data and transformation over object modeling.
- Preserve code-as-data clarity. Forms should be easy to read as plain data structures.
- Keep the language surface small. Use composition before abstraction.
- Treat macros as a last resort. If a function will do, use a function.

### Clojure Principles

- Optimize for immutable values, pure functions, and REPL-aided development.
- Prefer maps, vectors, sets, and seqs over custom types unless a type buys something concrete.
- Use namespaced data and clear function boundaries.
- Separate values, state, and effects explicitly.
- Prefer a few composable functions over framework-like abstraction layers.

### Project Principles

- Code is the primary specification.
- Tests are executable specification.
- Scenario EDN stays only when executed by tests.
- Non-code artifacts must justify their existence by changing execution, preventing confusion, or constraining future edits.
- Duplicate descriptive layers should be removed.

## Desired Outcome

After this change, an agent working in this repo should have:

- one always-on instruction surface
- one deep Clojure/Lisp skill surface
- one executable lint surface
- one obvious verification path

This should reduce hallucination risk by minimizing duplicated truth and by making repo expectations explicit and executable.

## Proposed Repository Changes

### 1. Expand `AGENTS.md`

`AGENTS.md` becomes the always-read project contract for all agents.

It should include:

- this repo is domain-first and code-as-spec
- when editing `*.clj`, `*.cljc`, `deps.edn`, lint config, or domain docs, agents must use the repo-local Clojure skill before implementation
- prefer standard library functions and idiomatic data transformations
- avoid Java-shaped naming, object modeling, needless wrapper namespaces, and speculative abstraction
- prefer executable tests and scenarios over extra prose

`AGENTS.md` should remain short. It should direct deeper guidance to the repo skill rather than duplicate it.

### 2. Add A Repo-Local Skill

Add a project-local skill under:

` .opencode/skills/clojure-craft/SKILL.md `

This skill should be comprehensive and opinionated. It should cover:

- Lisp philosophy
- Clojure philosophy
- idiomatic naming and namespace structure
- function shape guidance
- data-oriented modeling guidance
- macro restraint
- REPL-oriented development guidance
- test-as-spec rules
- review checklist for future edits
- project-specific domain rules for this repo

The skill should explicitly help agents answer questions like:

- Is this namespace earning its existence?
- Is this code simpler as data?
- Is this function easy to evaluate at the REPL?
- Is this abstraction reducing complexity or adding it?
- Is there duplicate truth between code, tests, and docs?

### 3. Add Executable Guardrails With `clj-kondo`

Add project lint configuration under:

` .clj-kondo/config.edn `

The lint config should support idiomatic Clojure without turning the repo into warning noise.

The initial rules should focus on high-signal guidance such as:

- unsorted required namespaces
- shadowed vars
- redundant wrappers and redundant lets
- alias consistency and unused aliases
- dynamic var naming if applicable
- discouraging noisy or misleading forms where lint support exists

The point is not maximal linting. The point is enforceable, useful idiomatic pressure.

### 4. Add A Lint Alias

Add a `:lint` alias in `deps.edn` so humans and agents have one obvious lint command.

The repo should make these two commands canonical:

- `clojure -M:test`
- `clojure -M:lint`

### 5. Re-Review The Codebase Under The New Rules

After the guidance and lint surface exist, review the current domain code again and clean up only justified issues:

- misleading names
- Java-shaped code
- unnecessary indirection
- awkward data flow
- non-idiomatic control flow
- duplicated truth between code, tests, and docs

Avoid churn for style alone.

## What Not To Add

- no large style handbook in `docs/`
- no separate philosophy essay duplicated outside the skill
- no formatter setup unless the repo actually needs it now
- no reintroduction of schema/spec/Malli layers unless they become executable and justified
- no heavy framework around agent guidance

## Why This Design Is Preferred

This design uses three layers with distinct jobs:

1. `AGENTS.md` for always-on repo instructions
2. repo-local skill for deep language and review guidance
3. `clj-kondo` for executable enforcement

That separation is better than either documentation-only or skill-only approaches.

Documentation-only is too weak. Skill-only is not universal enough. Lint-only cannot encode philosophy or review judgment. Together, these three layers cover instruction, reasoning, and enforcement.

## Follow-Up Cleanup Criteria

During the second code review pass, the standard for keeping or changing code is:

- keep names that are clear and aligned with current behavior
- remove names or layers that preserve historical structure rather than current meaning
- prefer direct data transformations over ceremony
- prefer fewer namespaces when extra splits do not buy clarity
- keep comments rare and only where code is not self-evident

## Verification

The implementation is complete when:

- the repo contains a durable always-on instruction layer
- the repo contains a deep local Clojure/Lisp skill
- the repo contains executable lint rules and a lint command
- tests still pass
- lint is usable and tuned, not noisy
- the current domain code has been re-reviewed and simplified where justified

## Success Criteria

Success means a future agent entering this repo can quickly infer:

- what idiomatic Clojure means here
- how strongly to prefer data and simplicity
- how to validate its changes
- what kinds of abstractions to reject
- where the actual specification lives

The repository should become easier for both humans and agents to understand, with less duplicated truth and lower risk of non-idiomatic edits.
