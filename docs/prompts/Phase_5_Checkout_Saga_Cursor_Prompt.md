Implement Phase 5 — Durable checkout Saga, inventory reservations, Kafka outbox/inbox and local payment simulation.

Phases 1–4 are complete. Extend this existing repository in place, finish the Phase 5 acceptance gates, document actual evidence and stop for review. Do not implement Phase 6 fulfilment/admin order dashboards or Phase 7 full observability/transport-security work.

1. Inspect and preserve current work

Read AGENTS.md, START_HERE.md, IMPLEMENTATION_PLAN.md, MASTER_PROMPT.md, docs/phase-3.md, docs/phase-4.md, docs/service-status.md, docs/progress.md, docs/verification.md, security/permission docs, cache/resilience ADR and applicable Cursor rules. Inspect cart aggregate_version/ownership/read locks and transactional writers, inventory stock constraints/command replay/history, catalog uncached batch, user address APIs, order shell, gateway routes, storefront pages, Compose Kafka/paymentdb setup and local scripts.

Prepared against HEAD 05680398183b2cb8f67400e1a4b7d0314a950467. Preserve newer/unrelated changes, pinned versions and working behavior. Phase 4 uses cart-owned PostgreSQL, no cart stock reservation, a catalog-owned Redis browse cache and uncached batch validation. Keep the existing broad after-commit cache eviction; targeted invalidation is deferred to a later iteration. Do not redesign caching during this phase.

Retain MapStruct and separate Spring transactional writer services. Coordinators doing remote calls stay outside database transactions; call writer beans through their Spring proxies. No TransactionTemplate reversal, shared JPA/domain/mapper JAR, cross-service SQL/FKs, gateway business logic or recreated portal backends.

Use Java 21/Maven Wrapper, WSL2 and Makefile/Bash. Consult official docs for pinned APIs. No Python project dependency, database reset, rewritten applied migration, deleted volume, Docker prune, secrets in logs, AWS work, push or publishing.

2. Scope and implementation cadence

Implement in demonstrable slices: contracts/state tables → quote/order persistence → messaging/outbox/inbox → inventory reservations → payment simulator → durable coordinator/recovery/cancellation → storefront → acceptance/failure demos. Verify each slice, then complete Phase 5; do not stop after a partial happy path and label the phase done.

Order-service owns the orchestration Saga and order state. Inventory owns stock/reservation correctness. Payment-service owns simulated charge/refund outcomes. Cart owns cart snapshots/cleanup. Gateway routes and validates only. PostgreSQL is authoritative; Kafka transports durable commands/outcomes and Redis remains a browse cache.

Reservations begin at checkout, not on adding to cart. Do not implement cart-inactivity reservation/renewal. The prior discussion explored that alternative but did not replace the existing roadmap.

3. Quote, owned address and accepted checkout

Add order-service persistence on orderdb/order_app with its own schema/migrations. Add payment-service now, parent module/build/scripts/Compose/checks, scoped paymentdb/payment_app credentials and a documented free port. Verify existing DB initialization and migrate/reconcile non-destructively if a running database needs changes.

Implement customer-owned quote and checkout, for example:
POST /api/v1/orders/quotes
POST /api/v1/orders
GET /api/v1/orders (bounded own list)
GET /api/v1/orders/{id}
POST /api/v1/orders/{id}/cancel
Expose explicit /api/v1/store/orders/... gateway routes only; map methods/paths to order-service. No arbitrary state setter or admin paid-state API.

Quote input: expected cart version and owned address selection. Read a consistent cart snapshot, revalidate every canonical SKU/variant/active chain and price through UNCACHED catalog batch, verify the saved address belongs to the customer through user-service, and produce a server-persisted owner-bound quote with cart version, immutable line/address snapshots, currency, totals and short configurable expiry. No stock reservation or charge during quoting.

Use customer token forwarding for own cart/address HTTP before acceptance; no access tokens persisted or published. Inspect existing address contract and add a narrowly protected owned-address read if required. For this demo reject multi-currency carts with an explicit review response; never convert or add currencies silently. Define rounding/scale, shipping/tax policy explicitly (zero shipping/tax is acceptable if labelled); snapshot calculated amounts.

POST order uses quoteId and Idempotency-Key. Check completed replay BEFORE quote expiry or new remote reads. Owner/key/request fingerprint has a PostgreSQL unique constraint; same payload returns original accepted result, different payload returns 409. Bound key format/retention; do not delete records while their Saga/replay can still be active. No exists-then-act correctness.

Validate quote ownership, expiry, current cart version and fresh authoritative catalog prices/active state before acceptance. On price/currency/cart changes return review-required 409 with no reservation or payment; obtain/review a new quote rather than charge a changed total. Freeze the accepted validated price for the simulated payment within the documented quote policy. This is point-in-time validation across services, not a distributed ACID transaction.

Consume a quote once in orderdb: different idempotency keys cannot create two orders from the same quote. Persist order, immutable lines/address, Saga, initial history, idempotency result and ReserveStock outbox command in ONE local transaction. Return 202 with stable orderId, pending state and Location; do not block HTTP waiting for Saga completion. Uncertain HTTP outcomes retry the SAME key/payload. Kafka unavailability leaves an accepted order visibly pending with durable outbox, not fake confirmation.

4. Domain states and durable coordinator

Persist separate order/payment/fulfilment state dimensions, @Version/concurrency guards, transition history, Saga step, stable operation IDs, deadlines, attempts, compensation obligations and terminal outcome. Initial fulfilment remains NOT_STARTED; process/dispatch/deliver commands are Phase 6.

Publish the actual transition table in docs before coding, including actor/event, current state, preconditions, mutation/outbox and compensation. Use readable pending states (stock, payment, consumption, compensation, cancellation), CONFIRMED, REJECTED, CANCELLED and MANUAL_REVIEW. Only order-service changes order state.

Happy path:
1. Order/Saga commit ReserveStock outbox.
2. Inventory commits all-lines reservation and StockReserved/StockRejected outbox.
3. Ensure reservation is protected for payment (see expiry rules below), then persist RequestPayment outbox.
4. Payment commits durable attempt and simulated success/failure outcome.
5. On payment success, persist ConsumeReservation command.
6. Inventory consumes exactly once and replies StockConsumed.
7. Confirm only after payment success AND stock consumption acknowledgements.
8. Emit cart cleanup independently; cleanup failure must not undo a confirmed order.

Serialize Saga changes using row lock/version checks in short transactions. Reply handlers validate sagaId/orderId/commandId and expected step; ignore harmless duplicates but reconcile meaningful late outcomes. No in-memory futures, async method annotations or scheduler memory as the durable state. Workers resume due work after restart.

Payment timeout is UNKNOWN, not FAILED. Reconcile by stable payment operation ID through durable query/status commands or a narrowly secured read before another charge or cancellation conclusion. Reissue safe commands with the same business operation identity. Define bounded retry/backoff/deadline and MANUAL_REVIEW after exhausted automation while retaining outstanding obligations. Do not resurrect a terminal order from an out-of-order reply.

5. Inventory reservation lifecycle

Add local Reservation/ReservationLine, business-operation deduplication, inbox/outbox and history using new inventory migrations. Preserve existing admin stock/history/idempotency behavior. Reservation history is distinct from physical stock adjustments.

Reserve all order lines or none. Merge/validate duplicates, bound quantities and lock stock rows in deterministic SKU/variant order. Use immutable canonical variant IDs/SKUs. Enforce onHand >= reserved >= 0 and available = onHand - reserved with existing database checks. No JVM/Redis locks or long-lived DB locks.

Dedicated commands/state transitions:
- reserve: available sufficient; increase reserved; commit reservation and outcome together;
- release: decrease reserved once; onHand unchanged;
- consume: decrease onHand AND reserved once; reservation becomes CONSUMED;
- restock: after consumption, increase onHand once as a distinct cancellation operation, never release a consumed reservation.

Stable reservation/order/command identities plus canonical payload hashes prevent same-key changed quantities. Different event IDs for the same operation must not repeat stock effects. Release/restock before a delayed reserve/consume must be handled by durable cancellation intent/tombstone or state rules so late commands cannot recreate a cancelled commitment. Commands/outcomes must be idempotent under reorder, not just duplicate event delivery.

Reservations may have a finite pre-payment expiry. Before requesting payment, order-service must receive an acknowledgement that inventory moved the reservation into a payment-protected state (e.g. CHECKOUT_HELD). Implement an explicit hold command if needed. Expiry worker and hold/consume race through the same row/state lock; only one transition wins. Never start payment for a reservation already expired.

Payment-protected/uncertain reservations cannot be blindly released by TTL. A recovery worker coordinates with Saga/payment state and either consumes, releases or escalates. MANUAL_REVIEW exposes and retains unresolved commitments until safely reconciled. Document limits so stock is not silently orphaned; no fixed-expiry algorithm that can free stock while a charge may have succeeded.

Admin adjustments still require newOnHand >= reserved; inbound receipts remain allowed. Reservations never call the admin adjustment HTTP endpoint. Record appropriate consume/restock physical movements without changing old immutable adjustment rows. Test reserved > 0 against existing adjustment commands.

6. Local payment simulator

Create payment-service with durable attempt/refund state, scoped database, inbox/outbox and stable operation IDs. No real card fields, providers or money transfers.

Support deterministic SUCCESS, DECLINE, DELAYED_SUCCESS, TIMEOUT/UNKNOWN, late success, duplicate outcomes and refund failure/recovery. Control scenarios through local configuration or dedicated test tooling, not a browser boolean that declares payment successful. Store delayed due actions in PostgreSQL and schedule them; no Thread.sleep or in-memory timer as recovery.

Same charge/refund operation/payload gives one business effect; changed amount/currency for same identity conflicts. Payment amount comes only from accepted order snapshots. Preserve the original success even after a timeout; status reconciliation exposes the durable outcome. Refund must apply once to a successful payment, and repeated cancellation cannot refund twice.

Do not expose arbitrary customer charge/refund/state mutation endpoints. Trusted Kafka command handling produces payment events; order cannot be marked paid by its own HTTP request or a caller-provided event body. Document the local Kafka trust boundary and lack of production broker transport/ACL guarantees; do not claim an envelope field authenticates a producer.

7. Compensation, cancellation and cart cleanup

Stock rejection: no charge; reject order.
Definitive payment decline: release reservation and track acknowledgement before concluding compensated failure.
Successful payment plus definitive consume failure: request refund and safe reservation cleanup, tracking each acknowledgement.
Uncertain payment/consume: reconcile actual participant states, not a guessed timeout outcome.
Cancellation before confirmation or while payment runs: CANCEL_PENDING, durable cancellation intent, no further forward steps; late payment success requires refund.
Confirmed pre-fulfilment cancellation: idempotent restock of consumed stock and refund if paid. Mark CANCELLED only after every required effect is acknowledged. Keep pre-dispatch policy ready for Phase 6; fulfilment commands themselves stay deferred.
Compensation failure remains visible, retryable and auditable; MANUAL_REVIEW is not a pretend completed refund/release.

Cart cleanup must be internal and authenticated as a service command, not reuse a customer token after its expiry. Use Kafka owner/cart snapshot metadata without tokens. Add cart inbox/outbox where necessary. Atomically clear ONLY if current aggregate_version and checked-out contents match the accepted snapshot; if customer changed the cart, skip cleanup with an explicit outcome rather than delete new/changed items. Deduplicate by cleanup operation/order. A changed-cart skip is a completed safe result, not endless retries. Cart cleanup does not change financial/order confirmation.

8. Kafka outbox/inbox correctness

Use current project-scoped Kafka KRaft events profile and pinned image; no unrelated broker. Document commands/outcomes topics, producers/subscribers, partitions, retention, groups and schemas. Key order-specific messages by orderId; do not claim ordering across topics or global ordering. Same logical subscriber replicas share a group; different logical subscribers use different groups.

Versioned envelope: eventId, eventType, eventVersion, aggregateId/version when relevant, sagaId, commandId, correlationId, causationId, occurredAt and bounded payload. Explicit DTOs/schemas; no access tokens, credentials or unnecessary address/payment PII in Kafka.

Outbox producer commits domain transition and event in one PostgreSQL transaction. Publisher uses bounded batch claim/lease and recovery of abandoned claims; publish outside long DB locks, wait for broker acknowledgements, then mark sent only if it still owns the claim. Preserve per-aggregate sequencing if required or explicitly rely on state-machine handling of reordering. Crash after broker acknowledgement before marking sent must produce a safe duplicate.

Implement immediate after-commit dispatch plus polling recovery to reduce normal delivery lag:
- Publish an in-process OutboxReady notification containing the persisted outbox row ID during the domain transaction. Handle it with @TransactionalEventListener(phase = AFTER_COMMIT) only after the order/domain and outbox have committed.
- The listener wakes a bounded asynchronous dispatcher; it does not wait for Kafka on the checkout HTTP thread. Executor rejection or a lost wake-up must leave the durable row available to the polling worker and must not turn an already committed operation into a reported failure.
- Immediate dispatch and the scheduled worker use the SAME service-local publisher and atomic database claim/lease mechanism, including a claim ownership token. Do not create a second direct-send implementation or bypass the outbox claim.
- Eligible rows are PENDING with nextAttemptAt due, or IN_PROGRESS with an expired lease. Atomically claim before publishing. SENT rows are excluded from BOTH immediate dispatch and worker selection and cannot be reclaimed as expired work.
- Publish the stored event using its stable eventId and wait for successful Kafka broker acknowledgement with a finite timeout. Only then mark the row SENT in a separate short transaction guarded by the current claim token. Kafka acknowledgement means broker acceptance under configured producer acks, not consumer processing.
- On send failure or timeout, do not mark SENT. Record retry metadata and return to PENDING only if the dispatcher still owns the claim, or let the lease expire after a crash. A timeout may still mean Kafka received it; re-send the same eventId and rely on consumer idempotency.
- If ownership was lost, the old dispatcher must not mark SENT or modify the new owner's claim. Set/renew leases consistently with the bounded send duration. Lease fencing prevents conflicting database updates; it does not promise exactly-once Kafka publication.
- Poll all eligible durable rows, not only rows explicitly marked failed: a crash may occur before the listener, before a send, or before failure metadata was recorded. Polling is the durable recovery mechanism even when immediate dispatch normally succeeds.
- Use separately injected @Transactional writer beans for claim/success/failure updates; no self-invocation transaction assumptions or database writes relying on the listener's completed transaction. Do not hold database locks while waiting for Kafka.

Test immediate dispatch after commit, no dispatch on rollback, successful acknowledgement then SENT, SENT exclusion from subsequent scans, lost wake-up/executor rejection recovered by polling, immediate/worker claim races, send failure/timeout retries, crash after acknowledgement before SENT, and expired-claim ownership fencing. Keep immediate dispatch consistent with the chosen per-aggregate ordering policy; waking a newer row must not bypass an older row when ordering is required.

Consumer inserts unique (consumer_name,event_id), applies business changes and writes response outbox in ONE local DB transaction. Ack Kafka only AFTER DB commit. Handle crash between DB commit and offset commit. Unique insert conflict must not leave a PostgreSQL transaction rollback-only and then continue business writes. Deduplicate business command IDs too; Kafka event dedup alone is insufficient.

Transient errors retry with bounded backoff; poison/schema/unsupported-version messages go to a documented durable dead-letter path and never fake participant success. Preserve original event/operation identities during replay. Do not acknowledge poison messages before durable dead-letter recording/publish is guaranteed. Distinguish retryable participant failures from terminal business rejection.

Use Kafka producer idempotence where appropriate, but describe delivery as at-least-once with effectively-once business effects. Kafka transactions do not atomically commit PostgreSQL. Use @TransactionalEventListener(AFTER_COMMIT) as the fast outbox-dispatch wake-up described above, never as a substitute for persisting an outbox row in the domain transaction.

9. Security, UI and resilience

Order-service independently verifies JWT signature, issuer, expiry, type and order-service audience; maps only owning-client permissions. Create/quote require order.create; reads order.read_own; customer cancellation order.cancel_own plus domain conditions. Derive owner from issuer/subject and enforce on all quotes/orders/cancel endpoints. No guessed-order-ID disclosure or forged ownership headers.

Add minimal controlled internal HTTP permissions/service account only if a real internal endpoint requires it; prefer Kafka for durable participant commands. Reconcile Keycloak scopes/audiences non-destructively; do not grant realm-admin, turn on password grants or grant admin privileges to storefront. Jobs must survive customer token expiry without stored customer credentials.

Use existing finite HTTP timeouts/Resilience4j for pre-acceptance cart/catalog/address reads. No remote HTTP inside DB transactions or retrying the entire checkout method as a charge. Persisted Saga retry policy is distinct from short HTTP retry policy.

Use MapStruct with parent-managed processor, service-local config, ERROR on unmapped targets and Spring/constructor injection for new/changed mappings. Domain/state/amount/ownership decisions stay outside mappers.

Storefront adds quote review showing authoritative amount/currency, expiry and selected address; accept checkout submits stable idempotency key. Add own orders/list/detail, pending progress, payment simulated label, rejection/compensation/manual-review states and allowed cancellation. Poll boundedly for asynchronous progress with cleanup on logout/navigation; no fake immediate confirmed order or paid toggle. Preserve cart drafts/ownership state reset. Show retries/network uncertainty safely, reusing same key/payload; price review requires a new accepted quote.

Admin fulfilment/dashboard screens remain Phase 6. Local failure/recovery tools may inspect state safely without arbitrary order/payment edits.

10. Acceptance tests and demo gates

Add real PostgreSQL/Kafka Testcontainers integration tests and deterministic unit/state-machine/HTTP tests:
- owner isolation for quotes/orders/address/cancel, wrong-client roles/invalid tokens;
- mixed currency, price/activation/cart-version changes, quote expiry, duplicate quote submission;
- concurrent same-key checkout one order; changed payload 409; replay after response loss/expiry;
- all-lines reservation rollback, competing orders for scarce stock, reserved constraints and admin corrections;
- duplicate/different-event-same-operation/reordered reserve/release/consume/restock/refund;
- expiry vs hold/consume and payment uncertainty protection;
- success only after charge AND consume; stock reject means zero payment;
- decline/release, late payment/cancellation/refund, consume failure/refund, failed compensation/manual review;
- publisher crash after send, abandoned claim, consumer crash after DB commit before ack;
- poison/DLT/replay and broker outage without lost accepted orders;
- restart each participant/coordinator/publisher with pending work; resume once, no double charge/stock effect;
- cart changes during checkout preserved; cleanup replay/failure isolated from confirmation.
Use deterministic barriers/clock/test hooks and persistent state; do not mock away Kafka/DB boundaries for the key acceptance scenarios.

Add repeatable Bash make checkout-check/saga-check (or clearly named equivalents) with real PKCE tokens and deterministic simulator scenarios, status polling deadlines, assertions and restoration of changed test configuration. Existing make check/backend-verify/frontend-test/frontend-build/smoke/security-check/catalog-check/inventory-check/cart-check/cache-check remain passing. Don't reset seed roles/passwords or assume catalog-viewer is pure read-only.

Update Makefile/start scripts/Compose/manual IDE guides for order/payment/Kafka, their scoped DBs, advertised host/container listeners and startup readiness. Only start this project's required services/profiles; no deletion/prune. Run meaningful container-mode smoke if available; record whether source-run or container path was verified.

Exercise real browser quote→accepted→confirmed, rejected stock/payment, pending/compensation and cancellation. Distinguish mocks, integration tests, real-token HTTP and browser evidence. Missing prerequisites/disabled ITs remain explicit blockers, not passed gates.

11. Deliver

Add docs/phase-5.md, API contracts/examples, event schemas, Saga/state/compensation tables, ADR for orchestration/outbox/inbox, failure demo guide and focused Mermaid flow. Update service guides, permissions, local/Postman commands, status/progress/verification and implementation plan.

Explain implemented happy path and restart/duplicate/unknown-payment recovery in interview-ready terms grounded in actual code. State remaining localhost broker/security limits; full operational hardening is Phase 7.

Progress entries record date, task, changed behavior, commands/results, blockers and next task. Finish with behavior/files, actual checks, demo commands and readiness for Phase 6. Stop after Phase 5; no fulfilment implementation, cache redesign, cloud work, push or publishing.

