package com.umar.ecommerce.user.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Finite, version-controlled permission catalog. Custom role bundles may only
 * reference these names; they cannot invent endpoint behavior.
 */
public final class PermissionCatalog {

    public static final Map<String, String> OWNING_CLIENT = owningClients();

    public static final Set<String> ALL = Set.copyOf(OWNING_CLIENT.keySet());

    public static final Set<String> PRIVILEGED = Set.of(
            "role.manage",
            "user.disable_identity",
            "role.assign",
            "user.manage_staff",
            "user.create",
            "user.update"
    );

    private PermissionCatalog() {
    }

    public static boolean isKnown(String permission) {
        return OWNING_CLIENT.containsKey(permission);
    }

    public static String owningClient(String permission) {
        String client = OWNING_CLIENT.get(permission);
        if (client == null) {
            throw new IllegalArgumentException("Unknown permission: " + permission);
        }
        return client;
    }

    private static Map<String, String> owningClients() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("admin.access", "api-gateway");
        map.put("profile.read_own", "user-service");
        map.put("profile.update_own", "user-service");
        map.put("user.read", "user-service");
        map.put("user.create", "user-service");
        map.put("user.update", "user-service");
        map.put("user.manage_staff", "user-service");
        map.put("user.disable_identity", "user-service");
        map.put("role.read", "user-service");
        map.put("role.assign", "user-service");
        map.put("role.manage", "user-service");
        map.put("catalog.read", "catalog-service");
        map.put("catalog.create", "catalog-service");
        map.put("catalog.update", "catalog-service");
        map.put("catalog.activate", "catalog-service");
        map.put("inventory.read", "inventory-service");
        map.put("inventory.adjust", "inventory-service");
        map.put("cart.read_own", "cart-service");
        map.put("cart.write_own", "cart-service");
        map.put("order.create", "order-service");
        map.put("order.read_own", "order-service");
        map.put("order.cancel_own", "order-service");
        map.put("order.read_all", "order-service");
        map.put("order.process", "order-service");
        map.put("order.dispatch", "order-service");
        map.put("order.deliver", "order-service");
        map.put("order.cancel_any", "order-service");
        return Map.copyOf(map);
    }
}
