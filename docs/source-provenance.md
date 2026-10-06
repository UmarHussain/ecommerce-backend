# Source provenance and intentional changes

Source: user-supplied `ecommerce-aws-learning.zip`, inspected 2026-09-30. The upload was read into a separate working directory; it was not modified.

Retained: Maven Wrapper/checksum and Java 21/Spring Boot 3.5.16 baseline; catalog DTOs, entities, services, repositories, Flyway schema/seed, exception handling, correlation filter and tests; React shells, package lock and test tooling; cart/inventory/order application shells.

Adapted: catalog URLs now `/api/v1/catalog` and `/api/v1/admin/catalog`; legacy ADMIN mapping replaced with owning-client permissions and method checks. Frontend branding changed to local platform. Catalog SQL is retained only for the new independent catalogdb. No old persistent database is targeted.

Added: gateway, storefront/admin composition skeletons, user-service principal endpoint and integration port; role bundles; database-per-service initialization; Keycloak realm template; Compose; generated secrets; local helper commands; security negative tests; phase plan and scoped Cursor rules.

Excluded: original .git history, .idea state, build artifacts, legacy AWS master prompt/docs, old phase claims/review output, and conflicting Cursor rules. No old machine-specific configuration or credentials copied.

The source contains useful catalog code, not a complete storefront/admin application. Retention does not imply the catalog has been fully revalidated here. See verification.md.
