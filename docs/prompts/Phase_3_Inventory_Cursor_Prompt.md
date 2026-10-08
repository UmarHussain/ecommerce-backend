Implement Phase 3 — Inventory administration in this existing repository.

Phase 2 is complete. Extend the current implementation, verify inventory end to end, and stop after Phase 3 for review. Do not restart identity/catalog or regenerate the project.

1. Read and inspect first

Read AGENTS.md, START_HERE.md, IMPLEMENTATION_PLAN.md, MASTER_PROMPT.md, docs/service-status.md, docs/phase-2.md, docs/progress.md, docs/verification.md, docs/permission-matrix.md and the applicable .cursor/rules. Inspect inventory-service, catalog admin read contracts, gateway routes/security, user-service's admin permission summary, Keycloak template/reconciliation, frontend catalog/API patterns, PostgreSQL init/Compose, and Makefile/Bash tooling.

This prompt was prepared against GitHub HEAD 179e19718334960dafa1b0851fd0d30112a988ec. Inspect the actual current checkout and preserve newer/unrelated changes. Phase 2 has MapStruct, immutable SKUs, expectedVersion writes, explicit routes, catalog screens and make catalog-check. Inventory is still a secured health shell. Do not infer completion from a directory or document.

The verification notes report catalog-viewer@example.test has both CATALOG_VIEWER and CATALOG_EDITOR. Inspect actual role assignments before using accounts in permission tests; use controlled dedicated fixtures for pure read-only tests rather than assuming seed names imply current permissions. Do not reset existing users/passwords.

Use make help and make check. Keep Java 21, the Maven Wrapper, WSL2 and Makefile/Bash local tooling. No Python project dependency, volume deletion, database resets, broad Docker cleanup, secrets in logs, push or publishing.

2. Scope and boundaries

Keep React → API gateway → inventory-service. Inventory owns stock, adjustments, history and stock invariants. Catalog owns SKU identity and product/variant metadata. No cross-service SQL, foreign keys into catalogdb, shared JPA/domain JAR, gateway business logic, or recreated storefront/admin portal backends.

Deliver stock persistence, setup, paginated reads, safe delta adjustments, durable idempotency, history, catalog validation, admin screens and permission/concurrency/failure tests.

Keep later phases deferred:
- Phase 4: cart, Redis cache-aside, Spring Cache, Resilience4j circuit breaker/retry/timeouts.
- Phase 5: order-service durable Checkout Saga, Kafka outbox/inbox, reservations, payment simulator, compensation and recovery.
- Phase 6: fulfilment commands and admin order views.
- Phase 7: metrics/logs/traces, local transport-security profile and failure demos.

Do not implement reservation APIs/tables, checkout, payment, Kafka, Redis, Saga or public stock endpoints now. Basic finite HTTP timeouts are required now for the catalog call; the full Resilience4j policies remain in Phase 4.

3. Inventory persistence and contracts

Add JPA/Flyway/PostgreSQL using the existing inventorydb and scoped inventory_app credentials. Verify exact environment variable conventions from current Compose and service configs; use the application's scoped DB login, never a bootstrap superuser. Add dependencies using parent-managed versions.

Use service-local migrations/entities:
- StockItem: UUID id, unique catalogVariantId and immutable canonical SKU, onHand, reserved, @Version, UTC createdAt/updatedAt.
- StockAdjustment/history: UUID id, stock reference, operation type, signed delta, before/after onHand, reserved snapshot, resulting stock version, reason code, bounded note/reference, actor issuer/subject and UTC timestamp.
- Durable command/idempotency record: actor issuer/subject, operation scope, idempotency key, canonical request fingerprint and stored result sufficient for exact replay.

Store product/name metadata only as clearly labelled snapshots if useful; catalog remains authoritative. Use a single stock pool per variant for this phase; defer warehouses/location modelling. Use integer quantities with explicit bounds and overflow-safe arithmetic. Define available = onHand - reserved. reserved starts at zero and is not client-writable.

Enforce database NOT NULL, uniqueness, foreign keys within inventorydb and CHECK constraints: onHand >= 0, reserved >= 0, reserved <= onHand. Use DTOs, bounded pagination and allowlisted sorting with deterministic tie-breakers. No hard delete or direct set-onHand/set-reserved API.

Use explicit admin routes under /api/v1/admin/inventory, for example:
POST /stock-items — setup stock for a catalogVariantId, initialOnHand >= 0, mandatory reason, optional note/reference.
GET /stock-items — paginated list, filters/search by SKU or stock/catalog variant identifier.
GET /stock-items/{id} — detail.
POST /stock-items/{id}/adjustments — nonzero signed delta, mandatory reason and expectedVersion.
GET /stock-items/{id}/adjustments — paginated immutable history.

Document exact selected contracts in OpenAPI. Setup may include an opening balance; write stock, opening history (including zero setup), idempotency result and audit identity in one transaction. Distinguish zero-value setup from forbidden zero-value adjustments. Return 201 with Location for setup and a documented result containing the updated stock and history for an adjustment. Reasons should distinguish opening balance, inbound receipt, correction, damage/loss and return; define allowed delta signs for each.

4. SKU checks through catalog

Create a CatalogLookupPort with a synchronous HTTP adapter inside inventory-service. Reuse Phase 2 protected catalog product/variant detail endpoints, including inactive visibility. The UI selects a catalog product/variant, but inventory-service independently fetches and verifies the supplied catalogVariantId; never trust browser SKU, name or active flags.

During setup confirm variant/product/category are active using authoritative catalog responses. Store the canonical SKU and immutable variant id. No anonymous public lookup that misclassifies an inactive SKU as nonexistent, no catalog database access, and no new Keycloak service account just to avoid current authorization.

For this admin flow, forward the validated caller access token to catalog-service. Verify real admin tokens include catalog-service audience and catalog.read (INVENTORY_MANAGER already includes catalog.read) as well as inventory-service permissions. Document required catalog.read for setup; return 403 if it is absent. Do not weaken audience validation or map inventory roles into catalog authorities.

Configure bounded connect/read and pool-acquisition timeouts where supported. Preserve correlation IDs; never log tokens. Treat 404 as missing catalog identity, inactive setup as a documented business rejection, 401/403 as authentication/permission errors, unavailable catalog as 503 and timeout as 504. No fallback claiming that an unverified SKU is valid; no unbounded retries.

Perform remote reads BEFORE entering the short inventory write transaction. Record that this is a point-in-time validation: catalog may deactivate afterward and there is no cross-service atomic activation guarantee. Deactivation must never remove existing stock/history. Permit physical corrections/receipts against an existing stock item even if its catalog entry is now inactive; they do not reactivate or make it sellable. Existing stock adjustments and history need not synchronously revalidate catalog on every request. Make this policy explicit in docs/UI.

5. Safe concurrency, atomic history and idempotency

For adjustments require expectedVersion from the loaded stock. Within one transaction use a justified PostgreSQL row lock plus version comparison, or a conditional atomic update plus appropriate conflict detection. Choose and document one consistent strategy. Enforce all invariants, update the stock once and commit history and idempotency result together. Never use JVM/Redis locks or an exists-then-act check as the correctness mechanism.

Return 400 for missing/invalid quantity/reason/version, 404 for missing stock, 409 for stale versions, stock invariant violations or conflicting identities/commands. Never silently clamp negative stock or overwrite a newer value. Arithmetic overflow must reject without partial changes.

Require Idempotency-Key on setup and adjustments. Scope by authenticated issuer/subject and operation, include target/payload/expectedVersion in a stable request fingerprint, and enforce uniqueness in PostgreSQL. Same key/scope/payload returns the stored original status/result with no second adjustment/history. Same key with different payload returns 409. Replay must work after restart and after a response was lost.

Resolve a completed replay BEFORE checking today's expectedVersion or contacting catalog again. Handle concurrent same-key requests correctly with database constraints/locking: one effect, one history entry, consistent replay. Do not catch a unique violation and continue using an already rollback-only transaction. Define safe short waiting/retryable behavior for in-flight commands. Rollbacks must not leave false success records. Different setup keys for the same variant must not add opening stock twice; return a documented conflict.

The browser must reuse the same key/payload after an uncertain network outcome and use a new key for an intentional new command. Do not automatically retry version-conflicting commands.

6. MapStruct and service structure

Use MapStruct for new/changed Java DTO mappings, retaining the Phase 2 parent-managed version and compiler annotation-processing pattern. Use Spring-managed mappers and a service-local @MapperConfig with unmappedTargetPolicy = ERROR and constructor injection. Explicit nested/renamed mappings and ignores; generated code stays in target/. No handwritten field-by-field mapper or shared mapper JAR.

Mappers convert representations only. Services/domain methods enforce identity, quantities, reasons, versions, audit ownership, activation policy and persistence. Never map a request blindly onto a managed stock entity or copy caller-supplied reserved/audit/identity/version fields into it. Keep external catalog DTOs local to the inventory adapter, not shared domain entities.

7. Security, routing and frontend

Inventory-service independently validates JWT signature, issuer, expiry, access-token type and inventory-service audience. Convert only allowlisted inventory-service client roles. Require inventory.read for reads/history and inventory.adjust for setup/adjustment in URL and method security, denying unknown endpoints. Gateway requires admin.access and forwards the caller token unchanged. UI visibility is not enforcement.

Add explicit method/path inventory routes to the gateway, service URL configuration, correlation/error propagation and consistent ProblemDetail responses with stable inventory error codes. Reuse Phase 2 patterns without importing another service's implementation. Preserve user-service's permission summary and ensure inventory permissions are represented under their owning client.

Admin-web: Inventory navigation for inventory.read; stock list/search/pagination/detail; product/variant picker using catalog admin reads; setup form; onHand/reserved/available/version display; adjustment form; history with delta, before/after, reason, actor and timestamp. Hide mutation controls without inventory.adjust. If setup lacks catalog.read, show the capability limitation. Preserve drafts on validation/version conflicts; offer reload/review. Show loading/empty/401/403/404/409/unavailable/uncertain-outcome states. Prevent accidental double-submit and fake success. No raw reserved editing.

Keep storefront catalog and identity flows unchanged. Do not expose exact stock quantities publicly or add checkout placeholders.

8. Tests and runtime gates

Add unit/MVC/security tests plus real PostgreSQL Testcontainers integration tests covering:
- setup with valid/missing/inactive variant and forged browser SKU;
- catalog 401/403/404/503/timeouts, correlation forwarding, no DB mutation on failed setup;
- positive/negative adjustments, reason/sign rules, zero/invalid/overflow quantities;
- rollback atomicity: no quantity change without matching history/result;
- nonnegative stock and reserved <= onHand, including test fixtures with reserved > 0 without adding reservation APIs;
- sequential stale forms and overlapping decrements: no lost update, no negative stock;
- concurrent setup uniqueness;
- identical sequential/concurrent idempotency replay, changed payload conflict, uncertain response and restart replay;
- inventory role isolation, wrong-client roles, forged identity headers and invalid tokens;
- explicit gateway method/path routes and downstream errors;
- pagination/history ordering and immutability.
Use independent database transactions and deterministic synchronization for concurrency tests, not mocks or arbitrary sleeps.

Frontend tests should cover permission controls, setup/adjustment/history, available calculation, stale draft preservation and same-key retry after uncertain outcomes. Preserve Phase 1–2 checks.

Add make inventory-check via a Bash script using the existing real Authorization Code + PKCE token helpers. Test INVENTORY_MANAGER/PLATFORM_ADMIN success, catalog-only/customer/storefront dual-role denial, pure inventory.read-only denial of writes, wrong-audience/direct-service negatives and idempotent replay. Obtain role fixtures through controlled existing user administration/reconciliation, not password grants or broad privilege changes. Print statuses/assertions without tokens/passwords.

During implementation run relevant checks; final gates:
make check
make backend-verify
make frontend-test
make frontend-build
make smoke
make security-check
make catalog-check
make inventory-check

Reuse project PostgreSQL/Keycloak; start inventory-service alongside gateway/user/catalog and admin-web. Check existing source-run and container configs both support scoped inventory DB credentials. Do not require Redis/Kafka/later services. Exercise browser stock setup, inbound receipt, correction, history and two-session stale edit. If browser automation/runtime prerequisites are unavailable, record the blocker and exact manual steps; never label mock tests as browser/runtime verification.

9. Deliver and stop

Update service guides, OpenAPI/contracts, permission matrix, local/Postman commands, IMPLEMENTATION_PLAN.md, docs/service-status.md, docs/progress.md and docs/verification.md. Add docs/phase-3.md explaining boundaries, point-in-time catalog checks, DB concurrency/idempotency, inactive-stock policy, examples, actual evidence, limits and demo steps.

Progress entries include date, task, changed behavior, commands executed, result, blockers and next task. Distinguish implementation, unit/integration checks, real-token runtime checks and browser verification.

Finish with behavior changes, relevant files, actual checks/results, remaining blockers and a short demo walkthrough. State readiness for Phase 4 without implementing it. Stop after Phase 3; do not push/publish.

