# Implementation plan

## Current state and assignment

Phase 0 verified and Phase 1 implemented in this checkout. A later architecture refactor removed the two portal backends; browsers now call the gateway, which routes to the owning domain service. Do not restart Phase 1. Write-up: [docs/phase-1.md](docs/phase-1.md). Phases 2–3 complete the first business milestone; later phases remain roadmap.

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

## Phase 2 — Catalog business slice

Preserve retained service logic. Add admin read/search APIs (the inherited service currently has public reads and admin writes), validate SKU identity immutability, propagate downstream errors and correlation IDs, and build real catalog screens. Add creator-only vs editor permission tests and concurrency/version-conflict tests. Verify public inactive-data isolation. Gate: catalog administration end to end through the gateway to catalog-service, with real issued tokens.

## Phase 3 — Inventory administration

Add inventorydb migrations/entities, stock setup, atomic safe adjustments, reasons/history, SKU verification through catalog, fine-grained permissions, and UI. Never permit on_hand < reserved or negative stock. Gate: permission and concurrent adjustment tests with PostgreSQL.

## Phase 4 — Cart and Redis

Implement own carts, quantities, cart versions, catalog cache-aside, TTL/invalidation, degraded-cache behavior. Add Spring Cache annotations intentionally and explain proxy boundaries. Redis never owns stock/order correctness.

## Phase 5 — Checkout Saga

Implement order state machine, durable coordinator, Kafka/outbox/inbox, reservation/consume/release/restock operations and payment simulator. Create payment-service only now. Demonstrate duplicate delivery, timeouts, late success, compensation, and restart recovery. No real payment provider.

## Phase 6 — Fulfilment and admin dashboards

Valid process/dispatch/deliver/cancel commands in order-service, role-specific admin UI, partial dashboard degradation, history and audit. No arbitrary paid-state setter.

## Phase 7 — Operational demonstration

Metrics/logs/traces, local transport-security profile, chaos/failure scripts, and portfolio documentation. Do not claim production transport security for the localhost HTTP starter.

## Working cadence

Before each task: read current status, inspect relevant code, choose a small demonstrable slice. After each slice: run relevant checks, fix concrete failures, update progress. Avoid rebuilding everything solely because a folder is called a skeleton. No cloud work or destructive cleanup.
