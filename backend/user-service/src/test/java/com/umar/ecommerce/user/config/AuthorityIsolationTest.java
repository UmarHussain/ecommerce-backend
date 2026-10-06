package com.umar.ecommerce.user.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuthorityIsolationTest {

    @Test
    void ignoresOtherClientsAndUnknownPermissions() {
        Jwt jwt = Jwt.withTokenValue("fixture").header("alg", "RS256").subject("staff")
                .claim("realm_access", Map.of("roles", List.of("PLATFORM_ADMIN")))
                .claim("resource_access", Map.of(
                        "untrusted-client", Map.of("roles", List.of("user.create")),
                        "user-service", Map.of("roles", List.of("user.read", "realm-admin", "catalog.create"))
                ))
                .build();
        var token = new SecurityConfig().jwtAuthenticationConverter().convert(jwt);
        assertNotNull(token);
        assertEquals(Set.of("PERM_user.read"), new HashSet<>(token.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .toList()));
    }

    @Test
    void portalEntryIsSeparateFromOperationAuthorities() {
        Jwt jwt = Jwt.withTokenValue("fixture").header("alg", "RS256").subject("staff")
                .claim("resource_access", Map.of(
                        "api-gateway", Map.of("roles", List.of("admin.access", "user.read")),
                        "user-service", Map.of("roles", List.of("user.read", "admin.access")),
                        "catalog-service", Map.of("roles", List.of("catalog.read")),
                        "admin-portal-backend", Map.of("roles", List.of("user.create"))
                ))
                .build();
        var token = new SecurityConfig().jwtAuthenticationConverter().convert(jwt);
        assertNotNull(token);
        assertEquals(Set.of("PERM_user.read", "PERM_admin.access"), new HashSet<>(token.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .toList()));
    }
}
