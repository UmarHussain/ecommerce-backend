# Local development (WSL2)

All commands run in a WSL2 Ubuntu terminal from the repository root (`/mnt/d/GitHub/ecommerce-local-platform` or a WSL-filesystem checkout, which is faster for Docker and Node). The root `Makefile` is the entry point; every target wraps a plain Maven, npm, Docker Compose, or Bash command that you can also run yourself (shown under "Equivalent direct command").

Python is not required anywhere in this project. Keep it that way; see `.cursor/rules/00-project.mdc`.

## 1. Prerequisites

| Tool | Why | Install (Ubuntu in WSL2) |
|---|---|---|
| `make`, `bash`, `curl`, `openssl` | Makefile, scripts, secret generation, smoke test | `sudo apt-get install -y make curl openssl` |
| `jq` | Safe JSON rendering of the Keycloak realm and static checks | `sudo apt-get install -y jq` |
| Java 21 (Temurin or OpenJDK) | All backend modules target 21 | `sudo apt-get install -y openjdk-21-jdk` or SDKMAN |
| Docker Engine + Compose v2 | PostgreSQL, Keycloak, optional Redis/Kafka, Testcontainers | Docker Engine inside WSL2 or Docker Desktop with WSL integration |
| Node 24.20.x and npm 11.19.x | Frontend workspaces (`frontend/package.json` engines) | `nvm install 24.20.0 && nvm use 24.20.0` (Linux Node; a Windows `npm.cmd` found through `/mnt/d/...` does not work) |

Maven is not needed globally: `backend/mvnw` downloads Maven 3.9.16 on first use.

Quick self-test:

```bash
java -version && docker compose version && node --version && npm --version && jq --version && make --version | head -1
```

## 2. Makefile commands

Run `make help` for the same list.

| Target | What it does | Equivalent direct command |
|---|---|---|
| `make bootstrap` | Creates `.env` with OpenSSL-generated secrets only if it does not exist; renders `.local/keycloak/ecommerce-local-realm.json` from the template with jq only if missing. Never overwrites either file; never prints secret values. | `bash scripts/local/bootstrap.sh` |
| `make check` | Offline static checks: Bash syntax, Maven module layout (read from `backend/pom.xml`, no fixed count), frontend `package.json` parsing, realm template invariants (unique seed users, defined roles, SPA clients public + PKCE S256 + no direct grants + no full scope, storefront scope = CUSTOMER), Cursor rule front matter, required docs and relative Markdown links, and `docker compose config` when Docker and `.env` exist. | `bash scripts/local/check.sh` |
| `make infra-up` | Prints which project containers already run, then starts PostgreSQL (55432) and Keycloak (8180). Containers started by the manual-startup file are reused, not duplicated. | `bash scripts/local/infra-status.sh && bash scripts/local/compose.sh up -d postgres keycloak` |
| `make infra-down` | Stops and removes this project's containers across all profiles (including ones started from `manual-startup/`). Volumes are kept. | `bash scripts/local/compose.sh --profile apps --profile later --profile cache --profile events down` |
| `make infra-logs` | Follows the last 100 lines of logs of all running project containers. | `bash scripts/local/compose.sh logs --tail=100 -f` |
| `make infra-status` | Lists running containers of the Compose project and which compose file started them. | `bash scripts/local/infra-status.sh` |
| `make apps-up` | Builds the backend image and starts the six `apps`-profile services in containers. | `bash scripts/local/compose.sh --profile apps up -d --build` |
| `make backend-compile` | Compiles main and test sources of all modules. No tests run. | `cd backend && ./mvnw -B test-compile` |
| `make backend-test` | `mvn test`: Surefire unit/MVC/context tests. Failsafe `*IT` tests are not executed. | `cd backend && ./mvnw -B test` |
| `make backend-verify` | `mvn verify`: everything in `test` plus Failsafe `*IT` tests (catalog Testcontainers; needs Docker). | `cd backend && ./mvnw -B verify` |
| `make backend-clean` | Removes all `target/` directories. | `cd backend && ./mvnw -B -q clean` |
| `make frontend-install` | Installs exact lockfile dependencies for both workspaces. | `cd frontend && npm ci` |
| `make frontend-test` | Runs Vitest in `storefront-web` and `admin-web`. | `cd frontend && npm test` |
| `make frontend-build` | `tsc --noEmit` and `vite build` for both apps. | `cd frontend && npm run build` |
| `make frontend-clean` | Deletes `dist/` in both apps. `node_modules` stays. | `cd frontend && npm run clean` |
| `make run-service SERVICE=<module>` | Runs one backend module from source in the current terminal, with `.env` loaded and local OIDC/DB settings exported. | `bash scripts/local/run-service.sh <module>` |
| `make run-frontend APP=<storefront-web\|admin-web>` | Runs one Vite dev server in the current terminal. | `cd frontend && npm run dev --workspace @ecommerce/<app>` |
| `make smoke` | HTTP checks against Keycloak discovery, gateway health, public catalog, and a 401 on an admin endpoint. | `bash scripts/local/smoke.sh` |
| `make security-check` | Phase 1 acceptance: real PKCE tokens, audience/role isolation, profile ownership, USER_ADMIN anti-elevation, rejected ID tokens and identity headers. Needs infra + gateway, user-service, and catalog. | `bash scripts/local/security-check.sh` |
| `make catalog-check` | Phase 2 catalog acceptance: anonymous browsing, viewer/creator/editor/customer/dual-role tokens, SKU immutability, stale `expectedVersion`, inactive isolation, direct catalog-service denial, and correlation ids. Needs infra + gateway, user-service, and catalog. | `bash scripts/local/catalog-check.sh` |
| `make realm-reconcile` | Additive Keycloak realm update from the template. Never deletes users or resets passwords. | `bash scripts/local/realm-reconcile.sh` |
| `make realm-migrate-portals` | Moves `admin.access` to `api-gateway` and deletes the obsolete portal clients. Keeps users, passwords, and volumes. | `bash scripts/local/migrate-remove-portal-backends.sh` |
| `make verify` | `check` + `backend-verify` + `frontend-test` + `frontend-build`. `backend-test` is not run separately because `verify` already includes the test phase. Run `make frontend-install` once before. | the four commands above |
| `make clean` | `backend-clean` + `frontend-clean`. Does not touch `.env`, `.local/`, or Docker volumes. | see above |

Nothing in the Makefile deletes Docker volumes. If you ever need that, do it deliberately with the explicit `down -v` command in [section 11](#11-wipe-this-project-and-start-from-scratch), understanding it destroys local databases and the imported realm.

## 3. First-time setup

Empty checkout, no containers yet. Run in this order:

```bash
make bootstrap          # 1. .env with generated secrets + rendered realm (private files, git-ignored)
make check              # 2. static sanity checks
make frontend-install   # 3. npm ci for both frontends
make infra-up           # 4. PostgreSQL :55432 and Keycloak :8180
```

Wait until Keycloak has imported the realm (30–60 s on first boot):

```bash
curl -s http://localhost:8180/realms/ecommerce-local/.well-known/openid-configuration | jq -r .issuer
# http://localhost:8180/realms/ecommerce-local
```

Then start the applications, one terminal each (source mode):

```bash
make run-service SERVICE=catalog-service     # 5.
make run-service SERVICE=user-service        # 6.
make run-service SERVICE=api-gateway         # 7.
make run-frontend APP=storefront-web         # 8. http://localhost:5173
make run-frontend APP=admin-web              # 9. http://localhost:5174
make smoke                                   # 10. realm, gateway health, public catalog, 401 on admin
```

Container mode instead of steps 5–7: `make apps-up`, then steps 8–10. IntelliJ mode: replace step 4 with `bash infrastructure/local/manual-startup/infra.sh up` and follow [manual-startup/README.md](../infrastructure/local/manual-startup/README.md).

The first boot of an empty `postgres-data` volume creates the service databases and roles from `.env` and imports the realm from `.local/keycloak/ecommerce-local-realm.json`. Seed users (see [seed-users.md](seed-users.md)) use `DEMO_USER_PASSWORD` from `.env`.

`bootstrap` is safe to rerun: it reports "Keeping existing .env" and "Keeping existing rendered realm". If `.env` exists but lacks keys, it warns with the key names and leaves the file alone.

### Next time

`.env`, the rendered realm, and the volumes already exist. Do not run `bootstrap` again unless one of those files is missing.

```bash
make infra-up        # reuses the existing containers and volumes
```

Then start only the services and frontends you need (steps 5–9 above) and `make smoke` if you want the quick check. Databases, passwords, users, and the realm come from the volume, so a later edit to `.env` or `realm-template.json` does not change them. Additive realm changes: `make realm-reconcile`. If the realm was imported before the portal-backend removal: `make realm-migrate-portals` once, then sign in again.

At the end of the day: Ctrl+C in each service and Vite terminal, then `make infra-down` (containers only; volumes stay).

## 4. Infrastructure

```bash
make infra-up
make infra-logs      # Ctrl+C to stop following
```

| Component | Host address | Notes |
|---|---|---|
| PostgreSQL 16 | `localhost:55432` | Bootstrap superuser `platform_bootstrap`; one database/role per service created on first boot by `infrastructure/local/postgres/01-databases.sh`. Host, JDBC, and `psql` connection steps: [infrastructure/local/README.md](../infrastructure/local/README.md) |
| Keycloak 26 | http://localhost:8180 | Realm `ecommerce-local` imported from `.local/keycloak/` on first boot only |

Ports are loopback-only. If 55432 or 8180 is already in use by another local project, change the port in `infrastructure/local/compose.yaml` and the matching URLs (`run-service.sh`, Vite proxy, OIDC issuer) consistently; do not stop unrelated services.

First boot creates databases and imports the realm. Later boots reuse the volume. Editing `.env`, the realm template, or the rendered import after that does not change the running databases or realm; see "Realm lifecycle" below.

Optional profiles (no application code uses them yet):

```bash
bash scripts/local/compose.sh --profile cache up -d redis    # host: localhost:56379
bash scripts/local/compose.sh --profile events up -d kafka   # host: localhost:59092 (inside Docker: kafka:9092)
```

### Manual/IDE mode: infrastructure only, services in IntelliJ

`infrastructure/local/manual-startup/` contains an infrastructure-only Compose file (PostgreSQL, Keycloak, Redis, Kafka; no profiles needed) that `extends` the services of the main file and uses the same project, service, and volume names. Containers are therefore shared with `make infra-up`/`make infra-down`; whichever side starts first, the other reuses the running containers. Guides: [manual-startup/README.md](../infrastructure/local/manual-startup/README.md) and [intellij-services.md](../infrastructure/local/manual-startup/intellij-services.md).

```bash
bash infrastructure/local/manual-startup/infra.sh up      # or: status | down | logs [service]
```

## 5. Running Java services from source (one terminal each)

With infrastructure up, start services in separate WSL2 terminals. Each service validates JWTs against `http://localhost:8180/realms/ecommerce-local`.

| Service | Command | Port | State today |
|---|---|---|---|
| catalog-service | `make run-service SERVICE=catalog-service` | 8094 | Implemented: Flyway migrations, seed data, products/categories CRUD, search, permission checks, tests |
| user-service | `make run-service SERVICE=user-service` | 8093 | Profiles, `/api/v1/admin/me`, Keycloak Admin adapter, durable operations |
| api-gateway | `make run-service SERVICE=api-gateway` | 8090 | Routes and rewrites to catalog-service and user-service |
| inventory-service | `make run-service SERVICE=inventory-service` | 8095 | Secured shell with health endpoint; no business API (Phase 3) |
| cart-service | `make run-service SERVICE=cart-service` | 8096 | Secured shell; Phase 4 |
| order-service | `make run-service SERVICE=order-service` | 8097 | Secured shell; Phase 5 |

Minimum set for the public catalog path through the gateway: `catalog-service` and `api-gateway`. Add `user-service` before profile or admin calls.

Equivalent direct command (what `run-service.sh` does for catalog-service):

```bash
set -a; source .env; set +a
export OIDC_ISSUER_URI=http://localhost:8180/realms/ecommerce-local
export OIDC_JWK_SET_URI=http://localhost:8180/realms/ecommerce-local/protocol/openid-connect/certs
export DATABASE_URL=jdbc:postgresql://localhost:55432/catalogdb DATABASE_USERNAME=catalog_app \
       DATABASE_PASSWORD="$CATALOG_DB_PASSWORD" DATABASE_SCHEMA=catalog SPRING_PROFILES_ACTIVE=local
cd backend && ./mvnw -B -pl catalog-service spring-boot:run
```

Swagger UI is available at `http://localhost:<port>/swagger-ui/index.html` on the MVC services; the gateway does not aggregate it.

### Container alternative

Instead of terminals, `make apps-up` builds one image per module (tests skipped during the build) and runs gateway, user, catalog, and inventory in containers. Do not run containers and source services on the same ports at once. Internal services are not host-published unless you add the debug override:

```bash
docker compose --env-file .env -f infrastructure/local/compose.yaml -f infrastructure/local/compose.debug.yaml --profile apps up -d --build
```

## 6. Running the frontends (one terminal each)

```bash
make run-frontend APP=storefront-web   # http://localhost:5173
make run-frontend APP=admin-web        # http://localhost:5174
```

Direct: `cd frontend && npm run dev --workspace @ecommerce/storefront-web` (or `@ecommerce/admin-web`).

Vite proxies `/api` to the gateway at `http://localhost:8090`. Both apps use `oidc-client-ts` / `react-oidc-context` (Authorization Code + PKCE S256). Tokens stay in memory (reload requires sign-in). Storefront: http://localhost:5173 — sign-in and own profile. Admin: http://localhost:5174 — sign-in, users, roles; navigation hides links the token cannot use. Catalog admin screens remain Phase 2.

Minimum identity stack: PostgreSQL, Keycloak, user-service, api-gateway, plus the two Vite apps. Keep `catalog-service` up for `make smoke` and public catalog.

## 7. Keycloak and local credentials

- Admin console: http://localhost:8180/admin/ — user `KEYCLOAK_ADMIN` (default `local-admin`), password `KEYCLOAK_ADMIN_PASSWORD`.
- Realm: `ecommerce-local`; OIDC discovery at http://localhost:8180/realms/ecommerce-local/.well-known/openid-configuration.
- Seed application users (customer@example.test, platform-admin@example.test, ...) all use `DEMO_USER_PASSWORD`. Full list and roles: [seed-users.md](seed-users.md).

Secrets are only in the git-ignored `.env`. No script prints them. To read one deliberately in your own terminal:

```bash
grep '^KEYCLOAK_ADMIN_PASSWORD=' .env     # prints the value; do not paste it into chats or commits
```

### Realm lifecycle

Keycloak's `--import-realm` only imports a realm that does not exist in its database. Once the first boot has run, changing `realm-template.json`, re-rendering `.local/keycloak/ecommerce-local-realm.json`, or rotating `DEMO_USER_PASSWORD` in `.env` has no effect on the running realm. Apply additive template changes with `make realm-reconcile` (never deletes users or resets passwords). The portal-backend removal is not additive: run `make realm-migrate-portals` on an already imported realm. That script moves `admin.access` onto `api-gateway`, removes the obsolete audience mappers, then deletes `storefront-backend` and `admin-portal-backend`. It does not reset passwords or volumes. Sign in again afterward so tokens pick up the new client role. Do not delete the PostgreSQL volume to force a re-import. The same applies to database passwords: `01-databases.sh` runs only on an empty data volume.

## 8. Health checks, logs, and smoke test

```bash
curl -s http://localhost:8180/realms/ecommerce-local/.well-known/openid-configuration | jq .issuer
curl -s http://localhost:8090/actuator/health            # gateway
curl -s http://localhost:8094/actuator/health            # any source-run service on its own port
curl -s http://localhost:8090/api/v1/store/catalog/products | jq . | head -40
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8090/api/v1/admin/me   # expect 401
make smoke                                                # the same four checks with retries
make infra-logs                                           # container logs
bash scripts/local/compose.sh logs -f keycloak            # one container
```

Source-run services log to the terminal they run in.

## 9. Tests

```bash
make check             # static, seconds
make backend-test      # mvn test: unit/MVC tests, no Docker needed
make backend-verify    # mvn verify: adds catalog and user-service *IT Testcontainers tests, needs Docker
make security-check    # real PKCE tokens against the running stack
make frontend-test
make frontend-build
make verify            # check + backend-verify + frontend-test + frontend-build
```

`make security-check` is the issued-token Phase 1 gate. It drives the Keycloak hosted login page with Authorization Code + PKCE (`scripts/local/oidc-login.sh`); password grants stay disabled. See [verification.md](verification.md). The same login, drawn as a sequence and repeated as Postman requests, is [postman-user-token.md](postman-user-token.md).

Optional Mailpit: `bash scripts/local/compose.sh --profile mail up -d mailpit` then http://localhost:8025. The imported realm does not send mail yet.

## 10. Shutdown and cleanup

```bash
make infra-down   # stops containers of this Compose project; keeps postgres-data, redis-data, kafka-data volumes
make clean        # removes backend target/ and frontend dist/; keeps .env, .local/, node_modules, volumes
```

Stop source-run services and Vite dev servers with Ctrl+C in their terminals. To remove `node_modules`, run `rm -rf frontend/node_modules` yourself. There is intentionally no target that deletes volumes or `.env`.

## 11. Wipe this project and start from scratch

This deletes every container of Compose project `ecommerce-local-platform` and its three named volumes (`postgres-data`, `redis-data`, `kafka-data`). All local databases, the imported Keycloak realm (users, passwords, role assignments made in the admin console), Redis, and Kafka data are gone afterward. Other Docker projects on the machine are not touched; do not use `docker system prune` or `docker volume prune` for this.

1. Stop Java services and Vite dev servers with Ctrl+C (they are not containers).
2. Remove containers and volumes:

   ```bash
   bash scripts/local/compose.sh \
     --profile apps --profile later --profile cache --profile events --profile mail \
     down -v --remove-orphans
   ```

   The profiles make Compose include every service of the file, including the ones started from `manual-startup/`; `--remove-orphans` also removes containers of service names that no longer exist in the file (for example the removed portal backends).

3. Keep `.env`. If you want the empty Keycloak database to import the current `realm-template.json` (rather than the file rendered when you first bootstrapped), re-render the import from the same `.env`:

   ```bash
   rm -f .local/keycloak/ecommerce-local-realm.json
   make bootstrap     # keeps .env, renders a new realm file
   ```

4. Start again: `make infra-up` (or `infra.sh up`), wait for the issuer, then steps 5–10 of [section 3](#3-first-time-setup). The first boot recreates the databases and imports the realm. `make realm-migrate-portals` is not needed after a fresh import of the current template.
