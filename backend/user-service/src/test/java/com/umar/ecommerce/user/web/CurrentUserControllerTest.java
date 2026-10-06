package com.umar.ecommerce.user.web;

import com.umar.ecommerce.user.application.ProfileService;
import com.umar.ecommerce.user.application.UserAdministrationService;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({CurrentUserController.class, AdminUserController.class})
@Import({SecurityConfig.class, SecurityProblemWriter.class})
class CurrentUserControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ProfileService profiles;
    @MockitoBean UserAdministrationService administration;
    @MockitoBean com.umar.ecommerce.user.application.IdentityOperationService operations;
    @MockitoBean com.umar.ecommerce.user.application.port.UserDirectoryPort directory;

    @Test
    void ownProfileRequiresReadPermission() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void forgedIdentityHeadersDoNotAuthenticate() throws Exception {
        mvc.perform(get("/api/v1/users/me")
                        .header("X-User-Id", "someone-else")
                        .header("X-Roles", "PLATFORM_ADMIN"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(profiles);
    }

    @Test
    void customerCanReadOwnProfile() throws Exception {
        when(profiles.initializeOwnProfile(any())).thenReturn(new ProfileResponse(
                UUID.randomUUID(), "iss", "sub-1", "customer@example.test", "Customer",
                Map.of(), List.of(), null, true
        ));
        mvc.perform(get("/api/v1/users/me")
                        .with(jwt().jwt(jwt -> jwt.subject("sub-1").issuer("http://localhost:8180/realms/ecommerce-local")
                                .claim("email", "customer@example.test"))
                                .authorities(new SimpleGrantedAuthority("PERM_profile.read_own"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePersistenceImplemented").value(true));
    }

    @Test
    void customerCannotReadAdminUsers() throws Exception {
        mvc.perform(get("/api/v1/admin/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("PERM_profile.read_own"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(administration);
    }

    @Test
    void updateRequiresOwnUpdatePermission() throws Exception {
        mvc.perform(patch("/api/v1/users/me")
                        .contentType("application/json")
                        .content("{\"displayName\":\"A\"}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("PERM_profile.read_own"))))
                .andExpect(status().isForbidden());
    }
}
