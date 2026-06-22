# REPL Operator Interface Design

## Goal

Add a trusted same-JVM REPL operator interface for inspecting and observing live Crazy Eights games.

The first slice includes only operator inspection and observation. Simulations and bots remain future work.

## Architecture

The REPL operator interface runs inside the same JVM as the web server. A process-local runtime namespace owns the shared application store and server lifecycles. The web layer remains an HTTP adapter around the app layer. The operator layer is a trusted REPL adapter around the same app store.

Normal web startup does not start a REPL. Operator startup is explicit and starts the web server plus a local-only nREPL server.

## REPL Protocol

Use nREPL only.

The nREPL server binds to `127.0.0.1`, writes `.nrepl-port`, and is started only by the operator runtime. This supports Neovim Conjure locally and SSH shell access on a production host without exposing a remote REPL port.

## Operator Surface

The operator namespace provides a small REPL-friendly facade:

```clojure
(require '[crazy_eights.operator :as op])

(op/games)
(op/game "game-0")
(op/observe! "game-0")
(op/unobserve! "observer-id")
(op/unobserve-all!)
(op/observers)
```

The REPL remains trusted. Operators may also switch namespaces and call lower-level functions directly:

```clojure
(require '[crazy_eights.app.core :as app])
(require '[crazy_eights.runtime :as runtime])

(app/get-game runtime/store "game-0")
@runtime/store
```

## Observation

`op/games` returns all live tables grouped by status. Waiting, in-progress, finished, and between-games tables are all visible.

`op/game` returns full operator state for one game, including players, hands, draw pile, discard pile, subscribers, and any other in-memory app state.

`op/observe!` subscribes to app events for a game and prints readable event summaries. Full state remains available through `op/game`.

## Security Boundary

The nREPL is arbitrary code execution in the live process. It is trusted operator access, not an end-user interface. It must bind to `127.0.0.1` only. Production access is by SSHing into the server host and connecting to localhost.

## Scope

Included:

- explicit operator runtime mode
- local-only nREPL
- shared runtime store
- operator inspection functions
- operator observation functions
- documentation for Conjure and SSH shell use

Excluded:

- bots
- simulation runners
- HTTP admin routes
- auth
- persistence
- remote REPL exposure
