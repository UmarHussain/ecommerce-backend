# Manual startup: infrastructure in Docker, services in IntelliJ

This folder is for day-to-day development: only PostgreSQL, Keycloak, Redis, and Kafka run in Docker; the Java services run from IntelliJ (or `make run-service`) and the frontends run with Vite.

The main file `infrastructure/local/compose.yaml` stays the one Cursor and the Makefile use (it can additionally build and run the services in containers with `make apps-up`). Both files share the same infrastructure containers, so you never end up with two PostgreSQL or two Keycloak instances:

| | Main: `infrastructure/local/compose.yaml` | Manual: `infrastructure/local/manual-startup/compose.yaml` |
|---|---|---|
| Used by | `make infra-up`, `make apps-up`, `make smoke`, Cursor | You, from a terminal, before starting IntelliJ run configurations |
| Starts | `postgres`, `keycloak` (+ apps with `--profile apps`, Redis/Kafka with `--profile cache/events`) | `postgres`, `keycloak`, `redis`, `kafka` — always, no profiles |
| Definitions | Source of truth | `extends` each service from the main file; same project name `ecommerce-local-platform`, same service and volume names |
| Already running? | `make infra-up` first prints `infra-status`, then `up -d` reports existing containers as `Running` and does not recreate them | `infra.sh up` does the same |

Because the project name, service names, and volumes are identical, Docker Compose recognises containers started by either file. `make infra-down` stops them all; volumes (`postgres-data`, `redis-data`, `kafka-data`) are always kept.

## Files here

| File | Purpose |
|---|---|
| `compose.yaml` | Infrastructure-only Compose file (extends the main file) |
| `infra.sh` | `up` / `down` / `status` / `ps` / `logs [service]` wrapper that passes the root `.env` and this file to `docker compose` |
| `README.md` | This guide: infrastructure, ports, credentials, health checks, shutdown |
| `intellij-services.md` | One IntelliJ run configuration per Java service, plus the frontends |

## 1. One-time setup

From the repository root in WSL2 (prerequisites: `make`, `jq`, `openssl`, Docker, Java 21 — see `docs/local-development.md`):

```bash
make bootstrap    # creates .env and .local/keycloak/ecommerce-local-realm.json if missing
make check
```

## 2. Start the infrastructure

```bash
bash infrastructure/local/manual-startup/infra.sh up
```

Equivalent direct command (run from the repository root so `--env-file .env` resolves):

```bash
docker compose --env-file .env -f infrastructure/local/manual-startup/compose.yaml up -d
```

Expected output on a first start: `postgres` becomes `Healthy`, then `keycloak`, `redis`, `kafka` start. Keycloak needs 30–60 s to import the realm; check with:

```bash
curl -s http://localhost:8180/realms/ecommerce-local/.well-known/openid-configuration | jq -r .issuer
# http://localhost:8180/realms/ecommerce-local
```

If containers are already up (for example from `make infra-up`), the script prints an `infra-status` table and `docker compose up -d` reports them as `Running`.

| Component | Host address (loopback only) | Credentials / notes |
|---|---|---|
| PostgreSQL 16 | `localhost:55432` | superuser `platform_bootstrap` / `POSTGRES_PASSWORD`; per-service logins in the table below. `psql` and JDBC steps: [../README.md](../README.md) |
| Keycloak 26 | http://localhost:8180 (admin console: http://localhost:8180/admin/) | `KEYCLOAK_ADMIN` (`local-admin`) / `KEYCLOAK_ADMIN_PASSWORD`; realm `ecommerce-local` |
| Redis 7 | `localhost:56379` | no auth; nothing in the code uses it yet (Phase 4) |
| Kafka 3.9 (KRaft) | `localhost:59092` from the host, `kafka:9092` inside Docker | no auth; nothing in the code uses it yet (Phase 5) |

Port 6379 is deliberately not used because another local Redis commonly occupies it.

Each service database is created on first boot. Connect to `localhost:55432` with that database’s user. The password is the matching variable in `.env`.

| Database | User | Password variable | Schema |
|---|---|---|---|
| `catalogdb` | `catalog_app` | `CATALOG_DB_PASSWORD` | `catalog` |
| `userdb` | `user_app` | `USER_DB_PASSWORD` | `user` |
| `inventorydb` | `inventory_app` | `INVENTORY_DB_PASSWORD` | `inventory` |
| `cartdb` | `cart_app` | `CART_DB_PASSWORD` | `cart` |
| `orderdb` | `order_app` | `ORDER_DB_PASSWORD` | `order` |
| `paymentdb` | `payment_app` | `PAYMENT_DB_PASSWORD` | `payment` |
| `keycloakdb` | `keycloak_app` | `KEYCLOAK_DB_PASSWORD` | `keycloak` |

All passwords live in the git-ignored root `.env`. Nothing prints them; read one yourself when you need it:

```bash
grep '^KEYCLOAK_ADMIN_PASSWORD=' .env
grep '^CATALOG_DB_PASSWORD=' .env
grep '^DEMO_USER_PASSWORD=' .env        # password of every seed user in docs/seed-users.md
```

## 3. Run the services

Follow [intellij-services.md](intellij-services.md). Minimum for the public catalog path: `catalog-service` and `api-gateway`. Add `user-service` before profile or admin APIs. Customer and admin Postman login: [docs/postman-user-token.md](../../../docs/postman-user-token.md).

Without IntelliJ, the same thing from terminals:

```bash
make run-service SERVICE=catalog-service
make run-service SERVICE=user-service
make run-service SERVICE=api-gateway
```

## 4. Verify

```bash
curl -s http://localhost:8094/actuator/health      # catalog-service
curl -s http://localhost:8093/actuator/health      # user-service
curl -s http://localhost:8090/actuator/health      # api-gateway
curl -s 'http://localhost:8090/api/v1/store/catalog/products?size=2' | jq '{totalElements, items: [.items[].name]}'
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8090/api/v1/admin/me    # 401 without a token
make smoke                                          # the same checks with retries
```

What is implemented versus still a shell is listed in `docs/service-status.md`. Phase 1 login and admin screens are implemented. The gateway talks to catalog-service and user-service directly.

## 5. Logs, status, shutdown

```bash
bash infrastructure/local/manual-startup/infra.sh status          # which containers run, started from which file
bash infrastructure/local/manual-startup/infra.sh logs keycloak   # one service; omit the name for all
bash infrastructure/local/manual-startup/infra.sh down            # stop infrastructure; volumes kept
make infra-down                                                   # same effect, also stops any app containers
```

Stop IntelliJ services from the Run tool window. Nothing here deletes volumes; see "Realm lifecycle" in `docs/local-development.md` before you consider doing so by hand.

## 6. Switching between manual and container mode

Do not run the containerised apps (`make apps-up`) while the same services run in IntelliJ: both bind 8090–8097. Stop one side first. The infrastructure containers can stay up throughout.
