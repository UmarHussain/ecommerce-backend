# inventory-service

Admin stock setup, adjustments, and history. See [docs/phase-3.md](../../docs/phase-3.md), [docs/service-status.md](../../docs/service-status.md), and [docs/local-development.md](../../docs/local-development.md).

Security: JWT signature, issuer, expiry, audience `inventory-service`, and Keycloak access-token type. Only `inventory.read` and `inventory.adjust` from the `inventory-service` client become authorities. Setup also requires `catalog.read` on the caller token's `catalog-service` client. Unknown routes are denied.

Persistence is Flyway on `inventorydb` / schema `inventory` as `inventory_app`. Catalog identity is loaded over HTTP at setup, outside the stock write. `StockCommandService` coordinates validation, replay, and recovery. `StockTransactionService` commits stock, history, and the command result in one transaction. Adjustments do not call catalog again. Admin routes stay the only HTTP API.

Checkout reservations are Kafka commands on `checkout.inventory.commands` (group `inventory-checkout`), not HTTP. Outcomes go to `checkout.inventory.outcomes`. A command reserves every line or none, and the same `commandId` does not apply a stock effect twice. Pre-payment reservations expire from `ACTIVE` after `inventory.reservation.pre-payment-ttl` (default 2 minutes). `CHECKOUT_HELD` is left for payment. The outbox row is written in the stock transaction and marked sent only after the broker acknowledges it.
