# payment-service

Local payment simulator for the checkout saga. It does not accept cards, call a payment provider, or move money. Customers have no HTTP charge, refund, or payment-state API.

Default port `8098`. Persistence is database `paymentdb`, login `payment_app`, schema `payment`. Platform init creates the schema; Flyway does not. Datasource credentials come from `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, and `DATABASE_SCHEMA`.

## Topics

| Topic | Direction | Notes |
| --- | --- | --- |
| `checkout.payment.commands` | consumed | group `payment-checkout` |
| `checkout.payment.outcomes` | produced | payment and refund results |

Both topics are created with 3 partitions and replication factor 1. Records are keyed by `orderId`. That orders messages for one order on one partition. It does not order messages across orders or across topics.

Envelope version 1 fields: `eventId`, `eventType`, `eventVersion`, `aggregateId`, `aggregateVersion`, `sagaId`, `commandId`, `correlationId`, `causationId`, `occurredAt`, `payload`. Payloads carry order, amount, and currency only. No access tokens and no card data.

`RequestPayment` and `RefundPayment` use `commandId` as the stable operation id. `QueryPaymentStatus` and `RefundPayment` name the charge with `paymentOperationId`. A `scenario` value on `RequestPayment` is ignored. The outcome comes from `payment.simulator.charge-outcome` / `refund-outcome`, or from the `simulator_control` row when that row exists.

## Scenarios

Charge: `SUCCESS`, `DECLINE`, `DELAYED_SUCCESS`, `TIMEOUT`, `LATE_SUCCESS`.

Refund: `SUCCESS`, `REFUND_FAILURE`.

`payment.simulator.delay` (default `2s`) is the wait stored on `simulator_due` for `DELAYED_SUCCESS` and `LATE_SUCCESS`. A worker polls due rows and completes them in PostgreSQL. Recovery does not use `Thread.sleep` or an in-memory timer.

The same operation id and payload hash replays the stored outcome. A different amount, currency, or hash emits `PaymentConflict` or `RefundConflict` and does not change the stored row. A refund applies once. `FAILED` means it was not applied, so that same operation id can later apply exactly once when the configured refund outcome is `SUCCESS`.

## Local control

The API gateway does not route these URLs. They answer only when `payment.simulator.control-enabled=true` and header `X-Simulator-Control` matches `payment.simulator.control-token` using a constant-time compare. The token is never logged. Any other case, including a disabled control plane, responds `404`.

- `PUT /internal/simulator/outcomes` with `{ "chargeOutcome", "refundOutcome" }` upserts future attempts only. It does not edit an existing payment row.
- `GET /internal/payments/{operationId}` returns that attempt, or `404`.

Every other route is denied. Actuator health is public. JWT signature, issuer, expiry, audience `payment-service`, and access-token type are still validated on routes that are not `permitAll`.

## Trust boundary

Local Kafka is PLAINTEXT. Nothing in the envelope authenticates the producer. Publication is at-least-once. PostgreSQL inbox, operation id, and payload hash make the business effect once. A broker acknowledgement means the broker accepted the record under producer `acks=all`. It does not mean a consumer finished, and it is not the same commit as the payment database.
