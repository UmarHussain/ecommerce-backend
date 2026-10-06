# Local infrastructure connections

Host connection details for the services in `compose.yaml`. Start them before connecting. From the repository root in WSL2:

```bash
make bootstrap    # creates .env if it is missing; never prints or overwrites secrets
make infra-up     # PostgreSQL on 55432 and Keycloak on 8180
```

IDE-only infrastructure (PostgreSQL, Keycloak, Redis, and Kafka, same containers) is `bash infrastructure/local/manual-startup/infra.sh up`. See [manual-startup/README.md](manual-startup/README.md).

Published ports bind to `127.0.0.1` only. Passwords are in the git-ignored root `.env`. Read a value when you need it; do not paste it into chat or commit it:

```bash
grep '^POSTGRES_PASSWORD=' .env
grep '^CATALOG_DB_PASSWORD=' .env
```

Inside the Compose network, use the service name and the container port (`postgres:5432`, `keycloak:8080`, `redis:6379`, `kafka:9092`). From the host, IntelliJ, or a GUI client, use `localhost` and the published port below.

## PostgreSQL 16

| Setting | From the host | From another container |
|---|---|---|
| Host | `127.0.0.1` or `localhost` | `postgres` |
| Port | `55432` | `5432` |
| Image | `postgres:16.10` | same |

`make infra-up` must be running and the `postgres` container healthy before a client connects.

### Bootstrap superuser

Use this account for ad-hoc inspection of any database. Application services do not use it.

| Setting | Value |
|---|---|
| Database | `postgres` |
| User | `platform_bootstrap` |
| Password | `POSTGRES_PASSWORD` in `.env` |

```bash
set -a; source .env; set +a
psql "postgresql://platform_bootstrap:${POSTGRES_PASSWORD}@127.0.0.1:55432/postgres"
```

JDBC URL: `jdbc:postgresql://localhost:55432/postgres`

### One database per service

On the first boot of an empty `postgres-data` volume, `postgres/01-databases.sh` creates one login role, one database, and one schema per service. Later boots reuse the volume. Changing `.env` after that does not alter existing roles or passwords.

Each app role can connect only to its own database. The schema name matches the service name.

| Database | User | Password variable | Schema |
|---|---|---|---|
| `catalogdb` | `catalog_app` | `CATALOG_DB_PASSWORD` | `catalog` |
| `userdb` | `user_app` | `USER_DB_PASSWORD` | `user` |
| `inventorydb` | `inventory_app` | `INVENTORY_DB_PASSWORD` | `inventory` |
| `cartdb` | `cart_app` | `CART_DB_PASSWORD` | `cart` |
| `orderdb` | `order_app` | `ORDER_DB_PASSWORD` | `order` |
| `paymentdb` | `payment_app` | `PAYMENT_DB_PASSWORD` | `payment` |
| `keycloakdb` | `keycloak_app` | `KEYCLOAK_DB_PASSWORD` | `keycloak` |

Host JDBC URL pattern (catalog shown):

```text
jdbc:postgresql://localhost:55432/catalogdb
```

Username `catalog_app`, password `$CATALOG_DB_PASSWORD`. Services running in Compose use `jdbc:postgresql://postgres:5432/<servicedb>` instead. `make run-service` exports the host URL, username, password, and schema for you.

psql as the catalog role:

```bash
set -a; source .env; set +a
psql "postgresql://catalog_app:${CATALOG_DB_PASSWORD}@127.0.0.1:55432/catalogdb"
```

DataGrip, DBeaver, and pgAdmin use the same host, port, database, user, and password. Set the current schema to the service schema (for example `catalog`) when you browse tables.

## Other host endpoints

| Component | Host address | Credentials |
|---|---|---|
| Keycloak 26 | http://localhost:8180 (admin console: http://localhost:8180/admin/) | `KEYCLOAK_ADMIN` (default `local-admin`) / `KEYCLOAK_ADMIN_PASSWORD`. Realm `ecommerce-local`. |
| Redis 7 | `localhost:56379` | no auth. Start with `bash scripts/local/compose.sh --profile cache up -d redis`, or via manual-startup. |
| Kafka 3.9 | `localhost:59092` | no auth. Bootstrap server `localhost:59092`. Start with `bash scripts/local/compose.sh --profile events up -d kafka`, or via manual-startup. Inside Docker the listener is `kafka:9092`. |

Keycloak realm discovery:

```bash
curl -s http://localhost:8180/realms/ecommerce-local/.well-known/openid-configuration | jq -r .issuer
```
