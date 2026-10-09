# Checkout failure and recovery demo

This guide changes only future attempts in the local payment simulator. It does not edit an order or payment row. The control endpoint stays off the gateway and returns `404` unless explicitly enabled.

## Start the local stack

For containers:

```bash
export PAYMENT_SIMULATOR_CONTROL_ENABLED=true
export PAYMENT_SIMULATOR_CONTROL_TOKEN="$(openssl rand -hex 32)"
make checkout-debug-up
```

The debug target publishes service ports 8093–8098 so `saga-check` can reach the payment control endpoint. `make checkout-up` keeps those internal service ports private and is enough for browser traffic through the gateway.

For source-run payment-service, export the same two variables before `make run-service SERVICE=payment-service`. Never paste the token into source, Compose YAML, a command transcript, or a committed `.env`.

## Automated scenarios

```bash
make checkout-check
make saga-check
```

`checkout-check` creates a unique catalog variant and stock item, creates a quote with a real PKCE customer token, accepts it, verifies completed idempotency replay and owner isolation, and waits for `CONFIRMED`.

`saga-check` requires the enabled control endpoint. It runs:

1. `DECLINE`: the order stays non-confirmed, inventory releases the held reservation, and final state is `REJECTED` / `DECLINED`.
2. `SUCCESS` followed by customer cancellation: payment is refunded, consumed stock is restocked once, and final state is `CANCELLED` / `REFUNDED`.

The script uses a trap to restore future scenarios to `SUCCESS` / `SUCCESS`. It keeps databases and Kafka data.

## Broker outage after acceptance

1. Stop only this project's Kafka:

   ```bash
   bash scripts/local/compose.sh --profile events stop kafka
   ```

2. Accept a reviewed quote through the storefront or Postman. The HTTP response is `202`; the order remains in a pending state because `ReserveStock` is durable in orderdb.
3. Start Kafka:

   ```bash
   bash scripts/local/compose.sh --profile events up -d kafka
   ```

4. Poll `GET /api/v1/store/orders/{id}`. The outbox poller reclaims the due row and the Saga resumes. Do not accept again with a new key.

Stopping Kafka does not stop PostgreSQL or delete a volume. A send timeout can still mean the broker accepted an event, so recovery republishes the same `eventId`.

## Unknown payment

Set future charge outcomes to `TIMEOUT` using the direct control endpoint or start payment-service with `PAYMENT_SIMULATOR_CHARGE_OUTCOME=TIMEOUT`. Accept a new quote. The order moves to payment `UNKNOWN` and issues durable `QueryPaymentStatus` commands for the original operation id. It does not create a second charge.

After eight unresolved attempts, the order becomes `MANUAL_REVIEW`. The inventory reservation remains `CHECKOUT_HELD`; the demo must not free stock while a charge might have succeeded. Restore `SUCCESS` before creating the next attempt:

```bash
curl -fsS -X PUT \
  -H "X-Simulator-Control: $PAYMENT_SIMULATOR_CONTROL_TOKEN" \
  -H "Content-Type: application/json" \
  --data '{"chargeOutcome":"SUCCESS","refundOutcome":"SUCCESS"}' \
  http://localhost:8098/internal/simulator/outcomes
```

Do not include the token in screenshots or saved Postman examples.

## Evidence to capture

- The accepted `202` order id and initial pending state.
- `order_history` progression or customer GET responses, not manually edited database rows.
- Inventory `onHand`, `reserved`, and `available` before and after compensation.
- Payment attempt status under the same operation id.
- Kafka stopped/started and final recovery state.

Local Kafka is PLAINTEXT. The demo proves durable recovery and idempotent business effects, not producer authentication or production transport security.
