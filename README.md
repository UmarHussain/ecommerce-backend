# E-commerce Local Platform

A Cursor-ready local starter derived from the supplied e-commerce project. Start with **[START_HERE.md](START_HERE.md)**.

## Included

- Six Spring applications: gateway, user, catalog, inventory, cart, and order. Storefront and admin portal backends were removed after Phase 1.
- Retained catalog entities, migrations, seed data, validation, search, REST APIs, error handling, and tests; versioned routes and finer permissions added.
- Gateway → catalog-service for public reads, and gateway → user-service for profiles and staff administration.
- JWT signature/issuer/audience/access-token checks with deny-by-default security.
- Keycloak realm template with separate clients, scoped role bundles, and generated local-only seed credentials.
- Isolated PostgreSQL databases/users; optional Redis and Kafka Compose profiles.
- Two React shells with Vite API proxying.
- Implementation plan, master specification, Cursor rules, service guides, and honest verification records.

Phase 1 identity and staff administration are implemented. Cart, checkout, and Kafka Saga are not. See [service status](docs/service-status.md).

## Commands

Use WSL2 with `make`, `jq`, Java 21, Docker Compose v2+, and the Node/npm versions recorded in frontend/package.json. No Python. The root `Makefile` is the entry point; `make help` lists all targets.

```bash
make bootstrap                            # .env + rendered realm, only if missing
make check                                # offline static checks
make infra-up                             # PostgreSQL + Keycloak
make run-service SERVICE=catalog-service  # one Java service per terminal, or: make apps-up
make smoke
```

Frontends: `make frontend-install`, then `make run-frontend APP=storefront-web` / `APP=admin-web`. Tests: `make backend-test`, `make backend-verify`, `make frontend-test`, `make frontend-build`, or `make verify`. Details and direct command equivalents: [local development](docs/local-development.md).

`make infra-down` stops only this Compose project and keeps its volumes. `make clean` removes build outputs only. No target deletes volumes, `.env`, or the rendered realm.

## Navigation

- [Implementation plan](IMPLEMENTATION_PLAN.md)
- [Architecture](docs/architecture.md)
- [Security and permissions](docs/security-model.md)
- [Seed users and role test cases](docs/seed-users.md)
- [User data ownership](docs/user-data-ownership.md)
- [Source provenance](docs/source-provenance.md)
- [Verification](docs/verification.md)
- [Progress](docs/progress.md)
- [Design specification](MASTER_PROMPT.md)
