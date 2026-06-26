# Hetzner VPS Infrastructure And Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first infrastructure-as-code deployment path for the Crazy Eights web app on one Hetzner VPS using Terraform, GitHub Actions, GHCR, Docker, and Docker Compose.

**Architecture:** CI builds and verifies the Clojure app, packages an uberjar into a container image, and pushes the image to GHCR. Terraform provisions a single Hetzner VPS and bootstraps Docker plus a deploy directory. Deployment is an inspectable SSH step that writes the image tag, logs in to GHCR when needed, pulls the image, and restarts the Compose service.

**Tech Stack:** Clojure CLI, `tools.build`, Docker, Docker Compose, GitHub Actions, GHCR, Terraform, Hetzner Cloud.

---

## File Structure

- Create `build.clj`: build script for cleaning, copying sources/resources, compiling, and creating `target/crazy-eights.jar`.
- Modify `deps.edn`: add a `:build` alias using `io.github.clojure/tools.build`.
- Create `.dockerignore`: keep Docker build context small and avoid copying local caches/secrets.
- Create `Dockerfile`: multi-stage image that builds the uberjar and runs it on a JRE image.
- Create `deploy/compose.yml`: production-style Compose runtime for the app service.
- Create `deploy/env.example`: documented runtime environment shape without secrets.
- Create `infra/hetzner/main.tf`: Hetzner provider, SSH key, firewall, server, cloud-init wiring.
- Create `infra/hetzner/variables.tf`: typed Terraform variables.
- Create `infra/hetzner/outputs.tf`: server IP and SSH output.
- Create `infra/hetzner/cloud-init.yml.tftpl`: installs Docker/Compose, creates deploy user/directory, writes Compose file.
- Create `infra/hetzner/terraform.tfvars.example`: non-secret example values.
- Create `.github/workflows/ci.yml`: test/lint/build workflow.
- Create `.github/workflows/container.yml`: build and push GHCR image.
- Create `.github/workflows/deploy.yml`: manual deploy workflow over SSH.
- Create `docs/deployment/hetzner-vps.md`: operator instructions for Hetzner, GitHub secrets, Terraform, CI, deploy, and smoke testing.

## Task 1: Uberjar Build Path

**Files:**
- Create: `build.clj`
- Modify: `deps.edn`

- [ ] **Step 1: Add `tools.build` alias to `deps.edn`**

Add this alias under `:aliases`:

```clojure
:build {:deps {io.github.clojure/tools.build {:git/tag "v0.10.6"
                                              :git/sha "52cf7d6"}}
        :ns-default build}
```

- [ ] **Step 2: Create `build.clj`**

```clojure
(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'crazy-eights/app)
(def version "0.1.0-SNAPSHOT")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file "target/crazy-eights.jar")

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main 'crazy_eights.web.server}))
```

- [ ] **Step 3: Verify the uberjar builds**

Run: `clojure -T:build uber`

Expected: command exits `0` and creates `target/crazy-eights.jar`.

## Task 2: Container Image

**Files:**
- Create: `.dockerignore`
- Create: `Dockerfile`

- [ ] **Step 1: Create `.dockerignore`**

```text
.git
.github
.clj-kondo/.cache
.cpcache
.nrepl-port
.playwright-cli
target
docs
infra
deploy/*.env
*.log
```

- [ ] **Step 2: Create `Dockerfile`**

```dockerfile
# syntax=docker/dockerfile:1

FROM clojure:temurin-21-tools-deps AS build
WORKDIR /app
COPY deps.edn build.clj ./
COPY src ./src
COPY resources ./resources
RUN clojure -T:build uber

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV PORT=8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
COPY --from=build /app/target/crazy-eights.jar /app/crazy-eights.jar
EXPOSE 8080
CMD ["java", "-jar", "/app/crazy-eights.jar"]
```

- [ ] **Step 3: Verify the image builds locally**

Run: `docker build -t crazy-eights:local .`

Expected: command exits `0`.

## Task 3: Docker Compose Runtime

**Files:**
- Create: `deploy/compose.yml`
- Create: `deploy/env.example`

- [ ] **Step 1: Create `deploy/compose.yml`**

```yaml
services:
  app:
    image: ${APP_IMAGE:?APP_IMAGE is required}
    restart: unless-stopped
    ports:
      - "${APP_HOST_PORT:-80}:8080"
    environment:
      JAVA_TOOL_OPTIONS: ${JAVA_TOOL_OPTIONS:--XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError}
```

- [ ] **Step 2: Create `deploy/env.example`**

```text
APP_IMAGE=ghcr.io/<owner>/<repo>:<git-sha>
APP_HOST_PORT=80
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError
```

- [ ] **Step 3: Verify Compose config renders**

Run: `APP_IMAGE=crazy-eights:local docker compose -f deploy/compose.yml config`

Expected: command exits `0` and prints one `app` service.

## Task 4: Terraform Hetzner VPS

**Files:**
- Create: `infra/hetzner/main.tf`
- Create: `infra/hetzner/variables.tf`
- Create: `infra/hetzner/outputs.tf`
- Create: `infra/hetzner/cloud-init.yml.tftpl`
- Create: `infra/hetzner/terraform.tfvars.example`

- [ ] **Step 1: Create Terraform variables**

```hcl
variable "hcloud_token" {
  description = "Hetzner Cloud API token. Prefer TF_VAR_hcloud_token instead of committing a tfvars value."
  type        = string
  sensitive   = true
}

variable "project_name" {
  description = "Name prefix for Hetzner resources."
  type        = string
  default     = "crazy-eights"
}

variable "server_type" {
  description = "Hetzner server type for the first VPS."
  type        = string
  default     = "cx23"
}

variable "location" {
  description = "Hetzner location."
  type        = string
  default     = "fsn1"
}

variable "image" {
  description = "Base OS image."
  type        = string
  default     = "ubuntu-24.04"
}

variable "ssh_public_key_path" {
  description = "Local path to the SSH public key Terraform should register with Hetzner."
  type        = string
  default     = "~/.ssh/id_ed25519.pub"
}

variable "ssh_allowed_ips" {
  description = "CIDR ranges allowed to SSH to the server. Use your public IP /32 when possible."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "app_image" {
  description = "Initial app image written into /opt/crazy-eights/.env by cloud-init."
  type        = string
  default     = "ghcr.io/OWNER/REPO:latest"
}
```

- [ ] **Step 2: Create `infra/hetzner/main.tf`**

```hcl
terraform {
  required_version = ">= 1.8.0"

  required_providers {
    hcloud = {
      source  = "hetznercloud/hcloud"
      version = "~> 1.49"
    }
  }
}

provider "hcloud" {
  token = var.hcloud_token
}

locals {
  deploy_dir = "/opt/crazy-eights"
}

resource "hcloud_ssh_key" "default" {
  name       = "${var.project_name}-deploy"
  public_key = file(pathexpand(var.ssh_public_key_path))
}

resource "hcloud_firewall" "web" {
  name = "${var.project_name}-web"

  rule {
    direction  = "in"
    protocol   = "tcp"
    port       = "22"
    source_ips = var.ssh_allowed_ips
  }

  rule {
    direction  = "in"
    protocol   = "tcp"
    port       = "80"
    source_ips = ["0.0.0.0/0", "::/0"]
  }
}

resource "hcloud_server" "app" {
  name        = var.project_name
  image       = var.image
  server_type = var.server_type
  location    = var.location
  ssh_keys    = [hcloud_ssh_key.default.id]
  firewall_ids = [hcloud_firewall.web.id]

  user_data = templatefile("${path.module}/cloud-init.yml.tftpl", {
    deploy_dir  = local.deploy_dir
    app_image   = var.app_image
    compose_yml = file("${path.module}/../../deploy/compose.yml")
  })
}
```

- [ ] **Step 3: Create cloud-init template**

```yaml
#cloud-config
package_update: true
package_upgrade: true

packages:
  - ca-certificates
  - curl
  - gnupg

write_files:
  - path: ${deploy_dir}/compose.yml
    permissions: "0644"
    content: |
${indent(6, compose_yml)}
  - path: ${deploy_dir}/.env
    permissions: "0600"
    content: |
      APP_IMAGE=${app_image}
      APP_HOST_PORT=80
      JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError

runcmd:
  - install -m 0755 -d /etc/apt/keyrings
  - curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  - chmod a+r /etc/apt/keyrings/docker.gpg
  - sh -c 'echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" > /etc/apt/sources.list.d/docker.list'
  - apt-get update
  - apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  - systemctl enable --now docker
  - useradd --create-home --shell /bin/bash --groups docker deploy || true
  - chown -R deploy:deploy ${deploy_dir}
```

- [ ] **Step 4: Create outputs**

```hcl
output "server_ipv4" {
  description = "Public IPv4 address for the app server."
  value       = hcloud_server.app.ipv4_address
}

output "ssh_command" {
  description = "SSH command for the deploy user after cloud-init creates it."
  value       = "ssh deploy@${hcloud_server.app.ipv4_address}"
}
```

- [ ] **Step 5: Create example tfvars**

```hcl
project_name        = "crazy-eights"
server_type         = "cx23"
location            = "fsn1"
image               = "ubuntu-24.04"
ssh_public_key_path = "~/.ssh/id_ed25519.pub"
ssh_allowed_ips     = ["203.0.113.10/32"]
app_image           = "ghcr.io/OWNER/REPO:latest"
```

- [ ] **Step 6: Validate Terraform formatting and config**

Run: `terraform -chdir=infra/hetzner fmt -check`

Expected: command exits `0`.

Run: `terraform -chdir=infra/hetzner init -backend=false`

Expected: command exits `0`.

Run: `terraform -chdir=infra/hetzner validate`

Expected: command exits `0`.

## Task 5: GitHub Actions CI And Deploy Workflows

**Files:**
- Create: `.github/workflows/ci.yml`
- Create: `.github/workflows/container.yml`
- Create: `.github/workflows/deploy.yml`

- [ ] **Step 1: Create CI workflow**

```yaml
name: CI

on:
  pull_request:
  push:
    branches: [main]
  workflow_dispatch:

jobs:
  test:
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
```

- [ ] **Step 2: Create container workflow**

```yaml
name: Container

on:
  push:
    branches: [main]
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
      - uses: docker/setup-buildx-action@v3
      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - name: Build and push image
        uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          tags: |
            ${{ env.IMAGE_NAME }}:${{ github.sha }}
            ${{ env.IMAGE_NAME }}:latest
```

- [ ] **Step 3: Create deploy workflow**

```yaml
name: Deploy

on:
  workflow_dispatch:
    inputs:
      image_tag:
        description: Image tag to deploy. Defaults to the current commit SHA.
        required: false
        type: string

jobs:
  deploy:
    runs-on: ubuntu-latest
    env:
      IMAGE_TAG: ${{ inputs.image_tag || github.sha }}
      IMAGE_NAME: ghcr.io/${{ github.repository }}
    steps:
      - name: Configure SSH
        run: |
          mkdir -p ~/.ssh
          printf '%s\n' "${{ secrets.VPS_SSH_PRIVATE_KEY }}" > ~/.ssh/deploy_key
          chmod 600 ~/.ssh/deploy_key
          ssh-keyscan -H "${{ secrets.VPS_HOST }}" >> ~/.ssh/known_hosts
      - name: Deploy image
        run: |
          ssh -i ~/.ssh/deploy_key "${{ secrets.VPS_USER || 'deploy' }}@${{ secrets.VPS_HOST }}" \
            "set -eu
             cd /opt/crazy-eights
             if [ -n '${{ secrets.GHCR_READ_TOKEN }}' ]; then printf '%s' '${{ secrets.GHCR_READ_TOKEN }}' | docker login ghcr.io -u '${{ github.actor }}' --password-stdin; fi
             printf '%s\n' 'APP_IMAGE=${{ env.IMAGE_NAME }}:${{ env.IMAGE_TAG }}' 'APP_HOST_PORT=80' 'JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError' > .env
             docker compose -f compose.yml --env-file .env pull
             docker compose -f compose.yml --env-file .env up -d"
```

- [ ] **Step 4: Validate workflow YAML**

Run: `clojure -M:test`

Expected: command exits `0`. GitHub validates workflow syntax when pushed.

## Task 6: Deployment Documentation

**Files:**
- Create: `docs/deployment/hetzner-vps.md`

- [ ] **Step 1: Document external setup and commands**

Include:

```markdown
# Hetzner VPS Deployment

## What You Need Outside This Repo

- Hetzner Cloud account.
- Hetzner Cloud project.
- Hetzner Cloud API token with read/write access for Terraform.
- Local SSH key pair for server access.
- GitHub repository with Actions enabled.
- GitHub Actions secrets for deploy.

## GitHub Secrets

- `VPS_HOST`: public IPv4 of the VPS after Terraform apply.
- `VPS_USER`: `deploy`.
- `VPS_SSH_PRIVATE_KEY`: private key matching an authorized key on the VPS for the deploy user.
- `GHCR_READ_TOKEN`: optional for public packages, required if the image is private and the VPS must pull it.

## Terraform

```bash
export TF_VAR_hcloud_token=...
cp infra/hetzner/terraform.tfvars.example infra/hetzner/terraform.tfvars
terraform -chdir=infra/hetzner init
terraform -chdir=infra/hetzner plan
terraform -chdir=infra/hetzner apply
terraform -chdir=infra/hetzner output server_ipv4
```

## First Deploy

1. Push to `main` or manually run the Container workflow to publish an image.
2. Set `VPS_HOST` to the Terraform output IP.
3. Manually run the Deploy workflow.
4. Open `http://<server-ip>/`.

## SSH Smoke Check

```bash
ssh deploy@<server-ip>
cd /opt/crazy-eights
docker compose ps
docker compose logs --tail=100 app
```
```

## Task 7: Final Verification

**Files:**
- All created and modified files.

- [ ] **Step 1: Run Clojure tests**

Run: `clojure -M:test`

Expected: command exits `0`.

- [ ] **Step 2: Run Clojure lint**

Run: `clojure -M:lint`

Expected: command exits `0`.

- [ ] **Step 3: Build uberjar**

Run: `clojure -T:build uber`

Expected: command exits `0` and creates `target/crazy-eights.jar`.

- [ ] **Step 4: Render Compose config**

Run: `APP_IMAGE=ghcr.io/example/crazy-eights:test docker compose -f deploy/compose.yml config`

Expected: command exits `0`.

- [ ] **Step 5: Validate Terraform**

Run: `terraform -chdir=infra/hetzner fmt -check`

Expected: command exits `0`.

Run: `terraform -chdir=infra/hetzner init -backend=false`

Expected: command exits `0`.

Run: `terraform -chdir=infra/hetzner validate`

Expected: command exits `0`.
