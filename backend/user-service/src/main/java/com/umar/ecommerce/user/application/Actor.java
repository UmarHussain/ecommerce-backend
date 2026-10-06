package com.umar.ecommerce.user.application;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Set;
import java.util.stream.Collectors;

public record Actor(String issuer, String subject, String email, String displayName, Set<String> permissions) {

    public static Actor from(JwtAuthenticationToken authentication) {
        Jwt token = authentication.getToken();
        String email = token.getClaimAsString("email");
        String name = token.getClaimAsString("name");
        if (name == null || name.isBlank()) {
            String given = token.getClaimAsString("given_name");
            String family = token.getClaimAsString("family_name");
            name = ((given == null ? "" : given) + " " + (family == null ? "" : family)).trim();
        }
        if (name.isBlank()) {
            name = email == null ? token.getSubject() : email;
        }
        Set<String> permissions = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
        return new Actor(
                token.getIssuer().toString(),
                token.getSubject(),
                email,
                name,
                permissions
        );
    }

    public boolean has(String permission) {
        return permissions.contains(permission);
    }

    public boolean canManageRoles() {
        return has("PERM_role.manage");
    }
}
