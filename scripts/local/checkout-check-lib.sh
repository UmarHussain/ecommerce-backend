#!/usr/bin/env bash
# Shared Phase 5 acceptance helpers. Source this file after defining CHECK_NAME.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BODY="/tmp/elp-${CHECK_NAME}-body"
HEADERS="/tmp/elp-${CHECK_NAME}-headers"

login() { bash "$ROOT/scripts/local/oidc-login.sh" "$@"; }
fail() { echo "FAIL  $*" >&2; exit 1; }
ok() { echo "ok    $*"; }

request() {
  local method=$1 url=$2 token=${3:-} body=${4:-} key=${5:-}
  local args=(-sS -o "$BODY" -D "$HEADERS" -w '%{http_code}')
  [[ -z "$token" ]] || args+=(-H "Authorization: Bearer $token")
  [[ -z "$key" ]] || args+=(-H "Idempotency-Key: $key")
  [[ "$method" == GET ]] || args+=(-X "$method")
  [[ -z "$body" ]] || args+=(-H "Content-Type: application/json" --data "$body")
  curl "${args[@]}" "$url"
}

expect() {
  local actual=$1 expected=$2 label=$3
  [[ "$actual" == "$expected" ]] ||
    fail "$label returned $actual, expected $expected ($(cat "$BODY" 2>/dev/null || true))"
}

jwt_claim() {
  local token=$1 expression=$2 payload
  payload="$(printf '%s' "$token" | awk -F. '{print $2}' | tr '_-' '/+')"
  local mod=$(( ${#payload} % 4 ))
  if [[ "$mod" -eq 2 ]]; then payload="${payload}=="; elif [[ "$mod" -eq 3 ]]; then payload="${payload}="; fi
  printf '%s' "$payload" | openssl base64 -d -A 2>/dev/null | jq -e "$expression" >/dev/null
}

phase5_tokens() {
  CUSTOMER="$(login storefront-spa customer@example.test | jq -r .access_token)"
  OTHER_CUSTOMER="$(login storefront-spa dual-role@example.test | jq -r .access_token)"
  CREATOR="$(login admin-spa catalog-creator@example.test | jq -r .access_token)"
  INVENTORY_MANAGER="$(login admin-spa inventory-manager@example.test | jq -r .access_token)"
  [[ -n "$CUSTOMER" && "$CUSTOMER" != null && -n "$OTHER_CUSTOMER" && "$OTHER_CUSTOMER" != null ]] ||
    fail "PKCE tokens were not issued"
  jwt_claim "$CUSTOMER" '(.aud | if type == "array" then . else [.] end | index("order-service")) and (.resource_access["order-service"].roles | index("order.create") and index("order.read_own") and index("order.cancel_own"))' ||
    fail "customer token is missing the order-service audience or own-order permissions"
}

ensure_address() {
  expect "$(request GET http://localhost:8090/api/v1/store/me "$CUSTOMER")" 200 "load customer profile"
  ADDRESS_ID="$(jq -r '.addresses[0].id // empty' "$BODY")"
  if [[ -z "$ADDRESS_ID" ]]; then
    expect "$(request POST http://localhost:8090/api/v1/store/me/addresses "$CUSTOMER" \
      '{"label":"Phase 5 local","line1":"5 Saga Street","city":"Local City","postalCode":"50005","countryCode":"US"}')" 201 "save checkout address"
    ADDRESS_ID="$(jq -r .id "$BODY")"
  fi
  [[ -n "$ADDRESS_ID" && "$ADDRESS_ID" != null ]] || fail "no customer address is available"
}

create_stocked_variant() {
  local suffix=$1 on_hand=${2:-5}
  local slug="phase-five-${suffix}"
  expect "$(request POST http://localhost:8090/api/v1/admin/catalog/categories "$CREATOR" \
    "{\"name\":\"Phase Five ${suffix}\",\"slug\":\"${slug}\"}")" 201 "create checkout category"
  local category_id
  category_id="$(jq -r .id "$BODY")"
  expect "$(request POST http://localhost:8090/api/v1/admin/catalog/products "$CREATOR" \
    "{\"name\":\"Phase Five Product ${suffix}\",\"slug\":\"${slug}-product\",\"description\":\"checkout acceptance\",\"categoryId\":\"${category_id}\"}")" 201 "create checkout product"
  local product_id
  product_id="$(jq -r .id "$BODY")"
  SKU="P5-${suffix}"
  expect "$(request POST "http://localhost:8090/api/v1/admin/catalog/products/${product_id}/variants" "$CREATOR" \
    "{\"sku\":\"${SKU}\",\"name\":\"Checkout item\",\"price\":\"12.50\",\"currency\":\"USD\"}")" 201 "create checkout variant"
  VARIANT_ID="$(jq -r .id "$BODY")"
  expect "$(request POST http://localhost:8090/api/v1/admin/inventory/stock-items "$INVENTORY_MANAGER" \
    "{\"catalogVariantId\":\"${VARIANT_ID}\",\"initialOnHand\":${on_hand},\"reasonCode\":\"OPENING_BALANCE\",\"note\":\"Phase 5 acceptance\"}" "p5-setup-${suffix}")" 201 "stock checkout variant"
}

put_only_item_in_cart() {
  expect "$(request GET http://localhost:8090/api/v1/store/cart "$CUSTOMER")" 200 "load customer cart"
  local version
  version="$(jq -r .version "$BODY")"
  if jq -e '.items | length > 0' "$BODY" >/dev/null; then
    expect "$(request DELETE "http://localhost:8090/api/v1/store/cart?expectedVersion=${version}" "$CUSTOMER")" 200 "clear previous cart"
    version="$(jq -r .version "$BODY")"
  fi
  expect "$(request PUT "http://localhost:8090/api/v1/store/cart/items/${SKU}" "$CUSTOMER" \
    "{\"quantity\":1,\"expectedVersion\":${version}}")" 200 "add checkout item"
  CART_VERSION="$(jq -r .version "$BODY")"
}

quote_cart() {
  expect "$(request POST http://localhost:8090/api/v1/store/orders/quotes "$CUSTOMER" \
    "{\"expectedCartVersion\":${CART_VERSION},\"addressId\":\"${ADDRESS_ID}\"}")" 200 "create checkout quote"
  QUOTE_ID="$(jq -r .id "$BODY")"
  jq -e --arg variant "$VARIANT_ID" --arg address "$ADDRESS_ID" '
    .currency == "USD"
    and .grandTotal == 12.50
    and .shippingTotal == 0
    and .taxTotal == 0
    and .shippingPolicy == "LOCAL_DEMO_FREE_SHIPPING"
    and .taxPolicy == "LOCAL_DEMO_TAX_NOT_CALCULATED"
    and (.expiresAt | length > 0)
    and .address.addressId == $address
    and .lines[0].catalogVariantId == $variant
  ' "$BODY" >/dev/null || fail "quote does not contain the expected frozen snapshots and policies"
}

accept_quote() {
  local key=$1
  expect "$(request POST http://localhost:8090/api/v1/store/orders "$CUSTOMER" \
    "{\"quoteId\":\"${QUOTE_ID}\"}" "$key")" 202 "accept checkout quote"
  ORDER_ID="$(jq -r .id "$BODY")"
  [[ -n "$ORDER_ID" && "$ORDER_ID" != null ]] || fail "checkout response has no order id"
}

poll_order_status() {
  local wanted=$1 timeout=${2:-60} started=$SECONDS status=
  while (( SECONDS - started < timeout )); do
    expect "$(request GET "http://localhost:8090/api/v1/store/orders/${ORDER_ID}" "$CUSTOMER")" 200 "poll order"
    status="$(jq -r .orderStatus "$BODY")"
    [[ "$status" == "$wanted" ]] && return 0
    [[ "$status" == MANUAL_REVIEW ]] && fail "order entered MANUAL_REVIEW while waiting for $wanted ($(cat "$BODY"))"
    sleep 1
  done
  fail "order stayed ${status:-unknown} while waiting for $wanted"
}
