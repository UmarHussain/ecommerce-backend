#!/usr/bin/env bash
# Phase 3 inventory checks with Authorization Code + PKCE tokens.
# Requires Keycloak, api-gateway, user-service, catalog-service, and inventory-service.
# Reconciles the realm so the dedicated inventory reader exists. Does not print tokens or passwords.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

login() { bash scripts/local/oidc-login.sh "$@"; }
fail() { echo "FAIL  $*" >&2; exit 1; }
ok() { echo "ok    $*"; }

code_for() {
  local method=$1 url=$2 token=${3:-} body=${4:-} key=${5:-}
  local args=(-sS -o /tmp/elp-inv-body -D /tmp/elp-inv-headers -w '%{http_code}')
  if [[ -n "$token" ]]; then
    args+=(-H "Authorization: Bearer $token")
  fi
  if [[ -n "$key" ]]; then
    args+=(-H "Idempotency-Key: $key")
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

claim() {
  local token=$1 name=$2
  local payload
  payload="$(printf '%s' "$token" | awk -F. '{print $2}' | tr '_-' '/+')"
  local mod=$(( ${#payload} % 4 ))
  if [[ "$mod" -eq 2 ]]; then payload="${payload}=="; elif [[ "$mod" -eq 3 ]]; then payload="${payload}="; fi
  printf '%s' "$payload" | openssl base64 -d -A 2>/dev/null | jq -e "$name" >/dev/null
}

echo "1. Reconcile the dedicated inventory reader"
bash scripts/local/realm-reconcile.sh >/tmp/elp-inv-reconcile.txt
grep -q "inventory-reader@example.test" /tmp/elp-inv-reconcile.txt || fail "reader fixture was not reconciled"
ok "reader fixture"

echo "2. Issue PKCE tokens"
manager="$(login admin-spa inventory-manager@example.test | jq -r .access_token)"
reader="$(login admin-spa inventory-reader@example.test | jq -r .access_token)"
platform="$(login admin-spa platform-admin@example.test | jq -r .access_token)"
catalog_only="$(login admin-spa catalog-viewer@example.test | jq -r .access_token)"
customer_json="$(login storefront-spa customer@example.test)"
customer="$(echo "$customer_json" | jq -r .access_token)"
customer_id="$(echo "$customer_json" | jq -r .id_token)"
dual_store="$(login storefront-spa dual-role@example.test | jq -r .access_token)"
creator="$(login admin-spa catalog-creator@example.test | jq -r .access_token)"
editor="$(login admin-spa catalog-editor@example.test | jq -r .access_token)"
[[ -n "$manager" && "$manager" != null && -n "$reader" && "$reader" != null ]] || fail "tokens were not issued"
claim "$manager" '(.aud | if type == "array" then . else [.] end | index("inventory-service")) and (.aud | if type == "array" then . else [.] end | index("catalog-service")) and (.resource_access["inventory-service"].roles | index("inventory.read") and index("inventory.adjust")) and (.resource_access["catalog-service"].roles | index("catalog.read"))' || fail "manager token is missing inventory audience, inventory.adjust, or catalog.read"
claim "$reader" '(.resource_access["inventory-service"].roles == ["inventory.read"]) and (.resource_access["catalog-service"].roles // [] | index("catalog.read") | not)' || fail "reader token is not inventory.read only"
claim "$catalog_only" '.resource_access["inventory-service"].roles // [] | index("inventory.read") | not' || fail "catalog account has inventory.read; use the dedicated reader instead"
ok "tokens issued"

echo "3. Permission boundaries"
expect "$(code_for GET http://localhost:8090/api/v1/admin/inventory/stock-items "$reader")" 200 "reader list"
expect "$(code_for POST http://localhost:8090/api/v1/admin/inventory/stock-items "$reader" '{"catalogVariantId":"00000000-0000-0000-0000-000000000099","initialOnHand":1,"reasonCode":"OPENING_BALANCE"}' "reader-write")" 403 "reader setup"
expect "$(code_for GET http://localhost:8090/api/v1/admin/inventory/stock-items "$catalog_only")" 403 "catalog-only inventory list"
expect "$(code_for GET http://localhost:8090/api/v1/admin/inventory/stock-items "$customer")" 403 "customer inventory"
expect "$(code_for GET http://localhost:8090/api/v1/admin/inventory/stock-items "$dual_store")" 403 "storefront dual-role inventory"
expect "$(code_for GET http://localhost:8090/api/v1/admin/inventory/stock-items "$platform")" 200 "platform admin list"
direct="$(code_for GET http://localhost:8095/api/v1/admin/inventory/stock-items "$customer")"
[[ "$direct" == 401 || "$direct" == 403 ]] || fail "customer direct inventory returned $direct"
expect "$(code_for GET http://localhost:8095/api/v1/admin/inventory/stock-items "$customer_id")" 401 "ID token direct"
forged="$(curl -sS -o /tmp/elp-inv-body -w '%{http_code}' -H 'X-User-Id: admin' -H 'X-Roles: INVENTORY_MANAGER' http://localhost:8095/api/v1/admin/inventory/stock-items)"
[[ "$forged" == 401 ]] || fail "forged identity headers returned $forged"
ok "permission boundaries"

suffix="$(date +%s)"
echo "4. Setup verifies catalog SKU and replays one opening balance"
expect "$(code_for POST http://localhost:8090/api/v1/admin/catalog/categories "$creator" "{\"name\":\"Phase Three $suffix\",\"slug\":\"phase-three-$suffix\"}")" 201 "creator category"
category_id="$(jq -r .id /tmp/elp-inv-body)"
expect "$(code_for POST http://localhost:8090/api/v1/admin/catalog/products "$creator" "{\"name\":\"Phase Three Product $suffix\",\"slug\":\"phase-three-product-$suffix\",\"description\":\"stock\",\"categoryId\":\"$category_id\"}")" 201 "creator product"
product_id="$(jq -r .id /tmp/elp-inv-body)"
sku="P3-$suffix"
expect "$(code_for POST "http://localhost:8090/api/v1/admin/catalog/products/$product_id/variants" "$creator" "{\"sku\":\"$sku\",\"name\":\"Only\",\"price\":\"12.0000\",\"currency\":\"USD\"}")" 201 "creator variant"
variant_id="$(jq -r .id /tmp/elp-inv-body)"
setup_key="setup-$suffix"
expect "$(code_for POST http://localhost:8090/api/v1/admin/inventory/stock-items "$manager" "{\"catalogVariantId\":\"$variant_id\",\"initialOnHand\":5,\"reasonCode\":\"OPENING_BALANCE\",\"note\":\"open\",\"sku\":\"FORGED-SKU\"}" "$setup_key")" 201 "manager setup"
jq -e --arg sku "$sku" '.stockItem.sku == $sku and .stockItem.available == 5 and .adjustment.delta == 5 and .adjustment.reasonCode == "OPENING_BALANCE"' /tmp/elp-inv-body >/dev/null || fail "setup did not store the catalog SKU"
stock_id="$(jq -r .stockItem.id /tmp/elp-inv-body)"
location="$(awk 'tolower($1)=="location:" { print $2 }' /tmp/elp-inv-headers | tr -d '\r')"
[[ "$location" == *"$stock_id"* ]] || fail "setup Location did not name the stock item"
expect "$(code_for POST http://localhost:8090/api/v1/admin/inventory/stock-items "$manager" "{\"catalogVariantId\":\"$variant_id\",\"initialOnHand\":5,\"reasonCode\":\"OPENING_BALANCE\",\"note\":\"open\"}" "$setup_key")" 201 "setup replay"
jq -e --arg id "$stock_id" '.stockItem.id == $id' /tmp/elp-inv-body >/dev/null || fail "replay changed the stock id"
expect "$(code_for POST http://localhost:8090/api/v1/admin/inventory/stock-items "$manager" "{\"catalogVariantId\":\"$variant_id\",\"initialOnHand\":9,\"reasonCode\":\"OPENING_BALANCE\",\"note\":\"open\"}" "$setup_key")" 409 "setup key conflict"
jq -e '.code == "INVENTORY_IDEMPOTENCY_CONFLICT"' /tmp/elp-inv-body >/dev/null || fail "idempotency conflict code"
other_key="other-$suffix"
expect "$(code_for POST http://localhost:8090/api/v1/admin/inventory/stock-items "$manager" "{\"catalogVariantId\":\"$variant_id\",\"initialOnHand\":9,\"reasonCode\":\"OPENING_BALANCE\"}" "$other_key")" 409 "second opening"
jq -e '.code == "INVENTORY_VARIANT_ALREADY_STOCKED"' /tmp/elp-inv-body >/dev/null || fail "duplicate variant code"
ok "setup and replay"

echo "5. Receipt, stale version, and history"
version="$(jq -r .stockItem.version /tmp/elp-inv-body)"
# The duplicate setup conflict does not include the current version. Reload it.
expect "$(code_for GET "http://localhost:8090/api/v1/admin/inventory/stock-items/$stock_id" "$manager")" 200 "stock detail"
version="$(jq -r .version /tmp/elp-inv-body)"
adjust_key="adjust-$suffix"
expect "$(code_for POST "http://localhost:8090/api/v1/admin/inventory/stock-items/$stock_id/adjustments" "$manager" "{\"delta\":2,\"reasonCode\":\"INBOUND_RECEIPT\",\"expectedVersion\":$version,\"note\":\"receipt\"}" "$adjust_key")" 200 "receipt"
jq -e '.stockItem.onHand == 7 and .stockItem.available == 7 and .adjustment.delta == 2' /tmp/elp-inv-body >/dev/null || fail "receipt quantities"
expect "$(code_for POST "http://localhost:8090/api/v1/admin/inventory/stock-items/$stock_id/adjustments" "$manager" "{\"delta\":2,\"reasonCode\":\"INBOUND_RECEIPT\",\"expectedVersion\":$version,\"note\":\"receipt\"}" "$adjust_key")" 200 "receipt replay"
expect "$(code_for POST "http://localhost:8090/api/v1/admin/inventory/stock-items/$stock_id/adjustments" "$manager" "{\"delta\":1,\"reasonCode\":\"INBOUND_RECEIPT\",\"expectedVersion\":$version,\"note\":\"receipt\"}" "$adjust_key")" 409 "adjustment key conflict"
expect "$(code_for POST "http://localhost:8090/api/v1/admin/inventory/stock-items/$stock_id/adjustments" "$manager" "{\"delta\":1,\"reasonCode\":\"INBOUND_RECEIPT\",\"expectedVersion\":$version,\"note\":\"stale\"}" "stale-$suffix")" 409 "stale adjustment"
jq -e '.code == "INVENTORY_STALE_VERSION"' /tmp/elp-inv-body >/dev/null || fail "stale code"
expect "$(code_for GET "http://localhost:8090/api/v1/admin/inventory/stock-items/$stock_id/adjustments" "$reader")" 200 "history"
jq -e '[.items[].reasonCode] | index("OPENING_BALANCE") != null and index("INBOUND_RECEIPT") != null' /tmp/elp-inv-body >/dev/null || fail "history missing opening or receipt"
ok "adjustments and history"

echo "6. Inactive catalog chain is rejected at setup and does not remove stock"
expect "$(code_for POST http://localhost:8090/api/v1/admin/catalog/products "$creator" "{\"name\":\"Hidden $suffix\",\"slug\":\"phase-three-hidden-$suffix\",\"description\":\"hidden\",\"categoryId\":\"$category_id\"}")" 201 "hidden product"
hidden_product="$(jq -r .id /tmp/elp-inv-body)"
hidden_version="$(jq -r .version /tmp/elp-inv-body)"
hidden_sku="P3H-$suffix"
expect "$(code_for POST "http://localhost:8090/api/v1/admin/catalog/products/$hidden_product/variants" "$creator" "{\"sku\":\"$hidden_sku\",\"name\":\"Hidden\",\"price\":\"3.0000\",\"currency\":\"USD\"}")" 201 "hidden variant"
hidden_variant="$(jq -r .id /tmp/elp-inv-body)"
expect "$(code_for PATCH "http://localhost:8090/api/v1/admin/catalog/products/$hidden_product/status" "$editor" "{\"active\":false,\"expectedVersion\":$hidden_version}")" 200 "deactivate product"
expect "$(code_for POST http://localhost:8090/api/v1/admin/inventory/stock-items "$manager" "{\"catalogVariantId\":\"$hidden_variant\",\"initialOnHand\":1,\"reasonCode\":\"OPENING_BALANCE\"}" "hidden-$suffix")" 409 "inactive setup"
jq -e '.code == "INVENTORY_CATALOG_INACTIVE"' /tmp/elp-inv-body >/dev/null || fail "inactive setup code"
expect "$(code_for GET "http://localhost:8090/api/v1/admin/inventory/stock-items/$stock_id" "$manager")" 200 "existing stock remains"
jq -e '.onHand == 7' /tmp/elp-inv-body >/dev/null || fail "existing stock changed after an unrelated deactivation"
expect "$(code_for GET "http://localhost:8090/api/v1/admin/inventory/stock-items/00000000-0000-0000-0000-000000000099" "$manager")" 404 "missing stock"
header_id="$(awk 'tolower($1)=="x-correlation-id:" { print $2 }' /tmp/elp-inv-headers | tr -d '\r')"
body_id="$(jq -r .correlationId /tmp/elp-inv-body)"
[[ -n "$header_id" && "$header_id" == "$body_id" ]] || fail "correlation id was not propagated"
ok "inactive policy and correlation"

echo "inventory-check passed"
