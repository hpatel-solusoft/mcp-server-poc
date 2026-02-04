# Allow the app to read ONLY its own configuration
path "secret/data/claims-mcp-server" {
  capabilities = ["read"]
}