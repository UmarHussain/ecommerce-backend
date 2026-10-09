# API gateway

Reactive Spring Cloud Gateway. No JPA/database or business logic.

Current routes include public catalog reads, the storefront cart, the storefront principal, and admin catalog, inventory, and user routes. Tokens are validated for the gateway audience, issuer, access-token type, and required coarse permission. Every downstream service validates again.

Run and verification commands: ../../docs/local-development.md. Next task: explicit user/role routes after Phase 1 portal contracts exist. Do not replace this with an unrestricted admin proxy.
