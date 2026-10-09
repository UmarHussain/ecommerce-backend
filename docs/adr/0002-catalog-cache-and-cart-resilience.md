# ADR 0002 — Catalog cache-aside and cart resilience

Phase 4 caches public catalog browse responses in this project's Redis and protects the cart and inventory HTTP calls to catalog-service with Resilience4j. Redis is not a second catalog, cart, or stock database.

## Decision

catalog-service owns the public-browse cache. cart-service does not cache catalog responses. `POST /api/v1/catalog/variants/batch` stays uncached and reads PostgreSQL so a cart add or quantity increase cannot accept a SKU that browse still shows from a stale entry.

Spring Cache annotations sit on `PublicBrowseFacade`. Controllers call that bean. The facade calls the domain services. A method on the same class calling another `@Cacheable` method would skip the cache because the call would not go through the Spring proxy. Eviction is a separate `PublicCacheEvictor` bean. Catalog writes publish `PublicCatalogChanged` inside the database transaction. `PublicCatalogCacheListener` handles it `AFTER_COMMIT` and calls the evictor. A rollback does not emit that phase, so it does not evict. If Redis rejects the eviction, the listener logs the correlation id and region and the committed write still succeeds. The region TTL is the bound on that stale window. A concurrent request can repopulate a region before the eviction runs. This design does not claim that the cache and PostgreSQL change atomically.

Cache names are `catalog:v1:categories`, `catalog:v1:products`, `catalog:v1:product-id`, `catalog:v1:product-slug`, and `catalog:v1:variants`. Keys are built after the same normalization as the query: trimmed lowercase search, omitted currency when there is no price bound, sort rendered as `field,direction`, and money with trailing zeros stripped. Invalid input throws and is not stored. Values are JSON for the public DTO types, including records, UUID, `BigDecimal`, and UTC timestamps. There is no default typing and no JPA entity in Redis. Null values are not cached. Exceptions, 401, 403, and transient failures are not cached.

Default TTLs are 60 seconds for categories, 45 seconds for product list and product detail, and 30 seconds for variants. Each write adds up to 5 seconds of jitter through `RedisCacheWriter.TtlFunction`. Admin reads and writes are not cached. `management.health.redis.enabled` is false so a stopped Redis process does not mark catalog-service down. Cache get, put, and evict failures are logged and do not replace a PostgreSQL result or turn a committed write into an error. Connect and command timeouts are 200 milliseconds.

`@Cacheable(sync = true)` calls `Cache.get(key, loader)`. `CoalescingCacheManager` adds 32 in-process stripes and waits at most 2 seconds, then loads the key itself. That coalescing is one JVM. It is not a Redis lock. Another instance can load the same cold key at the same time. The Redis cache manager is initialized with `afterPropertiesSet()` before it is wrapped, so each region uses its declared DTO serializer rather than the default `Object` configuration.

## Resilience

Retry wraps the circuit breaker. Each HTTP attempt goes through the breaker. `CallNotPermittedException` is not retried. Breaker metrics therefore count HTTP attempts, not logical commands. The instances are `cartCatalog` and `inventoryCatalog`.

Both use a count window of 10, a minimum of 8 calls, a 50 percent failure rate, 10 seconds open, and one half-open probe. Only `CatalogTechnicalException` counts as a failure. A missing or inactive SKU, 400, 401, 403, and other domain problems do not open the breaker and are not retried. Connection failures and 502/503 are unavailable and are retried. Timeouts and 504 are timeouts and are retried. 429 is returned as unavailable and is not retried. 500 is not retried. The HTTP client has automatic retries disabled. TimeLimiter is not used, because it would not cancel these blocking HTTP calls.

Connect, pool acquire, and read timeouts are 300, 300, and 800 milliseconds. Retry allows 2 attempts, starts at 80 milliseconds, doubles, and caps the wait at 200 milliseconds with a 0.5 random factor. A rough budget for one logical call is two attempts of connect plus pool plus read, plus the maximum backoff: `2 * (300 + 300 + 800) + 200 = 3000` milliseconds. An open circuit becomes 503. A timeout becomes 504. There is no fallback that treats catalog validation or stock setup as successful.

Cart GET may return the stored display snapshots when the batch refresh fails. The response says the refresh is `UNKNOWN`. It does not invent a price or a stock figure, and it does not overwrite the stored snapshot. Add and increase still require the live uncached lookup.

## Consequences

Browsing can be stale for one TTL after a commit whose eviction failed, or until the next eviction when a request repopulated the region. Cart membership still does not reserve stock. Display prices are not checkout prices. Checkout, in a later phase, has to revalidate the active catalog chain, the price, and the stock.
