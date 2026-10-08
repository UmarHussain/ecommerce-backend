# inventory-service

Admin stock setup, adjustments, and history. See [docs/phase-3.md](../../docs/phase-3.md), [docs/service-status.md](../../docs/service-status.md), and [docs/local-development.md](../../docs/local-development.md).

Security: JWT signature, issuer, expiry, audience `inventory-service`, and Keycloak access-token type. Only `inventory.read` and `inventory.adjust` from the `inventory-service` client become authorities. Setup also requires `catalog.read` on the caller token's `catalog-service` client. Unknown routes are denied.

Persistence is Flyway on `inventorydb` / schema `inventory` as `inventory_app`. Catalog identity is loaded over HTTP at setup, outside the stock write. `StockCommandService` coordinates validation, replay, and recovery. `StockTransactionService` commits stock, history, and the command result in one transaction. Adjustments do not call catalog again. Reservations, checkout, and public stock endpoints are not in this service.
