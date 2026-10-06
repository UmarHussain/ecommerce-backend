# Agent instructions

Read START_HERE.md, IMPLEMENTATION_PLAN.md, docs/service-status.md, and docs/verification.md before changing code. Consult MASTER_PROMPT.md for design details.

This repository is a prepared starter created from an authorized uploaded copy. Do not recreate it, nest another repository, or modify the original project. Never infer completion from a directory, document, or TODO. Preserve working catalog code and unrelated changes.

Phase 1 is complete. A later architecture refactor removed `storefront-backend` and `admin-portal-backend`: browsers call the API gateway, which routes to the owning domain service. Do not recreate those modules or restart Phase 1 unless the user asks. Portal backends may return later for real API composition. Later phases remain planned. Local-only; no AWS/Terraform/Kubernetes work.

Use the WSL2 environment for Java 21, Maven Wrapper, npm, Docker, and Testcontainers. Inspect available tools before running commands. Use the root Makefile targets (`make help`) and the Bash scripts in scripts/local; they call mvnw, npm, docker compose, jq, and openssl directly. Do not introduce Python as a project dependency. Run the relevant tests and report missing prerequisites, skipped tests, and failures honestly.

Keep domain rules inside owning services and orchestration in order-service. The gateway routes, validates tokens, and applies coarse checks; it does not compose responses or make business decisions. Never access another service database or expose JPA entities through APIs. No shared domain JAR.

Every service validates tokens itself. Gateway checks do not replace permission/ownership checks. Never grant realm-admin to the application to bypass permission setup. Never use browser identity headers as authentication. Do not enable password grants for tests.

Never commit local secrets, generated realm imports, node_modules, target, or IDE files. Do not delete volumes, reset databases, run broad Docker prune commands, or touch unrelated local services. Application credentials must be scoped to their own databases.

Update service status, API docs, local commands, progress, and verification with each behavioral change. Explicit user instructions take precedence over this file. Do not push or publish without a request.
