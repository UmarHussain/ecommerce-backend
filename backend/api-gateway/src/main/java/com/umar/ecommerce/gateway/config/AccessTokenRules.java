package com.umar.ecommerce.gateway.config;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Coarse gateway checks. Operation permissions stay on the destination service.
 * {@code admin.access} is the portal-entry permission on the api-gateway client.
 */
public final class AccessTokenRules {

    static final String AUDIENCE = "api-gateway";
    private static final String PORTAL_ENTRY = "admin.access";
    private static final String RETIRED_PORTAL_CLIENT = "admin-portal-backend";
    private static final Set<String> PROFILE_PERMISSIONS = Set.of("profile.read_own", "profile.update_own");
    private static final Set<String> CART_PERMISSIONS = Set.of("cart.read_own", "cart.write_own");

    private AccessTokenRules() {
    }

    public static OAuth2TokenValidator<Jwt> validator(String issuer) {
        OAuth2TokenValidator<Jwt> intended = jwt -> jwt.getAudience() != null
                && jwt.getAudience().contains(AUDIENCE)
                && "Bearer".equals(jwt.getClaimAsString("typ"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token", "Access token or audience rejected", null));
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), intended);
    }

    public static Collection<GrantedAuthority> coarseAuthorities(Jwt jwt) {
        Set<GrantedAuthority> result = new LinkedHashSet<>();
        Object access = jwt.getClaim("resource_access");
        if (!(access instanceof Map<?, ?> resources)) {
            return result;
        }
        addRoles(result, resources, "user-service", PROFILE_PERMISSIONS);
        addRoles(result, resources, "cart-service", CART_PERMISSIONS);
        if (hasRole(resources, AUDIENCE, PORTAL_ENTRY) || hasRole(resources, RETIRED_PORTAL_CLIENT, PORTAL_ENTRY)) {
            result.add(new SimpleGrantedAuthority("PERM_" + PORTAL_ENTRY));
        }
        return result;
    }

    private static void addRoles(Set<GrantedAuthority> result, Map<?, ?> resources, String client, Set<String> allowed) {
        if (!(resources.get(client) instanceof Map<?, ?> entry) || !(entry.get("roles") instanceof Collection<?> roles)) {
            return;
        }
        for (Object role : roles) {
            if (role instanceof String value && allowed.contains(value)) {
                result.add(new SimpleGrantedAuthority("PERM_" + value));
            }
        }
    }

    private static boolean hasRole(Map<?, ?> resources, String client, String role) {
        if (!(resources.get(client) instanceof Map<?, ?> entry) || !(entry.get("roles") instanceof Collection<?> roles)) {
            return false;
        }
        return roles.contains(role);
    }

    public static List<String> allowedCorsOrigins() {
        return List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:5174",
                "http://127.0.0.1:5174"
        );
    }

    public static CorsConfiguration apiCors() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedCorsOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-ID"));
        cors.setExposedHeaders(List.of("X-Correlation-ID", "Location"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(600L);
        return cors;
    }
}
