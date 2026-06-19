---
name: web-verification
description: Use when running, testing, or debugging live web behavior in this repository, especially when working with the web observer, SSE, browser interactions, request/response flow, server lifecycle, or Playwright-based verification.
---

# Web Verification

## Purpose

Use this skill when verifying or debugging live web behavior in this repository.

The goal is to avoid guesswork and make web debugging repeatable, cheap, and complete.

## Core Rule

Do not stop at unit tests for web work.

For any meaningful web change, verify all of these layers:

1. server starts successfully
2. route behavior works via direct HTTP requests
3. browser behavior works in a real page session
4. server is shut down cleanly afterward

## Required Workflow

### 1. Prefer The Repo Verification Script

For normal live web verification, run the repo-local script:

```bash
./scripts/verify-web
```

Use this before doing the manual steps below unless you need to debug one
specific layer. It starts `clojure -M:run-web`, waits for readiness, verifies
core HTTP routes with `curl`, drives a browser flow with `playwright-cli`,
prints recent server logs, and stops the server.

For backend-only verification:

```bash
./scripts/verify-web --no-browser
```

For Codex in a managed sandbox, this command may need one escalated approval
because it binds `localhost:8080` and opens Playwright's browser cache. Prefer
requesting approval for `./scripts/verify-web` once instead of asking for
separate approvals for `clojure`, `curl`, `playwright-cli`, and `kill`.

### 2. Manual Server Start With A Stored PID

Use manual startup only when the script is not enough for debugging. Store the
pid in a fixed temp area:

```bash
mkdir -p /tmp/crazy-eights
clojure -M:run-web > /tmp/crazy-eights/web-server.log 2>&1 & echo $! > /tmp/crazy-eights/web-server.pid
```

Never rely on remembering the PID from `lsof` later. `clojure -M:run-web`
blocks until killed, so it is safe to run in the background.

### 3. Verify Readiness Before Browser Work

Check the port explicitly:

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
```

Do not open the browser until the server is actually listening.

Also verify HTTP readiness explicitly, not just the socket:

```bash
curl --noproxy '*' -i http://localhost:8080/
```

If the port is listening but the first browser navigation still races or fails, retry only after the HTTP probe succeeds.

### 4. Verify HTTP Behavior Directly First

Before debugging the browser, hit the backend endpoints with `curl`.

Examples:

```bash
curl --noproxy '*' -i http://localhost:8080/
curl --noproxy '*' -i -X POST -d "player-count=4" http://localhost:8080/simulations
curl --noproxy '*' -N http://localhost:8080/simulations/sim-0/events
```

This isolates backend problems from frontend problems.

### 5. Use Playwright By Snapshot, Not Guessing

Use the Playwright CLI against the live page.

Recommended sequence:

```bash
playwright-cli open http://localhost:8080
playwright-cli goto http://localhost:8080
playwright-cli snapshot
playwright-cli click e5
playwright-cli requests
playwright-cli snapshot
```

Use snapshot references when possible. Do not assume text-based selectors will always work.

If snapshot-ref clicking is flaky in the current environment, do not stop there. Fall back to a two-track verification:

- verify backend behavior with direct HTTP requests first
- verify browser state changes (status text, DOM updates, console, requests) separately
- use `playwright-cli run-code` for stable locator-based flows when snapshots are too brittle

Do not conclude the page is broken just because a specific `playwright-cli click <ref>` invocation fails. Distinguish tool interaction issues from application issues.

### 6. Inspect Browser Console And Requests

If the page looks fine but nothing happens:

- inspect console output
- inspect network requests
- compare expected POST/SSE requests with actual requests

Typical signal patterns:

- repeated `GET /?player-count=4` means browser default submission happened and JS did not intercept correctly
- no POST means client-side event handling failed
- POST succeeds but no SSE means startup/subscription ordering is wrong
- SSE connects but no messages means simulation or subscriber wiring is wrong

Also check visible browser state changes in the page itself. A minimal observer page should expose status text such as `idle`, `starting`, `running`, `error`, or `stream closed` so browser verification is possible without deep DOM inspection.

### 7. Inspect Server Log File

Read the log file directly:

`/tmp/crazy-eights/web-server.log`

Use it to confirm:

- incoming requests
- outgoing responses
- ordering between POST and SSE connect

### 8. Shut Down The Server Explicitly

Always stop the server using the stored PID:

```bash
kill "$(cat /tmp/crazy-eights/web-server.pid)"
```

Then verify the port is free:

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
```

If the stored PID no longer exists, do not guess. Re-check the actual listening process with `lsof -i :8080` and stop only the live server process.

## Required Checks For Web Changes

Before claiming a web fix works, confirm all of these:

- route/unit tests pass
- full test suite passes
- lint passes
- live HTTP request works
- live browser interaction works
- server log confirms expected flow
- server is stopped afterward

## Common Failure Modes In This Repo

### Client-Side Script Not Running

Symptoms:

- page renders
- button click reloads or navigates instead of staying in place
- browser console shows JavaScript parse error

Check:

- script escaping in server-rendered HTML
- console log for syntax errors

### Simulation Starts Before SSE Subscriber Connects

Symptoms:

- POST returns simulation id
- SSE connects but misses early events
- page appears dead or incomplete

Fix:

- start the simulation after SSE subscription is attached, not before

### Output Capture Hides Runtime Logs

Symptoms:

- tests pass but no logs visible

Fix:

- disable test output capture for logging commands
- use dedicated runners/aliases when needed

## Logging Guidance

For web/runtime debugging in this repo, logs should help both humans and AI.

Prefer logs with:

- clear direction: incoming/outgoing
- route/method/params
- event type
- compact state summary, not giant full-state dumps by default
- visible status changes in the browser page

## What Not To Do

- do not verify only with unit tests
- do not rely on guessed browser selectors when snapshot refs are available
- do not rely on a single flaky browser action as the only verification path
- do not leave background servers running
- do not start debugging in Playwright before confirming the server is ready
- do not debug browser behavior before checking direct HTTP behavior
