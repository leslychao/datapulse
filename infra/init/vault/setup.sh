#!/bin/sh
set -e
export VAULT_ADDR='http://127.0.0.1:8200'
export VAULT_TOKEN='dev-token'
vault secrets enable -path=secret kv-v2 2>/dev/null || true
vault kv put secret/datapulse/dev note="Development secrets placeholder"
echo "Vault initialized for development"
