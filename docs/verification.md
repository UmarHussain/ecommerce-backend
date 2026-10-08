# Verification

This file distinguishes checks actually executed from commands still pending. Never turn a static check or successful image build into a runtime verification claim.

## 2026-10-01 — Makefile/Bash tooling refactor (WSL2 Ubuntu, Java 21.0.12, Docker 29.3, Compose v5.1)

Executed from `/mnt/d/GitHub/ecommerce-local-platform` unless noted. `jq` was not installed in WSL2 at the time; a temporary `jq` 1.7.1 binary under `/tmp` was used for the checks below (install it permanently with `sudo apt-get install -y jq`).

- `bash -n` on all `scripts/local/*.sh`, `infrastructure/local/postgres/01-databases.sh`, `backend/mvnw`: passed.
- `make -n` for every target and `make help`: all targets resolve; help renders from `##` comments.
- `make check` (static): passed — 8 Maven modules read from `backend/pom.xml`, frontend workspaces, realm template invariants, Cursor rule front matter, required docs, Markdown links. `docker compose config -q` across all profiles passed in a temporary bootstrapped copy (skipped in the main checkout because no `.env` exists there).
- Negative tests for `check.sh` in a temporary copy: an undefined user realm role, `directAccessGrantsEnabled: true` on `storefront-spa`, a composite pointing at an undefined client role, and a storefront scope mapping beyond `CUSTOMER` were each reported as `FAIL`.
- `scripts/local/bootstrap.sh` in a temporary copy (`/tmp`, deleted afterwards):
  - Fresh run: created `.env` (mode 600, 11 generated hex secrets + `COMPOSE_PROJECT_NAME`, `KEYCLOAK_ADMIN`) and `.local/keycloak/ecommerce-local-realm.json` (mode 600); 11 seed users rendered; no `__PLACEHOLDER__` left; after mapping the two secrets back to placeholders the rendered JSON is structurally identical to the template (`diff` of `jq -S` output: 0 lines); both SPA clients keep `pkce.code.challenge.method = S256`.
  - Repeat run: printed "Keeping existing" for both files; SHA-256 of `.env` and realm unchanged.
  - `.env` present, realm deleted: realm re-rendered from the existing `.env`, byte-identical to the first render.
  - `.env.example` copied as `.env`: rejected with a non-zero exit, nothing written.
  - `.env` missing a key: warning names the key; file left untouched.
  - A `DEMO_USER_PASSWORD` containing `"` and `\`: rendered realm stays valid JSON with the value correctly escaped.
- `make backend-compile` (`./mvnw -B test-compile`): BUILD SUCCESS, all 8 modules.
- `make backend-test` (`./mvnw -B test`): BUILD SUCCESS. 26 tests: catalog 23 (application context, authority isolation, controller, permission, validation, service), inventory/cart/order context tests 1 each. api-gateway, storefront-backend, admin-portal-backend, user-service report "No tests to run" (no test classes yet).
- `make backend-verify` (`./mvnw -B verify`): BUILD SUCCESS. Same 26 Surefire tests plus Failsafe `CatalogPostgresIT` (8 tests) using Testcontainers PostgreSQL on the WSL2 Docker daemon. No project volumes or containers were created or left behind.
- Frontend `npm ci`, `npm test`, `npm run build`: passed for both workspaces (1 test each; both production builds). Executed with Node 24.20.0 / npm 11.19.0 from the Windows Git Bash shell because WSL2 had no Linux Node installed; the generated `node_modules` and `dist` were removed afterwards. Re-run `make frontend-install && make frontend-test && make frontend-build` in WSL2 after installing Node 24.20 there.
- Not executed in the main checkout: `make bootstrap` (left for the user so no `.env` is created unseen), `make apps-up`, login, issued-token checks. No `ecommerce-local-platform` Compose volumes exist on this machine yet.

### Runtime verification in a throwaway Compose project (same day)

Executed in a copy under `/tmp` with `COMPOSE_PROJECT_NAME=elp-test`, so the real project name, containers, and volumes were never created; the temp project was removed afterwards with `down -v` (its volumes only).

- `infrastructure/local/manual-startup/infra.sh up`: postgres healthy, keycloak, redis started. Realm `ecommerce-local` discoverable after ~48 s. `01-databases.sh` created `cartdb catalogdb inventorydb keycloakdb orderdb paymentdb userdb`.
- `make infra-up` afterwards: `infra-status` listed the running containers; Compose reported `Container ... Running` for postgres/keycloak with no recreate — the manual and main files share containers as intended.
- Defect found and fixed: Kafka exited with `AccessDeniedException` on `/tmp/kraft-combined-logs` (root-owned named volume, non-root image user). Fixed by mounting `kafka-data` at `/var/lib/kafka/data` (`KAFKA_LOG_DIRS` updated). After the fix: `Kafka Server started`, broker answers inside the network, host listener `localhost:59092` reachable. Redis reachable on `localhost:56379`.
- Defect found and fixed: `api-gateway` failed to start with `BeanDefinitionOverrideException` (`@Configuration class Routes` and `@Bean routes` shared the bean name). Fixed by renaming the bean method to `gatewayRoutes`; no routing behaviour changed.
- `scripts/local/run-service.sh` for catalog-service, storefront-backend, admin-portal-backend, api-gateway: all four `/actuator/health` → `UP` within 25 s of compilation finishing.
- `make smoke`: passed (realm discovery, gateway health, public catalog through gateway → storefront backend → catalog service, `401` on `/api/v1/admin/me`). `/api/v1/store/me` without a token also returns `401`. Catalog payload keys: `first, items, last, page, size, sort, totalElements, totalPages`.
- `docker compose config` passes for the main file (all profiles) and for the manual-startup file (`extends` resolves relative mounts against the main file; `profiles: !reset []` makes redis/kafka unconditional there).
- Not verified: issued-token authorization, container app mode (`make apps-up`), frontends under WSL2 Linux Node, user-service/inventory/cart/order beyond `make backend-test` context tests.

Not installed in WSL2 at verification time: `jq` (required by `make bootstrap`/`make check`), Linux Node/npm, `shellcheck` (optional; not used by any target).

## 2026-09-30 — Packaging environment (pre-handoff)

- Frontend dependency install, tests, and production builds passed (Node 24.19.0 / npm 11.9.0 with engine warnings).
- All eight backend modules compiled with Temurin 21.0.12.1 (`./mvnw -o -B test-compile`). Test execution was blocked offline by a missing Surefire provider; superseded by the 2026-10-01 runs above.
- Static scaffold checks, YAML/Compose structure checks, and bootstrap double-run checks passed with the earlier Python tooling, since replaced by `scripts/local/check.sh` and `scripts/local/bootstrap.sh`.
- Docker, Compose, Testcontainers, realm import, login, and token checks could not run there.

Narrative review (what shipped, flows, limits): [phase-1.md](phase-1.md).

## 2026-10-01 — Phase 0 runtime + Phase 1 identity (this checkout)

Executed from `/mnt/d/GitHub/ecommerce-local-platform` in WSL2 Ubuntu (Java 21.0.12, Node 24.20.0 / npm 11.19.0, Docker 29.3.0, Compose v5.1.0, jq 1.7.1).

- `make help`, `make bootstrap` (kept existing `.env` and rendered realm), `make check`: passed, including `docker compose config` with the `mail` profile.
- `make infra-up`: postgres healthy, Keycloak Up; realm discovery `200`.
- `make backend-compile`: BUILD SUCCESS, all 8 modules.
- `make smoke` after starting catalog-service, storefront-backend, admin-portal-backend, api-gateway: passed.
- Issued-token inspection via `scripts/local/oidc-login.sh` (Authorization Code + PKCE, no password grant):
  - Storefront customer and dual-role: `typ=Bearer`, audiences include `api-gateway`, `storefront-backend`, `user-service`; `realm_access.roles` is `["CUSTOMER"]` only; no `admin-portal-backend` resource roles.
  - Extra storefront scopes did not add staff roles.
  - Admin dual-role: `CATALOG_CREATOR` + `CUSTOMER` + `admin.access` + `catalog.create`/`catalog.read`.
  - Admin USER_ADMIN: user/role permissions without `role.manage` or `user.disable_identity`.
  - ID token: `typ=ID`, audience `storefront-spa`.
- `make realm-reconcile`: added `view-clients` (and restated existing least-privilege grants) to `user-service-admin`. Nothing deleted.
- `make backend-test`: BUILD SUCCESS. User-service 18 Surefire tests (role policy, MVC 401/403, forged headers, authority isolation, context load). Catalog 23 retained.
- `make backend-verify`: BUILD SUCCESS. Added `UserServicePostgresIT` (1, Testcontainers PostgreSQL 16.10) and retained `CatalogPostgresIT` (8). `UserServiceKeycloakIT` reported 0 tests (JUnit aborted before the method when the Failsafe JVM did not see a usable admin secret); live directory/token proof is `make security-check`.
- `make frontend-test`: storefront 1, admin 4 (including permission-nav). `make frontend-build`: both production builds.
- After Phase 1 code: user-service Flyway V1–V3 on `userdb`/`user_app`; services restarted from source. `make smoke` passed again.
- `make security-check` passed: customer and storefront dual-role get `403` on `/api/v1/admin/me`; admin dual-role `200`; own profile `200` with `profilePersistenceImplemented=true`; customer cannot read another user via admin users API; USER_ADMIN `403` on PLATFORM_ADMIN assign (self and other) and `200`/`202` on CATALOG_EDITOR; ID token, malformed bearer, and `X-User-Id`/`X-Roles` headers are `401`. Role-change note: access token lifespan 300s, no online revocation.
- Frontends served at http://localhost:5173 and http://localhost:5174 (`200`). Interactive click-through in a Cursor browser was not available in this session; PKCE login used the Keycloak hosted page through `oidc-login.sh`, and both SPAs embed `oidc-client-ts` with in-memory token storage.

Not executed: `make apps-up` container mode; forced staff MFA / email verification (realm still has `verifyEmail: false` by design); Playwright browser suite.

## 2026-10-05 — Portal backend removal (this checkout)

Executed from `/mnt/d/GitHub/ecommerce-local-platform` in WSL2 Ubuntu (Java 21.0.12.1, Node 24.20.0 / npm 11.19.0, Docker already running). Infrastructure containers for this project were already up (postgres healthy, Keycloak, Redis, Kafka). No volumes were deleted. The rendered first-boot file `.local/keycloak/ecommerce-local-realm.json` was not rewritten.

- `make check`: passed. Six Maven modules. Realm template has no `storefront-backend` or `admin-portal-backend`; `admin.access` is an `api-gateway` client role composited by the seven staff realm roles.
- `make realm-migrate-portals`: passed against the existing realm. Created `api-gateway/admin.access`, moved that composite on `PLATFORM_ADMIN`, `USER_ADMIN`, `ORDER_MANAGER`, `CATALOG_CREATOR`, `INVENTORY_MANAGER`, `CATALOG_EDITOR`, and `CATALOG_VIEWER`, removed the obsolete audience mappers, and deleted clients `storefront-backend` and `admin-portal-backend`. A second run reported those steps unchanged. Users, passwords, and volumes were not reset.
- `make backend-test`: BUILD SUCCESS. 60 Surefire tests: api-gateway 10, user-service 24, catalog-service 23, inventory/cart/order 1 each.
- `make backend-verify`: BUILD SUCCESS. Same unit tests plus Failsafe `CatalogPostgresIT` (8) and `UserServicePostgresIT` (1) on Testcontainers PostgreSQL. `UserServiceKeycloakIT` reported 0 tests (admin secret not exported into that JVM). Live directory proof is `make security-check`.
- `make frontend-test`: storefront 1, admin 4. `make frontend-build`: both production builds.
- Source processes started after verify: catalog-service on 8094, user-service on 8093, api-gateway on 8090. Neither portal backend is in the reactor or running.
- `make smoke`: passed (realm discovery, gateway health `UP`, public catalog through the gateway, `401` on unauthenticated `/api/v1/admin/me`).
- `make security-check`: passed with new PKCE tokens. Storefront tokens include `api-gateway` and `user-service` audiences and do not include the removed portal clients or `admin.access`. Admin tokens carry `api-gateway` role `admin.access`. Customer and storefront dual-role get `403` on `/api/v1/admin/me`; admin dual-role `200` with structured `profile.profilePersistenceImplemented` and UI permissions `PERM_admin.access` and `PERM_catalog.read`. Catalog viewer can open `/api/v1/admin/me` and gets `403` on user list and on `GET /api/v1/admin/users/{id}/roles`. Cross-customer admin read is `403`. `USER_ADMIN` cannot assign `PLATFORM_ADMIN` (`403`) and can assign `CATALOG_EDITOR` (`200`). ID token, malformed bearer, and forged identity headers are `401`.
- Extra live calls after security-check, tokens not printed: CORS preflight from `http://localhost:5174` returned `200` with `Access-Control-Allow-Origin`; an unknown origin returned `403`. Customer address create returned `201` and echoed `X-Correlation-ID`. The other customer's update of that address returned `404`; the owner update returned `200`. Reusing an Idempotency-Key with a different role-assignment body returned `409` `USER_IDEMPOTENCY_CONFLICT`. Missing token on `/api/v1/store/me` returned `401`.
- Browser: storefront at `http://localhost:5173` completed Authorization Code + PKCE as `customer@example.test` and the profile page showed the application user id, email, and display name. Admin at `http://localhost:5174` completed PKCE as `platform-admin@example.test` (Keycloak reused that account after the form), showed Users and Roles navigation, listed users, and listed realm roles including `admin.access` on staff roles, with the custom-bundle form visible. A full reload drops the in-memory token, which is existing SPA behavior.

Not executed this session: `make apps-up` (containerized application images). Expired and wrong-audience tokens were covered by `AccessTokenRulesTest`, not by a live signed expired token. Inventory, cart, and order services were not started.

## 2026-10-08 — Phase 2 catalog

Executed from `/mnt/d/GitHub/ecommerce-local-platform` in WSL2 (Java 21.0.12.1, Node/npm already used by the frontend workspace).

- `make check`: passed, including `bash -n` on `scripts/local/catalog-check.sh` and Markdown links.
- `npm test` in `frontend`: storefront 4 passed, admin 10 passed (catalog component tests 5). The first admin run failed because renders leaked between tests; both `setup.ts` files now call Testing Library `cleanup`.
- `make frontend-build`: both production builds passed.
- `make backend-verify`: failed in catalog Failsafe. `CatalogPostgresIT.adminReadsIncludeInactiveWhilePublicReadsExcludeTheChain` threw `IllegalAccessException` because Hibernate's proxy could not call public getters declared on package-private `AuditableEntity`. `staleSequentialWritesAndOverlappingUpdatesConflict` expected a version bump from an update that did not change any field. `AuditableEntity` is now public. The IT updates the name before asserting the version. Resume command `./mvnw -B verify -rf :catalog-service`: BUILD SUCCESS. Catalog Surefire 29, `CatalogPostgresIT` 12, inventory/cart/order context tests 1 each. The earlier part of the same verify had already passed api-gateway Surefire 14, user-service Surefire 24, `UserServicePostgresIT` 1, and `UserServiceKeycloakIT` 0 tests.
- Infrastructure: project Postgres and Keycloak were exited. `make infra-up` started them; they then received a fast shutdown (Postgres exit 0, Keycloak 143) with no Docker kill event captured. A later `compose.sh up -d postgres keycloak` stayed up. No volumes were deleted. Unrelated containers were not stopped.
- Source processes: catalog-service 8094, user-service 8093, api-gateway 8090, storefront 5173, admin 5174.
- `make smoke`: passed.
- `make security-check`: passed. The catalog-viewer token's realm roles were `CATALOG_EDITOR,CATALOG_VIEWER` both before and after re-login. That extra editor role is leftover from an earlier role-assignment check; this run did not remove it and did not reset seed users.
- `make catalog-check`: passed. Anonymous product, category, slug, id, variants, and batch reads; price bound without currency `400`; viewer read `200` and create `403`; customer and storefront dual-role admin catalog `403`; editor create `403`; creator create stayed active and creator update/activate `403`; editor update then stale `expectedVersion` `409` `CATALOG_STALE_VERSION`; missing version `400`; SKU change `409` `CATALOG_SKU_IMMUTABLE`; same SKU in another case kept the stored SKU; deactivating the new category hid the product from the public API and left the admin read `200`; direct catalog-service customer call was denied; missing product `404` echoed `X-Correlation-ID`.
- Browser at `http://localhost:5173/catalog` without login listed Books and Electronics and opened Wireless Headphones with two active variants and 79.99 USD. Opening admin first as `http://127.0.0.1:5174` failed callback state because the registered redirect is `localhost`. From `http://localhost:5174`, `catalog-editor@example.test` saw category and product lists, including an inactive category, and no New category action. Two editor tabs loaded Electronics at version 0. The second saved "Electronics session B" (version 1). The first saved "Electronics session A" and showed "Catalog data changed since it was loaded" with the draft still in the form and a correlation reference. Reload and review replaced the draft. The name was then saved back to Electronics (version 2). `catalog-creator@example.test` saw New category, and the Electronics detail said the account can view the category and cannot edit it. `catalog-viewer@example.test` was not used in the browser because that account currently also has `CATALOG_EDITOR`.

Not executed: `make apps-up`. A live connection-refused catalog outage was not produced; the gateway unit test maps `ConnectException` to 503, and `GatewayUnavailableTest` expects 504 `GATEWAY_DOWNSTREAM_TIMEOUT` for `127.0.0.1:1` on this host.

## 2026-10-08 — Phase 3 inventory

Executed from `/mnt/d/GitHub/ecommerce-local-platform` in WSL2 (Java 21.0.12.1). Node/npm are the frontend workspace already used for Phase 2. No volumes were deleted and no passwords were reset or printed.

- `make check`: passed, including `bash -n` on `scripts/local/inventory-check.sh` and Markdown links.
- `make frontend-test`: the first run failed one admin assertion because `INBOUND_RECEIPT` appears both as a reason option and a history cell. The test now looks for that history cell. The rerun passed: storefront 4, admin 16 (inventory screens 5).
- `make frontend-build`: both production builds passed.
- `./mvnw -B -pl inventory-service verify`: the first Failsafe run failed schema validation because `request_fingerprint` was `CHAR(64)` and Hibernate expected `varchar(64)`. The migration column is `VARCHAR(64)`. The rerun passed: Surefire 12, `InventoryPostgresIT` 11 on Testcontainers PostgreSQL 17.6.
- `./mvnw -B verify` (all six modules): BUILD SUCCESS. Surefire: api-gateway 15, user-service 24, catalog-service 29, inventory-service 12, cart-service 1, order-service 1. Failsafe: `UserServiceKeycloakIT` 0 (admin secret not in that JVM), `UserServicePostgresIT` 1, `CatalogPostgresIT` 12, `InventoryPostgresIT` 11.
- Infrastructure: the first `make infra-up` in this session started Postgres and Keycloak and both received a fast shutdown about 15 seconds later (Postgres exit 0, Keycloak 143). A later `make infra-up` stayed up. WSL then returned `Wsl/Service/E_UNEXPECTED`; `wsl --shutdown` recovered the service and stopped containers without deleting volumes. Another `make infra-up` stayed up, and port 55432 accepted connections from WSL.
- Source processes: user-service 8093, catalog-service 8094, inventory-service 8095, api-gateway 8090, admin-web 5174. The first inventory start failed with connection refused on 55432 while Postgres was down. After the stable `infra-up`, all four services started.
- `make smoke`: passed.
- `make security-check`: passed. `catalog-viewer@example.test` still has realm roles `CATALOG_EDITOR,CATALOG_VIEWER` before and after re-login. This run did not remove that role and did not reset the user.
- `make catalog-check`: passed.
- `make inventory-check`: passed. It reconciled `inventory-reader@example.test` without resetting passwords. Reader GET 200 and POST 403; catalog-only, customer, and storefront dual-role GET 403; manager setup stored the catalog SKU rather than the forged body SKU; same-key replay and a changed payload conflict behaved as specified; a second key for the same variant was `INVENTORY_VARIANT_ALREADY_STOCKED`; receipt replay, stale `expectedVersion`, reader history, inactive setup `INVENTORY_CATALOG_INACTIVE`, and a missing stock `404` that echoed `X-Correlation-ID` passed.
- Browser at `http://localhost:5174` as `inventory-manager@example.test`: set up Wireless Headphones black (`HEADPHONES-BLK`) with opening on-hand 4. History showed `OPENING_BALANCE` (4 from 0) at version 0. A second signed-in tab loaded the same stock at version 0. The first tab recorded an inbound receipt of 2 (on hand 6, version 1, history `INBOUND_RECEIPT` then opening). The second tab submitted a correction of -1 and kept that draft, showing "Stock changed since it was loaded" and Reload and review. After reload, the same correction applied: on hand 5, version 2, history `CORRECTION`, `INBOUND_RECEIPT`, `OPENING_BALANCE`.

Not executed: `make apps-up`. Reservations, cart, checkout, payment, Redis, and Kafka were not implemented.

## 2026-10-09 — Inventory transaction boundary

Executed from `/mnt/d/GitHub/ecommerce-local-platform` in WSL2 (Java 21.0.12.1). No volumes were deleted and no secrets were printed. The refactor keeps the stock rules and HTTP contracts. `StockCommandService.setup()` and `adjust()` are `Propagation.NEVER`. `StockTransactionService.writeSetup`, `writeAdjustment`, and `recordSetupConflict` are `Propagation.REQUIRED` and are called through the Spring bean.

- `make check`: passed.
- `./mvnw -B -pl inventory-service verify`: the first Failsafe run failed `InventoryPostgresIT.catalogLookupRunsWithNoActiveTransaction` because the assertion counted `CatalogLookupPort.load()` calls as two. One `load()` performs the variant request and the product request. The assertion now expects one `load()` and two catalog HTTP hits. The rerun passed: Surefire 12, `InventoryPostgresIT` 22 on Testcontainers PostgreSQL 17.6.
- `make backend-verify` (`./mvnw -B verify`): BUILD SUCCESS. Surefire: api-gateway 15, user-service 24, catalog-service 29, inventory-service 12, cart-service 1, order-service 1. Failsafe: `UserServiceKeycloakIT` 0 (admin secret not in that JVM), `UserServicePostgresIT` 1, `CatalogPostgresIT` 12, `InventoryPostgresIT` 22.
- The inventory-service process that had been listening on 8095 was stopped and started again with `make run-service SERVICE=inventory-service`. `/actuator/health` returned `UP`. Gateway 8090, user-service 8093, and catalog-service 8094 were already listening and were left running.
- `make inventory-check`: passed. Reader fixture, PKCE tokens, permission boundaries, setup replay, adjustments and history, and the inactive-catalog policy all passed. Tokens were not printed.

`InventoryPostgresIT` covers atomic setup and adjustment commits, an injected adjustment-insert failure that rolls stock, history, and the command back, same-key setup and adjustment recovery after the failed insert rolls back, SKU and variant uniqueness conflicts stored in a later transaction and replayed, stored stale-version and missing-stock results, lock timeout `INVENTORY_COMMAND_IN_PROGRESS` with the command rolled back, catalog `load()` with no active transaction, writer inserts inside a transaction that is committed before return, a joined writer transaction that is not visible to another connection, and `Propagation.NEVER` rejecting `setup()` and `adjust()` when a transaction is already active.

Not executed: `make apps-up`. Reservations, cart, checkout, payment, Redis, and Kafka were not implemented.

## Still required

- `make apps-up` + `make smoke` in container mode (source-run path is what was verified, including the 2026-10-08 inventory checks and the 2026-10-09 `make inventory-check`).
- Do not treat the catalog-viewer account as a pure viewer until `CATALOG_EDITOR` is removed from it in Keycloak. Inventory read denial uses `inventory-reader@example.test`.

## Verification limits

JWT validation of expiry/issuer/audience is implemented on every resource server; `make security-check` exercised ID tokens, malformed bearers, and forged headers against the running gateway. A dedicated expired-token fixture was not minted. User-service Keycloak adapter live IT (`UserServiceKeycloakIT`) runs only when Keycloak is up and `KEYCLOAK_ADMIN_CLIENT_SECRET` is exported; `make security-check` is the primary live directory/token gate.

The repository deliberately excludes generated secrets, rendered realm credentials, downloaded tooling, node_modules, target, dist, and IDE state.
