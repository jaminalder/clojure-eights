# Hetzner VPS Infrastructure And Runtime Design

## Goal

Create the first production-style deployment path for the Crazy Eights web app on a Hetzner VPS. The setup should be simple enough to operate now, but shaped so it can grow into a learning environment for high availability, fault tolerance, and distributed systems using open, understandable tools.

The first milestone is public access by server IP. Domain, TLS, and Caddy come later.

## Current App Context

- The web app runs with `clojure -M:run-web` and listens on port `8080`.
- HTTP is served by HTTP Kit.
- Live game updates use SSE.
- Game state is in-memory only; a process restart forgets all games.
- There is no Docker image or uberjar build yet.
- nREPL is trusted process access and must remain bound to localhost only. It must not be exposed publicly.

## Constraints

- Everything practical should be defined as code in this repository.
- Infrastructure is provisioned with Terraform from the start.
- The CI pipeline is defined as code under `.github/workflows/`.
- CI builds the deployable container image and pushes it to GitHub Container Registry.
- The first runtime uses one Hetzner VPS and Docker Compose.
- No database, durable game persistence, Caddy, domain, TLS, or cluster runtime in the first milestone.
- Secrets are referenced by code but secret values are not committed. Secret values live in GitHub Actions secrets, Terraform variables, or local secret stores.

## Recommended Approach

Use a single Hetzner VPS, provisioned by Terraform, running one app container through Docker Compose. GitHub Actions runs tests and lint, builds an uberjar-based container image, pushes it to GHCR, and deploys by SSHing to the VPS to pull and restart the container.

This keeps the first deployment boring and debuggable while preserving clean seams for later growth.

## Architecture

```text
GitHub repository
  -> GitHub Actions workflow from .github/workflows/*.yml
  -> run tests and lint
  -> build uberjar container image
  -> push ghcr.io/<owner>/<repo>:<git-sha>

Terraform
  -> create Hetzner VPS
  -> attach SSH key
  -> configure minimal firewall rules
  -> bootstrap Docker and Docker Compose with cloud-init

VPS
  -> docker compose pulls GHCR image
  -> app container listens on port 8080
  -> host exposes HTTP by IP

User
  -> http://<server-ip>/
```

## Components

### Terraform

Terraform owns the first server shape:

- Hetzner server resource
- SSH key association
- minimal firewall rules for SSH and HTTP
- cloud-init user data for installing Docker/Compose and preparing the deploy directory
- outputs such as server IP address

Terraform should not build the app image. It provisions infrastructure only.

### GitHub Actions

GitHub Actions is defined as repository code in `.github/workflows/*.yml`.

The first workflow should:

- trigger on `push`, `pull_request`, and `workflow_dispatch`
- run `clojure -M:test`
- run `clojure -M:lint`
- build the app uberjar
- build the container image
- tag the image with the git SHA
- optionally tag selected branch builds as `latest`
- push the image to GHCR
- deploy only from the selected deployment branch or a manual dispatch

Workflow definitions are versioned code. Secret values are not: GHCR credentials, SSH keys, and Hetzner tokens must be stored outside the repository.

### Container Image

The container should package an uberjar rather than run from source. A multi-stage image keeps the runtime smaller and separates build-time dependencies from runtime dependencies.

The runtime command should be equivalent to starting `crazy_eights.web.server`, listening on port `8080` inside the container.

JVM memory settings should be explicit enough for a small VPS, for example through `JAVA_TOOL_OPTIONS` in Compose.

### Docker Compose Runtime

Docker Compose owns the running process on the VPS:

- one app service
- image reference to GHCR
- restart policy
- port mapping from host HTTP to container `8080`
- environment variables such as JVM options
- optional health check once the app has a dedicated health endpoint

For the first milestone, Compose can live in the repository and be copied to the VPS during bootstrap or deployment.

### Deployment

Deployment should be a small, inspectable SSH step from CI:

```text
ssh deploy@<server-ip> "docker compose pull && docker compose up -d"
```

The deploy step should use an immutable image tag based on the git SHA. Rollback means changing the Compose image tag to a previous SHA and running Compose again.

## Network And Access

The first milestone exposes the app by IP address.

- Open SSH for administration and deployment.
- Open HTTP for app access.
- Do not expose nREPL.
- Do not expose Docker daemon sockets.
- Delay HTTPS until the domain and Caddy milestone.

Step two adds a domain, Caddy, automatic TLS, and host routing from `443` to the app container.

## State And Failure Behavior

The current app stores games in memory. This is acceptable for the first runtime milestone.

Expected behavior:

- process restart loses active games
- VPS reboot loses active games
- redeploy loses active games
- single VPS failure makes the app unavailable

This should be documented as an intentional trade-off, not treated as an operational bug.

## What Is Out Of Scope Initially

- durable game persistence
- database provisioning
- Caddy and TLS
- domain setup
- multi-node deployment
- orchestrators such as Nomad or Kubernetes
- self-hosted registry
- metrics and log aggregation
- backups

## Future Growth Path

The first setup should leave room for these later experiments:

- Caddy for domain routing and TLS
- app health endpoint and container health checks
- structured logs and journald/log shipping
- Prometheus and Grafana
- WireGuard private networking between nodes
- Nomad or k3s as a small open orchestrator
- self-hosted registry
- persistent storage experiments
- replicated app state or event log experiments
- blue/green or canary deployment patterns
- multi-node failover and load balancing

The first milestone should not implement these. It should only avoid choices that make them hard later.

## Open Decisions For Implementation Planning

- exact Hetzner server type and region
- whether the repository is public or private
- whether the deploy target is `master` only or manual dispatch only
- exact image name under GHCR
- whether Compose uses host port `80:8080` immediately or `8080:8080` for the first smoke test
- whether to add a dedicated `/healthz` endpoint before the first deployment
