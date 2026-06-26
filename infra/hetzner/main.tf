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
  deploy_dir     = "/opt/crazy-eights"
  ssh_public_key = file(pathexpand(var.ssh_public_key_path))
}

resource "hcloud_ssh_key" "default" {
  name       = "${var.project_name}-deploy"
  public_key = local.ssh_public_key
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
  name         = var.project_name
  image        = var.image
  server_type  = var.server_type
  location     = var.location
  ssh_keys     = [hcloud_ssh_key.default.id]
  firewall_ids = [hcloud_firewall.web.id]

  user_data = templatefile("${path.module}/cloud-init.yml.tftpl", {
    deploy_dir     = local.deploy_dir
    app_image      = var.app_image
    compose_yml    = file("${path.module}/../../deploy/compose.yml")
    ssh_public_key = trimspace(local.ssh_public_key)
  })
}
