# Running the services from IntelliJ IDEA

Prerequisite: infrastructure is up (`bash infrastructure/local/manual-startup/infra.sh up`) and `make bootstrap` has produced `.env`.

## Open the project

1. `File > Open...` → select `backend/pom.xml` → *Open as Project*. The parent POM imports the current modules (gateway, user, catalog, inventory, cart, order).
2. `File > Project Structure > SDK`: Java 21. If you work from the WSL2 filesystem, use IntelliJ's WSL support (`\\wsl$\Ubuntu\...`) and a WSL JDK; from `D:\GitHub\...` a Windows JDK 21 also works because the services only talk to `localhost` ports published by Docker.
3. Maven settings: leave the bundled Maven or point to the wrapper; both work.

## Common settings for every run configuration

`Run > Edit Configurations... > + > Spring Boot` (or *Application*):

| Field | Value |
|---|---|
| Module / classpath | the service module (`-cp <module>`), Java 21 |
| Working directory | `$MODULE_WORKING_DIR$` |
| Environment variables | only what the table below lists; all services default to `http://localhost:8180/realms/ecommerce-local` as issuer and the matching JWKS URL, and to `localhost` ports for downstream services |

PostgreSQL from the IDE is `localhost:55432`. Use the database, user, and schema for that service. The password is the named variable in `.env`.

| Database | User | Password variable | Schema |
|---|---|---|---|
| `catalogdb` | `catalog_app` | `CATALOG_DB_PASSWORD` | `catalog` |
| `userdb` | `user_app` | `USER_DB_PASSWORD` | `user` |
| `inventorydb` | `inventory_app` | `INVENTORY_DB_PASSWORD` | `inventory` |
| `cartdb` | `cart_app` | `CART_DB_PASSWORD` | `cart` |
| `orderdb` | `order_app` | `ORDER_DB_PASSWORD` | `order` |
| `paymentdb` | `payment_app` | `PAYMENT_DB_PASSWORD` | `payment` |
| `keycloakdb` | `keycloak_app` | `KEYCLOAK_DB_PASSWORD` | `keycloak` |

Passwords come from `.env`. Options:

- Paste the value into the run configuration's environment variables (run configurations live in the git-ignored `.idea/`).
- Install the *EnvFile* plugin and point it at the repository `.env`; add the service-specific variables on top.
- Or skip IntelliJ for that service and use `make run-service SERVICE=<name>`, which sources `.env` for you.

## Per-service run configurations

Start in this order; each one is independent except where noted.

| # | Service | Main class | Port | Environment variables | Depends on | What works today |
|---|---|---|---|---|---|---|
| 1 | catalog-service | `com.umar.ecommerce.catalog.CatalogServiceApplication` | 8094 | `DATABASE_URL=jdbc:postgresql://localhost:55432/catalogdb`, `DATABASE_USERNAME=catalog_app`, `DATABASE_PASSWORD=<CATALOG_DB_PASSWORD from .env>`, `DATABASE_SCHEMA=catalog`, `SPRING_PROFILES_ACTIVE=local` | PostgreSQL, Keycloak | Implemented: Flyway migrations, local seed data (profile `local`), products/categories CRUD, search, permission checks |
| 2 | user-service | `com.umar.ecommerce.user.UserServiceApplication` | 8093 | `DATABASE_URL=jdbc:postgresql://localhost:55432/userdb`, `DATABASE_USERNAME=user_app`, `DATABASE_PASSWORD=<USER_DB_PASSWORD from .env>`, `DATABASE_SCHEMA=user`, `SPRING_PROFILES_ACTIVE=local`, `KEYCLOAK_ADMIN_CLIENT_SECRET=<USER_SERVICE_ADMIN_SECRET from .env>` | PostgreSQL, Keycloak | Profiles, admin principal, staff and role administration |
| 3 | api-gateway | `com.umar.ecommerce.gateway.ApiGatewayApplication` | 8090 | optional `USER_SERVICE_URL=http://localhost:8093`, `CATALOG_SERVICE_URL=http://localhost:8094` | user-service, catalog-service | Explicit routes and rewrites to those services |
| 4 | inventory-service | `com.umar.ecommerce.inventory.InventoryServiceApplication` | 8095 | none today | Keycloak | Secured shell + health; no business API (Phase 3) |
| 5 | cart-service | `com.umar.ecommerce.cart.CartServiceApplication` | 8096 | none today (Redis at `localhost:56379` later) | Keycloak | Secured shell + health (Phase 4) |
| 6 | order-service | `com.umar.ecommerce.order.OrderServiceApplication` | 8097 | none today (Kafka at `localhost:59092` later) | Keycloak | Secured shell + health (Phase 5) |

Override a port with `SERVER_PORT=<port>` only if you also change the dependent URL variables and the Vite proxy.

Health for each service: `http://localhost:<port>/actuator/health`. Swagger UI on the MVC services (not the gateway): `http://localhost:<port>/swagger-ui/index.html`.

### Compound configuration

`Run > Edit Configurations... > + > Compound`, add catalog-service, user-service, and api-gateway (and more as they gain behaviour). IntelliJ starts them in parallel; the gateway connects downstream on first request.

### Debugging

Use *Debug* instead of *Run* on any configuration; no extra flags are needed because the process runs in the IDE. Java services started with `make run-service` can be attached remotely by adding `-Dspring-boot.run.jvmArguments=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005` to the Maven command.

## Frontends

IntelliJ (Ultimate) can run npm scripts: `frontend/package.json` → right-click → *Show npm Scripts*, or create *npm* run configurations with package.json `frontend/storefront-web/package.json`, command `run`, script `dev` (same for `admin-web`). From a terminal:

```bash
make frontend-install                   # once
make run-frontend APP=storefront-web    # http://localhost:5173
make run-frontend APP=admin-web         # http://localhost:5174
```

Vite proxies `/api` to the gateway on 8090. Both apps sign in with Authorization Code + PKCE. Storefront shows the caller's profile. Admin shows users and roles when the permission summary allows it.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `Connection refused` to `localhost:55432` or `8180` | Infrastructure not running: `bash infrastructure/local/manual-startup/infra.sh status` / `up` |
| catalog-service: `FATAL: password authentication failed for user "catalog_app"` | `DATABASE_PASSWORD` does not match `CATALOG_DB_PASSWORD` in `.env`, or `.env` was regenerated after the database volume was created (passwords are set on first boot only) |
| 401 from every protected endpoint | Expected without a token. Use `make smoke` for the public catalog path and `make security-check` for issued tokens |
| Gateway `503`/`Connection refused` for `/api/v1/store/catalog/...` | catalog-service (8094) is not up |
| Gateway `503`/`Connection refused` for `/api/v1/store/me` or `/api/v1/admin/...` | user-service (8093) is not up |
| Port already in use on 8090–8097 | `make apps-up` containers are running, or another IntelliJ instance; stop one side |
| Realm changes in the template are not visible | Keycloak imports only a missing realm; edit via the admin console (see `docs/local-development.md`, "Realm lifecycle") |
