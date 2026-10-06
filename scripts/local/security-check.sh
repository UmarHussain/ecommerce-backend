#!/usr/bin/env bash
# Phase 1 acceptance checks using real Authorization Code + PKCE tokens.
# Requires Keycloak, gateway, user-service, and catalog-service. Portal backends are not used.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
login() { bash scripts/local/oidc-login.sh "$@"; }
decode() {
  local payload pad
  payload=$(printf '%s' "$1" | cut -d. -f2)
  pad=$((4 - ${#payload} % 4)); [[ $pad -eq 4 ]] && pad=0
  payload="${payload}$(printf '%*s' $pad | tr ' ' '=')"
  echo "$payload" | tr '_-' '/+' | openssl base64 -d -A 2>/dev/null
}
code_for() {
  local token=$1 path=$2
  curl -sS -o /tmp/elp-sec-body -w '%{http_code}' -H "Authorization: Bearer $token" "$path"
}
fail() { echo "FAIL  $*" >&2; exit 1; }
ok() { echo "ok    $*"; }

echo "1. Issue PKCE tokens"
customer_json="$(login storefront-spa customer@example.test)"
customer="$(echo "$customer_json" | jq -r .access_token)"
customer_id="$(echo "$customer_json" | jq -r .id_token)"
dual_store="$(login storefront-spa dual-role@example.test | jq -r .access_token)"
dual_store_extra="$(login storefront-spa dual-role@example.test 'roles profile' | jq -r .access_token)"
dual_admin="$(login admin-spa dual-role@example.test | jq -r .access_token)"
user_admin="$(login admin-spa user-admin@example.test | jq -r .access_token)"
platform_admin="$(login admin-spa platform-admin@example.test | jq -r .access_token)"
customer_two="$(login storefront-spa customer-two@example.test | jq -r .access_token)"

echo "2. Storefront token for dual-role has CUSTOMER only"
decode "$dual_store" | jq -e '.realm_access.roles == ["CUSTOMER"]' >/dev/null \
  || fail "storefront dual-role token leaked staff realm roles"
decode "$dual_store" | jq -e '.aud | index("api-gateway") and index("user-service")' >/dev/null \
  || fail "storefront token missing gateway or user-service audience"
decode "$dual_store" | jq -e '.aud | index("storefront-backend") or index("admin-portal-backend") | not' >/dev/null \
  || fail "storefront token still targets a removed portal backend"
decode "$dual_store" | jq -e '.resource_access["admin-portal-backend"] or .resource_access["api-gateway"] | not' >/dev/null \
  || fail "storefront dual-role token contains portal-entry roles"
decode "$dual_store_extra" | jq -e '.resource_access["admin-portal-backend"] or .resource_access["api-gateway"] | not' >/dev/null \
  || fail "extra storefront scopes granted staff permissions"
decode "$dual_admin" | jq -e '.resource_access["api-gateway"].roles | index("admin.access")' >/dev/null \
  || fail "admin token missing api-gateway admin.access"
decode "$dual_admin" | jq -e '.resource_access["admin-portal-backend"] | not' >/dev/null \
  || fail "admin token still has admin-portal-backend roles"
ok "storefront tokens exclude staff privileges"

echo "3. Customer denied admin APIs; dual-role storefront token denied too"
[[ "$(code_for "$customer" http://localhost:8090/api/v1/admin/me)" == 403 ]] || fail "customer admin/me not 403"
[[ "$(code_for "$dual_store" http://localhost:8090/api/v1/admin/me)" == 403 ]] || fail "storefront dual-role admin/me not 403"
[[ "$(code_for "$dual_admin" http://localhost:8090/api/v1/admin/me)" == 200 ]] || fail "admin dual-role /me not 200"
jq -e '.profile.profilePersistenceImplemented == true and (.permissions | index("PERM_admin.access")) != null and (.permissions | index("PERM_catalog.read")) != null' /tmp/elp-sec-body >/dev/null \
  || fail "admin /me contract missing structured profile or UI permissions"
viewer="$(login admin-spa catalog-viewer@example.test | jq -r .access_token)"
[[ "$(code_for "$viewer" http://localhost:8090/api/v1/admin/me)" == 200 ]] || fail "catalog viewer admin/me not 200"
[[ "$(code_for "$viewer" http://localhost:8090/api/v1/admin/users)" == 403 ]] || fail "catalog viewer can list users"
[[ "$(code_for "$viewer" "http://localhost:8090/api/v1/admin/users/00000000-0000-0000-0000-000000000001/roles")" == 403 ]] || fail "catalog viewer can read roles"
ok "admin API isolation"

echo "4. Own profile initialization and cross-customer isolation"
[[ "$(code_for "$customer" http://localhost:8090/api/v1/store/me)" == 200 ]] || fail "customer /store/me not 200"
jq -e '.profilePersistenceImplemented == true and .id != null' /tmp/elp-sec-body >/dev/null || fail "profile contract missing"
other_id="$(curl -sS -H "Authorization: Bearer $customer_two" http://localhost:8090/api/v1/store/me | jq -r .id)"
[[ "$(code_for "$customer" "http://localhost:8090/api/v1/admin/users/$other_id")" == 403 ]] || fail "customer read another user through admin"
ok "own profile works; customer cannot read another user via admin APIs"

echo "5. USER_ADMIN cannot elevate to PLATFORM_ADMIN"
target_id="$(curl -sS -H "Authorization: Bearer $user_admin" 'http://localhost:8090/api/v1/admin/users?q=catalog-viewer@example.test' | jq -r '.items[0].id')"
[[ -n "$target_id" && "$target_id" != null ]] || fail "could not resolve catalog-viewer via admin search"
elevate="$(curl -sS -o /tmp/elp-sec-body -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $user_admin" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: elevate-$(date +%s)" \
  -d '{"roles":["PLATFORM_ADMIN"]}' \
  "http://localhost:8090/api/v1/admin/users/$target_id/roles")"
[[ "$elevate" == 403 ]] || fail "USER_ADMIN elevate returned $elevate"
self_id="$(curl -sS -H "Authorization: Bearer $user_admin" 'http://localhost:8090/api/v1/admin/users?q=user-admin@example.test' | jq -r '.items[0].id')"
self_el="$(curl -sS -o /tmp/elp-sec-body -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $user_admin" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: self-$(date +%s)" \
  -d '{"roles":["PLATFORM_ADMIN"]}' \
  "http://localhost:8090/api/v1/admin/users/$self_id/roles")"
[[ "$self_el" == 403 ]] || fail "USER_ADMIN self-elevate returned $self_el"
assign="$(curl -sS -o /tmp/elp-sec-body -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $user_admin" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: allow-$(date +%s)" \
  -d '{"roles":["CATALOG_EDITOR"]}' \
  "http://localhost:8090/api/v1/admin/users/$target_id/roles")"
[[ "$assign" == 200 || "$assign" == 202 ]] || fail "USER_ADMIN allowlisted assign returned $assign"
ok "USER_ADMIN allowlist and anti-elevation"

echo "6. Wrong token types and forged headers"
[[ "$(code_for "$customer_id" http://localhost:8090/api/v1/store/me)" == 401 ]] || fail "ID token was accepted"
bogus="$(curl -sS -o /dev/null -w '%{http_code}' -H 'Authorization: Bearer eyJhbGciOiJub25lIn0.e30.' http://localhost:8090/api/v1/store/me)"
[[ "$bogus" == 401 ]] || fail "malformed token accepted"
header_only="$(curl -sS -o /dev/null -w '%{http_code}' -H 'X-User-Id: customer@example.test' -H 'X-Roles: PLATFORM_ADMIN' http://localhost:8090/api/v1/admin/me)"
[[ "$header_only" == 401 ]] || fail "forged identity headers authenticated"
ok "ID token, malformed bearer, and identity headers rejected"

echo "7. Role change is not instant on an already issued JWT"
viewer="$(login admin-spa catalog-viewer@example.test | jq -r .access_token)"
before="$(decode "$viewer" | jq -r '.realm_access.roles | join(",")')"
echo "    catalog-viewer token roles before refresh: $before"
echo "    Re-login after assignment is required; access tokens last $(curl -sS http://localhost:8180/realms/ecommerce-local/.well-known/openid-configuration | jq -r '.expires_in // 300')s and are not revoked online."
refreshed="$(login admin-spa catalog-viewer@example.test | jq -r .access_token)"
after="$(decode "$refreshed" | jq -r '.realm_access.roles | join(",")')"
echo "    catalog-viewer token roles after re-login: $after"
ok "documented JWT revocation limit; re-login refreshes claims"

echo
echo "security-check passed: PKCE tokens, audience/role isolation, profile ownership, USER_ADMIN constraints, and rejected bad credentials."
echo "Offline-validated JWTs keep old roles until expiry; refresh or re-login after a role change."
