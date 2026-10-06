# Keycloak starter realm
`realm-template.json` has no usable credentials. `make bootstrap` (`scripts/local/bootstrap.sh`, using jq) renders it under ignored `.local/keycloak/`, replacing only the exact `__DEMO_USER_PASSWORD__` and `__USER_SERVICE_ADMIN_SECRET__` string values. `make check` validates the template invariants listed below.

- One realm, separate public SPA clients, PKCE S256, no implicit/password grants.
- Permissions are roles on owning resource clients; realm composite roles bundle them.
- Storefront role-scope mappings include CUSTOMER only; admin scope mappings include staff roles.
- Audience mappers target the declared call graph. Services validate their own audience and `typ=Bearer`.
- Fictional seed users use a generated development password, available in ignored `.env`; no real email is sent.
- Runtime `user-service-admin` client intentionally has **no realm-management grants**. Cursor must implement and verify least-privilege administration in Phase 1, not solve it with realm-admin.
- First boot imports only a missing realm. Changing this template does not update an existing realm automatically; use a controlled reconciliation script in Phase 1. Do not delete volumes to force an import.
- Local bootstrap relaxations: email verification off; no SMTP; MFA not yet configured; localhost wildcard callback paths bounded to each specific port. Phase 1 must tighten callbacks to actual paths, wire a mail catcher and verification, and test staff MFA.
- Test default CUSTOMER assignment on real self-registration and prove the dual-role user's storefront token cannot acquire staff roles by requesting scopes. Realm JSON is a starter, not evidence of a verified login flow.
