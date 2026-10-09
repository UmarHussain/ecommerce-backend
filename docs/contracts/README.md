# Contract backlog

Implemented: catalog OpenAPI annotations and user-service OpenAPI. The gateway rewrites the storefront paths below and forwards admin paths unchanged. There is no portal backend.

| Browser path | Gateway auth | Destination |
|---|---|---|
| `GET /api/v1/store/catalog/products` | public | catalog-service `GET /api/v1/catalog/products` |
| `GET /api/v1/store/catalog/products/{id}` | public | catalog-service `GET /api/v1/catalog/products/{id}` |
| `GET /api/v1/store/catalog/products/slug/{slug}` | public | catalog-service `GET /api/v1/catalog/products/slug/{slug}` |
| `GET /api/v1/store/catalog/products/{id}/variants` | public | catalog-service `GET /api/v1/catalog/products/{id}/variants` |
| `POST /api/v1/store/catalog/variants/batch` | public | catalog-service `POST /api/v1/catalog/variants/batch` |
| `GET /api/v1/store/catalog/categories` | public | catalog-service `GET /api/v1/catalog/categories` |
| `GET /api/v1/store/cart` | `PERM_cart.read_own` | cart-service `GET /api/v1/cart` |
| `PUT /api/v1/store/cart/items/{sku}` | `PERM_cart.write_own` | cart-service `PUT /api/v1/cart/items/{sku}` |
| `DELETE /api/v1/store/cart/items/{sku}` | `PERM_cart.write_own` | cart-service, same item path; query `expectedVersion` is required |
| `DELETE /api/v1/store/cart` | `PERM_cart.write_own` | cart-service `DELETE /api/v1/cart`; query `expectedVersion` is required |
| `GET/PATCH /api/v1/store/me` | `PERM_profile.read_own` / `update_own` | user-service `/api/v1/users/me` |
| `POST /api/v1/store/me/addresses` and `PUT/DELETE .../{addressId}` | `PERM_profile.update_own` | user-service `/api/v1/users/me/addresses...` |
| `GET /api/v1/admin/me` | `PERM_admin.access` | user-service. Profile is a JSON object, not a string |
| `GET/POST /api/v1/admin/users` | coarse `admin.access`, then `user.read` / `user.create` | user-service, same path |
| `GET /api/v1/admin/users/{id}/roles` | coarse `admin.access`, then `role.read` | user-service, same path |
| `POST /api/v1/admin/users/{id}/staff` | `user.manage_staff` | user-service |
| `POST /api/v1/admin/users/{id}/roles` | `role.assign` | user-service |
| `GET/POST /api/v1/admin/roles` | `role.read` / `role.manage` | user-service |
| `GET /api/v1/admin/operations/{id}` | create, manage-staff, assign, or manage | user-service |
| `GET /api/v1/admin/catalog/categories` and `GET .../categories/{id}` | coarse `admin.access`, then `catalog.read` | catalog-service, same path |
| `GET /api/v1/admin/catalog/products`, `GET .../products/{id}`, `GET .../products/{id}/variants`, `GET .../variants/{id}` | coarse `admin.access`, then `catalog.read` | catalog-service, same path |
| `POST/PUT/PATCH /api/v1/admin/catalog/...` | coarse `admin.access`, then catalog create/update/activate | catalog-service, same path |
| `GET /api/v1/admin/inventory/stock-items` and `GET .../stock-items/{id}` | coarse `admin.access`, then `inventory.read` | inventory-service, same path |
| `GET /api/v1/admin/inventory/stock-items/{id}/adjustments` | coarse `admin.access`, then `inventory.read` | inventory-service, same path |
| `POST /api/v1/admin/inventory/stock-items` | coarse `admin.access`, then `inventory.adjust` plus caller `catalog.read` | inventory-service, same path. `201` and `Location` |
| `POST /api/v1/admin/inventory/stock-items/{id}/adjustments` | coarse `admin.access`, then `inventory.adjust` | inventory-service, same path |

### Catalog filters and versions

Public product search accepts `search`, `category` (slug), `minPrice`, `maxPrice`, `currency`, `page`, `size` (1–100), and `sort` (`name`, `slug`, `createdAt`, or `updatedAt`, then `asc` or `desc`). Results are ordered by that field and then by `id` ascending. A price bound requires `currency`. The comparison uses the stored amount of active variants in that currency and does not convert currencies.

Admin category search accepts `search`, optional `active`, `page`, `size`, and the same sort allowlist. Admin product search adds `categoryId`, optional `active`, and the same price rule. Admin price matching includes inactive variants. Admin variant lists return at most 100 rows ordered by SKU then id, including inactive variants. Public reads still require an active product, active category, and active variant together.

Creates use the original request bodies and always start active. Updates and status commands require `expectedVersion`. A missing or negative version is `400` `CATALOG_VALIDATION_FAILED`. A version that does not match the row loaded in the write transaction is `409` `CATALOG_STALE_VERSION`. A race after that check is `409` `CATALOG_CONCURRENT_MODIFICATION`. A different SKU on variant update is `409` `CATALOG_SKU_IMMUTABLE`. Duplicate slug or SKU remains `409` `CATALOG_RESOURCE_CONFLICT`.

Example stale update:

```json
{ "name": "Electronics", "slug": "electronics", "expectedVersion": 0 }
```

```json
{ "title": "Conflict", "status": 409, "code": "CATALOG_STALE_VERSION", "detail": "Catalog data changed since it was loaded; reload and review the current values", "correlationId": "..." }
```

When catalog-service cannot be reached, the gateway returns `503` `GATEWAY_DOWNSTREAM_UNAVAILABLE`, `504` `GATEWAY_DOWNSTREAM_TIMEOUT`, or `502` `GATEWAY_DOWNSTREAM_ERROR`, with the same correlation id. A response catalog-service already wrote, including problem JSON, is forwarded unchanged. The same gateway mapping applies when inventory-service or cart-service cannot be reached.

### Cart

`PUT /api/v1/store/cart/items/{sku}` body is `{ "quantity": 1, "expectedVersion": 0 }`. Quantity is absolute, from 1 to 99. The service stores the catalog SKU, variant id, and price. A stale `expectedVersion` is `409` `CART_STALE_VERSION` and leaves the line unchanged. Details and the other cart error codes: [phase-4.md](../phase-4.md).

### Inventory setup, adjustments, and history

Setup and adjustment require header `Idempotency-Key`. The same key, caller, operation, and payload replays the stored status and body. A different payload is `409` `INVENTORY_IDEMPOTENCY_CONFLICT`.

Stock list query parameters are `search` (SKU, or a UUID matching the stock id or catalog variant id), `page`, `size` (1–100), and `sort` (`sku`, `onHand`, `createdAt`, or `updatedAt`, then `asc` or `desc`, with `id` ascending as the tie-break). History accepts `page`, `size`, and `sort` (`createdAt,asc` or `createdAt,desc`, then `id` in that direction). Defaults are `sku,asc` and `createdAt,desc`.

Setup body: `catalogVariantId`, `initialOnHand` (0 through 1000000000), `reason` (`OPENING_BALANCE`), optional `note` and `reference`. The service stores the catalog SKU. Adjustment body: nonzero `delta`, `reason` (`INBOUND_RECEIPT`, `CORRECTION`, `DAMAGE_LOSS`, or `RETURN`), `expectedVersion`, optional `note` and `reference`. `reserved` is not a request field. `available` on the response is `onHand - reserved`.

| Condition | Status | Code |
|---|---|---|
| Missing or invalid quantity, reason, version, or idempotency key | 400 | `INVENTORY_VALIDATION_FAILED` or `INVENTORY_MALFORMED_REQUEST` |
| Integer overflow | 400 | `INVENTORY_QUANTITY_OVERFLOW` |
| Missing token or wrong token type | 401 | `INVENTORY_AUTHENTICATION_REQUIRED` |
| Missing inventory permission | 403 | `INVENTORY_ACCESS_DENIED` |
| Setup without `catalog.read` | 403 | `INVENTORY_CATALOG_READ_REQUIRED` |
| Catalog rejects the forwarded token | 403 | `INVENTORY_CATALOG_FORBIDDEN` |
| Unknown stock id | 404 | `INVENTORY_NOT_FOUND` |
| Unknown catalog variant | 404 | `INVENTORY_CATALOG_VARIANT_NOT_FOUND` |
| `expectedVersion` does not match | 409 | `INVENTORY_STALE_VERSION` |
| Result would be negative or below reserved | 409 | `INVENTORY_STOCK_INVARIANT` |
| Variant already has stock | 409 | `INVENTORY_VARIANT_ALREADY_STOCKED` |
| Same idempotency key, different payload | 409 | `INVENTORY_IDEMPOTENCY_CONFLICT` |
| Same key still in progress after lock timeout | 409 | `INVENTORY_COMMAND_IN_PROGRESS` (`Retry-After: 1`) |
| Variant, product, or category inactive at setup | 409 | `INVENTORY_CATALOG_INACTIVE` |
| Catalog cannot be read | 503 | `INVENTORY_CATALOG_UNAVAILABLE` |
| Catalog or lock wait timed out | 504 | `INVENTORY_CATALOG_TIMEOUT` |

Example stale adjustment:

```json
{ "delta": 2, "reason": "INBOUND_RECEIPT", "expectedVersion": 0 }
```

```json
{ "title": "Conflict", "status": 409, "code": "INVENTORY_STALE_VERSION", "detail": "Stock changed since it was loaded", "correlationId": "..." }
```

Full behavior: [phase-3.md](../phase-3.md).

`GET /api/v1/admin/users/{id}/roles` is authorized with `role.read` and is registered before the single-segment user read rule, so `user.read` does not grant role reads.

`GET /api/v1/admin/me` returns `subject`, `issuer`, `email`, `permissions`, `realmRoles`, and `profile`. `profile` is the profile object from `ProfileService` (previously a raw JSON string, or null when the portal's downstream call failed). Failures from profile initialization are returned as user-service errors. The admin UI reads `permissions` only.

Uncertain Keycloak outcomes return `202` with `Location: /api/v1/admin/operations/{id}`. Problem Details carry `code` and `correlationId`. JPA entities are not exposed.

Future Kafka envelopes and Saga transitions are specified in MASTER_PROMPT.md; no event contracts are claimed implemented yet.
