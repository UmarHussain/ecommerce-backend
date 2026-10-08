# Security model

One Keycloak realm contains identities that can be customers, staff, or both. Storefront and admin are separate clients; email is not an authorization key. Link profiles by validated `(issuer, subject)`.

The realm template defines composite realm roles and permission roles on owning resource clients. The storefront role scope contains CUSTOMER only; the admin client contains approved staff bundles. Actual token behavior must be checked with Keycloak before marking the phase complete.

Backend authority convention: `PERM_catalog.create`, `PERM_inventory.adjust`, etc. Spring's default `SCOPE_` mapping for actual OAuth scopes is a different mechanism. Each service allowlists its own client claims. The gateway's coarse check reads `profile.read_own` and `profile.update_own` from `user-service`, and `admin.access` from `api-gateway`. `admin.access` is portal entry only. The admin UI permission list is built separately in user-service and is not that service's authorization authority set.

JWT checks: signature (default RS256 decoder), issuer, timestamps, expected audience, and Keycloak `typ=Bearer`. ID tokens must fail. No custom password login, browser role headers, or gateway-only security. Tokens are relayed for delegated calls once Phase 1 adds those calls. Do not replace the user token with an unrestricted service token.

Starter endpoints permit health and API documentation. Other unlisted endpoints are denied. Business documentation is local-only; sensitive actuator endpoints are not exposed. Catalog admin GET requires `catalog.read`, and POST/PUT/PATCH require create/update/activate, also enforced via method security. Catalog-service converts only its own client roles.

The runtime service account `user-service-admin` has `manage-users`, `view-users`, `query-clients`, `view-clients`, and `view-realm` only. `make realm-reconcile` applies those grants additively. It is not realm-admin and cannot create Keycloak realm roles or clients. Custom application bundles are therefore stored in userdb and expanded to existing client-role permissions.

Registration email verification and staff MFA remain off in the imported realm (`verifyEmail: false`). SPA redirect and logout URIs are exact (no wildcards). Optional Mailpit (`compose` profile `mail`) is available for later SMTP wiring.

Role assignment restrictions are enforced in `RoleAssignmentPolicy`: USER_ADMIN may assign the non-admin allowlist only; PLATFORM_ADMIN (`PERM_role.manage`) controls privileged bundles and bundle creation. The last PLATFORM_ADMIN cannot be suspended or stripped. See [permission-matrix.md](permission-matrix.md).

Access tokens last 300 seconds and are validated offline. Role changes appear after refresh or re-login; already issued JWTs keep their old claims until expiry.

Official references:
- https://www.keycloak.org/docs/latest/server_admin/
- https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
- https://spring.io/projects/spring-cloud/ (2025.0.x corresponds to Boot 3.5.x)
- https://cursor.com/docs/rules (MDC project rules)

Version choices in the supplied project are retained. New infrastructure versions are pinned for reproducibility; they are not claimed to be the latest releases.
