# Contract backlog

Implemented: catalog OpenAPI annotations and user-service OpenAPI. The gateway rewrites the storefront paths below and forwards admin paths unchanged. There is no portal backend.

| Browser path | Gateway auth | Destination |
|---|---|---|
| `GET /api/v1/store/catalog/products` | public | catalog-service `GET /api/v1/catalog/products` |
| `GET /api/v1/store/catalog/categories` | public | catalog-service `GET /api/v1/catalog/categories` |
| `GET/PATCH /api/v1/store/me` | `PERM_profile.read_own` / `update_own` | user-service `/api/v1/users/me` |
| `POST /api/v1/store/me/addresses` and `PUT/DELETE .../{addressId}` | `PERM_profile.update_own` | user-service `/api/v1/users/me/addresses...` |
| `GET /api/v1/admin/me` | `PERM_admin.access` | user-service. Profile is a JSON object, not a string |
| `GET/POST /api/v1/admin/users` | coarse `admin.access`, then `user.read` / `user.create` | user-service, same path |
| `GET /api/v1/admin/users/{id}/roles` | coarse `admin.access`, then `role.read` | user-service, same path |
| `POST /api/v1/admin/users/{id}/staff` | `user.manage_staff` | user-service |
| `POST /api/v1/admin/users/{id}/roles` | `role.assign` | user-service |
| `GET/POST /api/v1/admin/roles` | `role.read` / `role.manage` | user-service |
| `GET /api/v1/admin/operations/{id}` | create, manage-staff, assign, or manage | user-service |
| `POST/PUT/PATCH /api/v1/admin/catalog/...` | coarse `admin.access`, then catalog create/update/activate | catalog-service, same path |

`GET /api/v1/admin/users/{id}/roles` is authorized with `role.read` and is registered before the single-segment user read rule, so `user.read` does not grant role reads.

`GET /api/v1/admin/me` returns `subject`, `issuer`, `email`, `permissions`, `realmRoles`, and `profile`. `profile` is the profile object from `ProfileService` (previously a raw JSON string, or null when the portal's downstream call failed). Failures from profile initialization are returned as user-service errors. The admin UI reads `permissions` only.

Uncertain Keycloak outcomes return `202` with `Location: /api/v1/admin/operations/{id}`. Problem Details carry `code` and `correlationId`. JPA entities are not exposed.

Future Kafka envelopes and Saga transitions are specified in MASTER_PROMPT.md; no event contracts are claimed implemented yet.
