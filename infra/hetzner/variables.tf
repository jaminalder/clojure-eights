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

variable "ci_ssh_allowed_ips" {
  description = "CIDR ranges allowed to SSH from CI deploy runners. GitHub-hosted runners need broad access unless you use a stable egress runner."
  type        = list(string)
  default     = []
}

variable "app_image" {
  description = "Initial app image written into /opt/crazy-eights/.env by cloud-init."
  type        = string
  default     = "ghcr.io/OWNER/REPO:latest"
}
