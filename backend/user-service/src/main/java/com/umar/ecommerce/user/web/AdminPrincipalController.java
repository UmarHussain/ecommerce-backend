package com.umar.ecommerce.user.web;

import com.umar.ecommerce.user.application.Actor;
import com.umar.ecommerce.user.application.ProfileService;
import com.umar.ecommerce.user.domain.UiPermissionSummary;
import com.umar.ecommerce.user.web.dto.AdminPrincipalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Admin principal")
public class AdminPrincipalController {

    private final ProfileService profiles;

    public AdminPrincipalController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/api/v1/admin/me")
    @Operation(summary = "Current staff principal, UI permission summary, and application profile")
    public AdminPrincipalResponse me(JwtAuthenticationToken authentication) {
        Jwt token = authentication.getToken();
        return new AdminPrincipalResponse(
                token.getSubject(),
                token.getIssuer().toString(),
                token.getClaimAsString("email"),
                UiPermissionSummary.from(token),
                realmRoles(token),
                profiles.initializeOwnProfile(Actor.from(authentication))
        );
    }

    private static List<String> realmRoles(Jwt token) {
        Object claim = token.getClaim("realm_access");
        if (claim instanceof Map<?, ?> realm && realm.get("roles") instanceof List<?> roles) {
            return roles.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }
}
