package com.umar.ecommerce.user.web.dto;

import java.util.Set;
import java.util.UUID;

public record RoleDefinitionResponse(
        String name,
        String kind,
        String description,
        Set<String> permissions,
        UUID bundleId,
        boolean privileged
) {
}
