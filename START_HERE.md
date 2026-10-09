# Start here — Cursor handoff

This is a separate local starter based on your uploaded project. Open the `ecommerce-local-platform` folder itself in Cursor. Do not open the old project and do not create another folder inside this one.

Run terminal commands in WSL2 Ubuntu, where Java 21, Docker Engine, Node, `make`, and `jq` are installed. Prefer a WSL filesystem checkout for faster Docker/Node work, but `/mnt/d/GitHub/ecommerce-local-platform` is also usable. Python is not used by this project; see [docs/local-development.md](docs/local-development.md) for prerequisites and installation.

## First Cursor message

Paste this into a new Agent chat:

> Read START_HERE.md, AGENTS.md, IMPLEMENTATION_PLAN.md, MASTER_PROMPT.md, docs/service-status.md, and docs/verification.md. This repository is an existing prepared starter, so build on it without recreating or nesting the project. First verify the scaffold using my WSL2 Java 21, Node, and Docker environment through the root Makefile. Inspect the available targets, run the scaffold and build checks, then start the infrastructure and required application services before running smoke checks. Resolve any concrete startup/build issues. Then implement Phase 1: real Keycloak login in both frontends, user profiles, controlled Keycloak user/role administration, permission-aware admin UI, and the security acceptance tests listed in the implementation plan. Preserve the catalog business code. Do not implement cart, checkout, Kafka Saga, or AWS yet. Do not introduce Python; local tooling is Makefile + Bash. Update the progress and verification documents, explain the implemented flows, and stop after Phase 1 for review.

## Before starting containers

```bash
make help        # list every target
make bootstrap   # .env with generated secrets + rendered Keycloak realm (only if missing)
make check       # offline static checks
make infra-up    # PostgreSQL 55432 + Keycloak 8180
```

`make bootstrap` never overwrites an existing `.env` or rendered realm, prints no secret values, and does not touch containers. Do not paste generated credentials into Cursor chats. A Keycloak realm is imported only on the first boot; later edits to the import file do not update it.

## Run the backend

Either from source, one service per WSL2 terminal:

```bash
make run-service SERVICE=catalog-service
make run-service SERVICE=user-service
make run-service SERVICE=api-gateway
make smoke
```

or in containers:

```bash
make apps-up
make smoke
```

The public catalog path is gateway → catalog-service. Phase 1 login/profiles/staff administration, Phase 2 catalog, Phase 3 inventory, and Phase 4 cart/cache are implemented. Phase 5 adds gateway → order-service checkout, Kafka reservations, a local payment simulator, and cart cleanup; see [docs/phase-5.md](docs/phase-5.md). Request and response bodies for that path are in [docs/cart-quote-order.md](docs/cart-quote-order.md). `storefront-backend` and `admin-portal-backend` stay removed. Checkout in containers needs `make checkout-up`; source-run services use Kafka at `localhost:59092`. After pulling the portal-removal refactor onto an already imported realm, run `make realm-migrate-portals` and sign in again. A realm imported before Phase 3 also needs `make realm-reconcile` so `INVENTORY_READER` and `inventory-reader@example.test` exist.

## Run the frontends (two terminals)

```bash
make frontend-install                   # once
make run-frontend APP=storefront-web    # http://localhost:5173
make run-frontend APP=admin-web         # http://localhost:5174
```

## Tests and shutdown

```bash
make backend-test      # mvn test
make backend-verify    # mvn verify, includes Testcontainers *IT (Docker)
make frontend-test
make frontend-build
make infra-down        # stops containers, keeps volumes
```

See [docs/local-development.md](docs/local-development.md) for every command with its direct equivalent, per-service ports and status, Keycloak credentials lookup, health checks, logs, and known limitations. Seed users and role test cases are listed in [docs/seed-users.md](docs/seed-users.md). Phase 1 review: [docs/phase-1.md](docs/phase-1.md). Phase 2 review: [docs/phase-2.md](docs/phase-2.md). Phase 3 review: [docs/phase-3.md](docs/phase-3.md).
