output "server_ipv4" {
  description = "Public IPv4 address for the app server."
  value       = hcloud_server.app.ipv4_address
}

output "ssh_command" {
  description = "SSH command for the deploy user after cloud-init creates it."
  value       = "ssh deploy@${hcloud_server.app.ipv4_address}"
}
