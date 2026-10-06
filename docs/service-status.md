# Service status

Status after Phase 1 and the direct-gateway refactor: identity and staff administration stay implemented. Requests go from the React apps through the API gateway to the owning domain service. `storefront-backend` and `admin-portal-backend` are removed. Catalog business code is unchanged. Cart, checkout, and Kafka Saga remain planned.

| Component | Present now | Next work |
|---|---|---|
| api-gateway | Explicit routes and path rewrites to catalog-service and user-service; JWT audience, coarse checks, CORS, rate limit, correlation id | Later business routes only when those phases start |
| user-service | PostgreSQL profiles, `GET /api/v1/admin/me`, Keycloak Admin adapter, durable operations, audit, custom role bundles, OpenAPI | Email-verification and MFA enrollment (realm still off) |
| catalog-service | Inherited CRUD/domain validation/search/migrations/seed/tests; versioned API and granular security | Admin reads/UI composition, SKU immutability review (Phase 2) |
| inventory-service | Secured application/health shell | Stock persistence and business APIs in Phase 3 |
| cart-service | Secured application/health shell; optional later profile | Phase 4 |
| order-service | Secured application/health shell; optional later profile | Phase 5 Saga/order domain |
| payment-service | No module by design | Phase 5 simulator |
| storefront-web | `oidc-client-ts` + `react-oidc-context`, PKCE login/logout, own-profile screen | Catalog UI (Phase 2) |
| admin-web | Same OIDC stack, permission-aware nav, users/roles screens | Catalog/inventory screens later |
| PostgreSQL/Keycloak | Compose/init/template; realm imported in this checkout; `make realm-reconcile` applied `view-clients` | Do not delete volumes to re-import |
| Mailpit | Optional Compose profile `mail` (8025 UI / 1025 SMTP) | Wire realm SMTP when email verification is enabled |
| Redis/Kafka | Optional profiles; not used by Phase 1 | Phases 4–5 |

`profilePersistenceImplemented: true` on `/api/v1/users/me` after the first authenticated call. Staff access still comes from Keycloak roles, not from the existence of `staff_profile`.
