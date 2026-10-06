#!/usr/bin/env bash
# Manual/IDE workflow: start only the infrastructure containers
# (PostgreSQL, Keycloak, Redis, Kafka) and run the Java services from IntelliJ.
#
# Usage: bash infrastructure/local/manual-startup/infra.sh [up|down|status|ps|logs [service]]
# Containers are shared with `make infra-up` (same Compose project and names).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "$ROOT"

if [[ ! -f .env || ! -f .local/keycloak/ecommerce-local-realm.json ]]; then
  echo 'Missing .env or rendered realm. Run: make bootstrap' >&2
  exit 1
fi

compose() {
  docker compose --env-file "$ROOT/.env" -f "$ROOT/infrastructure/local/manual-startup/compose.yaml" "$@"
}

case "${1:-status}" in
  up)
    bash scripts/local/infra-status.sh
    compose up -d          # no-op for containers that are already running
    compose ps
    echo
    echo "Keycloak: http://localhost:8180  PostgreSQL: localhost:55432  Redis: localhost:56379  Kafka: localhost:59092"
    echo "Next: run services from IntelliJ (see infrastructure/local/manual-startup/intellij-services.md)."
    ;;
  down)
    compose down           # stops containers of this project; volumes are kept
    ;;
  status)
    bash scripts/local/infra-status.sh
    ;;
  ps)
    compose ps
    ;;
  logs)
    shift
    compose logs --tail=100 -f "$@"
    ;;
  *)
    echo "Usage: $0 [up|down|status|ps|logs [service]]" >&2
    exit 2
    ;;
esac
