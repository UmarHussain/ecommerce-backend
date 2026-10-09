package com.umar.ecommerce.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessTokenRulesTest {

    private static final String ISSUER = "http://localhost:8180/realms/ecommerce-local";

    @Test
    void acceptsBearerTokenForTheGatewayAudience() {
        assertFalse(AccessTokenRules.validator(ISSUER).validate(token(
                Instant.now().plusSeconds(60),
                List.of("api-gateway", "user-service"),
                "Bearer"
        )).hasErrors());
    }

    @Test
    void rejectsWrongAudienceExpiredAndIdTokens() {
        assertTrue(AccessTokenRules.validator(ISSUER).validate(token(
                Instant.now().plusSeconds(60),
                List.of("user-service"),
                "Bearer"
        )).hasErrors());
        assertTrue(AccessTokenRules.validator(ISSUER).validate(token(
                Instant.now().minusSeconds(120),
                List.of("api-gateway"),
                "Bearer"
        )).hasErrors());
        assertTrue(AccessTokenRules.validator(ISSUER).validate(token(
                Instant.now().plusSeconds(60),
                List.of("storefront-spa"),
                "ID"
        )).hasErrors());
    }

    @Test
    void coarseAuthoritiesStayOnOwningClients() {
        Jwt jwt = Jwt.withTokenValue("fixture").header("alg", "RS256").subject("staff")
                .claim("resource_access", Map.of(
                        "user-service", Map.of("roles", List.of("profile.read_own", "user.read", "admin.access")),
                        "api-gateway", Map.of("roles", List.of("admin.access", "profile.read_own")),
                        "catalog-service", Map.of("roles", List.of("catalog.create")),
                        "admin-portal-backend", Map.of("roles", List.of("user.create"))
                ))
                .build();
        Set<String> authorities = AccessTokenRules.coarseAuthorities(jwt).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        assertEquals(Set.of("PERM_profile.read_own", "PERM_admin.access"), authorities);
    }

    @Test
    void cartRolesComeOnlyFromTheCartClient() {
        Jwt customer = Jwt.withTokenValue("fixture").header("alg", "RS256").subject("customer")
                .claim("resource_access", Map.of(
                        "cart-service", Map.of("roles", List.of("cart.read_own", "cart.write_own", "order.create")),
                        "api-gateway", Map.of("roles", List.of("admin.access"))
                ))
                .build();
        Set<String> customerAuthorities = AccessTokenRules.coarseAuthorities(customer).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        assertEquals(Set.of("PERM_cart.read_own", "PERM_cart.write_own", "PERM_admin.access"), customerAuthorities);

        Jwt staff = Jwt.withTokenValue("fixture").header("alg", "RS256").subject("staff")
                .claim("resource_access", Map.of(
                        "api-gateway", Map.of("roles", List.of("admin.access")),
                        "catalog-service", Map.of("roles", List.of("cart.read_own"))
                ))
                .build();
        Set<String> staffAuthorities = AccessTokenRules.coarseAuthorities(staff).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        assertEquals(Set.of("PERM_admin.access"), staffAuthorities);
    }

    @Test
    void retiredPortalClientStillCarriesOnlyPortalEntry() {
        Jwt jwt = Jwt.withTokenValue("fixture").header("alg", "RS256").subject("staff")
                .claim("resource_access", Map.of(
                        "admin-portal-backend", Map.of("roles", List.of("admin.access", "user.read"))
                ))
                .build();
        Set<String> authorities = AccessTokenRules.coarseAuthorities(jwt).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        assertEquals(Set.of("PERM_admin.access"), authorities);
    }

    private static Jwt token(Instant expiresAt, List<String> audience, String typ) {
        return Jwt.withTokenValue("fixture")
                .header("alg", "RS256")
                .issuedAt(expiresAt.minusSeconds(60))
                .expiresAt(expiresAt)
                .issuer(ISSUER)
                .audience(audience)
                .claim("typ", typ)
                .subject("user")
                .build();
    }
}
