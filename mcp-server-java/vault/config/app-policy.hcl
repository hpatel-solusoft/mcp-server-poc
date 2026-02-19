# Allow the app to read its own configuration and profile sub-paths
path "secret/data/claims-mcp-server*" {
  capabilities = ["read", "list"]
}

path "secret/metadata/claims-mcp-server*" {
  capabilities = ["read", "list"]
}