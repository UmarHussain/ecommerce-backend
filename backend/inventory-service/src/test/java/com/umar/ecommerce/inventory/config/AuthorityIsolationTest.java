package com.umar.ecommerce.inventory.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthorityIsolationTest {

    @Test
    void mapsOnlyInventoryClientRoles() {
        Jwt jwt = Jwt.withTokenValue("fixture").header("alg", "none").subject("staff")
                .claim("resource_access", Map.of(
                        "catalog-service", Map.of("roles", List.of("catalog.read", "inventory.read")),
                        "inventory-service", Map.of("roles", List.of("inventory.read", "inventory.adjust", "realm-admin")),
                        "untrusted-client", Map.of("roles", List.of("inventory.adjust"))
                ))
                .build();

        Set<String> authorities = new SecurityConfig().jwtAuthenticationConverter().convert(jwt)
                .getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertEquals(Set.of("PERM_inventory.read", "PERM_inventory.adjust"), authorities);
    }
}
