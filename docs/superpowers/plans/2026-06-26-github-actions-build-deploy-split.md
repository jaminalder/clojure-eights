# GitHub Actions Build Deploy Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split GitHub Actions into a Build workflow that validates and publishes images for every branch, and a Deploy workflow that deploys automatically after successful `master` builds or manually with `latest` as the default tag.

**Architecture:** Build owns test, lint, uberjar, Docker image build, and GHCR publish. Deploy owns SSH and Docker Compose restart only. `workflow_run` connects successful `master` builds to automatic deploys, while `workflow_dispatch` supports manual deploys of `latest`, a SHA tag, or a branch tag.

**Tech Stack:** GitHub Actions, Docker Buildx, GHCR, SSH, Docker Compose.

---

## File Structure

- Delete `.github/workflows/ci.yml`: redundant because Build validates every branch.
- Delete `.github/workflows/container.yml`: replaced by a clearer Build workflow.
- Create `.github/workflows/build.yml`: validate and publish images for every branch push and PR.
- Modify `.github/workflows/deploy.yml`: deploy automatically after successful `master` Build workflow, and manually with `latest` as the default tag.
- Modify `docs/deployment/hetzner-vps.md`: document the new build/deploy process and tag names.

## Task 1: Build Workflow

**Files:**
- Delete: `.github/workflows/ci.yml`
- Delete: `.github/workflows/container.yml`
- Create: `.github/workflows/build.yml`

- [ ] **Step 1: Replace CI and Container with Build**

Create `.github/workflows/build.yml`:

```yaml
name: Build

on:
  pull_request:
  push:
  workflow_dispatch:

permissions:
  contents: read
  packages: write

env:
  IMAGE_NAME: ghcr.io/${{ github.repository }}

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: DeLaGuardo/setup-clojure@13.2
        with:
          cli: latest
      - name: Cache Clojure dependencies
        uses: actions/cache@v4
        with:
          path: ~/.m2/repository
          key: clojure-${{ runner.os }}-${{ hashFiles('deps.edn') }}
          restore-keys: clojure-${{ runner.os }}-
      - name: Run tests
        run: clojure -M:test
      - name: Run lint
        run: clojure -M:lint
      - name: Build uberjar
        run: clojure -T:build uber
      - uses: docker/setup-buildx-action@v3
      - name: Docker metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.IMAGE_NAME }}
          tags: |
            type=sha,format=long
            type=ref,event=branch,prefix=branch-
            type=raw,value=latest,enable=${{ github.ref == 'refs/heads/master' }}
      - name: Log in to GHCR
        if: github.event_name != 'pull_request'
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - name: Build image
        uses: docker/build-push-action@v6
        with:
          context: .
          push: ${{ github.event_name != 'pull_request' }}
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
```

Delete `.github/workflows/ci.yml` and `.github/workflows/container.yml`.

## Task 2: Deploy Workflow

**Files:**
- Modify: `.github/workflows/deploy.yml`

- [ ] **Step 1: Deploy after master Build or manual dispatch**

Replace `.github/workflows/deploy.yml` with:

```yaml
name: Deploy

on:
  workflow_run:
    workflows: [Build]
    types: [completed]
    branches: [master]
  workflow_dispatch:
    inputs:
      image_tag:
        description: Image tag to deploy.
        required: false
        default: latest
        type: string

jobs:
  deploy:
    if: github.event_name == 'workflow_dispatch' || github.event.workflow_run.conclusion == 'success'
    runs-on: ubuntu-latest
    env:
      IMAGE_TAG: ${{ inputs.image_tag || github.event.workflow_run.head_sha }}
      IMAGE_NAME: ghcr.io/${{ github.repository }}
      VPS_USER: ${{ secrets.VPS_USER || 'deploy' }}
    steps:
      - name: Configure SSH
        run: |
          mkdir -p ~/.ssh
          printf '%s\n' "${{ secrets.VPS_SSH_PRIVATE_KEY }}" > ~/.ssh/deploy_key
          chmod 600 ~/.ssh/deploy_key
          ssh-keyscan -H "${{ secrets.VPS_HOST }}" >> ~/.ssh/known_hosts
      - name: Deploy image
        run: |
          ssh -i ~/.ssh/deploy_key "${VPS_USER}@${{ secrets.VPS_HOST }}" \
            "set -eu
             cd /opt/crazy-eights
             if [ -n '${{ secrets.GHCR_READ_TOKEN }}' ]; then printf '%s' '${{ secrets.GHCR_READ_TOKEN }}' | docker login ghcr.io -u '${{ github.actor }}' --password-stdin; fi
             printf '%s\n' 'APP_IMAGE=${{ env.IMAGE_NAME }}:${{ env.IMAGE_TAG }}' 'APP_HOST_PORT=80' 'JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError' > .env
             docker compose -f compose.yml --env-file .env pull
             docker compose -f compose.yml --env-file .env up -d"
```

## Task 3: Documentation

**Files:**
- Modify: `docs/deployment/hetzner-vps.md`

- [ ] **Step 1: Update build/deploy wording**

Document:

```markdown
## Build Workflow

The Build workflow runs on every branch push, pull request, and manual dispatch.
It runs tests, lint, builds the uberjar, builds the container image, and pushes
images to GHCR for non-PR events.

Build publishes these tags:

- `sha-<commit-sha>` for every pushed commit
- `branch-<branch-name>` for branch pushes
- `latest` for `master`

## Deploy Workflow

The Deploy workflow runs automatically after a successful Build workflow on
`master`. It deploys the commit SHA image from that build.

The Deploy workflow can also be run manually. Its default image tag is `latest`.
To deploy a branch build manually, use `branch-<branch-name>`. To deploy an
exact build, use `sha-<commit-sha>`.
```

## Task 4: Verification

**Files:**
- All changed workflow and docs files.

- [ ] **Step 1: Check no stale workflow names remain**

Run: `grep -R "name: CI\|name: Container\|branches: \[main\]" .github docs/deployment/hetzner-vps.md`

Expected: no matches.

- [ ] **Step 2: Run local verification**

Run: `clojure -M:test`

Expected: `0 failures`.

Run: `clojure -M:lint`

Expected: `errors: 0, warnings: 0`.
