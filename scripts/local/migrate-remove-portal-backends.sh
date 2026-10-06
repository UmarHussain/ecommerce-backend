#!/usr/bin/env bash
# Move portal-entry permission admin.access from the removed admin-portal-backend
# client onto api-gateway, drop obsolete audience mappers, then delete the
# storefront-backend and admin-portal-backend clients.
#
# Idempotent. Does not delete users, reset passwords, change role assignments
# other than this permission move, or touch database volumes.
#
# Usage: bash scripts/local/migrate-remove-portal-backends.sh
#    or: make realm-migrate-portals
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$ROOT/.env"
KC_URL="${KEYCLOAK_URL:-http://localhost:8180}"

need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing required tool: $1" >&2; exit 1; }; }
need jq; need curl
[[ -f "$ENV_FILE" ]] || { echo 'Missing .env. Run: make bootstrap' >&2; exit 1; }

env_value() { grep -E "^$1=" "$ENV_FILE" | tail -n 1 | cut -d= -f2- || true; }
KEYCLOAK_ADMIN="$(env_value KEYCLOAK_ADMIN)"; KEYCLOAK_ADMIN="${KEYCLOAK_ADMIN:-local-admin}"
KEYCLOAK_ADMIN_PASSWORD="$(env_value KEYCLOAK_ADMIN_PASSWORD)"
[[ -n "$KEYCLOAK_ADMIN_PASSWORD" && "$KEYCLOAK_ADMIN_PASSWORD" != CHANGE* ]] \
  || { echo "Error: KEYCLOAK_ADMIN_PASSWORD missing or placeholder in .env" >&2; exit 1; }

REALM=ecommerce-local
ADMIN="$KC_URL/admin/realms/$REALM"
TOKEN=""
LAST_CODE=0

refresh_token() {
  TOKEN="$(curl -sS --fail-with-body -X POST "$KC_URL/realms/master/protocol/openid-connect/token" \
    --data-urlencode 'grant_type=password' --data-urlencode 'client_id=admin-cli' \
    --data-urlencode "username=$KEYCLOAK_ADMIN" --data-urlencode "password=$KEYCLOAK_ADMIN_PASSWORD" \
    | jq -r '.access_token // empty')"
  [[ -n "$TOKEN" ]] || { echo "Could not obtain a Keycloak admin token from $KC_URL" >&2; exit 1; }
}

kc() {
  local method=$1 path=$2 body=${3:-}
  local out; out="$(mktemp)"
  if [[ -n "$body" ]]; then
    LAST_CODE="$(curl -sS -o "$out" -w '%{http_code}' -X "$method" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' "$ADMIN$path" --data-binary "$body")"
  else
    LAST_CODE="$(curl -sS -o "$out" -w '%{http_code}' -X "$method" -H "Authorization: Bearer $TOKEN" "$ADMIN$path")"
  fi
  cat "$out"; rm -f "$out"
}

must() {
  if (( LAST_CODE >= 400 )); then echo "FAILED ($LAST_CODE): $1" >&2; exit 1; fi
}

client_uuid() {
  kc GET "/clients?clientId=$(jq -rn --arg v "$1" '$v|@uri')" | jq -r '.[0].id // empty'
}

note() { printf '  %-10s %s\n' "$1" "$2"; }

refresh_token
kc GET "" >/dev/null
(( LAST_CODE == 200 )) || { echo "Realm '$REALM' not found at $KC_URL (HTTP $LAST_CODE)." >&2; exit 1; }
echo "Migrating realm '$REALM' off storefront-backend and admin-portal-backend"

gateway_uuid="$(client_uuid api-gateway)"
[[ -n "$gateway_uuid" ]] || { echo "FAILED: api-gateway client is missing" >&2; exit 1; }

existing_roles="$(kc GET "/clients/$gateway_uuid/roles" | jq -r '.[].name')"
if grep -qxF admin.access <<<"$existing_roles"; then
  note unchanged "api-gateway/admin.access"
else
  kc POST "/clients/$gateway_uuid/roles" '{"name":"admin.access"}' >/dev/null
  must "create api-gateway/admin.access"
  note created "api-gateway/admin.access"
fi
new_role="$(kc GET "/clients/$gateway_uuid/roles/admin.access")"
must "read api-gateway/admin.access"

old_uuid="$(client_uuid admin-portal-backend)"
if [[ -n "$old_uuid" ]]; then
  echo "Realm role composites"
  refresh_token
  while IFS= read -r role; do
    [[ -n "$role" ]] || continue
    comps="$(kc GET "/roles/$(jq -rn --arg v "$role" '$v|@uri')/composites")"
    if (( LAST_CODE == 404 )); then continue; fi
    must "read composites of $role"
    has_old="$(echo "$comps" | jq --arg id "$old_uuid" '[.[] | select(.name == "admin.access" and .containerId == $id)] | length')"
    has_new="$(echo "$comps" | jq --arg id "$gateway_uuid" '[.[] | select(.name == "admin.access" and .containerId == $id)] | length')"
    if [[ "$has_old" != 0 && "$has_new" == 0 ]]; then
      kc POST "/roles/$(jq -rn --arg v "$role" '$v|@uri')/composites" "[$new_role]" >/dev/null
      must "add api-gateway/admin.access to $role"
      note updated "realm role $role +api-gateway/admin.access"
    fi
    if [[ "$has_old" != 0 ]]; then
      old_repr="$(echo "$comps" | jq -c --arg id "$old_uuid" '[.[] | select(.name == "admin.access" and .containerId == $id)]')"
      kc DELETE "/roles/$(jq -rn --arg v "$role" '$v|@uri')/composites" "$old_repr" >/dev/null
      must "remove admin-portal-backend/admin.access from $role"
      note updated "realm role $role -admin-portal-backend/admin.access"
    fi
  done < <(kc GET "/roles?max=1000" | jq -r '.[].name')

  echo "Direct user role mappings"
  refresh_token
  users="$(kc GET "/clients/$old_uuid/roles/admin.access/users?max=500")"
  if (( LAST_CODE == 404 )); then
    users='[]'
  else
    must "list users with admin-portal-backend/admin.access"
  fi
  while IFS= read -r user_id; do
    [[ -n "$user_id" ]] || continue
    kc POST "/users/$user_id/role-mappings/clients/$gateway_uuid" "[$new_role]" >/dev/null
    must "grant api-gateway/admin.access to user $user_id"
    old_direct="$(kc GET "/users/$user_id/role-mappings/clients/$old_uuid" | jq -c '[.[] | select(.name == "admin.access")]')"
    if [[ "$(echo "$old_direct" | jq length)" != 0 ]]; then
      kc DELETE "/users/$user_id/role-mappings/clients/$old_uuid" "$old_direct" >/dev/null
      must "remove admin-portal-backend/admin.access from user $user_id"
      note updated "user mapping $user_id admin.access moved"
    fi
  done < <(echo "$users" | jq -r '.[].id')
else
  note unchanged "admin-portal-backend client already absent"
fi

echo "Audience mappers"
refresh_token
remove_mapper() {
  local client_id=$1 mapper_name=$2
  local uuid; uuid="$(client_uuid "$client_id")"
  [[ -n "$uuid" ]] || { echo "FAILED: client $client_id missing" >&2; exit 1; }
  local models; models="$(kc GET "/clients/$uuid/protocol-mappers/models")"
  must "list mappers of $client_id"
  local mapper_id; mapper_id="$(echo "$models" | jq -r --arg n "$mapper_name" '.[] | select(.name == $n) | .id')"
  if [[ -z "$mapper_id" ]]; then
    note unchanged "$client_id mapper $mapper_name"
  else
    kc DELETE "/clients/$uuid/protocol-mappers/models/$mapper_id" >/dev/null
    must "delete mapper $mapper_name from $client_id"
    note updated "$client_id mapper -$mapper_name"
  fi
}
remove_mapper storefront-spa aud-storefront-backend
remove_mapper admin-spa aud-admin-portal-backend

echo "Obsolete clients"
refresh_token
for client_id in storefront-backend admin-portal-backend; do
  uuid="$(client_uuid "$client_id")"
  if [[ -z "$uuid" ]]; then
    note unchanged "client $client_id"
  else
    kc DELETE "/clients/$uuid" >/dev/null
    must "delete client $client_id"
    note updated "deleted client $client_id"
  fi
done

echo
echo "Migration finished. Users, passwords, realm-role assignments, profiles, and database volumes were not reset."
echo "Sign in again. Access tokens last 300 seconds and keep their previous claims until they expire."
echo "Staff calls need a new token whose resource_access.api-gateway.roles contains admin.access."
echo "A refresh may be enough; if an admin call returns 403, sign in again."
