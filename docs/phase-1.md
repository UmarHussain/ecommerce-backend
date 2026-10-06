# Phase 1 review — identity and staff administration

Phase 1 remains complete. A later refactor removed `storefront-backend` and `admin-portal-backend` and routes the gateway directly to user-service and catalog-service. The narrative below records the Phase 1 implementation as it was verified on 2026-10-01, including the portal hop that no longer exists. Current routes are in [contracts/README.md](contracts/README.md) and [architecture.md](architecture.md).

Stopped here for review. Phase 0 was verified in this checkout, then Phase 1 was implemented. Catalog business code is unchanged. Cart, checkout, and Kafka Saga were not started.

Command-by-command evidence: [verification.md](verification.md). Dated log: [progress.md](progress.md). Component table: [service-status.md](service-status.md). How to run the stack: [local-development.md](local-development.md).

## Foundation verification (commands actually run)

WSL2 Ubuntu, repository `/mnt/d/GitHub/ecommerce-local-platform`, 2026-10-01. Java 21.0.12, Node 24.20.0 / npm 11.19.0, Docker 29.3.0, Compose v5.1.0, jq 1.7.1. `.env` already existed; `make bootstrap` did not overwrite it.

| Command | Result |
|---|---|
| `make help` | All targets listed |
| `make bootstrap` | Kept existing `.env` and rendered realm |
| `make check` | Passed (including Compose config with the `mail` profile) |
| `make backend-compile` | BUILD SUCCESS, all 8 modules |
| `make infra-up` | Postgres healthy, Keycloak already running, realm discovery `200` |
| `make smoke` | Passed before and after Phase 1 (source-run services) |
| `make backend-test` | Passed: user-service 18 new tests + retained catalog 23 |
| `make backend-verify` | Passed: `UserServicePostgresIT` (1) + `CatalogPostgresIT` (8) |
| `make frontend-test` / `make frontend-build` | Passed (storefront 1 test, admin 4 tests; both production builds) |
| `make security-check` | Passed with real Authorization Code + PKCE tokens |
| `make realm-reconcile` | Added `view-clients` to `user-service-admin`; nothing deleted |
| `make apps-up` | Not run (source-run path was used) |

Issued-token inspection (`scripts/local/oidc-login.sh`, no password grant):

- Storefront customer and dual-role: `typ=Bearer`; audiences include `api-gateway`, `storefront-backend`, `user-service`; `realm_access.roles` is `["CUSTOMER"]` only; no `admin-portal-backend` resource roles.
- Extra storefront scopes did not add staff roles.
- Admin dual-role: `CATALOG_CREATOR` + `CUSTOMER` + `admin.access` + `catalog.create` / `catalog.read`.
- Admin USER_ADMIN: user/role permissions without `role.manage` or `user.disable_identity`.
- ID token: `typ=ID`, audience `storefront-spa`.

`UserServiceKeycloakIT` reported 0 tests under Failsafe (admin secret not visible to that JVM). Live directory/token proof is `make security-check`.

## What Phase 1 implemented

- **Login.** Both SPAs use `oidc-client-ts` + `react-oidc-context`, Authorization Code + PKCE S256, exact redirect/logout URIs, tokens in memory. Reload requires sign-in. Storefront http://localhost:5173; admin http://localhost:5174.
- **Profiles.** `GET /api/v1/store/me` → storefront-backend → user-service `GET /api/v1/users/me` creates `app_user` + `customer_profile` idempotently on `(issuer, subject)`. Ownership comes from the JWT, not email. Staff rows are created only by authorized onboarding.
- **Keycloak administration.** `KeycloakUserDirectoryAdapter` uses `user-service-admin` (`manage-users`, `view-users`, `query-clients`, `view-clients`, `view-realm`). Not realm-admin. Infrastructure changes go through `make realm-reconcile`.
- **Admin APIs.** Explicit gateway routes only — no wildcard proxy. Portal backends relay the caller access token. USER_ADMIN can assign the non-admin allowlist and cannot grant `PLATFORM_ADMIN` or elevate itself. The last platform admin is locked. See [permission-matrix.md](permission-matrix.md).
- **Durable operations.** Directory-changing calls require `Idempotency-Key`. A Keycloak timeout is stored as `UNCERTAIN` and returns `202` with an operation URL. A 15s reconciler looks up the identity before retrying create.
- **Custom role bundles.** Stored in userdb and expanded to catalog client-role permissions. Runtime cannot create Keycloak realm roles (`manage-realm` is forbidden).
- **Email / MFA.** Optional Mailpit Compose profile `mail` (8025 / 1025). The imported realm still has `verifyEmail: false` and does not force staff MFA. Callback allowlists stay exact.

Route table: [contracts/README.md](contracts/README.md). Profile vs Keycloak ownership: [user-data-ownership.md](user-data-ownership.md).

## Implemented flows

### Customer login and profile

1. Storefront redirects the browser to Keycloak (`storefront-spa`).
2. After the PKCE callback, the SPA calls `GET /api/v1/store/me`.
3. Gateway checks `PERM_profile.read_own` and audience `api-gateway`, then forwards to storefront-backend.
4. Storefront-backend relays the same bearer token to user-service `/api/v1/users/me`.
5. `ProfileService.initializeOwnProfile` links `(issuer, subject)` and returns the profile contract (`profilePersistenceImplemented: true`).

Passwords never enter user-service. The frontend never calls the Keycloak Admin REST API.

### Staff administration

1. Admin-web redirects to Keycloak (`admin-spa`).
2. A customer-only account can authenticate at that client but receives `403` on `/api/v1/admin/me` and an access-denied message.
3. A dual-role person's storefront token still has no staff privileges; they must use the admin client for staff APIs.
4. User/role screens call `/api/v1/admin/users/**` and `/api/v1/admin/roles/**` (gateway → admin-portal-backend → user-service → Keycloak Admin REST).
5. Navigation hides Users/Roles without `PERM_user.read` / `PERM_role.read`. The backend remains authoritative.

### Role change

Access tokens last 300 seconds and are validated offline. A grant is visible after refresh or re-login. Already issued JWTs keep their old claims until they expire.

## Security-check evidence

- Customer and storefront dual-role: `403` on `/api/v1/admin/me`; admin dual-role: `200`.
- Own profile: `200`; a customer cannot read another user through admin APIs.
- USER_ADMIN: `403` assigning `PLATFORM_ADMIN` (self and other); allowlisted `CATALOG_EDITOR` succeeds.
- ID token, malformed bearer, and `X-User-Id` / `X-Roles` headers: `401`.

Side effect of that live check: `catalog-viewer@example.test` now also has `CATALOG_EDITOR` in the running realm. Passwords were not reset.

## Honest limits

- No Cursor browser click-through in that session. SPAs were served (`200` on 5173/5174) and use a maintained OIDC client; PKCE login was exercised against the Keycloak hosted page via `oidc-login.sh`.
- Email verification and staff MFA are not enabled in the realm.
- Container app mode (`make apps-up`) was not re-verified.
- A dedicated expired-token fixture was not minted; expiry/issuer/audience checks exist on every resource server.

## Next

Phase 2: catalog administration through the gateway to catalog-service, with real issued tokens. Do not start cart, checkout, or Kafka Saga until asked.
