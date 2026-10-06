package com.umar.ecommerce.user.web.dto;

import java.util.List;

/**
 * Admin UI principal. {@code permissions} is a display summary assembled from
 * each permission's owning client. It is not the authority set used to authorize
 * user-service operations. {@code profile} is the application profile object.
 */
public record AdminPrincipalResponse(
        String subject,
        String issuer,
        String email,
        List<String> permissions,
        List<String> realmRoles,
        ProfileResponse profile
) {
}
