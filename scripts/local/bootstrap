#!/usr/bin/env bash
# Local setup: create .env with generated secrets (only if missing) and render
# the Keycloak realm import from the template (only if missing).
#
# - Never overwrites an existing .env or rendered realm.
# - Never prints secret values.
# - Does not start containers or touch databases.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$ROOT/.env"
TEMPLATE="$ROOT/infrastructure/local/keycloak/realm-template.json"
REALM_DIR="$ROOT/.local/keycloak"
REALM_FILE="$REALM_DIR/ecommerce-local-realm.json"

# Keys that receive a generated value in a fresh .env.
SECRET_KEYS=(
  POSTGRES_PASSWORD KEYCLOAK_DB_PASSWORD
  USER_DB_PASSWORD CATALOG_DB_PASSWORD INVENTORY_DB_PASSWORD
  CART_DB_PASSWORD ORDER_DB_PASSWORD PAYMENT_DB_PASSWORD
  KEYCLOAK_ADMIN_PASSWORD USER_SERVICE_ADMIN_SECRET DEMO_USER_PASSWORD
)
# Keys substituted into the realm template; must be present and non-placeholder.
REALM_KEYS=(USER_SERVICE_ADMIN_SECRET DEMO_USER_PASSWORD)

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required tool: $1 (WSL2 Ubuntu: sudo apt-get install -y $1)" >&2
    exit 1
  }
}
need openssl
need jq

# Read KEY=VALUE from .env without executing it. Prints the value or nothing.
env_value() {
  grep -E "^$1=" "$ENV_FILE" 2>/dev/null | tail -n 1 | cut -d= -f2- || true
}

umask 077 # new files are private to the current user

# --- 1. .env ---------------------------------------------------------------
if [[ -f "$ENV_FILE" ]]; then
  echo "Keeping existing .env (not modified)."
else
  {
    echo '# Generated local-only secrets. Never commit.'
    echo 'COMPOSE_PROJECT_NAME=ecommerce-local-platform'
    echo 'KEYCLOAK_ADMIN=local-admin'
    for key in "${SECRET_KEYS[@]}"; do
      echo "$key=$(openssl rand -hex 24)"
    done
  } >"$ENV_FILE"
  chmod 600 "$ENV_FILE"
  echo "Created .env with generated secrets (mode 600)."
fi

# Warn about keys an existing .env lacks; do not fill them in silently.
missing=()
for key in "${SECRET_KEYS[@]}"; do
  [[ -n "$(env_value "$key")" ]] || missing+=("$key")
done
if ((${#missing[@]})); then
  echo "Warning: .env has no value for: ${missing[*]}" >&2
  echo "         Add them by hand; this script never edits an existing .env." >&2
fi

for key in "${REALM_KEYS[@]}"; do
  value="$(env_value "$key")"
  if [[ -z "$value" || "$value" == CHANGE* ]]; then
    echo "Error: $key is missing or still a placeholder in .env." >&2
    echo "       Set a real value (for example: openssl rand -hex 24) and rerun." >&2
    exit 1
  fi
done

# --- 2. Keycloak realm import --------------------------------------------------
if [[ -f "$REALM_FILE" ]]; then
  echo "Keeping existing rendered realm at .local/keycloak/$(basename "$REALM_FILE")."
else
  mkdir -p "$REALM_DIR"
  tmp="$(mktemp "$REALM_DIR/.render.XXXXXX")"
  trap 'rm -f "$tmp"' EXIT
  # Secrets travel through the environment, not argv, and jq performs proper
  # JSON string escaping. Only exact placeholder strings are replaced.
  DEMO_USER_PASSWORD="$(env_value DEMO_USER_PASSWORD)" \
  USER_SERVICE_ADMIN_SECRET="$(env_value USER_SERVICE_ADMIN_SECRET)" \
    jq 'walk(
          if . == "__DEMO_USER_PASSWORD__" then $ENV.DEMO_USER_PASSWORD
          elif . == "__USER_SERVICE_ADMIN_SECRET__" then $ENV.USER_SERVICE_ADMIN_SECRET
          else . end)' "$TEMPLATE" >"$tmp"
  if grep -Eq '__[A-Z_]+__' "$tmp"; then
    echo "Error: unresolved placeholders remain in the rendered realm; nothing written." >&2
    exit 1
  fi
  mv "$tmp" "$REALM_FILE"
  trap - EXIT
  chmod 600 "$REALM_FILE"
  echo "Rendered Keycloak realm import to .local/keycloak/$(basename "$REALM_FILE") (mode 600)."
fi

cat <<'EOF'

Local configuration ready. Secrets stay in .env and .local/ (both git-ignored).
No containers or databases were changed.

Note: Keycloak imports the realm file only when the realm does not exist yet.
Re-rendering or editing the import does not update an already initialized realm;
apply later changes through the admin console or a controlled reconciliation step.
Do not delete the Keycloak/PostgreSQL volume to force a re-import.
EOF
