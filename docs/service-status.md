# Service status

Status during Phase 5: identity, catalog, inventory, cart, checkout quotes, and the local payment simulator are in the tree. Public catalog browse is cached in Redis. Requests go from the React apps through the API gateway to the owning domain service. `storefront-backend` and `admin-portal-backend` stay removed. `make checkout-check`, `make saga-check`, and the storefront browser checkout pass against the live stack (2026-10-09).

| Component | Present now | Next work |
|---|---|---|
| api-gateway | Explicit method-and-path routes to catalog-service, user-service, inventory-service, cart-service, and order-service; JWT audience, coarse checks, CORS, rate limit, correlation id; downstream 502/503/504 problem JSON | Payment stays off the gateway |
| user-service | PostgreSQL profiles, `GET /api/v1/admin/me`, Keycloak Admin adapter, durable operations, audit, custom role bundles, OpenAPI | Email-verification and MFA enrollment (realm still off) |
| catalog-service | Admin and public catalog reads, writes, SKU immutability, required `expectedVersion`, MapStruct responses, inactive isolation, PostgreSQL migrations/seed, Redis cache-aside for public browse | The SKU batch used by cart stays uncached |
| inventory-service | `inventorydb` stock, adjustments, and idempotent admin commands; catalog lookup outside the write; checkout reservations on Kafka (`checkout.inventory.commands` / `checkout.inventory.outcomes`) with an outbox, inbox, and pre-payment TTL that releases only `ACTIVE` rows | `InventoryReservationIT` covers the reservation rules |
| cart-service | `cartdb` own-cart aggregate, absolute quantities, versions; catalog batch outside the write; Resilience4j on that HTTP call; Kafka cleanup that clears only a matching cart version | Cleanup skip stays a completed result |
| order-service | `orderdb` quotes, idempotent checkout accept, saga columns, and outbox/inbox on `checkout.*.outcomes` | `OrderCheckoutSagaIT` (13) plus live `checkout-check`/`saga-check` pass; no Kafka Testcontainers in the IT |
| payment-service | `paymentdb` simulated charge and refund attempts, outbox, and a local control endpoint that stays disabled unless `payment.simulator.control-enabled=true` | No customer charge route and no gateway route |
| storefront-web | `oidc-client-ts` + `react-oidc-context`, PKCE login/logout, own-profile screen, anonymous catalog browse, signed-in cart, quote review, and order progress | Browser quote, confirm, stock rejection, payment decline, and cancel passed on 2026-10-09 |
| admin-web | Same OIDC stack, permission-aware nav, users/roles screens, catalog lists and editors, inventory list/setup/detail | No customer cart screen |
| PostgreSQL/Keycloak | Compose/init/template; service-scoped databases include `orderdb` and `paymentdb`; realm template gives CUSTOMER order create/read-own/cancel-own roles | Do not delete volumes to re-import |
| Mailpit | Optional Compose profile `mail` (8025 UI / 1025 SMTP) | Wire realm SMTP when email verification is enabled |
| Redis/Kafka | Redis profile caches public catalog browse. Checkout commands use the `events` profile (`kafka:9092` in Compose, `localhost:59092` from the host). Envelope fields are not producer authentication | Phase 7 hardens broker transport |

`make checkout-up` starts the application, cache, and event profiles without deleting volumes. `make checkout-check` is the real-token happy path. `make saga-check` is the local deterministic compensation gate and requires the payment simulator control endpoint to be explicitly enabled.

`profilePersistenceImplemented: true` on `/api/v1/users/me` after the first authenticated call. Staff access still comes from Keycloak roles, not from the existence of `staff_profile`.
