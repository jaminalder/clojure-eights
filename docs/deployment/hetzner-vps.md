# Hetzner VPS Deployment

This is the first production-style deployment path for the Crazy Eights web app.

The setup is intentionally small:

- one Hetzner VPS
- Terraform-managed infrastructure
- Docker Compose runtime
- GitHub Actions CI and image publishing
- GHCR container image
- manual GitHub Actions deploy over SSH
- HTTP by public server IP

Domain, Caddy, TLS, persistence, and multi-node runtime are later milestones.

## What You Need Outside This Repo

- A Hetzner Cloud account.
- A Hetzner Cloud project.
- A Hetzner Cloud API token with read/write access for Terraform.
- A local SSH key pair for server access and deploy access.
- A GitHub repository with Actions enabled.
- GitHub Actions secrets for deployment.

## Hetzner Setup

1. Create or choose a Hetzner Cloud project.
2. Create an API token in the project.
3. Give the token read/write permissions.
4. Keep the token out of the repo. Use `TF_VAR_hcloud_token` locally.

Terraform registers your local public SSH key with Hetzner and installs that same key for the `deploy` user through cloud-init.

## GitHub Actions Secrets

Configure these in GitHub under repository settings, Actions secrets:

- `VPS_HOST`: public IPv4 address from `terraform output server_ipv4`.
- `VPS_USER`: `deploy`.
- `VPS_SSH_PRIVATE_KEY`: private key matching `ssh_public_key_path` in Terraform.
- `GHCR_READ_TOKEN`: optional for a public package; required if the image is private and the VPS must authenticate to pull it.

For a private GHCR image, create a GitHub personal access token with package read permissions and store it as `GHCR_READ_TOKEN`.

## Terraform Variables

Copy the example variables file:

```bash
cp infra/hetzner/terraform.tfvars.example infra/hetzner/terraform.tfvars
```

Edit `infra/hetzner/terraform.tfvars`:

```hcl
project_name        = "crazy-eights"
server_type         = "cx23"
location            = "fsn1"
image               = "ubuntu-24.04"
ssh_public_key_path = "~/.ssh/id_ed25519.pub"
ssh_allowed_ips     = ["YOUR_PUBLIC_IP/32"]
app_image           = "ghcr.io/YOUR_GITHUB_OWNER/YOUR_REPO:latest"
```

Use `cx23` as the default cheap shared x86 VPS in `fsn1`. Older examples may
mention `cpx11`, but Hetzner no longer accepts that server type in `fsn1`.
`cpx22` is another valid shared x86 option with a larger disk. ARM instances
such as `cax11` can be cheaper, but they require an ARM-compatible container
image, so this first deployment stays on x86.

To list the server types available to your project:

```bash
HCLOUD_TOKEN="$TF_VAR_hcloud_token" hcloud server-type list
```

Use a specific `/32` for `ssh_allowed_ips` when possible. The example `203.0.113.10/32` is documentation-only and will not work for your real SSH access.

Set the Hetzner token in your shell:

```bash
export TF_VAR_hcloud_token=...
```

## Provision The VPS

Initialize and review Terraform:

```bash
terraform -chdir=infra/hetzner init
terraform -chdir=infra/hetzner plan
```

Create the server:

```bash
terraform -chdir=infra/hetzner apply
```

Print the IP and SSH command:

```bash
terraform -chdir=infra/hetzner output server_ipv4
terraform -chdir=infra/hetzner output ssh_command
```

Cloud-init may take a few minutes after the server is created. Check it with root if needed:

```bash
ssh root@<server-ip> cloud-init status --wait
```

Then connect as the deploy user:

```bash
ssh deploy@<server-ip>
```

## Build And Publish The Image

The image is built by `.github/workflows/container.yml`.

It runs on pushes to `main` and manual dispatch. It publishes:

- `ghcr.io/<owner>/<repo>:<git-sha>`
- `ghcr.io/<owner>/<repo>:latest`

The CI workflow `.github/workflows/ci.yml` runs tests, lint, and the uberjar build.

## First Deploy

1. Push these deployment files to GitHub.
2. Let the Container workflow publish an image, or run it manually.
3. Set `VPS_HOST` in GitHub Actions secrets to the Terraform output IP.
4. Set `VPS_USER` to `deploy`.
5. Set `VPS_SSH_PRIVATE_KEY` to the private key matching the public key used by Terraform.
6. Set `GHCR_READ_TOKEN` if the package is private.
7. Run the Deploy workflow manually.

The Deploy workflow writes `/opt/crazy-eights/.env`, pulls the requested image, and runs:

```bash
docker compose -f compose.yml --env-file .env up -d
```

Open the app at:

```text
http://<server-ip>/
```

## SSH Smoke Checks

On the VPS:

```bash
cd /opt/crazy-eights
docker compose -f compose.yml --env-file .env ps
docker compose -f compose.yml --env-file .env logs --tail=100 app
```

From your local machine:

```bash
curl --noproxy '*' -i http://<server-ip>/
```

## Rollback

Use a previous image SHA tag in the Deploy workflow `image_tag` input.

The workflow will rewrite `APP_IMAGE` and restart the app with that tag.

## Current Limitations

- Active games are lost when the process restarts.
- A VPS reboot loses active games.
- A deploy loses active games.
- A single VPS failure makes the app unavailable.
- nREPL is not exposed and should remain private.
- HTTPS is not enabled until the Caddy/domain milestone.
