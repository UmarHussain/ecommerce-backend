# Service status

Status after Phase 4: identity, catalog, inventory administration, and the customer cart are implemented. Public catalog browse is cached in Redis. Requests go from the React apps through the API gateway to the owning domain service. `storefront-backend` and `admin-portal-backend` stay removed. Checkout, payment, and Kafka Saga remain planned.

| Component | Present now | Next work |
|---|---|---|
| api-gateway | Explicit method-and-path routes to catalog-service, user-service, inventory-service, and cart-service; JWT audience, coarse checks, CORS, rate limit, correlation id; downstream 502/503/504 problem JSON | Later business routes only when those phases start |
| user-service | PostgreSQL profiles, `GET /api/v1/admin/me`, Keycloak Admin adapter, durable operations, audit, custom role bundles, OpenAPI | Email-verification and MFA enrollment (realm still off) |
| catalog-service | Admin and public catalog reads, writes, SKU immutability, required `expectedVersion`, MapStruct responses, inactive isolation, PostgreSQL migrations/seed, Redis cache-aside for public browse | The SKU batch used by cart stays uncached |
| inventory-service | `inventorydb` stock, adjustments, and idempotent commands; catalog lookup outside the write; Resilience4j on that HTTP call; `StockCommandService` coordinates and `StockTransactionService` commits the write; admin stock APIs | Reservations stay in Phase 5 |
| cart-service | `cartdb` own-cart aggregate, absolute quantities, versions; catalog batch outside the write; Resilience4j on that HTTP call | Checkout stays in Phase 5 |
| order-service | Secured application/health shell; optional later profile | Phase 5 Saga/order domain |
| payment-service | No module by design | Phase 5 simulator |
| storefront-web | `oidc-client-ts` + `react-oidc-context`, PKCE login/logout, own-profile screen, anonymous catalog browse, signed-in cart | No checkout action |
| admin-web | Same OIDC stack, permission-aware nav, users/roles screens, catalog lists and editors, inventory list/setup/detail | No customer cart screen |
| PostgreSQL/Keycloak | Compose/init/template; realm imported in this checkout; `make realm-reconcile` applied `view-clients` and adds `INVENTORY_READER` plus `inventory-reader@example.test` without resetting passwords | Do not delete volumes to re-import |
| Mailpit | Optional Compose profile `mail` (8025 UI / 1025 SMTP) | Wire realm SMTP when email verification is enabled |
| Redis/Kafka | Redis profile caches public catalog browse. Kafka remains unused | Kafka in Phase 5 |

`profilePersistenceImplemented: true` on `/api/v1/users/me` after the first authenticated call. Staff access still comes from Keycloak roles, not from the existence of `staff_profile`.
