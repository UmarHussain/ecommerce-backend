# Implementation plan

## Current state and assignment

Phase 0 verified and Phases 1–3 implemented in this checkout. A later architecture refactor removed the two portal backends; browsers now call the gateway, which routes to the owning domain service. Do not restart Phase 1. Write-ups: [docs/phase-1.md](docs/phase-1.md), [docs/phase-2.md](docs/phase-2.md), and [docs/phase-3.md](docs/phase-3.md). Later phases remain roadmap.

The user explicitly authorized using the uploaded project to prepare this separate repository. Retain its catalog business implementation and tests; the original application outside this directory remains untouched.

## Phase 0 — Verify the prepared foundation

- [x] Root handoff, master specification, AGENTS, and scoped Cursor rules.
- [x] Gateway, user-service, and retained domain modules. Portal backends were removed after Phase 1.
- [x] Catalog routes versioned and permissions separated by create/update/activate.
- [x] Project-scoped Compose, databases, realm template, generated secrets, scripts.
- [x] Frontend shells and Vite same-origin API proxy configuration.
- [x] Resolve any build issues on Java 21 and documented Node/npm versions (2026-10-01: `make backend-compile`, `make backend-test`, frontend test/build passed; see docs/verification.md).
- [x] Run backend `verify`, including catalog PostgreSQL Testcontainers tests (2026-10-01: `make backend-verify` passed in WSL2).
- [x] Run Compose startup and smoke tests; verify realm import and DB isolation (`make bootstrap`, `make infra-up`, `make smoke` on 2026-10-01 in this checkout).
- [x] Inspect actual tokens and confirm audience/role-scope restrictions (storefront dual-role token contains CUSTOMER only; extra scopes do not add staff roles; ID token `typ=ID`).

Gate: no claim of fully working foundation until Java 21 tests, Compose, catalog smoke, and real token checks pass. Record actual evidence in docs/verification.md.

## Phase 1 — Identity, user service, and staff administration (implemented; stopped for review)

1. Introduce a maintained OIDC client to both frontend apps; exact redirect/logout paths, in-memory tokens, PKCE S256, login/logout and refresh behavior.
2. Add user-service JPA/Flyway/PostgreSQL setup using `userdb/user_app`, not the bootstrap superuser. Tables: app_user with unique `(issuer,subject)`, customer_profile, customer_address, staff_profile, identity_operation, access_audit.
3. Implement idempotent own-profile initialization. Replace principal-only `/api/v1/users/me` behavior with a documented profile contract; keep token-derived ownership.
4. Implement Keycloak Admin REST adapter behind UserDirectoryPort. Give the runtime service account verified minimum privileges. Use a separate bootstrap/reconciliation path for infrastructure configuration.
5. Add user creation/onboarding, search/detail, staff suspension, direct/effective role listing, role assignment/removal, and controlled custom role bundles from the fixed permission catalog.
6. Implement durable Keycloak/DB operation records with idempotency, uncertain-result reconciliation, and audit. Return pending status instead of false success.
7. Add typed admin APIs on user-service and explicit gateway routes. The original portal relay was removed; the gateway forwards the caller token. No wildcard admin proxy.
8. Build user/role screens, permission-aware navigation, and readable errors in admin-web. USER_ADMIN cannot grant PLATFORM_ADMIN or elevate itself; guard the last platform administrator.
9. Add local email catcher, email-verification flows, staff MFA enrollment, and precise callback allowlists. The initial realm intentionally does not implement these yet.
10. Add OpenAPI contracts and explicit 401/403/ownership behavior at the gateway and user service.

Acceptance gate:

- Actual browser OIDC login in both apps; no direct/password grants used for testing.
- Customer-only account denied admin APIs; dual-role account's storefront token has no staff privileges even with extra scope requests.
- User admin can assign allowlisted non-admin roles but cannot elevate itself or another user to platform admin.
- Expired, wrong-issuer, wrong-audience, ID tokens, and forged identity headers fail.
- Customers cannot read/update another customer's profile.
- Role change appears after refresh/re-login; document short-lived JWT revocation limits.
- Keycloak timeout/crash between remote success and DB commit reconciles without duplicate identities or false success.
- Backend tests, real Keycloak integration tests, frontend tests/builds, and browser security flows pass.
- Update progress, service guides, and verification. Explain the flow using actual code. Stop for review.

## Phase 2 — Catalog business slice (implemented; stopped for review)

Admin catalog reads, immutable SKU, required `expectedVersion`, MapStruct response mapping, explicit gateway routes, and catalog screens in both SPAs. Write-up: [docs/phase-2.md](docs/phase-2.md). Gate: catalog administration through the gateway to catalog-service with real issued tokens (`make catalog-check`).

## Phase 3 — Inventory administration (implemented; stopped for review)

Stock setup, signed adjustments, immutable history, catalog SKU checks at setup, and inventory screens. Write-up: [docs/phase-3.md](docs/phase-3.md). Gate: permission and concurrent adjustment tests with PostgreSQL, plus `make inventory-check` through the gateway.

## Phase 4 — Cart and Redis (implemented; stopped for review)

Own carts, absolute quantities, aggregate versions, public catalog cache-aside, and Resilience4j around the catalog HTTP adapters. Write-up: [docs/phase-4.md](docs/phase-4.md). Redis is not the cart or stock authority. Gate: `make cart-check` and `make cache-check`.

## Phase 5 — Checkout Saga

Implement order state machine, durable coordinator, Kafka/outbox/inbox, reservation/consume/release/restock operations and payment simulator. Create payment-service only now. Demonstrate duplicate delivery, timeouts, late success, compensation, and restart recovery. No real payment provider.

## Phase 6 — Fulfilment and admin dashboards

Valid process/dispatch/deliver/cancel commands in order-service, role-specific admin UI, partial dashboard degradation, history and audit. No arbitrary paid-state setter.

## Phase 7 — Operational demonstration

Metrics/logs/traces, local transport-security profile, chaos/failure scripts, and portfolio documentation. Do not claim production transport security for the localhost HTTP starter.

## Working cadence

Before each task: read current status, inspect relevant code, choose a small demonstrable slice. After each slice: run relevant checks, fix concrete failures, update progress. Avoid rebuilding everything solely because a folder is called a skeleton. No cloud work or destructive cleanup.
