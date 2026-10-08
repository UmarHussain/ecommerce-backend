# Service status

Status after Phase 3: identity, catalog, and inventory administration are implemented. Requests go from the React apps through the API gateway to the owning domain service. `storefront-backend` and `admin-portal-backend` stay removed. Cart, checkout, payment, Redis, and Kafka Saga remain planned.

| Component | Present now | Next work |
|---|---|---|
| api-gateway | Explicit method-and-path routes to catalog-service, user-service, and inventory-service; JWT audience, coarse checks, CORS, rate limit, correlation id; downstream 502/503/504 problem JSON | Later business routes only when those phases start. Resilience4j stays in Phase 4 |
| user-service | PostgreSQL profiles, `GET /api/v1/admin/me`, Keycloak Admin adapter, durable operations, audit, custom role bundles, OpenAPI | Email-verification and MFA enrollment (realm still off) |
| catalog-service | Admin and public catalog reads, writes, SKU immutability, required `expectedVersion`, MapStruct responses, inactive isolation, PostgreSQL migrations/seed | Inventory setup reads admin variant and product detail; no further catalog feature work |
| inventory-service | `inventorydb` stock, adjustments, and idempotent commands; catalog lookup outside the write; `StockCommandService` coordinates and `StockTransactionService` commits the write; admin stock APIs | Reservations stay in Phase 5 |
| cart-service | Secured application/health shell; optional later profile | Phase 4 |
| order-service | Secured application/health shell; optional later profile | Phase 5 Saga/order domain |
| payment-service | No module by design | Phase 5 simulator |
| storefront-web | `oidc-client-ts` + `react-oidc-context`, PKCE login/logout, own-profile screen, anonymous catalog browse | Cart UI in Phase 4 |
| admin-web | Same OIDC stack, permission-aware nav, users/roles screens, catalog lists and editors, inventory list/setup/detail | Cart UI stays in Phase 4 |
| PostgreSQL/Keycloak | Compose/init/template; realm imported in this checkout; `make realm-reconcile` applied `view-clients` and adds `INVENTORY_READER` plus `inventory-reader@example.test` without resetting passwords | Do not delete volumes to re-import |
| Mailpit | Optional Compose profile `mail` (8025 UI / 1025 SMTP) | Wire realm SMTP when email verification is enabled |
| Redis/Kafka | Optional profiles; not used by Phase 1 | Phases 4–5 |

`profilePersistenceImplemented: true` on `/api/v1/users/me` after the first authenticated call. Staff access still comes from Keycloak roles, not from the existence of `staff_profile`.
