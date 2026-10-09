# ADR 0003 — Checkout orchestration and transactional messaging

## Decision

order-service owns the checkout Saga. Inventory, payment, and cart own their effects and databases. The gateway only routes and performs coarse permission checks. There is no distributed database transaction and no shared domain JAR.

Every durable command or outcome is first inserted into a service-local PostgreSQL outbox in the same transaction as the state change. An `OutboxReady` application event wakes a bounded dispatcher only after commit. A scheduled poller is the recovery path. Both paths use the same atomic claim with a claim token and lease. Publishing waits at most five seconds for Kafka acknowledgement. A separate short transaction marks the row `SENT` only if the claim token still matches.

Consumers insert `(consumer_name, event_id)` into a local inbox and apply the business effect in the same transaction. `INSERT ... ON CONFLICT DO NOTHING` handles a duplicate without aborting the PostgreSQL transaction. The business command id and payload hash also deduplicate effects when the same operation arrives under a different event id. Poison or unsupported envelopes are recorded in a durable dead-letter table before the Kafka offset is acknowledged.

## Why orchestration

Checkout has readable customer state, cancellation policy, payment uncertainty, and multi-step compensation. Keeping that policy in one order state machine makes the outstanding obligation explicit. Choreography would spread terminal-state decisions across inventory, payment, and cart. Participants still decide their own domain transitions: order-service cannot edit a reservation, payment attempt, or cart row.

The coordinator stores its Saga step, stable command ids, deadline, attempt count, cancellation intent, terminal plan, cleanup status, and optimistic version on `customer_order`. `order_history` records transitions. Recovery locks one due order for a short transaction and reissues the same business operation identity. Remote HTTP is used only to build and revalidate a quote before acceptance and never while an order transaction is open.

## Delivery semantics

Delivery is at-least-once with effectively-once business effects. Kafka producer idempotence and `acks=all` reduce duplicate publication; they do not atomically commit Kafka and PostgreSQL. A process can crash after broker acknowledgement and before marking an outbox row sent. The lease expires and the same event id is sent again. Inbox and command-level deduplication make that retry safe.

The Kafka key is `orderId`. That preserves partition order for one topic and order. It does not create ordering across the inventory, payment, and cart topics. Every handler validates Saga, aggregate, command, and expected state so an out-of-order outcome cannot resurrect a terminal order.

## Payment uncertainty

A timeout is `UNKNOWN`, not a decline. order-service queries the same stable payment operation id. It does not create another charge. Automation is bounded; after eight attempts the order enters `MANUAL_REVIEW` with the protected reservation and obligation still visible. A late success after rejection or cancellation creates a refund obligation rather than confirming the order.

Inventory first creates an expiring `ACTIVE` reservation. order-service must receive `ReservationHeld` before requesting payment. `CHECKOUT_HELD` is not released by the expiry worker. That prevents stock from being freed while payment might have succeeded.

## Consequences and limits

- A committed checkout remains visible and pending during a broker outage.
- Confirmation requires both simulated payment success and inventory consumption.
- Cart cleanup is independent. A changed cart produces `CartCleanupSkipped` and does not reverse confirmation.
- Compensation can remain pending or enter manual review; it is never represented as completed without participant acknowledgement.
- Local Kafka uses PLAINTEXT. Envelope metadata is correlation data, not producer authentication. Broker authentication, ACLs, TLS, operational alerting, and replay tooling remain Phase 7.
