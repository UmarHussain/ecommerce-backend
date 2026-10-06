package com.umar.ecommerce.user.web;

import com.umar.ecommerce.user.application.IdentityOperationService;
import com.umar.ecommerce.user.application.ProfileService;
import com.umar.ecommerce.user.application.RoleBundleService;
import com.umar.ecommerce.user.application.UserAdministrationService;
import com.umar.ecommerce.user.application.port.UserDirectoryPort;
import com.umar.ecommerce.user.config.SecurityConfig;
import com.umar.ecommerce.user.web.dto.ProfileResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AdminUserController.class, AdminRoleController.class, AdminPrincipalController.class})
@Import({SecurityConfig.class, SecurityProblemWriter.class})
class AdminUserControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean UserAdministrationService administration;
    @MockitoBean IdentityOperationService operations;
    @MockitoBean RoleBundleService bundles;
    @MockitoBean ProfileService profiles;
    @MockitoBean UserDirectoryPort directory;

    @Test
    void customerTokenIsForbiddenOnAdminUsers() throws Exception {
        mvc.perform(get("/api/v1/admin/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("PERM_profile.read_own"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void userAdminCannotCreatePrivilegedBundle() throws Exception {
        mvc.perform(post("/api/v1/admin/roles")
                        .contentType("application/json")
                        .content("{\"name\":\"Lead\",\"permissions\":[\"role.manage\"]}")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("PERM_role.read"),
                                new SimpleGrantedAuthority("PERM_role.assign")
                        )))
                .andExpect(status().isForbidden());
        verify(bundles, never()).create(any(), any(), any());
    }

    @Test
    void userAdminCanSearchUsers() throws Exception {
        mvc.perform(get("/api/v1/admin/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("PERM_user.read"))))
                .andExpect(status().isOk());
        verify(administration).search(eq(null), eq(0), eq(20));
    }

    @Test
    void portalEntryDoesNotGrantUserAdministration() throws Exception {
        mvc.perform(get("/api/v1/admin/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("PERM_admin.access"))))
                .andExpect(status().isForbidden());
        verify(administration, never()).search(any(), anyInt(), anyInt());
    }

    @Test
    void roleReadIsNotShadowedByUserRead() throws Exception {
        UUID userId = UUID.randomUUID();
        mvc.perform(get("/api/v1/admin/users/{userId}/roles", userId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("PERM_user.read"))))
                .andExpect(status().isForbidden());
        verify(administration, never()).get(any());

        mvc.perform(get("/api/v1/admin/users/{userId}/roles", userId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("PERM_role.read"))))
                .andExpect(status().isOk());
        verify(administration).get(userId);
    }

    @Test
    void adminMeReturnsStructuredProfileAndOwningClientPermissions() throws Exception {
        when(profiles.initializeOwnProfile(any())).thenReturn(new ProfileResponse(
                UUID.randomUUID(), "http://localhost:8180/realms/ecommerce-local", "staff-1",
                "staff@example.test", "Staff", Map.of(), List.of(), null, true
        ));
        mvc.perform(get("/api/v1/admin/me").with(jwt()
                        .jwt(token -> token.subject("staff-1")
                                .issuer("http://localhost:8180/realms/ecommerce-local")
                                .claim("email", "staff@example.test")
                                .claim("realm_access", Map.of("roles", List.of("CATALOG_VIEWER")))
                                .claim("resource_access", Map.of(
                                        "api-gateway", Map.of("roles", List.of("admin.access")),
                                        "catalog-service", Map.of("roles", List.of("catalog.read")),
                                        "user-service", Map.of("roles", List.of("profile.read_own")),
                                        "untrusted-client", Map.of("roles", List.of("user.read"))
                                )))
                        .authorities(new SimpleGrantedAuthority("PERM_admin.access"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("staff-1"))
                .andExpect(jsonPath("$.email").value("staff@example.test"))
                .andExpect(jsonPath("$.realmRoles[0]").value("CATALOG_VIEWER"))
                .andExpect(jsonPath("$.permissions[?(@ == 'PERM_admin.access')]").exists())
                .andExpect(jsonPath("$.permissions[?(@ == 'PERM_catalog.read')]").exists())
                .andExpect(jsonPath("$.permissions[?(@ == 'PERM_profile.read_own')]").exists())
                .andExpect(jsonPath("$.permissions[?(@ == 'PERM_user.read')]").doesNotExist())
                .andExpect(jsonPath("$.profile.profilePersistenceImplemented").value(true))
                .andExpect(jsonPath("$.profile.id").isNotEmpty());
    }

    @Test
    void customerCannotReadAdminPrincipal() throws Exception {
        mvc.perform(get("/api/v1/admin/me")
                        .with(jwt().authorities(new SimpleGrantedAuthority("PERM_profile.read_own"))))
                .andExpect(status().isForbidden());
        verify(profiles, never()).initializeOwnProfile(any());
    }
}
