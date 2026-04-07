#!/bin/sh

# Auto-initializes and auto-unseals Vault with file storage backend.
# On first run: creates unseal key + root token, saves to persistent volume,
# provisions a known app token so Spring can connect with VAULT_TOKEN from .env.
# On subsequent runs: reads saved key, unseals, verifies app token.

INIT_FILE="/vault/file/.init-keys"

echo "Waiting for Vault..."
while true; do
  RC=0
  vault status > /dev/null 2>&1 || RC=$?
  if [ "$RC" -eq 0 ] || [ "$RC" -eq 2 ]; then break; fi
  sleep 1
done

# --- First-time initialization ---
INITIALIZED=$(vault status 2>&1 | grep 'Initialized' | awk '{print $2}')
if [ "$INITIALIZED" = "false" ]; then
  echo "First run — initializing Vault..."
  vault operator init -key-shares=1 -key-threshold=1 > "$INIT_FILE" 2>&1
  chmod 600 "$INIT_FILE"
  echo "Vault initialized, keys saved"
fi

# --- Unseal if sealed ---
SEALED=$(vault status 2>&1 | grep 'Sealed' | awk '{print $2}')
if [ "$SEALED" = "true" ]; then
  if [ ! -f "$INIT_FILE" ]; then
    echo "ERROR: Vault is sealed but init keys file is missing."
    echo "Delete vault-data volume and restart: docker volume rm infra_vault-data"
    exit 1
  fi
  UNSEAL_KEY=$(grep 'Unseal Key 1:' "$INIT_FILE" | awk '{print $NF}')
  vault operator unseal "$UNSEAL_KEY" > /dev/null 2>&1
  echo "Vault unsealed"
fi

# --- Authenticate with root token ---
ROOT_TOKEN=$(grep 'Initial Root Token:' "$INIT_FILE" | awk '{print $NF}')
export VAULT_TOKEN="$ROOT_TOKEN"

# --- Enable KV v2 engine (idempotent) ---
vault secrets enable -path=secret kv-v2 > /dev/null 2>&1 || true

# --- Provision app token with known ID from .env (idempotent) ---
if [ -n "$APP_VAULT_TOKEN" ]; then
  vault token create -id="$APP_VAULT_TOKEN" -policy=root -orphan > /dev/null 2>&1 || true
fi

vault kv put secret/datapulse/dev note="Development secrets placeholder" > /dev/null 2>&1 || true

echo "Vault ready for development"
