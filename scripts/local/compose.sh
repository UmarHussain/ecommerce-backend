#!/usr/bin/env bash
# Thin wrapper: docker compose with this project's env file and compose file.
# Usage: bash scripts/local/compose.sh <any docker compose arguments>
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
if [[ ! -f .env || ! -f .local/keycloak/ecommerce-local-realm.json ]]; then
  echo 'Missing .env or rendered realm. Run: make bootstrap' >&2
  exit 1
fi
exec docker compose --env-file "$ROOT/.env" -f "$ROOT/infrastructure/local/compose.yaml" "$@"
