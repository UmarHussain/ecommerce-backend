# Phase 2 — Catalog business slice

Phase 2 adds catalog administration and public browsing on the existing path: React → API gateway → catalog-service. Inventory, cart, checkout, payment, Redis, Kafka, Saga, Resilience4j, and AWS were not implemented.

## Request path

Storefront calls `/api/v1/store/catalog/...`. The gateway rewrites only the matching public routes onto `/api/v1/catalog/...` and forwards the caller token when one is present. Anonymous browsing stays allowed.

Admin calls `/api/v1/admin/catalog/...`. The gateway requires `PERM_admin.access` and forwards the same path and the caller access token. catalog-service then requires the operation permission.

Each route is an explicit method and path. There is no catch-all proxy. New public routes are product by UUID, product by slug, product variants, and `POST /variants/batch`. New admin routes are the category, product, and variant reads.

Responses are MapStruct mappings from entities to DTOs. Services still own SKU, version, activation, and which variant list is loaded. The mapper does not query relationships. Public and admin callers pass different lists, so an inactive variant cannot appear just because the product mapping loads every variant.

## Security

catalog-service maps only `catalog-service` client roles `catalog.read`, `catalog.create`, `catalog.update`, and `catalog.activate`. The same names on another client do not authorize catalog calls.

| Action | Permission |
|---|---|
| Admin GET | `catalog.read` |
| Create category, product, or variant | `catalog.create` |
| Update | `catalog.update` |
| Activate or deactivate | `catalog.activate` |

`CATALOG_VIEWER` can read. `CATALOG_CREATOR` can read and create. `CATALOG_EDITOR` can read, update, and activate. `PLATFORM_ADMIN` has the staff permissions. A customer token, including a dual-role person signed into the storefront, cannot call admin catalog. Create bodies ignore an `active` field; new rows start active. Activation is only the status command.

The admin UI reads `/api/v1/admin/me`. Viewers see catalog pages without save, create, or activation controls. Creators can create and cannot edit or activate. Editors can edit and activate and cannot create. SKU is read-only after creation.

## Concurrency and SKU

Updates and status commands require `expectedVersion`. A missing or invalid version is `400` `CATALOG_VALIDATION_FAILED`. The service compares that value with the row inside the write transaction. A mismatch is `409` `CATALOG_STALE_VERSION`. JPA `@Version` still rejects a writer that passes the check and then overlaps another commit; that response is `409` `CATALOG_CONCURRENT_MODIFICATION`. The API returns the version after flush. Conflicts are not replayed.

SKU is assigned only while unset. A variant update that normalizes to a different SKU is `409` `CATALOG_SKU_IMMUTABLE` and does not call the domain change. The same SKU with different case is accepted and the stored SKU stays unchanged. A variant cannot be moved to another product. Inactive SKUs stay reserved by the existing unique index. Duplicate slug or SKU is `409` `CATALOG_RESOURCE_CONFLICT`.

## Visibility and price filters

Public category, product, and variant reads require the category, product, and variant to be active together. Admin reads include inactive rows. Admin variant lists are capped at 100, ordered by SKU then id.

Sort fields are `name`, `slug`, `createdAt`, and `updatedAt`, each with `asc` or `desc`, then `id` ascending. Page size is 1–100. The response `sort` string is the requested field and direction.

`minPrice` or `maxPrice` requires `currency`. Amounts are compared in that currency and are not converted. Public price matching uses active variants. Admin price matching includes inactive variants so staff can find a hidden price. A currency without a price bound is ignored.

## Downstream errors

catalog-service problem JSON, status, content type, and correlation headers pass through the gateway. The gateway adds one `X-Correlation-ID` and catalog-service returns the same value. The admin and storefront clients surface it on errors.

If catalog-service cannot be reached, the gateway returns `502` `GATEWAY_DOWNSTREAM_ERROR`, `503` `GATEWAY_DOWNSTREAM_UNAVAILABLE`, or `504` `GATEWAY_DOWNSTREAM_TIMEOUT`. Connect failures map to 503. Timeouts map to 504. Other I/O failures map to 502. Full Resilience4j policies stay in Phase 4. The live blackhole used in the gateway test is a timeout on this host, so that test expects 504; a raw `ConnectException` is covered by the status unit test as 503.

## Screens

Admin catalog lives at `/catalog/categories` and `/catalog/products`, including create and detail routes. Lists support search, filters, and pagination. Details show inactive status. Variant editors show SKU, price, currency, and image URL. A `409` keeps the entered values and offers reload. Loading, empty, validation, 401, 403, 404, conflict, and unavailable states are shown.

Storefront catalog lives at `/catalog` and `/catalog/:productId`. Browsing does not require login. Product details list active variants with image URL, price, and currency. Login, logout, callback, renewal, and profile are unchanged.

## Demo

1. Start infrastructure, then user-service, catalog-service, the API gateway, and both frontends. Commands are in [local-development.md](local-development.md).
2. Open `http://localhost:5173/catalog` without signing in. Search and open a seeded product.
3. Sign in to `http://localhost:5174` as `catalog-viewer@example.test`. Open Catalog. Fields are read-only.
4. Sign in as `catalog-creator@example.test`. Create a category and a product with a variant. Edit and activation controls stay hidden.
5. Sign in as `catalog-editor@example.test`. Edit the category, then save again with the old version. The conflict notice keeps the draft. Reload, then deactivate the category. The storefront product disappears; the admin product remains.
6. On the variant, change the price and send the existing SKU. A different SKU is rejected.

Seed passwords stay in `.env` and are not printed here.

## Checks

Recorded on 2026-10-08 in WSL2 with Java 21.0.12.1. Details are in [verification.md](verification.md) and [progress.md](progress.md).

- `make check` passed.
- Frontend Vitest passed: storefront 4, admin 10. `make frontend-build` passed for both SPAs.
- The first `make backend-verify` failed in `CatalogPostgresIT` (Hibernate could not call getters on the package-private mapped superclass, and a no-op update did not bump `@Version`). After `AuditableEntity` was made public and the stale-write test changed a field, `./mvnw -B verify -rf :catalog-service` passed, including `CatalogPostgresIT` 12 tests.
- With PostgreSQL and Keycloak running, source-run catalog-service, user-service, and api-gateway: `make smoke`, `make security-check`, and `make catalog-check` passed. `make catalog-check` uses Authorization Code + PKCE and does not print tokens.
- Browser: anonymous storefront browsing and product variants; admin editor lists, a two-tab stale save that kept the draft and showed the conflict, then reload; creator sees New category and a read-only category detail. The seeded Electronics name was restored afterward. Its version is now 2.

`make apps-up` was not run. Resilience4j, inventory, cart, checkout, payment, Redis, and Kafka were not implemented.

## Limits

- Admin variant lists stop at 100 rows.
- Price filters compare one currency. They do not convert.
- Gateway unavailability is a timeout and status mapping, not a Resilience4j circuit breaker.
- Overlapping-write tests accept either `CATALOG_STALE_VERSION` or the optimistic-lock conflict, and require exactly one success.
- Browser verification is recorded separately from unit tests and HTTP checks. A mocked frontend test is not a browser login.
