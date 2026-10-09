#!/usr/bin/env bash
# Phase 5 happy-path checkout with real Authorization Code + PKCE tokens.
# Requires gateway, user, catalog, inventory, cart, order, payment, PostgreSQL, and Kafka.
set -euo pipefail
CHECK_NAME=checkout
# shellcheck disable=SC1091
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/checkout-check-lib.sh"
cd "$ROOT"

echo "1. Issue PKCE tokens and verify order permissions"
phase5_tokens
ok "customer and staff tokens issued"

echo "2. Prepare an owned address and a uniquely stocked catalog variant"
ensure_address
suffix="$(date +%s)-$RANDOM"
create_stocked_variant "$suffix" 5
put_only_item_in_cart
ok "checkout fixture prepared"

echo "3. Persist and review the authoritative quote"
quote_cart
ok "quote snapshots amount, policy, address, and line"

echo "4. Accept once and replay the completed idempotency result"
key="checkout-${suffix}"
accept_quote "$key"
accepted_order="$ORDER_ID"
accepted_body="$(cat "$BODY")"
expect "$(request POST http://localhost:8090/api/v1/store/orders "$CUSTOMER" \
  "{\"quoteId\":\"${QUOTE_ID}\"}" "$key")" 202 "replay accepted checkout"
[[ "$(jq -r .id "$BODY")" == "$accepted_order" ]] || fail "same key and quote created another order"
diff -u <(printf '%s\n' "$accepted_body" | jq -S .) <(jq -S . "$BODY") >/dev/null ||
  fail "completed replay did not return the stored response"

expect "$(request POST http://localhost:8090/api/v1/store/orders "$CUSTOMER" \
  '{"quoteId":"00000000-0000-0000-0000-000000000005"}' "$key")" 409 "changed payload on completed key"
jq -e '.code == "ORDER_IDEMPOTENCY_CONFLICT"' "$BODY" >/dev/null ||
  fail "changed idempotency payload did not return ORDER_IDEMPOTENCY_CONFLICT"
ORDER_ID="$accepted_order"
ok "checkout idempotency is stable"

echo "5. Enforce owner isolation and wait for the durable saga"
expect "$(request GET "http://localhost:8090/api/v1/store/orders/${ORDER_ID}" "$OTHER_CUSTOMER")" 404 "other customer order read"
poll_order_status CONFIRMED 90
cleanup_started=$SECONDS
while [[ "$(jq -r .cleanupStatus "$BODY")" == "REQUESTED" ]] && (( SECONDS - cleanup_started < 30 )); do
  sleep 1
  expect "$(request GET "http://localhost:8090/api/v1/store/orders/${ORDER_ID}" "$CUSTOMER")" 200 "poll cart cleanup"
done
jq -e '
  .paymentStatus == "SUCCEEDED"
  and .fulfilmentStatus == "NOT_STARTED"
  and .paymentSimulated == true
  and (.cleanupStatus == "CLEARED" or .cleanupStatus == "SKIPPED" or .cleanupStatus == "UNCONFIRMED")
' "$BODY" >/dev/null || fail "confirmed order has inconsistent payment, fulfilment, or cleanup state"
ok "order confirmed only after simulated payment and stock consumption"

echo "checkout-check passed"
