#!/usr/bin/env bash
# Phase 5 compensation checks with deterministic local payment scenarios.
# Requires payment-service control enabled on localhost:8098 and its token in the environment.
set -euo pipefail
CHECK_NAME=saga
# shellcheck disable=SC1091
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/checkout-check-lib.sh"
cd "$ROOT"

CONTROL_TOKEN="${PAYMENT_SIMULATOR_CONTROL_TOKEN:-}"
[[ -n "$CONTROL_TOKEN" ]] ||
  fail "export PAYMENT_SIMULATOR_CONTROL_TOKEN and start payment-service with PAYMENT_SIMULATOR_CONTROL_ENABLED=true"

set_scenario() {
  local charge=$1 refund=$2 code
  code="$(curl -sS -o "$BODY" -w '%{http_code}' -X PUT \
    -H "X-Simulator-Control: ${CONTROL_TOKEN}" \
    -H "Content-Type: application/json" \
    --data "{\"chargeOutcome\":\"${charge}\",\"refundOutcome\":\"${refund}\"}" \
    http://localhost:8098/internal/simulator/outcomes)"
  [[ "$code" == 204 ]] ||
    fail "payment simulator control returned $code; verify the local-only control configuration"
}

restore_control() {
  curl -sS -o /dev/null -X PUT \
    -H "X-Simulator-Control: ${CONTROL_TOKEN}" \
    -H "Content-Type: application/json" \
    --data '{"chargeOutcome":"SUCCESS","refundOutcome":"SUCCESS"}' \
    http://localhost:8098/internal/simulator/outcomes || true
}
trap restore_control EXIT

echo "1. Issue PKCE tokens and prepare the customer address"
phase5_tokens
ensure_address
ok "tokens and address ready"

echo "2. A definitive decline releases held stock before rejection"
set_scenario DECLINE SUCCESS
suffix="decline-$(date +%s)-$RANDOM"
create_stocked_variant "$suffix" 3
declined_variant="$VARIANT_ID"
put_only_item_in_cart
quote_cart
accept_quote "decline-${suffix}"
poll_order_status REJECTED 90
jq -e '
  .paymentStatus == "DECLINED"
  and .fulfilmentStatus == "NOT_STARTED"
  and .cancellationRequested == false
  and (.obligation == "" or .obligation == null)
' "$BODY" >/dev/null || fail "declined order completed with an inconsistent state"
expect "$(request GET "http://localhost:8090/api/v1/admin/inventory/stock-items?size=100" "$INVENTORY_MANAGER")" 200 "read stock after decline"
jq -e --arg variant "$declined_variant" '
  .items[] | select(.catalogVariantId == $variant) | .onHand == 3 and .reserved == 0 and .available == 3
' "$BODY" >/dev/null || fail "declined order did not release its reservation"
ok "decline compensation completed"

echo "3. Cancelling a confirmed order refunds and restocks exactly once"
set_scenario SUCCESS SUCCESS
suffix="cancel-$(date +%s)-$RANDOM"
create_stocked_variant "$suffix" 3
cancelled_variant="$VARIANT_ID"
put_only_item_in_cart
quote_cart
accept_quote "cancel-${suffix}"
poll_order_status CONFIRMED 90
expect "$(request POST "http://localhost:8090/api/v1/store/orders/${ORDER_ID}/cancel" "$CUSTOMER")" 202 "cancel confirmed order"
poll_order_status CANCELLED 90
jq -e '
  .paymentStatus == "REFUNDED"
  and .fulfilmentStatus == "NOT_STARTED"
  and .cancellationRequested == true
  and (.obligation == "" or .obligation == null)
' "$BODY" >/dev/null || fail "cancelled order completed with an inconsistent state"
expect "$(request POST "http://localhost:8090/api/v1/store/orders/${ORDER_ID}/cancel" "$CUSTOMER")" 202 "repeat cancelled order request"
jq -e '.orderStatus == "CANCELLED" and .paymentStatus == "REFUNDED"' "$BODY" >/dev/null ||
  fail "repeat cancellation changed the terminal result"
expect "$(request GET "http://localhost:8090/api/v1/admin/inventory/stock-items?size=100" "$INVENTORY_MANAGER")" 200 "read stock after cancellation"
jq -e --arg variant "$cancelled_variant" '
  .items[] | select(.catalogVariantId == $variant) | .onHand == 3 and .reserved == 0 and .available == 3
' "$BODY" >/dev/null || fail "confirmed cancellation did not restock once"
ok "refund and restock completed"

restore_control
trap - EXIT
echo "saga-check passed"
