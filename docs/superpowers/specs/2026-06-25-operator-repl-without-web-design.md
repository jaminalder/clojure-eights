# Operator REPL Without Web Design

## Goal

Add a project-owned nREPL mode for simulations and experiments that loads the app classpath and runtime context without starting the web server.

## Problem

Conjure's fallback auto-REPL is editor-owned. It is useful for scratch evaluation, but it is not the app runtime and does not clearly express the project context operators expect when running simulations and experiments.

The existing `:operator-web` alias starts both web and nREPL. Experiments do not need the web server.

## Design

Add an `:operator-repl` alias that starts only nREPL through `crazy_eights.runtime`.

The process should:

- bind nREPL to `127.0.0.1`
- write `.nrepl-port`
- keep running
- not start HTTP/web
- use the normal app classpath and `crazy_eights.runtime/store`

The existing `:operator-web` behavior stays unchanged.

## API

Runtime adds:

```clojure
(start-operator-repl!)
(repl-main)
```

CLI usage:

```bash
clojure -M:operator-repl
```

Then Neovim/Conjure connects to `.nrepl-port`.

## Testing

Tests cover that `start-operator-repl!` starts nREPL and does not start web.
