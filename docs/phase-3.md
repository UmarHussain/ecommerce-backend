# Phase 3 — Inventory administration

Phase 3 adds stock setup, adjustments, and history on the existing path: React admin-web → API gateway → inventory-service. Catalog-service still owns SKU identity and product metadata. Cart, checkout, payment, Redis, Kafka, Saga, Resilience4j, reservations, and public stock endpoints were not implemented.

## Boundaries

One stock row exists per catalog variant. There are no warehouses. Quantities are integers. `reserved` starts at 0 and these APIs do not write it. `available` is `onHand - reserved`. There is no hard delete and no API that sets `onHand` or `reserved` to an absolute value. A result below zero, or an `onHand` that would fall below `reserved`, is rejected. Quantities are not clamped.

Inventory does not query `catalogdb` and has no foreign key into it. The gateway forwards the caller token and does not compose stock responses or decide stock rules.

## Request path

Admin calls `/api/v1/admin/inventory/...`. The gateway requires `PERM_admin.access` and forwards the same path and the caller access token. inventory-service then requires the operation permission. Each route is an explicit method and path:

| Method and path | Permission |
|---|---|
| `GET /api/v1/admin/inventory/stock-items` | `inventory.read` |
| `GET /api/v1/admin/inventory/stock-items/{id}` | `inventory.read` |
| `GET /api/v1/admin/inventory/stock-items/{id}/adjustments` | `inventory.read` |
| `POST /api/v1/admin/inventory/stock-items` | `inventory.adjust` |
| `POST /api/v1/admin/inventory/stock-items/{id}/adjustments` | `inventory.adjust` |

An unknown method on these paths is not forwarded. inventory-service denies every other authenticated inventory path.

## Catalog check at setup

Setup loads the variant, then its product, from the catalog admin endpoints before the inventory write transaction. The adapter forwards `Authorization` and `X-Correlation-ID` and does not log the token. Connect, response, and connection-pool acquire timeouts are 2 seconds. Automatic HTTP retries are off. The base URL is `CATALOG_SERVICE_URL` (`http://localhost:8094` from source, `http://catalog-service:8094` in Compose).

The stored SKU is the catalog SKU. A SKU, name, or active flag sent in the setup body is ignored. Setup is rejected when the variant, product, or category is inactive (`409` `INVENTORY_CATALOG_INACTIVE`). A missing variant is `404` `INVENTORY_CATALOG_VARIANT_NOT_FOUND`. Catalog `401` and `403` become `INVENTORY_AUTHENTICATION_REQUIRED` and `INVENTORY_CATALOG_FORBIDDEN`. Other catalog failures, including an unreadable body, are `503` `INVENTORY_CATALOG_UNAVAILABLE`. Timeouts, including a PostgreSQL lock wait (`55P03`), are `504` `INVENTORY_CATALOG_TIMEOUT`.

`catalog.read` is required for setup. inventory-service reads it from the caller token's `catalog-service` resource access. It is not turned into an inventory authority. Missing `catalog.read` is `403` `INVENTORY_CATALOG_READ_REQUIRED` and does not call catalog.

An adjustment of an existing stock row does not call catalog again. Deactivating the catalog item later does not delete the stock row or its history, and an adjustment does not make that item sellable. Product and variant names stored on the stock row are snapshots taken at setup.

## Security

inventory-service validates signature, issuer, expiry, access-token type, and audience `inventory-service`. It maps only `inventory.read` and `inventory.adjust` from the `inventory-service` client. The same names on another client are ignored.

`INVENTORY_MANAGER` has `admin.access`, `catalog.read`, `inventory.read`, and `inventory.adjust`. `INVENTORY_READER` has `admin.access` and `inventory.read` only, so it can list stock and history and cannot set up or adjust stock. `PLATFORM_ADMIN` has the inventory write permissions. A catalog-only token, a customer token, and a storefront dual-role token do not.

The admin UI shows Inventory when `PERM_inventory.read` is present and hides setup and adjustment without `PERM_inventory.adjust`. If adjust is present without `catalog.read`, the setup screen explains the limitation and does not submit. Those controls are not the enforcement.

## Persistence and commands

Tables live in schema `inventory` on `inventorydb`, accessed as `inventory_app`. Flyway `V1` creates `stock_item`, `stock_adjustment`, and `inventory_command`. Checks keep `on_hand >= 0`, `reserved >= 0`, and `reserved <= on_hand`. A trigger rejects updates and deletes of `stock_adjustment`.

Setup and adjustment require `Idempotency-Key` matching `^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$`. A command is unique for issuer, subject, operation (`SETUP` or `ADJUST`), and key. The fingerprint is the SHA-256 hex of the operation, stock id, variant id, quantity, reason, normalized note, normalized reference, and expected version. Blank note and reference normalize to empty. The same key, scope, and fingerprint returns the stored status, content type, body, and `Location`, and does not write a second history row. A different payload for the same key is `409` `INVENTORY_IDEMPOTENCY_CONFLICT`. A completed replay is resolved before catalog lookup and before `expectedVersion` is applied.

`StockCommandService` coordinates the command and does not own the write transaction. `setup()` and `adjust()` are `@Transactional(propagation = NEVER)`, so validation, fingerprinting, completed replay, the `catalog.read` check, and the catalog HTTP call cannot join a surrounding transaction. The completed-command read uses its own short repository transaction. Replay still happens before catalog lookup and before `expectedVersion` is applied.

`StockTransactionService` is the Spring bean that writes. `writeSetup`, `writeAdjustment`, and `recordSetupConflict` are `@Transactional(propagation = REQUIRED)` and are called through that bean. The coordinator has no transaction, so `REQUIRED` starts the write. The write sets PostgreSQL `lock_timeout` to 2 seconds with `set_config(..., true)`, then locks or inserts the command. An adjustment also locks the stock row with `SELECT FOR UPDATE` and compares `expectedVersion` in that transaction. JPA `@Version` is a second guard. History and the completed command result commit together. Commit or rollback finishes before the coordinator continues. There is no `TransactionTemplate`, no JVM lock, and no Redis lock.

A unique-constraint failure rolls that write back. After it has ended, a collision on `uq_inventory_command_scope` re-reads the winner and replays it. A setup collision on `uq_stock_item_catalog_variant` or `uq_stock_item_sku` is stored as `409` `INVENTORY_VARIANT_ALREADY_STOCKED` by `recordSetupConflict` in a new `REQUIRED` transaction, so two different setup keys cannot add opening stock twice for one variant. A lock timeout becomes `409` `INVENTORY_COMMAND_IN_PROGRESS` with `Retry-After: 1`. Missing stock, a stale version, a bad reason or sign, overflow, and an invariant failure are stored on the command by `finishProblem` and commit with that command record. Database failures still roll the whole write back.

Opening stock, including zero, writes one `OPENING_BALANCE` history row with `onHand` before 0. A zero adjustment is stored as `400` and does not change `on_hand` or add history.

## Reasons

| Reason | Where | Sign |
|---|---|---|
| `OPENING_BALANCE` | setup only | delta >= 0, including zero |
| `INBOUND_RECEIPT` | adjustment | delta > 0 |
| `CORRECTION` | adjustment | nonzero, either sign |
| `DAMAGE_LOSS` | adjustment | delta < 0 |
| `RETURN` | adjustment | delta > 0 |

`initialOnHand` is 0 through 1,000,000,000. Adjustment addition uses exact integer arithmetic. Overflow is `400` `INVENTORY_QUANTITY_OVERFLOW`.

## Reads

Stock search matches a UUID against the stock id or the catalog variant id. Any other search matches SKU. Sort fields are `sku`, `onHand`, `createdAt`, and `updatedAt`, then `id` ascending. The default is `sku,asc`. History sorts by `createdAt`, then `id` in the same direction. The default is `createdAt,desc`. Page size is 1–100. Responses are MapStruct mappings. The service still owns validation, identity, version, and which rows are returned.

## Errors

Problem JSON uses `INVENTORY_VALIDATION_FAILED`, `INVENTORY_MALFORMED_REQUEST`, `INVENTORY_QUANTITY_OVERFLOW`, `INVENTORY_AUTHENTICATION_REQUIRED`, `INVENTORY_ACCESS_DENIED`, `INVENTORY_CATALOG_READ_REQUIRED`, `INVENTORY_CATALOG_FORBIDDEN`, `INVENTORY_NOT_FOUND`, `INVENTORY_CATALOG_VARIANT_NOT_FOUND`, `INVENTORY_STALE_VERSION`, `INVENTORY_STOCK_INVARIANT`, `INVENTORY_VARIANT_ALREADY_STOCKED`, `INVENTORY_IDEMPOTENCY_CONFLICT`, `INVENTORY_COMMAND_IN_PROGRESS`, `INVENTORY_CATALOG_INACTIVE`, `INVENTORY_CATALOG_UNAVAILABLE`, `INVENTORY_CATALOG_TIMEOUT`, and `INVENTORY_INTERNAL_ERROR`.

A stale adjustment keeps the admin draft and offers Reload and review. It clears the idempotency key so the next intentional command is new. A browser failure with no HTTP status keeps the same key and body for Retry the same command. The submit button stays disabled while a request is in flight.

Example stale adjustment:

```json
{ "delta": 2, "reason": "INBOUND_RECEIPT", "expectedVersion": 0 }
```

```json
{ "title": "Conflict", "status": 409, "code": "INVENTORY_STALE_VERSION", "detail": "Stock changed since it was loaded", "correlationId": "..." }
```

## Screens

`/inventory` lists stock. `/inventory/new` sets up a catalog variant. `/inventory/:stockId` shows on hand, reserved, available, version, the adjustment form, and history. Available is the value returned by the API.

## Demo

1. Start infrastructure, then user-service, catalog-service, inventory-service, the API gateway, and admin-web. Commands are in [local-development.md](local-development.md).
2. Sign in at `http://localhost:5174` as `inventory-manager@example.test`. Open Inventory, set up a variant, then record an inbound receipt and a correction. History shows the opening row and both adjustments.
3. Open the same stock in two sessions. Apply one adjustment, then submit the other with the old version. The second form keeps the draft and shows the conflict. Reload before sending a new command.
4. Sign in as `inventory-reader@example.test`. The list and history are visible. Setup and Apply adjustment are not.

Seed passwords stay in `.env` and are not printed here. `make inventory-check` performs the same API path with Authorization Code + PKCE.

## Checks

Recorded on 2026-10-08 in WSL2 with Java 21.0.12.1. Details are in [verification.md](verification.md) and [progress.md](progress.md).

- `make check`, `make frontend-test` (storefront 4, admin 16), and `make frontend-build` passed. The first admin test run failed on a duplicate `INBOUND_RECEIPT` label; the assertion now uses the history cell.
- `./mvnw -B verify` passed after `request_fingerprint` was changed from `CHAR(64)` to `VARCHAR(64)`. Inventory Surefire 12 and `InventoryPostgresIT` 11. Gateway 15, user-service 24 plus `UserServicePostgresIT` 1, catalog 29 plus `CatalogPostgresIT` 12.
- With PostgreSQL and Keycloak running, source-run user-service, catalog-service, inventory-service, and the API gateway: `make smoke`, `make security-check`, `make catalog-check`, and `make inventory-check` passed. `make inventory-check` uses Authorization Code + PKCE and does not print tokens.
- Browser: inventory manager set up `HEADPHONES-BLK` at on-hand 4, recorded an inbound receipt of 2, and a second session's correction of -1 kept the draft on conflict. Reload and review, then the correction, left on-hand 5 and three history rows.

`make apps-up` was not run. Cart, checkout, payment, Redis, Kafka, and reservations were not implemented.

On 2026-10-09 the write path moved from `TransactionTemplate` to `StockTransactionService`. `make check`, `make backend-verify`, and `make inventory-check` passed after inventory-service was restarted on 8095. `InventoryPostgresIT` is 22 tests. The first run of that class failed a catalog-call count and was corrected. Details are in [verification.md](verification.md).

## Limits

One pool per variant. No reservation API, cart, checkout, payment, or public stock endpoint. Catalog deactivation does not remove stock. Phase 4 was not started.
