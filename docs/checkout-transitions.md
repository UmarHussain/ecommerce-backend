# Checkout transitions

This is the Phase 5 contract. Order-service is the only writer of order, payment, and fulfilment status. Inventory owns stock and reservations. Payment-service owns simulated charge and refund attempts. Cart owns cleanup of a matching cart snapshot. PostgreSQL commits the business change and the outbox row together. Kafka delivers those rows at least once.

Fulfilment stays `NOT_STARTED` in this phase. Process, dispatch, and deliver are Phase 6.

## Money and quote policy

- Scale is 2 decimal places, half up. There is no currency conversion.
- A cart whose lines are not all the same currency is rejected with `ORDER_REVIEW_REQUIRED` / `MIXED_CURRENCY`.
- Shipping is `0.00` under policy `LOCAL_DEMO_FREE_SHIPPING`.
- Tax is `0.00` under policy `LOCAL_DEMO_TAX_NOT_CALCULATED`.
- The grand total is the sum of line totals plus those zeroes.
- A quote snapshots cart version, lines, address, and totals. It does not reserve stock or charge.
- Quote lifetime defaults to 10 minutes (`checkout.quote-ttl`).
- Accepting a quote re-reads the cart, the uncached catalog batch, and the owned address. A price, currency, activation, or cart-version change returns `ORDER_REVIEW_REQUIRED` and creates no order.
- The accepted snapshot is the amount sent to the simulator. A later catalog edit does not change that order.

## Customer HTTP

| Method and path | Permission | Effect |
|---|---|---|
| `POST /api/v1/orders/quotes` | `order.create` | Persist an owner-bound quote |
| `POST /api/v1/orders` | `order.create` | Accept a quote. Header `Idempotency-Key`. `202` and `Location` |
| `GET /api/v1/orders` | `order.read_own` | Bounded list of the caller's orders |
| `GET /api/v1/orders/{id}` | `order.read_own` | Own order, or 404 |
| `POST /api/v1/orders/{id}/cancel` | `order.cancel_own` | Record cancellation intent when the domain allows it |

The gateway exposes only `/api/v1/store/orders/...` and rewrites to those paths. There is no admin paid-state endpoint.

Idempotency key format is 8 to 128 characters from `A-Z a-z 0-9 . _ : -`. The unique key is owner issuer, owner subject, and the key. The same fingerprint returns the stored `202`. A different fingerprint returns `409`. A completed row is replayed before quote expiry and before remote reads. Rows are kept while a saga can still be replayed; this phase does not delete them.

`POST /api/v1/orders` commits the order, immutable lines, address snapshot, saga, history, idempotency result, and `ReserveStock` outbox row in one transaction. Kafka being down leaves that order pending. The HTTP thread does not wait for the saga.

## Status dimensions

Order status: `PENDING_STOCK`, `PENDING_HOLD`, `PENDING_PAYMENT`, `PENDING_CONSUMPTION`, `CONFIRMED`, `COMPENSATING`, `CANCEL_PENDING`, `REJECTED`, `CANCELLED`, `MANUAL_REVIEW`.

Payment status: `NOT_STARTED`, `REQUESTED`, `SUCCEEDED`, `DECLINED`, `UNKNOWN`, `REFUND_REQUESTED`, `REFUNDED`, `REFUND_FAILED`.

Fulfilment status: `NOT_STARTED` only.

Saga step: `AWAIT_RESERVATION`, `AWAIT_HOLD`, `AWAIT_PAYMENT`, `AWAIT_RECONCILE`, `AWAIT_CONSUMPTION`, `AWAIT_CLEANUP`, `AWAIT_RELEASE`, `AWAIT_REFUND`, `AWAIT_RESTOCK`, `COMPLETED`, `MANUAL_REVIEW`.

A reservation is `ACTIVE` (pre-payment TTL), `CHECKOUT_HELD` (payment-protected, not released by TTL), `CONSUMED`, `RELEASED`, or `RESTOCKED`. A tombstone blocks a late reserve after cancellation.

## Happy path

| Actor / event | Current step | Preconditions | Mutation and outbox | Compensation |
|---|---|---|---|---|
| Customer accept | none | Quote owned, unexpired, cart and catalog still match, quote not consumed | Order `PENDING_STOCK`, payment `NOT_STARTED`, step `AWAIT_RESERVATION`, outbox `ReserveStock` | None yet |
| `StockReserved` | `AWAIT_RESERVATION` | Matching reserve command, no cancel intent | Step `AWAIT_HOLD`, order `PENDING_HOLD`, outbox `HoldReservation` | Release if a later step fails |
| `ReservationHeld` | `AWAIT_HOLD` | Reservation was `ACTIVE` and this hold won the expiry race | Step `AWAIT_PAYMENT`, payment `REQUESTED`, order `PENDING_PAYMENT`, outbox `RequestPayment` | Do not pay a reservation that expired |
| `PaymentSucceeded` | `AWAIT_PAYMENT` or `AWAIT_RECONCILE` | Amount is the accepted snapshot | Payment `SUCCEEDED`, step `AWAIT_CONSUMPTION`, order `PENDING_CONSUMPTION`, outbox `ConsumeReservation` | Refund if consume is definitively rejected or the customer cancels |
| `StockConsumed` | `AWAIT_CONSUMPTION` | Reservation is `CHECKOUT_HELD` | Order `CONFIRMED`, step `AWAIT_CLEANUP`, outbox `ClearCheckedOutCart` | Confirmed cancel later restocks and refunds |
| `CartCleared` or `CartCleanupSkipped` | `AWAIT_CLEANUP` | Cleanup command matches | Step `COMPLETED`. Order stays `CONFIRMED` | Cleanup never undoes confirmation |

`CartCleanupSkipped` means the cart version or contents no longer match. That is a completed safe result.

## Failure and cancellation

| Actor / event | Current step | Preconditions | Mutation and outbox | Compensation |
|---|---|---|---|---|
| `StockRejected` | `AWAIT_RESERVATION` | All lines or none; nothing reserved | Order `REJECTED`, payment stays `NOT_STARTED`, step `COMPLETED` | No charge |
| `ReservationExpired` or `HoldRejected` before payment | `AWAIT_RESERVATION` or `AWAIT_HOLD` | Payment command not sent | Order `REJECTED`, step `COMPLETED` | Stock already released by inventory |
| `PaymentDeclined` | `AWAIT_PAYMENT` or `AWAIT_RECONCILE` | Definitive decline | Payment `DECLINED`, order `COMPENSATING`, step `AWAIT_RELEASE`, outbox `ReleaseReservation` | `StockReleased` then order `REJECTED` |
| Payment deadline | `AWAIT_PAYMENT` | No definitive outcome | Payment `UNKNOWN`, step `AWAIT_RECONCILE`, outbox `QueryPaymentStatus` for the same charge operation | Do not release and do not charge again |
| Still unknown after bounded queries | `AWAIT_RECONCILE` | Attempts exhausted | Order `MANUAL_REVIEW`. Reservation stays `CHECKOUT_HELD` | Outstanding charge and hold remain visible |
| `ConsumeRejected` | `AWAIT_CONSUMPTION` | Definitive business rejection, reservation not consumed | Order `COMPENSATING`, step `AWAIT_REFUND`, outbox `RefundPayment` | After refund, `ReleaseReservation`, then `REJECTED` |
| Consume deadline | `AWAIT_CONSUMPTION` | Outcome missing | Retry the same consume command, then `MANUAL_REVIEW` | Do not refund on a timeout guess |
| Customer cancel before confirmation | any in-flight step | Owner matches | `cancellationRequested`, order `CANCEL_PENDING`. No new forward command | Late `PaymentSucceeded` starts refund. Decline or no payment releases stock. Already consumed stock is restocked after refund |
| Customer cancel of `CONFIRMED` | `COMPLETED` or `AWAIT_CLEANUP` | Fulfilment `NOT_STARTED` | Order `CANCEL_PENDING`, step `AWAIT_REFUND`, outbox `RefundPayment` | `StockRestocked` after refund, then `CANCELLED` |
| `RefundFailed` or refund deadline | `AWAIT_REFUND` | Refund not applied | Retry the same refund operation, then payment `REFUND_FAILED` and `MANUAL_REVIEW` | Not treated as refunded |
| Contradictory reply after `REJECTED` with `PaymentSucceeded` | `COMPLETED` | Charge happened after rejection | Step `AWAIT_REFUND`. Order is not moved to `CONFIRMED` | Refund, then stay `REJECTED` |
| Any reply after `CANCELLED` or duplicate success | terminal | Same operation already applied | Ignored | Terminal orders are not reopened |
| Automation exhausted, or a conflict outcome | any | Obligation still outstanding | `MANUAL_REVIEW` with the obligation text | No pretend release or refund |

Inventory applies reserve, hold, release, consume, and restock under the reservation row lock. The pre-payment expiry worker only selects `ACTIVE` rows. `CHECKOUT_HELD` is not freed by TTL. Release or cancel intent stored before the reserve arrives is a tombstone: the late reserve does not increase `reserved`.

Payment timeout is `UNKNOWN`. Status queries use the original charge operation id. The same operation and payload have one business effect. A changed amount or currency conflicts. Refund applies once to a successful charge. A failed refund attempt does not count as refunded, so a later retry of that same operation can apply it once.

## Retry bounds

Participant commands use the same business command id on retry. Deadline backoff grows from 2 seconds and is capped at 30 seconds. Eight attempts move the saga to `MANUAL_REVIEW` except where a table above says a timeout is `UNKNOWN` rather than a business rejection. Short HTTP retries around cart, catalog, and address reads stay separate and never wrap the accept transaction.
