#!/usr/bin/env bash
# Phase 2 catalog checks with Authorization Code + PKCE tokens.
# Requires Keycloak, api-gateway, user-service, and catalog-service. Does not print tokens.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

login() { bash scripts/local/oidc-login.sh "$@"; }
fail() { echo "FAIL  $*" >&2; exit 1; }
ok() { echo "ok    $*"; }

code_for() {
  local method=$1 url=$2 token=${3:-} body=${4:-}
  local args=(-sS -o /tmp/elp-cat-body -D /tmp/elp-cat-headers -w '%{http_code}')
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
  [[ "$actual" == "$expected" ]] || fail "$label returned $actual, expected $expected"
}

echo "1. Anonymous public catalog"
expect "$(code_for GET http://localhost:8090/api/v1/store/catalog/products)" 200 "public products"
product_slug="$(jq -r '.items[0].slug' /tmp/elp-cat-body)"
expect "$(code_for GET http://localhost:8090/api/v1/store/catalog/categories)" 200 "public categories"
category_slug="$(jq -r '.[0].slug' /tmp/elp-cat-body)"
[[ -n "$product_slug" && "$product_slug" != null && -n "$category_slug" && "$category_slug" != null ]] || fail "public catalog was empty"
expect "$(code_for GET "http://localhost:8090/api/v1/store/catalog/products/slug/$product_slug")" 200 "product by slug"
product_id="$(jq -r '.id' /tmp/elp-cat-body)"
expect "$(code_for GET "http://localhost:8090/api/v1/store/catalog/products/$product_id")" 200 "product by id"
expect "$(code_for GET "http://localhost:8090/api/v1/store/catalog/products/$product_id/variants")" 200 "public variants"
expect "$(code_for POST http://localhost:8090/api/v1/store/catalog/variants/batch "" '{"skus":["HEADPHONES-BLK","MISSING-SKU"]}')" 200 "variant batch"
jq -e '.missingSkus | index("MISSING-SKU") != null' /tmp/elp-cat-body >/dev/null || fail "batch lookup did not report the missing SKU"
expect "$(code_for GET 'http://localhost:8090/api/v1/store/catalog/products?minPrice=1')" 400 "price filter without currency"
ok "anonymous browsing"

echo "2. Issue PKCE tokens"
viewer="$(login admin-spa catalog-viewer@example.test | jq -r .access_token)"
creator="$(login admin-spa catalog-creator@example.test | jq -r .access_token)"
editor="$(login admin-spa catalog-editor@example.test | jq -r .access_token)"
customer="$(login storefront-spa customer@example.test | jq -r .access_token)"
dual_store="$(login storefront-spa dual-role@example.test | jq -r .access_token)"
[[ -n "$viewer" && "$viewer" != null ]] || fail "viewer token was not issued"
ok "tokens issued"

echo "3. Gateway permission boundaries"
expect "$(code_for GET http://localhost:8090/api/v1/admin/catalog/categories "$viewer")" 200 "viewer list"
expect "$(code_for POST http://localhost:8090/api/v1/admin/catalog/categories "$viewer" '{"name":"Denied","slug":"denied-viewer"}')" 403 "viewer create"
expect "$(code_for GET http://localhost:8090/api/v1/admin/catalog/products "$customer")" 403 "customer admin catalog"
expect "$(code_for GET http://localhost:8090/api/v1/admin/catalog/products "$dual_store")" 403 "storefront dual-role admin catalog"
expect "$(code_for POST http://localhost:8090/api/v1/admin/catalog/categories "$editor" '{"name":"Denied","slug":"denied-editor"}')" 403 "editor create"
ok "gateway roles"

suffix="$(date +%s)"
echo "4. Creator cannot update or smuggle activation; editor updates with expectedVersion"
expect "$(code_for POST http://localhost:8090/api/v1/admin/catalog/categories "$creator" "{\"name\":\"Phase Two $suffix\",\"slug\":\"phase-two-$suffix\",\"active\":false}")" 201 "creator create"
jq -e '.active == true and .version != null' /tmp/elp-cat-body >/dev/null || fail "create accepted an activation change"
category_id="$(jq -r .id /tmp/elp-cat-body)"
version="$(jq -r .version /tmp/elp-cat-body)"
expect "$(code_for PUT "http://localhost:8090/api/v1/admin/catalog/categories/$category_id" "$creator" "{\"name\":\"Phase Two $suffix\",\"slug\":\"phase-two-$suffix\",\"expectedVersion\":$version}")" 403 "creator update"
expect "$(code_for PATCH "http://localhost:8090/api/v1/admin/catalog/categories/$category_id/status" "$creator" "{\"active\":false,\"expectedVersion\":$version}")" 403 "creator activate"
expect "$(code_for PUT "http://localhost:8090/api/v1/admin/catalog/categories/$category_id" "$editor" "{\"name\":\"Phase Two Edited $suffix\",\"slug\":\"phase-two-$suffix\",\"expectedVersion\":$version}")" 200 "editor update"
new_version="$(jq -r .version /tmp/elp-cat-body)"
expect "$(code_for PUT "http://localhost:8090/api/v1/admin/catalog/categories/$category_id" "$editor" "{\"name\":\"Stale $suffix\",\"slug\":\"phase-two-$suffix\",\"expectedVersion\":$version}")" 409 "stale update"
jq -e '.code == "CATALOG_STALE_VERSION"' /tmp/elp-cat-body >/dev/null || fail "stale update code"
expect "$(code_for PUT "http://localhost:8090/api/v1/admin/catalog/categories/$category_id" "$editor" "{\"name\":\"Missing version $suffix\",\"slug\":\"phase-two-$suffix\"}")" 400 "missing expectedVersion"
ok "versions and role split"

echo "5. SKU stays immutable and inactive records stay off the public API"
expect "$(code_for POST http://localhost:8090/api/v1/admin/catalog/products "$creator" "{\"name\":\"Phase Two Product $suffix\",\"slug\":\"phase-two-product-$suffix\",\"description\":\"check\",\"categoryId\":\"$category_id\"}")" 201 "creator product"
product_id="$(jq -r .id /tmp/elp-cat-body)"
sku="P2-$suffix"
expect "$(code_for POST "http://localhost:8090/api/v1/admin/catalog/products/$product_id/variants" "$creator" "{\"sku\":\"$sku\",\"name\":\"Only\",\"price\":\"15.0000\",\"currency\":\"USD\"}")" 201 "creator variant"
variant_id="$(jq -r .id /tmp/elp-cat-body)"
variant_version="$(jq -r .version /tmp/elp-cat-body)"
expect "$(code_for PUT "http://localhost:8090/api/v1/admin/catalog/variants/$variant_id" "$editor" "{\"sku\":\"OTHER-$suffix\",\"name\":\"Only\",\"price\":\"15.0000\",\"currency\":\"USD\",\"expectedVersion\":$variant_version}")" 409 "sku change"
jq -e '.code == "CATALOG_SKU_IMMUTABLE"' /tmp/elp-cat-body >/dev/null || fail "sku conflict code"
expect "$(code_for PUT "http://localhost:8090/api/v1/admin/catalog/variants/$variant_id" "$editor" "{\"sku\":\"$(echo "$sku" | tr '[:upper:]' '[:lower:]')\",\"name\":\"Renamed\",\"price\":\"16.0000\",\"currency\":\"USD\",\"expectedVersion\":$variant_version}")" 200 "same sku update"
jq -e --arg sku "$sku" '.sku == $sku' /tmp/elp-cat-body >/dev/null || fail "normalized sku changed"
expect "$(code_for PATCH "http://localhost:8090/api/v1/admin/catalog/categories/$category_id/status" "$editor" "{\"active\":false,\"expectedVersion\":$new_version}")" 200 "deactivate category"
expect "$(code_for GET "http://localhost:8090/api/v1/store/catalog/products/$product_id")" 404 "public hidden product"
expect "$(code_for GET "http://localhost:8090/api/v1/admin/catalog/products/$product_id" "$viewer")" 200 "admin still sees inactive chain"
ok "sku and inactive isolation"

echo "6. Direct catalog-service checks and correlation"
direct="$(code_for GET http://localhost:8094/api/v1/admin/catalog/categories "$customer")"
[[ "$direct" == 401 || "$direct" == 403 ]] || fail "customer direct admin catalog returned $direct"
expect "$(code_for GET http://localhost:8094/api/v1/admin/catalog/categories "$viewer")" 200 "viewer direct read"
missing="$(code_for GET http://localhost:8090/api/v1/store/catalog/products/00000000-0000-0000-0000-000000000099)"
[[ "$missing" == 404 ]] || fail "missing product returned $missing"
header_id="$(awk 'tolower($1)=="x-correlation-id:" { print $2 }' /tmp/elp-cat-headers | tr -d '\r')"
body_id="$(jq -r .correlationId /tmp/elp-cat-body)"
[[ -n "$header_id" && "$header_id" == "$body_id" ]] || fail "correlation id was not propagated"
ok "direct service and correlation"

echo "catalog-check passed"
