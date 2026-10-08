Implement Phase 2 — Catalog business slice in this existing repository.

Phase 1 and the removal of storefront-backend/admin-portal-backend are complete. Build on the current code; do not recreate the project or restart identity implementation. Implement the work below, verify it, document evidence, and stop after Phase 2 for review.

1. Inspect before editing

Read AGENTS.md, START_HERE.md, IMPLEMENTATION_PLAN.md, MASTER_PROMPT.md, docs/service-status.md, docs/verification.md, docs/progress.md, docs/permission-matrix.md, docs/security-model.md, and the applicable .cursor/rules files. Inspect catalog controllers/services/entities/repositories/DTOs/tests, gateway routes/security/correlation handling, both frontends, and current Makefile/Bash scripts. The actual checkout and these instructions take precedence over stale initial Phase 1 assignments.

Use make help and make check. Preserve unrelated changes, migrations, data, seed accounts, existing credentials, and working catalog behavior. Do not reset databases or delete volumes. Do not expose .env values or tokens in logs, docs, or commits.

2. Preserve boundaries and scope

The request path remains React → API gateway → catalog-service. Gateway code handles routing/path rewrites, token validation, coarse access checks, CORS, rate limits, and correlation IDs. Business rules, queries, validation, and transactions belong in catalog-service. Do not add gateway response composition or recreate either portal backend.

This phase implements catalog administration and public browsing only. Preserve PKCE S256, separate SPA clients, in-memory tokens, and existing profile/user/role flows. Do not replace login with an auth-service password relay or localStorage tokens.

Keep the later roadmap:
- Phase 3: Inventory stock setup, safe adjustments/history, catalog SKU checks.
- Phase 4: Cart and Redis cache-aside; Resilience4j circuit breaker, retry, and timeouts on synchronous calls.
- Phase 5: Checkout Saga in order-service, order state machine, Kafka outbox/inbox, reservations, local payment simulator, compensation/recovery.
- Phase 6: Fulfilment commands and admin order views.
- Phase 7: Metrics/logs/traces, local transport-security profile, failure demos.

Do not implement those phases now. No stock fields, cart/checkout buttons pretending to work, payment-service, Redis/Kafka integration, Saga, AWS, or broad dependency upgrades.

3. Complete protected admin catalog reads

Retain existing writes and add explicit GET endpoints under /api/v1/admin/catalog:
- categories: bounded list/search with active/inactive filtering, and detail by UUID;
- products: paginated search/filter/sort, and detail by UUID;
- products/{productId}/variants: bounded list including inactive variants;
- variants/{variantId}: detail.

Support the filters needed by the screens, including product category, text search, activation status, and documented price filters where meaningful. Reuse existing PageResponse, validation, normalization and DTO contracts; migrate the catalog mapping implementation to MapStruct as specified below. Validate bounds and allowlist sorting; use a deterministic tie-breaker. Avoid unbounded queries and N+1 fetching. Return explicit DTOs, never JPA entities.

DTO mapping standard from now onwards: use MapStruct for all new or changed Java DTO mappings in this phase and subsequent phases. Convert the existing handwritten CatalogMapper to MapStruct while preserving its public/admin response contracts and service call sites where practical. Use Spring-managed mappers with @Mapper(componentModel = "spring") and a service-local @MapperConfig with unmappedTargetPolicy = ReportingPolicy.ERROR; configure constructor injection for mapper dependencies. Explicitly map renamed/nested fields and explicitly ignore intentionally excluded target fields. MapStruct must perform routine field and collection mapping; do not introduce handwritten field-by-field mapper classes, reflection-based mappers, or ModelMapper.

Pin a compatible MapStruct version centrally in the backend Maven parent and configure mapstruct-processor annotation processing in modules that use it. Preserve existing compiler/annotation processor configuration; if Lombok is present, configure compatible processor integration. Keep generated implementations in target/, never commit them, and verify generation through the Maven Wrapper build rather than relying on IDE-only configuration. Each service owns its mappers; do not create a shared domain/DTO mapper JAR.

Keep business validation, SKU normalization/immutability, expectedVersion checks, authorization, activation decisions, relationship loading and persistence in services/domain code. Do not blindly map request DTOs onto managed entities or allow mappers to overwrite identity, SKU on update, version, audit fields, ownership or activation. Where entity constructors/domain methods enforce invariants, keep explicit service/domain calls; use MapStruct for structural DTO conversion without bypassing those rules. Preserve the caller-selected public/admin variant lists when mapping products so inactive variants cannot leak. Avoid triggering lazy relationship loads unexpectedly from mappers. Small custom conversion methods are allowed for nontrivial representation conversions, with their purpose documented.

Record this standard in AGENTS.md and the backend Cursor rule so later phases follow it. Add focused mapper tests for version/audit/nested fields and the supplied public/admin variant lists; retain API and inactive-isolation tests to verify contracts remain intact.

Admin reads may see inactive records; public reads must continue to exclude inactive categories/products/variants through the whole relationship chain. Document price filtering semantics and do not mix currencies into misleading comparisons.

Require PERM_catalog.read in catalog-service URL rules and method security. Inspect the current deny-by-default configuration: admin GET is currently missing. Keep catalog-service authority conversion restricted to catalog-service client roles.

4. Fix SKU identity and concurrent editing

SKU identifies a variant and is immutable after creation. Current ProductVariantService.update calls changeSku; remove that ability. Preserve normalization and database uniqueness. Keep the existing request contract where practical: if an update contains SKU, normalize it and require it to match the stored SKU; reject a different SKU with a documented 409 problem code. The admin form must display SKU as read-only after creation. Remove or guard domain mutation methods that still permit changing it. Do not reassign a variant to another product or reuse inactive SKUs.

Responses already expose version and entities use @Version, but updates currently accept no client version. Add a required expectedVersion to update and activation request contracts, using separate create/update DTOs where appropriate. Apply this to categories, products, and variants, including status commands. Reject missing/invalid versions with 400 and stale versions with 409. Never silently permit an unversioned update path.

Check the expected version within the write transaction and retain JPA optimistic locking for races after that check. Return the resulting version after flush. A stale browser form must fail even if its request arrives after the earlier transaction committed. Do not automatically replay conflicting writes.

Preserve existing price/currency/slug/relationship validation and activation behavior unless a concrete correction is needed and documented. Enforce uniqueness at the database as well as providing useful validation errors. Do not rewrite applied Flyway migrations; add a migration only if needed.

5. Finish explicit gateway routes and error behavior

Add path-and-method-specific routes for the new admin GETs. Extend public /api/v1/store/catalog routes to support existing product detail by UUID, detail by slug, product variants, and bounded POST variants/batch, alongside existing list/category routes. Rewrite only to corresponding public catalog endpoints. Do not introduce a catch-all proxy that exposes internal or admin APIs.

Anonymous public browsing must remain available. Protected admin calls require gateway admin.access plus the operation permissions enforced by catalog-service. A valid token alone is insufficient.

Forward the caller access token unchanged. Preserve downstream HTTP status, problem JSON, content type, and correlation headers. Ensure one validated/generated correlation ID flows gateway → catalog-service → response and that the frontend can show it. Catalog unavailability must produce a clear 502/503/504 error as appropriate, not fake success. Keep this phase's error handling small; defer the full Resilience4j work to Phase 4.

6. Build real screens in both existing frontends

Admin-web:
- catalog navigation and page access require PERM_catalog.read;
- category/product lists, search/filter/pagination, details and inactive status;
- create/edit category/product/variant forms, category selection, activation controls;
- show variants with SKU, price, currency and image URL metadata;
- create requires catalog.create, edit catalog.update, activation catalog.activate;
- viewer is read-only; creator cannot edit/activate; editor cannot create;
- use existing /api/v1/admin/me permissions and existing OIDC/API client patterns;
- use expectedVersion from the loaded resource on writes;
- on 409, preserve entered data and offer reload/review rather than overwriting;
- provide loading, empty, validation, 401, 403, 404, conflict and unavailable states.

Storefront-web:
- anonymous category/product browsing, search/filter/sort/pagination;
- product details and active variant selection with image URL, price and currency;
- public catalog API calls without requiring login;
- inactive/not-found/unavailable states;
- preserve login/logout, callback/renewal, and profile screens.

All screens use real APIs. Use sensible accessible labels, keyboard controls and readable layouts. Reuse existing React/TypeScript infrastructure; add dependencies only when justified and pin compatible versions. Handle errors in shared clients without breaking user administration.

7. Verify meaningful behavior

Extend retained unit/MVC/security tests and PostgreSQL Testcontainers integration tests:
- admin GET/read permissions and explicit gateway method/path rewrites;
- viewer/creator/editor/platform-admin boundaries, including direct service calls;
- creator cannot smuggle activation through create payloads, nor editor create records;
- catalog permissions placed on another client do not authorize catalog APIs;
- SKU change rejection and allowed updates keeping SKU unchanged;
- normalized SKU/slug uniqueness, including concurrent duplicate creation;
- stale sequential writes and overlapping transactions, for updates/status commands;
- public inactive isolation for list, UUID/slug detail, variants and batch lookup;
- bounded pagination, invalid filters/sort/version, missing records;
- downstream problem/status/correlation propagation and unavailable catalog.

Add frontend component tests for catalog flows, permission-aware controls, loading/errors, inactive visibility and stale edits.

Run relevant checks during implementation, then the final gates once:
make check
make backend-verify
make frontend-test
make frontend-build
make smoke
make security-check

Install frontend dependencies with make frontend-install if required. Reuse current project infrastructure/services; start missing ones through existing Makefile/Bash commands. Extend or add a documented make catalog-check Bash target for Phase 2 runtime checks using real Authorization Code + PKCE-issued tokens. Never enable password grants or weaken JWT/CORS validation for tests. Test admin catalog through the gateway with real issued viewer/creator/editor/customer/dual-role tokens; include negative direct-service calls. Preserve storefront/admin token isolation. Create required test-role assignments through controlled existing tooling; do not reset seed users.

Exercise browser login and the actual catalog screens with appropriate accounts, including anonymous browsing and two stale edit sessions. If browser automation is unavailable, record that limitation and provide exact manual steps; do not claim browser verification from mocks or HTTP tests. Do not claim Testcontainers or runtime checks passed when prerequisites are missing.

8. Document and deliver

Update OpenAPI and docs/contracts with routes, filters, DTOs, expectedVersion rules, errors and examples. Update docs/service-status.md, docs/progress.md, docs/verification.md, IMPLEMENTATION_PLAN.md, permission docs and local/Postman guides where affected. Create docs/phase-2.md with architecture, implemented flows, security/concurrency decisions, commands/results, limits and demo steps.

Record progress using date, task, changed behavior, commands executed, result, blockers and next task. Distinguish implementation, unit/integration checks, real-token runtime checks and actual browser verification. Align roadmap documents with the later phases listed above; do not mark them implemented.

Finish with changed behavior, relevant files, actual checks/results, remaining blockers, a short demo walkthrough and readiness for Phase 3. Stop after Phase 2; do not push/publish or begin inventory.

