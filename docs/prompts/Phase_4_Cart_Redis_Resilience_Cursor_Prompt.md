Implement Phase 4 — Customer cart, Redis catalog cache-aside, and Resilience4j synchronous-call protection in this existing repository.

Phases 1–3 are complete. Build on them, verify Phase 4 end to end, document actual evidence, and stop for review. Do not implement checkout, reservations, orders, payment, Kafka or Saga.

1. Inspect the actual checkout first

Read AGENTS.md, START_HERE.md, IMPLEMENTATION_PLAN.md, MASTER_PROMPT.md, docs/phase-2.md, docs/phase-3.md, docs/service-status.md, docs/progress.md, docs/verification.md, permission/security docs and applicable Cursor rules. Inspect cart shell/security, catalog public/admin services and batch SKU endpoint, inventory CatalogLookupPort/HTTP adapter, StockCommandService/StockTransactionService, gateway routes/errors, storefront catalog/profile screens, Keycloak scope/audience mappings, Compose profiles, PostgreSQL init and Makefile/Bash scripts.

Prepared against GitHub HEAD 8d9dbd6e767fc207c0f1c80338bbef751e5a6516; preserve newer/unrelated changes. Phase 3 has PostgreSQL stock/history/idempotency, MapStruct, real-token checks and separate transactional writer services. Cart is still a secured shell. No regeneration or nested project.

Use make help and make check. Keep Java 21, Maven Wrapper, WSL2 and Makefile/Bash only. No Python project dependency. Preserve data, seed accounts/passwords and existing migrations. Do not delete volumes, broadly prune Docker, print secrets, push or publish. Add only necessary version-pinned dependencies compatible with the current parent; consult official docs for the pinned Spring/Redis/Resilience4j versions.

2. Boundaries and deliberate cache design

Keep storefront-web → API gateway → cart-service for customer carts. Cart owns its PostgreSQL data and ownership rules. Catalog owns catalog data and its Redis public-browse cache. Inventory continues to own stock correctness. No cross-service SQL/FKs, shared JPA/domain/mapper JAR, gateway business composition or recreated portal backends.

Cache public catalog reads centrally in catalog-service so storefront benefits directly and catalog writes own invalidation. Do not add another cart-local catalog cache with an unrelated invalidation scheme in this phase.

Keep the existing POST /api/v1/catalog/variants/batch lookup UNCACHED for cart mutation validation. It is a read-only POST and may safely be retried under the configured policy. It checks the full active variant/product/category chain against PostgreSQL and returns canonical variant IDs, SKUs and prices. No new authenticated machine client is necessary for this existing public catalog lookup. Document whether the adapter omits Authorization for this public request; never forward customer identity headers or browser-supplied roles.

Cached browsing can be briefly stale. Cart additions/quantity increases must use the uncached lookup; catalog unavailable cannot mean valid SKU. This is point-in-time validation, not an atomic transaction across services. Cart membership never reserves or guarantees stock and display prices are not checkout prices.

Keep Phase 5 checkout/Saga/Kafka/payment/reservation/authoritative quote logic, Phase 6 fulfilment/admin orders and Phase 7 full observability/transport-security demos deferred. Do not expose inventory mutations or exact public warehouse stock. Redis must not store authoritative carts, quantities, orders, authorization or browser tokens.

3. Persistent owned carts

Add cart-service JPA/Flyway/PostgreSQL configuration using existing cartdb and cart_app scoped credentials, with a service-owned schema consistent with the other services. Never use the bootstrap superuser. Verify source-run and Compose configuration.

Model:
- Cart: UUID id, unique owner (issuer, subject), aggregate version, UTC audit timestamps.
- CartItem: cart-local FK, immutable canonical catalogVariantId and SKU, positive bounded quantity, display name/image/price/currency snapshot and snapshot timestamp. Unique (cart, catalogVariantId).
No FK into catalogdb/userdb; ownership is the validated issuer/subject, not email or a browser user ID.

Set explicit limits, e.g. 100 unique items/cart and quantity 1–99/item; align DTO, service and database constraints. Use BigDecimal with existing money conventions and overflow-safe arithmetic. Do not aggregate different currencies into one misleading total; group display subtotals by currency.

Provide explicit own-cart endpoints, for example:
GET /api/v1/cart — own cart; idempotent lazy initialization is allowed if concurrent creation is handled using the unique owner constraint and safe rollback/re-read.
PUT /api/v1/cart/items/{sku} — set absolute quantity, not increment; body quantity and expectedVersion.
DELETE /api/v1/cart/items/{sku} — remove line with documented expectedVersion input.
DELETE /api/v1/cart — clear own cart with documented expectedVersion input.

Gateway external routes use /api/v1/store/cart and corresponding subpaths, rewritten to cart-service. Choose one version contract consistently and document it. Never expose a caller-selected owner or another customer's cart. Removal/quantity reductions/clear can work without catalog; add/increase validates live catalog. Explain same-quantity no-op behavior and version policy.

All mutations validate expectedVersion inside a short write transaction and increment the aggregate version when cart contents change. Child-only JPA changes must advance the cart version too: explicitly dirty the root or use a correctly verified versioning strategy. Do not assume @Version on Cart notices item changes. Lock/check the root before modifying items to serialize aggregate invariants; enforce max item count and unique lines in the same transaction.

Use a nontransactional coordinator plus separate Spring @Transactional writer for remote validation followed by atomic cart changes, consistent with Phase 3. No remote calls inside cart DB write transactions, no TransactionTemplate refactor reversal and no Redis/JVM locks for correctness. Recheck version in the writer after remote validation.

Use absolute-quantity commands to avoid double-add on retry. If a response is lost, reload/reconcile with the server; do not silently replay with a new expectedVersion or claim the original request succeeded. A persistent cart idempotency table is not mandatory for these absolute commands; add one only with a concrete justified contract. Preserve drafts on 409.

Prepare for Phase 5 by keeping an aggregate version and consistent snapshots. Read cart/items in one database transaction with a locking/isolation strategy that prevents mixed-version responses. Do not implement checkout snapshots, cleanup-by-version endpoints, order APIs or service-account privileges yet.

4. Security and mappings

Each cart-service request independently verifies signature, issuer, expiry, access-token type and cart-service audience. Map only cart.read_own/cart.write_own from cart-service client roles. Read requires read_own; mutations require write_own, plus token-derived ownership. Deny unknown endpoints.

Gateway performs existing customer-path coarse checks and forwards the token unchanged. Verify actual storefront token scope/audience/role isolation, including dual-role accounts. Do not grant staff access to cart merely because they can enter admin-web. Browser-supplied owner/price/currency/active fields must never override canonical data.

Use MapStruct for new/changed Java DTO mappings with service-local @MapperConfig, unmappedTargetPolicy = ERROR, Spring component model, constructor injection and explicit nested/ignored fields. Reuse parent annotation processor versions; generated code remains target/. Domain rules/version/ownership remain in services. API DTOs and catalog adapter DTOs belong to their respective service, not a shared JAR.

5. Redis public catalog cache-aside

Enable the existing project-scoped Redis cache profile, using source-host versus Compose addresses correctly. Do not start Kafka or future services. Add documented make cache-up/cache-down/cache-status if needed; down must preserve data/volumes and affect only this project's Redis service.

Use Spring Cache deliberately: @EnableCaching, @Cacheable on a separate Spring public-browse facade and @CacheEvict on a Spring-managed invalidation component. Explain the proxy/self-invocation boundary. Keep admin reads/writes and public batch SKU validation uncached.

Cache finite public category/list/detail/variant DTOs only. Use service/version-prefixed keys including every normalized filter, currency, sort, page/size or product identifier/slug. Build keys after validation/normalization so case/whitespace equivalence and invalid inputs are handled consistently. Preserve endpoint contracts and active-chain rules.

Use short configurable per-region TTLs (e.g. 30–60 seconds) with small jitter using APIs supported by the pinned Redis version. Use explicit safe JSON serialization for DTO records, UUID, money and UTC timestamps; avoid broad polymorphic deserialization and JPA entities. Do not cache exceptions, 401/403, transient failures or private data. Negative caching is optional, short-lived and explicit if introduced.

On successful category/product/variant create/update/status changes, evict affected public regions AFTER DATABASE COMMIT. A simple allEntries eviction of these small public regions is acceptable for this phase and safer than incomplete parent/category/slug invalidation. Publish a local transaction event and invoke the proxied eviction component from an AFTER_COMMIT listener, or an equivalently verified approach. No Kafka/outbox is required now.

Rollback must not evict. Redis eviction failure after DB commit must not turn a committed catalog write into a reported failed command. Log correlation/region safely and continue; TTL bounds the stale interval. Document possible concurrent stale repopulation and that no strict cache/DB atomicity is claimed. Do not claim inactive public data disappears instantly during failed invalidation; the uncached batch remains fresh for cart writes.

If Redis is unavailable or an entry cannot be decoded, bypass the cache and query catalog PostgreSQL. Cache GET/PUT/EVICT failures must not fabricate results or block normal authoritative operations; keep Redis socket/connect timeouts short to avoid per-operation multi-second stalls. Do not hide PostgreSQL failures or domain exceptions as cache misses.

Provide a bounded per-key, per-instance cold-load coalescing/stampede demonstration if supported by the chosen Spring Cache design. Verify actual behavior with Redis; do not assume sync=true gives a distributed lock. Document its scope, TTL jitter and multi-pod limitations. No unbounded lock map or waiting. Direct RedisTemplate is acceptable for specific unsupported TTL/diagnostic needs with a documented reason; retain the annotation examples.

6. Resilience4j on outbound calls

Add named, configuration-driven Resilience4j policies for cart→catalog and the existing inventory→catalog adapter. Preserve inventory error codes/idempotency/transaction boundaries and setup semantics. Do not retry stock writes or cart mutations from the gateway. Protect the HTTP adapters, not whole command methods or database transactions.

Use existing supported blocking RestClient/HTTP patterns and finite connect/read/pool-acquisition timeouts. Disable the HTTP client's automatic retries so Resilience4j is the single retry layer. Do not use TimeLimiter to claim it cancels a blocking HTTP call; the client must enforce socket/pool timeouts.

Start with max 2 attempts total, exponential backoff with jitter and a documented total latency budget including connection acquisition, connect/read durations and waits. Select only genuinely transient connection/timeout/502/503/504 failures for retry. Handle 429/Retry-After explicitly if supported; otherwise document no automatic 429 retry. Do not retry invalid input, inactive/missing SKU, 401/403 or business conflicts.

Make decorator order explicit and test it. A reasonable policy is Retry outside CircuitBreaker, with each HTTP attempt passing through CircuitBreaker, and CallNotPermittedException excluded from retry. Document whether breaker statistics count attempts or logical requests. Configure minimum calls, sliding window, failure threshold, open duration and bounded half-open probes.

Breaker failure predicates count technical dependency failures, not valid catalog 404/missing SKUs or permission/domain rejections. Translate circuit-open to clear 503 and timeout to documented 504, preserving correlation IDs. No fallback success for catalog validation, stock setup or cart mutations. Do not add retries to both gateway and service for the same operation.

Cart GET may return already stored display snapshots when catalog cannot refresh them, explicitly marking snapshot timestamp/freshness and availability/price validation as unknown. No invented stock availability or authoritative totals. Do not overwrite good snapshots with failed refresh data. Prefer a bounded batch refresh outside the DB read transaction, not one HTTP call/item. Persist updated snapshots only through a safe version-aware policy; a read response enrichment can remain nonpersistent.

Keep resilience diagnostics minimal and safely exposed for local testing. Full metrics/traces infrastructure remains Phase 7.

7. Storefront cart UI and errors

Add Add to cart from catalog variant selection for authenticated customers, cart navigation/page, quantity edit, remove and clear. Anonymous visitors can browse and are prompted to log in to use their cart. Preserve PKCE, in-memory tokens, callbacks, logout, own profiles and all admin catalog/inventory screens.

Display quantities, snapshot price/currency, grouped display subtotal and freshness/unavailable-item notices. Label that prices/stock are validated again at checkout; do not add a working or fake checkout flow.

Use real APIs, loaded expectedVersion and token-derived ownership. Disable in-flight duplicate submit, handle loading/empty/401/403/404/409/503/504 states, retain draft on conflict and offer reload/review. Network uncertainty should trigger reconciliation, not double-add. React state/query keys must not show a previous customer's cart after logout/account switch; clear user-scoped cache/state on identity changes.

8. Verify behavior and failures

Add focused unit/MVC/security, PostgreSQL Testcontainers, real Redis integration and HTTP stub tests:
- own-cart persistence, canonical SKU validation, quantity/item limits and currency grouping;
- two customers cannot read/change each other's cart; forged owner/price fields and wrong-client roles fail;
- sequential stale edits, simultaneous add/update/remove/clear, aggregate version advancement and consistent GET snapshots;
- rollback atomicity, concurrent lazy creation, mutation during remote lookup;
- removal/reduction available during catalog outage, add/increase fail closed;
- cache miss/load/hit, normalized key separation, DTO serialization, TTL expiry/jitter, after-commit eviction and rollback non-eviction;
- Redis outage/corrupt entry fallback; eviction failure after commit does not report failed catalog writes;
- cached browse may be stale but deactivated SKU is rejected by uncached cart validation;
- coalescing demonstration and documented multi-instance limits;
- retry counts, ignored business errors, open/half-open recovery, timeout budget and correlation propagation;
- inventory setup policy still works with Resilience4j and retains existing permissions/errors;
- explicit gateway routes and no leakage of internal/private endpoints.
Use meaningful concurrency synchronization and controllable clocks/configurations where possible, not fragile sleeps. Test actual Spring cache/transaction/resilience proxies, not just direct object calls.

Frontend tests cover ownership state reset, login requirement, quantities, draft conflicts, unknown/snapshot states and error recovery.

Add make cart-check (real Authorization Code + PKCE tokens for two customers/dual-role/admin negatives) and make cache-check or phase4-check for repeatable Redis/resilience demonstrations. Reuse token helpers, never password grants. Test fixtures must inspect actual assigned roles, not just seed usernames. Do not expose tokens/passwords.

Use targeted checks during development; final gates once:
make check
make backend-verify
make frontend-test
make frontend-build
make smoke
make security-check
make catalog-check
make inventory-check
make cart-check
make cache-check (or documented equivalent)

Run the needed PostgreSQL/Keycloak/Redis and source services including cart. Failure demos must target only this project's dependency or dedicated stub, restore the state they changed, and preserve all volumes. Exercise real browser add/edit/remove/clear, second-session stale edit and account switch; record unavailable browser/runtime prerequisites honestly.

9. Documentation and stop

Update OpenAPI/contracts, service READMEs, permission matrix, local/Postman guides, Makefile help, IMPLEMENTATION_PLAN.md, docs/service-status.md, docs/progress.md and docs/verification.md. Add docs/phase-4.md and a focused cache/resilience ADR with keys/TTLs/invalidation, stale limits, safe serialization, proxy boundaries, decorator order and measured latency/failure examples.

Include a short interview explanation grounded in implemented code for @Cacheable/@CacheEvict, cache-aside, retries/circuit breakers and cart versioning. Explain why Redis is not the cart/stock authority and why checkout must revalidate prices/active items/stock later.

Progress records include date, task, changed behavior, commands/results, blockers and next task. Distinguish source implementation, integration checks, real-token runtime evidence and actual browser verification. Finish with changed behavior/files, actual tests, failure demo walkthrough, limitations and Phase 5 readiness. Stop after Phase 4; do not implement checkout/Saga or push/publish.

