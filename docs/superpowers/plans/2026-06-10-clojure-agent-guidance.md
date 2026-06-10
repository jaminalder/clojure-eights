# Clojure Agent Guidance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add durable repo-local Clojure/Lisp guidance, executable lint guardrails, and a follow-up domain cleanup pass aligned with those new rules.

**Architecture:** Put always-on project instructions in `AGENTS.md`, deep language guidance in a repo-local opencode skill, and executable style pressure in `.clj-kondo/config.edn` plus a `:lint` alias. Then re-review the current domain code under those rules and make only justified cleanups.

**Tech Stack:** Clojure, clj-kondo, clojure.test, Kaocha

---

## File Map

- Modify: `AGENTS.md`
- Modify: `deps.edn`
- Create: `.opencode/skills/clojure-craft/SKILL.md`
- Create: `.clj-kondo/config.edn`
- Modify: `README.md` if lint/test commands need surfacing
- Modify: `src/crazy_eights/domain/model.clj`
- Modify: `src/crazy_eights/domain/commands.clj`
- Modify: `src/crazy_eights/domain/invariants.clj`
- Modify: `src/crazy_eights/domain/scenarios.clj`
- Modify: `src/crazy_eights/domain/generators.clj`
- Modify: selected tests under `test/crazy_eights/domain/`

### Task 1: Add Always-On Project Instructions

**Files:**
- Modify: `AGENTS.md`

- [ ] **Step 1: Write the failing instruction-surface test in the file itself**

Replace `AGENTS.md` contents with this project-specific instruction set:

```markdown
# Project Instructions

## Git

- Work on the current branch in this repository.
- Do not create or use git worktrees.
- Commit whenever it is useful for progress.
- Never push.

## Clojure / Lisp Rules

- When editing `*.clj`, `*.cljc`, `deps.edn`, `.clj-kondo/**`, or domain docs, use the repo-local `clojure-craft` skill before implementation.
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
```

- [ ] **Step 2: Review the file manually for brevity and overlap**

Check that `AGENTS.md` remains short and points deeper language behavior to the skill instead of duplicating a full handbook.

- [ ] **Step 3: Commit the instruction layer**

```bash
git add AGENTS.md
git commit -m "docs: add project clojure guidance"
```

### Task 2: Add The Repo-Local Clojure Skill

**Files:**
- Create: `.opencode/skills/clojure-craft/SKILL.md`

- [ ] **Step 1: Write the skill file**

Create `.opencode/skills/clojure-craft/SKILL.md` with:

```markdown
---
name: clojure-craft
description: Use when editing Clojure, Lisp-style code, `deps.edn`, `.clj-kondo` config, or domain docs in this repository. Enforces idiomatic Lisp/Clojure, data-oriented design, REPL-friendly development, and code-as-spec review rules.
---

# Clojure Craft

## Purpose

Use this skill when working on Clojure code in this repository. It exists to keep edits idiomatic, data-oriented, composable, and easy for both humans and AI agents to read.

## Lisp Principles

- Prefer data and transformation over object modeling.
- Keep the surface area small.
- Preserve code-as-data clarity.
- Use macros only when a function cannot do the job.

## Clojure Principles

- Prefer immutable values and pure functions.
- Separate values, state, and effects.
- Prefer maps, vectors, sets, and seqs over custom types unless the type buys something concrete.
- Use namespaced data and direct function boundaries.
- Optimize for REPL-aided development.

## Repo Rules

- Code is the primary specification.
- Tests are executable specification.
- Scenario EDN exists only when executed by tests.
- Non-code artifacts must justify themselves.
- Avoid duplicate descriptive layers.

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
```

- [ ] **Step 2: Review the skill frontmatter and folder naming**

Check that:

- folder name is `clojure-craft`
- `name` matches the folder name
- description says both what it does and when to trigger it

- [ ] **Step 3: Commit the skill**

```bash
git add .opencode/skills/clojure-craft/SKILL.md
git commit -m "docs: add clojure craft skill"
```

### Task 3: Add Executable Lint Guardrails

**Files:**
- Create: `.clj-kondo/config.edn`
- Modify: `deps.edn`

- [ ] **Step 1: Write the initial lint config**

Create `.clj-kondo/config.edn` with:

```clojure
{:linters
 {:unused-alias {:level :warning}
  :unused-binding {:level :warning}
  :shadowed-var {:level :warning}
  :redundant-do {:level :warning}
  :redundant-let {:level :warning}
  :redundant-call {:level :warning}
  :redundant-fn-wrapper {:level :warning}
  :if-nil-return {:level :warning}
  :not-nil? {:level :warning}
  :unsorted-required-namespaces {:level :warning}
  :aliased-namespace-symbol {:level :warning}
  :single-key-in {:level :warning}}}
```

- [ ] **Step 2: Add the lint alias in `deps.edn`**

Update `deps.edn` to:

```clojure
{:paths ["src" "resources"]
 :mvn/repos {"artifactory.swisscom.com" {:url "https://repo.maven.apache.org/maven2/"}
             "artifactory-oce" {:url "https://repo.clojars.org/"}}
 :deps {org.clojure/clojure {:mvn/version "1.12.0"}
        metosin/malli {:mvn/version "0.16.4"}
        org.clojure/test.check {:mvn/version "1.1.1"}}
 :aliases
 {:test {:extra-paths ["test"]
         :extra-deps {lambdaisland/kaocha {:mvn/version "1.91.1392"}}
         :main-opts ["-m" "kaocha.runner"]}
  :lint {:replace-deps {clj-kondo/clj-kondo {:mvn/version "2024.08.29"}}
         :main-opts ["-m" "clj-kondo.main" "--lint" "src" "test"]}}}
```

- [ ] **Step 3: Run lint to verify the new command works**

Run: `clojure -M:lint`
Expected: either clean output or a focused set of warnings that reflect real issues in the codebase.

- [ ] **Step 4: Tune lint if the initial rule set is noisy**

If lint output reveals low-value warnings, adjust `.clj-kondo/config.edn` only enough to keep the rules high-signal.

- [ ] **Step 5: Commit the lint surface**

```bash
git add .clj-kondo/config.edn deps.edn
git commit -m "chore: add clojure lint guardrails"
```

### Task 4: Re-Review And Clean Up The Domain Under The New Rules

**Files:**
- Modify: `src/crazy_eights/domain/model.clj`
- Modify: `src/crazy_eights/domain/commands.clj`
- Modify: `src/crazy_eights/domain/invariants.clj`
- Modify: `src/crazy_eights/domain/scenarios.clj`
- Modify: `src/crazy_eights/domain/generators.clj`
- Modify: tests only if behavior or naming changes require it

- [ ] **Step 1: Run lint and identify one concrete idiomatic cleanup at a time**

Run: `clojure -M:lint`
Expected: warnings or no warnings. If there are warnings, select one real issue at a time.

- [ ] **Step 2: Write the smallest failing or pressure-revealing test if behavior or naming changes**

If a cleanup changes observable behavior or a misleading public name, add or adjust the minimal affected test first before code changes.

- [ ] **Step 3: Make only justified code cleanups**

Examples of acceptable cleanups:

- replace awkward boolean ceremony with `boolean`, `some?`, `when`, `if-let`, or `when-let`
- remove unnecessary local helpers or duplicated computations
- improve a misleading name that no longer matches current behavior
- simplify data flow with `update`, `cond->`, or direct transformations when that clearly improves readability

Do not add speculative abstractions or broad rewrites.

- [ ] **Step 4: Run the full test suite and lint again**

Run:

```bash
clojure -M:test
clojure -M:lint
```

Expected: tests pass and lint is clean or contains only consciously accepted warnings.

- [ ] **Step 5: Commit the follow-up cleanup**

```bash
git add src test README.md
git commit -m "refactor: tighten idiomatic clojure core"
```

### Task 5: Surface The Canonical Commands In The README

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update the README test section to include lint**

Adjust the `## Tests` section in `README.md` to:

```markdown
## Tests

Run all tests:

```bash
clojure -M:test
```

Run lint:

```bash
clojure -M:lint
```

Run one namespace:

```bash
clojure -M:test --focus crazy_eights.domain.commands-test
```
```

- [ ] **Step 2: Run the full verification commands again**

Run:

```bash
clojure -M:test
clojure -M:lint
```

Expected: PASS / usable lint output.

- [ ] **Step 3: Commit the README alignment**

```bash
git add README.md
git commit -m "docs: document clojure lint workflow"
```

## Plan Review Notes

- Spec coverage: covers always-on instructions, repo-local skill, executable linting, canonical commands, and a second cleanup pass under the new rules.
- Placeholder scan: no placeholders remain; every step specifies exact files and commands.
- Type consistency: the plan keeps the current domain namespace structure and only allows targeted simplification where lint or review justifies it.
