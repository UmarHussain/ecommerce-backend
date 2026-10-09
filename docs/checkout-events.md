# Checkout event contracts

Checkout uses six Kafka topics, each with three partitions and replication factor one in the local KRaft broker.

| Topic | Producer | Consumer / group |
|---|---|---|
| `checkout.inventory.commands` | order-service | inventory-service / `inventory-checkout` |
| `checkout.inventory.outcomes` | inventory-service | order-service / `order-checkout` |
| `checkout.payment.commands` | order-service | payment-service / `payment-checkout` |
| `checkout.payment.outcomes` | payment-service | order-service / `order-checkout` |
| `checkout.cart.commands` | order-service | cart-service / `cart-checkout` |
| `checkout.cart.outcomes` | cart-service | order-service / `order-checkout` |

The Kafka record key is `orderId`. Replicas of one logical subscriber share its group. Different logical subscribers require different groups. Ordering exists only within one partition of one topic; consumers must handle duplicates and reordering across topics.

## Envelope version 1

```json
{
  "eventId": "uuid",
  "eventType": "ReserveStock",
  "eventVersion": 1,
  "aggregateId": "order uuid",
  "aggregateVersion": 1,
  "sagaId": "order uuid",
  "commandId": "stable business operation uuid",
  "correlationId": "order uuid",
  "causationId": "optional source event uuid",
  "occurredAt": "2026-10-09T10:00:00Z",
  "payload": {}
}
```

`eventId` is stable across an outbox retry. `commandId` is stable across Saga deadline retries. `correlationId` remains useful after the originating HTTP request has expired. Access tokens, credentials, card data, and full address snapshots are forbidden in events.

## Inventory

Commands:

- `ReserveStock`: `orderId`, lines of `catalogVariantId`, canonical `sku`, and positive `quantity`.
- `HoldReservation`, `ReleaseReservation`, `ConsumeReservation`, `RestockReservation`: `orderId`.

Outcomes:

- `StockReserved`, `StockRejected`
- `ReservationHeld`, `HoldRejected`, `ReservationExpired`
- `ReservationReleased`, `ReleaseRejected`
- `StockConsumed`, `ConsumeRejected`
- `StockRestocked`, `RestockRejected`

Reserve merges duplicate variants, locks stock rows in variant-id order, and applies every line or none. Release before reserve creates a cancellation tombstone. Consume decreases `onHand` and `reserved`; restock after consumption increases `onHand` once.

## Payment

Commands:

- `RequestPayment`: order id, amount, and currency. Caller-supplied scenarios are ignored.
- `QueryPaymentStatus`: `paymentOperationId` naming the original charge.
- `RefundPayment`: `paymentOperationId` naming the charge to refund.

Outcomes:

- `PaymentSucceeded`, `PaymentDeclined`, `PaymentUnknown`, `PaymentConflict`
- `PaymentNotFound`
- `PaymentRefunded`, `RefundFailed`, `RefundConflict`

The request command id is the stable charge operation id. A timeout remains unknown and is queried under that identity. The refund command has its own stable id and references the charge. No customer payment HTTP endpoint exists.

## Cart

`ClearCheckedOutCart` carries the owner issuer/subject, accepted cart version, and checked-out SKU/variant/quantity set. It carries no customer token.

Outcomes:

- `CartCleared` when the current version and contents exactly match.
- `CartCleanupSkipped` when the customer changed the cart.

Both are successful cleanup acknowledgements. A skip protects newer cart contents and never reverses a confirmed order.

## Failure and replay

Outboxes publish outside database locks, using a fenced claim token and lease. Consumers commit inbox, domain changes, and response outbox before acknowledging Kafka. Unsupported versions or malformed envelopes are stored in the consuming service's `dead_letter` table. Delivery is described as at-least-once; PostgreSQL inbox and business-operation deduplication provide effectively-once effects.

The local broker listeners are `kafka:9092` inside Compose and `localhost:59092` from the host. Both are PLAINTEXT. Envelope fields do not authenticate a producer.
