# Project foundation

Prepared independent starter; source provenance is documented in source-provenance.md.

| Item | Choice |
|---|---|
| Backend | Java 21, Spring Boot 3.5.16, Maven Wrapper 3.9.16 |
| Gateway | Spring Cloud BOM 2025.0.3, reactive gateway |
| API documentation | Springdoc 2.8.17 for MVC applications |
| Frontend | Retained React/TypeScript/Vite packages and lockfile |
| Database | PostgreSQL 16.10; database/login per data owner |
| Identity | Keycloak 26.4.0, one local realm, separate SPA clients |
| Optional infrastructure | Redis 7.4.5, Kafka 3.9.1 |
| Execution | WSL2; Docker infrastructure and IDE or container applications |

Pinned baseline versions are reproducibility choices; no claim that they are the newest releases. Verify support/security/compatibility before use beyond local development. The Spring Cloud 2025.0 line is paired with Boot 3.5; do not casually swap the gateway BOM to another Boot line.

Local URLs/ports: local-development.md. Identity seed matrix: seed-users.md. Permission bundles: permission-matrix.md. Current executable versus planned behavior: service-status.md. Commands and evidence: verification.md.

Root Compose project name, database credentials, volumes, and network are isolated from the original project. All published ports bind to loopback. Generated `.env` and `.local` are private local output and excluded from Git and packaging.
