---
name: clojure-craft
description: Use when editing Clojure, Lisp-style code, `deps.edn`, `.clj-kondo` config, or domain docs in this repository. Enforces idiomatic Lisp/Clojure, data-oriented design, REPL-friendly development, and code-as-spec review rules.
---

# Clojure Craft

## Purpose

Use this skill when working on Clojure code in this repository. It exists to keep edits idiomatic, data-oriented, composable, and easy for both humans and AI agents to read.

This project combines Domain-Driven Design with bottom-up Lisp/Clojure design. DDD defines what language the software should speak. Lisp/Clojure design defines how that language grows inside the program.

## Lisp Principles

- Prefer data and transformation over object modeling.
- Keep the surface area small.
- Preserve code-as-data clarity.
- Grow a small internal domain language from simple functions and composition.
- Use macros only when a function cannot do the job.

## Clojure Principles

- Prefer immutable values and pure functions.
- Separate values, state, and effects.
- Prefer maps, vectors, sets, and seqs over custom types unless the type buys something concrete.
- Use namespaced data and direct function boundaries.
- Prefer boring, explicit, testable functions over clever abstraction.
- Optimize for REPL-aided development.

## Repo Rules

- Code is the primary specification.
- Tests are executable specification.
- Preserve the domain/application/web separation.
- Put domain rules in the domain, not in HTTP handlers or Hiccup views.
- Scenario EDN exists only when executed by tests.
- Non-code artifacts must justify themselves.
- Avoid duplicate descriptive layers.

## Domain Design

- Start behavior changes by naming the domain concept.
- Build small composable domain primitives.
- Model domain behavior as immutable data plus pure functions.
- Compose larger workflows from smaller transformations.
- Let abstractions emerge from repeated domain code.
- Avoid rule engines, abstract state machines, macro DSLs, protocols, multimethods, and framework-like abstractions unless concrete repetition proves the need.

## Naming And Boundaries

- Use idiomatic Clojure names in `lisp-case`.
- Predicate names end in `?`.
- Side-effecting functions end in `!`.
- Namespace splits must earn their existence.
- Remove thin wrappers that only rename or re-export behavior.

## Function Shape

- Prefer short, direct functions.
- Prefer transforming whole values over field-by-field ceremony.
- Prefer standard library functions to custom helpers.
- Prefer `when`, `when-let`, `if-let`, `cond->`, `some->`, `update`, `update-in`, `assoc`, and `reduce` where they simplify code.
- Avoid Java-style boolean ceremony and object vocabulary.

## Macros

- Do not introduce a macro if a function or higher-order function will do.
- If a macro is truly needed, keep it narrow and syntax-oriented.

## REPL And Development Style

- Write code that is easy to call with plain data at the REPL.
- Prefer pure helpers that can be evaluated independently.
- Avoid hidden global state.

## Testing

- Tests should describe behavior with concrete data.
- Property tests should check useful invariants.
- Keep test names aligned with the current code they exercise.
- Prefer executable examples over extra prose.

## Review Checklist

Before finalizing a Clojure change, ask:

- Is this code simpler as data?
- Is this namespace earning its existence?
- Is this function easy to evaluate at the REPL?
- Is the name idiomatic Clojure?
- Is this abstraction reducing complexity or adding it?
- Is there duplicate truth between code, tests, and docs?
- Would a future agent misread this?

## Anti-Patterns

- Java-shaped naming or class-like modeling without need
- wrapper namespaces with no semantic value
- non-executable specification layers that duplicate code
- defensive indirection without a real boundary
- verbose control flow where standard idioms are clearer
