package com.umar.ecommerce.inventory.service;

import com.umar.ecommerce.inventory.exception.InventoryProblem;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.Map;

public record Actor(String issuer, String subject) {

    public static Actor from(JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();
        if (jwt.getIssuer() == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new InventoryProblem(
                    HttpStatus.UNAUTHORIZED,
                    InventoryProblem.AUTHENTICATION_REQUIRED,
                    "A valid bearer token is required"
            );
        }
        return new Actor(jwt.getIssuer().toString(), jwt.getSubject());
    }

    public static boolean hasClientRole(Jwt jwt, String clientId, String role) {
        Object access = jwt.getClaim("resource_access");
        if (!(access instanceof Map<?, ?> resources)) {
            return false;
        }
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
