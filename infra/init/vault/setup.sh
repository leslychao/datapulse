#!/bin/sh
set -e
vault secrets enable -path=secret kv-v2 2>/dev/null || true
vault kv put secret/datapulse/dev note="Development secrets placeholder"
echo "Vault initialized for development"
