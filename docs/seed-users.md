# Seed users for WSL2 Keycloak

Run `make bootstrap` then `make infra-up` from WSL2. The project Keycloak container imports these fictional users on the first startup of the realm.

All seeded application users use the generated `DEMO_USER_PASSWORD` in the ignored root `.env`. The Keycloak administration console has a separate generated `KEYCLOAK_ADMIN_PASSWORD`. No password is committed or printed by setup. How to turn one of these users into an access token and call the API from Postman: [postman-user-token.md](postman-user-token.md).

| Username / email | Roles | What to test |
|---|---|---|
| customer@example.test | CUSTOMER | Own profile/cart/orders; denied admin |
| catalog-creator@example.test | CATALOG_CREATOR | Create allowed; update denied |
| catalog-editor@example.test | CATALOG_EDITOR | Update allowed; create denied |
| inventory-manager@example.test | INVENTORY_MANAGER | Stock setup and adjustment; denied user administration |
| inventory-reader@example.test | INVENTORY_READER | Stock read and history; setup and adjustment denied; no `catalog.read` |
| user-admin@example.test | USER_ADMIN | Allowed staff onboarding; no privileged escalation |
| platform-admin@example.test | PLATFORM_ADMIN | Application-wide staff permissions; not Keycloak realm-admin |
| dual-role@example.test | CUSTOMER, CATALOG_CREATOR | Same identity in storefront/admin; storefront token excludes staff permissions |
| customer-two@example.test | CUSTOMER | Cross-customer ownership isolation |
| catalog-viewer@example.test | CATALOG_VIEWER | Read-only catalog staff |
| catalog-manager@example.test | CATALOG_CREATOR, CATALOG_EDITOR | Combined catalog create/edit |
| order-manager@example.test | ORDER_MANAGER | Order operations later; no arbitrary paid-state edit |

These fixtures seed Keycloak identities and roles only. User-service PostgreSQL profile rows are created on first authenticated `/me` or admin search/onboarding. Do not seed password copies in userdb.

If you already run another Keycloak in WSL2, leave it untouched. This starter runs an isolated container at port 8180. To deliberately use your existing instance, first configure a non-conflicting application realm and update issuer/JWKS URLs and SPA clients; do not import into master or overwrite an existing realm.

Changing the seed template after the realm exists does not automatically reseed it. Use `make realm-reconcile` to add missing clients, roles, scope mappings, and seed users without deleting existing users or resetting passwords.
