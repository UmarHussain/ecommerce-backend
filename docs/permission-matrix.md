# Permission matrix

Application roles are Keycloak realm composites. Permissions are client roles on the owning API client and become Spring `PERM_*` authorities.

`admin.access` is the portal-entry permission, stored as a client role on `api-gateway`. The gateway requires it for `/api/v1/admin/**`. user-service requires it only for `GET /api/v1/admin/me`. It does not replace `user.read`, `role.read`, or any other operation permission. The `permissions` array on `/api/v1/admin/me` is a UI summary: each name is included only when it is present on its owning client, and that list is not the authority set user-service uses to authorize its own APIs.

| Role | Permissions |
|---|---|
| CUSTOMER | `profile.read_own`, `profile.update_own`, `cart.read_own`, `cart.write_own`, `order.create`, `order.read_own`, `order.cancel_own` |
| CATALOG_VIEWER | `admin.access`, `catalog.read` |
| CATALOG_CREATOR | `admin.access`, `catalog.read`, `catalog.create` |
| CATALOG_EDITOR | `admin.access`, `catalog.read`, `catalog.update`, `catalog.activate` |
| INVENTORY_MANAGER | `admin.access`, `catalog.read`, `inventory.read`, `inventory.adjust` |
| INVENTORY_READER | `admin.access`, `inventory.read` |
| ORDER_MANAGER | `admin.access`, `order.read_all`, `order.process`, `order.dispatch`, `order.deliver`, `order.cancel_any` |
| USER_ADMIN | `admin.access`, `user.read`, `user.create`, `user.update`, `user.manage_staff`, `role.read`, `role.assign` |
| PLATFORM_ADMIN | All staff permissions above plus `role.manage` and `user.disable_identity` |

USER_ADMIN may assign `CUSTOMER` and the catalog/inventory/order staff bundles. It cannot assign `USER_ADMIN` or `PLATFORM_ADMIN`, cannot elevate itself, and cannot create custom bundles. Custom bundles are stored in user-service and expand to catalog permissions; they are not created as Keycloak realm roles (that would need `manage-realm`).

Storefront tokens include CUSTOMER only. A dual-role person must use the admin client to receive staff permissions.

Catalog administration reads require `catalog.read` on the `catalog-service` client. `catalog.create`, `catalog.update`, and `catalog.activate` do not grant reads, and the same permission names on another client are ignored. Create requests cannot set activation; that stays on the status commands.

Inventory reads and history require `inventory.read` on the `inventory-service` client. Setup and adjustment require `inventory.adjust`. Setup also requires `catalog.read` on the `catalog-service` client in the same access token. inventory-service does not map that catalog role into an inventory authority. `INVENTORY_READER` can read stock and cannot set it up. `catalog-viewer@example.test` is not a pure inventory reader; that account has also carried `CATALOG_EDITOR` in this checkout. Use `inventory-reader@example.test` for a token with only `inventory.read`.
