package com.umar.ecommerce.user.domain;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Application role names are Keycloak realm composites. USER_ADMIN may assign
 * only the non-administrative allowlist. PLATFORM_ADMIN (role.manage) may grant
 * privileged bundles. Custom bundles live in userdb and expand to catalog
 * permissions; they are not created as Keycloak realm roles at runtime.
 */
public final class RolePolicy {

    public static final String CUSTOMER = "CUSTOMER";
    public static final String CATALOG_VIEWER = "CATALOG_VIEWER";
    public static final String CATALOG_CREATOR = "CATALOG_CREATOR";
    public static final String CATALOG_EDITOR = "CATALOG_EDITOR";
    public static final String INVENTORY_MANAGER = "INVENTORY_MANAGER";
    public static final String INVENTORY_READER = "INVENTORY_READER";
    public static final String ORDER_MANAGER = "ORDER_MANAGER";
    public static final String USER_ADMIN = "USER_ADMIN";
    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    public static final Set<String> BUILTIN = Set.of(
            CUSTOMER,
            CATALOG_VIEWER,
            CATALOG_CREATOR,
            CATALOG_EDITOR,
            INVENTORY_MANAGER,
            INVENTORY_READER,
            ORDER_MANAGER,
            USER_ADMIN,
            PLATFORM_ADMIN
    );

    public static final Set<String> USER_ADMIN_ASSIGNABLE = Set.of(
            CUSTOMER,
            CATALOG_VIEWER,
            CATALOG_CREATOR,
            CATALOG_EDITOR,
            INVENTORY_MANAGER,
            INVENTORY_READER,
            ORDER_MANAGER
    );

    public static final Set<String> PRIVILEGED_ROLES = Set.of(USER_ADMIN, PLATFORM_ADMIN);

    public static final Set<String> STAFF_ROLES = Set.of(
            CATALOG_VIEWER,
            CATALOG_CREATOR,
            CATALOG_EDITOR,
            INVENTORY_MANAGER,
            INVENTORY_READER,
            ORDER_MANAGER,
            USER_ADMIN,
            PLATFORM_ADMIN
    );

    private RolePolicy() {
    }

    public static boolean isBuiltin(String role) {
        return BUILTIN.contains(role);
    }

    public static boolean isPrivilegedRole(String role) {
        return PRIVILEGED_ROLES.contains(role);
    }

    public static boolean isStaffRole(String role) {
        return STAFF_ROLES.contains(role);
    }

    public static Set<String> unknownPermissions(Set<String> permissions) {
        Set<String> unknown = new LinkedHashSet<>();
        for (String permission : permissions) {
            if (!PermissionCatalog.isKnown(permission)) {
                unknown.add(permission);
            }
        }
        return unknown;
    }

    public static boolean containsPrivilegedPermission(Set<String> permissions) {
        return permissions.stream().anyMatch(PermissionCatalog.PRIVILEGED::contains);
    }
}
