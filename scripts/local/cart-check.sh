#!/usr/bin/env bash
# Phase 4 cart checks with Authorization Code + PKCE tokens.
# Requires Keycloak, api-gateway, user-service, catalog-service, and cart-service.
# Does not print tokens or passwords and does not reset them.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

login() { bash scripts/local/oidc-login.sh "$@"; }
fail() { echo "FAIL  $*" >&2; exit 1; }
ok() { echo "ok    $*"; }

code_for() {
  local method=$1 url=$2 token=${3:-} body=${4:-}
  local args=(-sS -o /tmp/elp-cart-body -D /tmp/elp-cart-headers -w '%{http_code}')
  if [[ -n "$token" ]]; then
    args+=(-H "Authorization: Bearer $token")
  fi
  if [[ "$method" != "GET" ]]; then
    args+=(-X "$method")
  fi
  if [[ -n "$body" ]]; then
    args+=(-H "Content-Type: application/json" --data "$body")
  fi
  curl "${args[@]}" "$url"
}

expect() {
  local actual=$1 expected=$2 label=$3
  [[ "$actual" == "$expected" ]] || fail "$label returned $actual, expected $expected ($(cat /tmp/elp-cart-body 2>/dev/null || true))"
}

claim() {
  local token=$1 name=$2
  local payload
  payload="$(printf '%s' "$token" | awk -F. '{print $2}' | tr '_-' '/+')"
  local mod=$(( ${#payload} % 4 ))
  if [[ "$mod" -eq 2 ]]; then payload="${payload}=="; elif [[ "$mod" -eq 3 ]]; then payload="${payload}="; fi
  printf '%s' "$payload" | openssl base64 -d -A 2>/dev/null | jq -e "$name" >/dev/null
}

echo "1. Issue PKCE tokens and inspect assigned cart roles"
customer_json="$(login storefront-spa customer@example.test)"
customer="$(echo "$customer_json" | jq -r .access_token)"
customer_id="$(echo "$customer_json" | jq -r .id_token)"
other_json="$(login storefront-spa dual-role@example.test)"
other="$(echo "$other_json" | jq -r .access_token)"
admin="$(login admin-spa platform-admin@example.test | jq -r .access_token)"
[[ -n "$customer" && "$customer" != null && -n "$other" && "$other" != null ]] || fail "tokens were not issued"
cart_roles='(.aud | if type == "array" then . else [.] end | index("cart-service")) and (.resource_access["cart-service"].roles | index("cart.read_own") and index("cart.write_own")) and (.realm_access.roles | index("CUSTOMER")) and (.realm_access.roles | index("CATALOG_CREATOR") | not)'
claim "$customer" "$cart_roles" || fail "customer token is missing the cart audience or cart own roles"
claim "$other" "$cart_roles" || fail "storefront dual-role token is missing customer-only cart roles"
claim "$admin" '.resource_access["cart-service"].roles // [] | index("cart.read_own") | not' || fail "admin token carries cart.read_own"
ok "tokens issued"

echo "2. Two customers, canonical price, and staff denial"
expect "$(code_for GET http://localhost:8090/api/v1/store/cart "$admin")" 403 "admin cart"
expect "$(code_for GET http://localhost:8090/api/v1/store/cart "$customer")" 200 "customer cart"
version="$(jq -r .version /tmp/elp-cart-body)"
if jq -e '.items | map(select(.sku == "HEADPHONES-BLK")) | length > 0' /tmp/elp-cart-body >/dev/null; then
  expect "$(code_for DELETE "http://localhost:8090/api/v1/store/cart/items/HEADPHONES-BLK?expectedVersion=${version}" "$customer")" 200 "reset line"
  version="$(jq -r .version /tmp/elp-cart-body)"
fi
expect "$(code_for PUT http://localhost:8090/api/v1/store/cart/items/HEADPHONES-BLK "$customer" "{\"quantity\":1,\"expectedVersion\":${version},\"ownerSubject\":\"someone-else\",\"unitPrice\":1.00,\"currency\":\"EUR\"}")" 200 "customer add"
jq -e '.items[0].sku == "HEADPHONES-BLK" and .items[0].unitPrice == 79.99 and .items[0].currency == "USD"' /tmp/elp-cart-body >/dev/null || fail "canonical snapshot was not stored"
added="$(jq -r .version /tmp/elp-cart-body)"
expect "$(code_for GET http://localhost:8090/api/v1/store/cart "$other")" 200 "other cart"
jq -e '.items | map(select(.sku == "HEADPHONES-BLK")) | length == 0' /tmp/elp-cart-body >/dev/null || fail "the second customer saw the first customer's headphones line"
expect "$(code_for PUT http://localhost:8090/api/v1/store/cart/items/HEADPHONES-BLK "$customer" '{"quantity":2,"expectedVersion":0}')" 409 "stale quantity"
expect "$(code_for DELETE "http://localhost:8090/api/v1/store/cart/items/HEADPHONES-BLK?expectedVersion=${added}" "$customer")" 200 "remove"
removed="$(jq -r .version /tmp/elp-cart-body)"
expect "$(code_for DELETE "http://localhost:8090/api/v1/store/cart?expectedVersion=${removed}" "$customer")" 200 "clear"
ok "cart ownership"

echo "3. Direct service checks"
direct="$(code_for GET http://localhost:8096/api/v1/cart "$admin")"
[[ "$direct" == 401 || "$direct" == 403 ]] || fail "admin direct cart returned $direct"
expect "$(code_for GET http://localhost:8096/api/v1/cart "$customer_id")" 401 "ID token direct"
forged="$(curl -sS -o /tmp/elp-cart-body -w '%{http_code}' -H 'X-User-Id: customer' -H 'X-Roles: cart.write_own' http://localhost:8096/api/v1/cart)"
[[ "$forged" == 401 ]] || fail "forged identity headers returned $forged"
ok "direct cart checks"
echo "cart-check passed"
