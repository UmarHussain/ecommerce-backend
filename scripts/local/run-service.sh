#!/usr/bin/env bash
# Run one backend module from source against the Dockerised infrastructure.
# Usage: bash scripts/local/run-service.sh <module>   (or: make run-service SERVICE=<module>)
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
service="${1:?Pass a backend module name, e.g. catalog-service}"

if [[ ! -f "$ROOT/backend/$service/pom.xml" ]]; then
  echo "Unknown service '$service'. Available modules:" >&2
  sed -n 's:.*<module>\(.*\)</module>.*:  \1:p' "$ROOT/backend/pom.xml" >&2
  exit 1
fi
[[ -f "$ROOT/.env" ]] || { echo 'Missing .env. Run: make bootstrap' >&2; exit 1; }

set -a
# shellcheck disable=SC1091
source "$ROOT/.env"
set +a

export OIDC_ISSUER_URI=http://localhost:8180/realms/ecommerce-local
export OIDC_JWK_SET_URI=http://localhost:8180/realms/ecommerce-local/protocol/openid-connect/certs

if [[ "$service" == catalog-service ]]; then
  export DATABASE_URL=jdbc:postgresql://localhost:55432/catalogdb
  export DATABASE_USERNAME=catalog_app
  export DATABASE_PASSWORD="$CATALOG_DB_PASSWORD"
  export DATABASE_SCHEMA=catalog
  export SPRING_PROFILES_ACTIVE=local
fi
if [[ "$service" == user-service ]]; then
  export DATABASE_URL=jdbc:postgresql://localhost:55432/userdb
  export DATABASE_USERNAME=user_app
  export DATABASE_PASSWORD="$USER_DB_PASSWORD"
  export DATABASE_SCHEMA=user
  export SPRING_PROFILES_ACTIVE=local
  export KEYCLOAK_ADMIN_TOKEN_URI=http://localhost:8180/realms/ecommerce-local/protocol/openid-connect/token
  export KEYCLOAK_ADMIN_BASE_URI=http://localhost:8180/admin/realms/ecommerce-local
  export KEYCLOAK_ADMIN_CLIENT_ID=user-service-admin
  export KEYCLOAK_ADMIN_CLIENT_SECRET="$USER_SERVICE_ADMIN_SECRET"
fi
if [[ "$service" == inventory-service ]]; then
  export DATABASE_URL=jdbc:postgresql://localhost:55432/inventorydb
  export DATABASE_USERNAME=inventory_app
  export DATABASE_PASSWORD="$INVENTORY_DB_PASSWORD"
  export DATABASE_SCHEMA=inventory
  export CATALOG_SERVICE_URL="${CATALOG_SERVICE_URL:-http://localhost:8094}"
  export SPRING_PROFILES_ACTIVE=local
fi
if [[ "$service" == api-gateway ]]; then
  export USER_SERVICE_URL="${USER_SERVICE_URL:-http://localhost:8093}"
  export CATALOG_SERVICE_URL="${CATALOG_SERVICE_URL:-http://localhost:8094}"
  export INVENTORY_SERVICE_URL="${INVENTORY_SERVICE_URL:-http://localhost:8095}"
fi

cd "$ROOT/backend"
exec ./mvnw -B -pl "$service" spring-boot:run
