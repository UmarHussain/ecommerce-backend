# Phase 4 — Cart, Redis, and resilience

Phase 4 adds a customer cart and a public catalog cache on the existing path: storefront-web → API gateway → the owning service. catalog-service owns catalog data and its Redis browse cache. cart-service owns the cart in PostgreSQL. inventory-service still owns stock. Checkout, reservations, orders, payment, Kafka, and Saga are not implemented.

## Request path

The gateway exposes one cart contract and rewrites it:

| Browser path | Permission | cart-service path |
|---|---|---|
| `GET /api/v1/store/cart` | `cart.read_own` | `GET /api/v1/cart` |
| `PUT /api/v1/store/cart/items/{sku}` | `cart.write_own` | `PUT /api/v1/cart/items/{sku}` |
| `DELETE /api/v1/store/cart/items/{sku}?expectedVersion=` | `cart.write_own` | same path under `/api/v1/cart` |
| `DELETE /api/v1/store/cart?expectedVersion=` | `cart.write_own` | `DELETE /api/v1/cart` |

The item routes are registered before the cart root. Query `expectedVersion` is preserved. The gateway forwards the access token and does not compose the cart. `CART_SERVICE_URL` defaults to `http://localhost:8096` and is `http://cart-service:8096` in Compose. cart-service remains on the Compose `later` profile.

`PUT` sets an absolute quantity from 1 to 99. It is not an increment. The body is `quantity` and `expectedVersion`. A caller-supplied owner, price, currency, or active flag is not part of the contract and is ignored. The first write to a missing cart uses `expectedVersion` 0. The same quantity is a no-op: the version stays put and catalog is not called to validate that line. Clearing an empty cart is also a no-op.

## Ownership and persistence

The cart row is unique on `(owner_issuer, owner_subject)` from the validated token. Email and a browser user id are not the owner. Each line stores the canonical catalog variant id and SKU, a quantity from 1 to 99, and a display snapshot: name, optional image URL, unit price, currency, and snapshot time. A cart holds at most 100 lines. The database checks the quantity and the normalized SKU. A before-insert trigger raises `CART_ITEM_LIMIT` at the 101st line. There is no foreign key into catalog or user data.

`aggregate_version` is a normal column. It is not Hibernate `@Version`, because changing a child would not dirty the cart row. The writer locks the cart, changes the lines, and calls `Cart.advance` only when the contents change. `CartCommandService` is `@Transactional(propagation = NEVER)`. Catalog HTTP runs outside the write. `CartTransactionService` rechecks `expectedVersion` inside the write. A lost response is reconciled by reading the cart again. The command is not replayed with a new version and reported as the original success.

`GET` reads the cart and its lines in one transaction under `PESSIMISTIC_READ` (`SELECT FOR SHARE`). That transaction is not read-only, because PostgreSQL rejects `FOR SHARE` in a read-only transaction. Concurrent first reads that both try to insert the same owner: one insert wins, the other reads the winner. Subtotals use the stored snapshot price times quantity, grouped by currency. Different currencies are not added together.

## Catalog validation

Add and quantity increase call `POST /api/v1/catalog/variants/batch` with no `Authorization` header and no customer or role headers. `X-Correlation-ID` is forwarded when the caller sent one. The batch stays uncached and checks the active variant, product, and category in PostgreSQL. Removal, a lower quantity, and clear do not call catalog. If catalog cannot refresh a GET, the stored snapshots stay, `catalogRefresh` is `UNKNOWN`, and each line is `UNKNOWN`. A SKU missing from a successful batch is `UNAVAILABLE` on that line. The checkout notice on every cart says prices and stock are validated again at checkout and that the cart does not reserve stock.

## Security

cart-service checks the signature, issuer, expiry, access-token type, and audience `cart-service`. It maps only `cart.read_own` and `cart.write_own` from the `cart-service` client. The same names on another client are ignored. `admin.access` does not grant a cart. Reads need `cart.read_own`. Mutations need `cart.write_own`. Unknown cart paths are denied. Staff can open admin-web without receiving a storefront cart token. A storefront token for `dual-role@example.test` carries `CUSTOMER` and the cart roles, and it does not carry `CATALOG_CREATOR`.

Cart responses are mapped with MapStruct. `CartMapperConfig` uses `unmappedTargetPolicy = ERROR`. The service still owns version, ownership, quantity limits, and which snapshot is stored.

## Cache and resilience

The cache and the retry/circuit-breaker policy are recorded in [adr/0002-catalog-cache-and-cart-resilience.md](adr/0002-catalog-cache-and-cart-resilience.md). Short version: public browse DTOs live in Redis for one TTL; cart and stock do not. `@Cacheable` runs only through `PublicBrowseFacade`. `@CacheEvict` runs after commit through `PublicCacheEvictor`. Retry is outside the circuit breaker. Catalog validation has no success fallback.

`make cache-up` starts this project's Redis only. `make cache-down` stops that container and keeps its volume and every other service. Source catalog uses `localhost:56379`. Compose catalog uses host `redis` port `6379`. Catalog does not wait for Redis at startup.

## Storefront

A signed-in customer can add the selected variant, change a quantity, remove a line, and clear the cart. The quantity sent is absolute: the page loads the cart and sends the current quantity plus one. A network failure reloads the cart instead of sending that add again. A 409 keeps the draft and offers reload. Anonymous visitors can browse and are asked to sign in. Cart state is reloaded when the token subject changes. There is no checkout button that places an order.

## Errors

`CART_VALIDATION_FAILED`, `CART_MALFORMED_REQUEST`, `CART_AUTHENTICATION_REQUIRED`, `CART_ACCESS_DENIED`, `CART_STALE_VERSION`, `CART_ITEM_LIMIT`, `CART_LINE_NOT_FOUND`, `CART_SKU_UNAVAILABLE`, `CART_REVALIDATION_REQUIRED`, `CART_CATALOG_UNAVAILABLE`, `CART_CATALOG_TIMEOUT`, `CART_COMMAND_IN_PROGRESS` (`Retry-After: 1`), and `CART_INTERNAL_ERROR`.

A stale `expectedVersion` is 409 and does not change the line. Deleting a missing line is 404. Deleting or clearing without `expectedVersion` is 400.

## Why Redis is not the cart

The cart quantity is the PostgreSQL row locked and versioned in cart-service. Redis holds public catalog DTOs with a short TTL. A cache hit can be stale. A cache miss or a broken payload reads PostgreSQL. Neither result reserves stock or sets the price charged at checkout. Checkout has to read the active catalog chain, the current price, and inventory again.

## What this phase measured

The 2026-10-09 gates are in [verification.md](verification.md). `make cache-check` stored `catalog:v1:categories::all` with a TTL of 62 seconds, then catalog categories still returned 200 while Redis was stopped. `make cart-check` used PKCE tokens and did not print them. The browser pass used `http://localhost:5173` because the registered redirect URI is that origin.
