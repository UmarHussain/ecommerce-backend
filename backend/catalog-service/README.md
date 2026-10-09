# catalog-service
See ../../docs/service-status.md and ../../docs/local-development.md.
Security: JWT signature, issuer, expiry, audience, and Keycloak access-token type; explicit permission mapping and default deny.
Catalog business code retained and adapted; requires its own PostgreSQL database. Public browse responses are also cached in Redis. See ../../docs/phase-4.md.
