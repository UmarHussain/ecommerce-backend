package com.umar.ecommerce.user.domain;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Permission names for admin UI rendering. This list is not the authority set
 * user-service uses to authorize its own operations. Each permission counts only
 * when it appears on its owning client.
 */
public final class UiPermissionSummary {

    private UiPermissionSummary() {
    }

    public static List<String> from(Jwt jwt) {
        Object access = jwt.getClaim("resource_access");
        if (!(access instanceof Map<?, ?> resources)) {
            return List.of();
        }
        List<String> summary = new ArrayList<>();
        for (Map.Entry<String, String> entry : PermissionCatalog.OWNING_CLIENT.entrySet()) {
            if (clientHasRole(resources, entry.getValue(), entry.getKey())) {
                summary.add("PERM_" + entry.getKey());
            }
        }
        return List.copyOf(summary);
    }

    public static boolean clientHasRole(Map<?, ?> resources, String clientId, String role) {
        if (!(resources.get(clientId) instanceof Map<?, ?> entry)) {
            return false;
        }
        if (!(entry.get("roles") instanceof Collection<?> roles)) {
            return false;
        }
        for (Object candidate : roles) {
            if (role.equals(candidate)) {
                return true;
            }
        }
        return false;
    }
}
