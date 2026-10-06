package com.umar.ecommerce.user.config;

import com.umar.ecommerce.user.domain.UiPermissionSummary;
import com.umar.ecommerce.user.web.SecurityProblemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(KeycloakAdminProperties.class)
public class SecurityConfig {

    static final String PORTAL_ENTRY = "admin.access";
    static final String PORTAL_ENTRY_CLIENT = "api-gateway";
    /**
     * Already issued tokens may still carry this client until they expire.
     * The role is the same portal-entry permission, not an operation permission.
     */
    static final String RETIRED_PORTAL_ENTRY_CLIENT = "admin-portal-backend";

    private static final Set<String> ALLOWED = Set.of(
            "profile.read_own",
            "profile.update_own",
            "user.read",
            "user.create",
            "user.update",
            "user.manage_staff",
            "user.disable_identity",
            "role.read",
            "role.assign",
            "role.manage"
    );

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            SecurityProblemWriter problemWriter
    ) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me", "/api/v1/users/me/**").hasAuthority("PERM_profile.read_own")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/me").hasAuthority("PERM_profile.update_own")
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/me/addresses").hasAuthority("PERM_profile.update_own")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/me/addresses/**").hasAuthority("PERM_profile.update_own")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/me/addresses/**").hasAuthority("PERM_profile.update_own")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/me").hasAuthority("PERM_admin.access")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/users/*/roles").hasAuthority("PERM_role.read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/users/*/roles").hasAuthority("PERM_role.assign")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/users/*/roles/**").hasAuthority("PERM_role.assign")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/users/*/staff/suspend").hasAuthority("PERM_user.manage_staff")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/users/*/staff").hasAuthority("PERM_user.manage_staff")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/users", "/api/v1/admin/users/*").hasAuthority("PERM_user.read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/users").hasAuthority("PERM_user.create")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/roles").hasAuthority("PERM_role.read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/roles").hasAuthority("PERM_role.manage")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/operations/*").hasAnyAuthority(
                                "PERM_user.create",
                                "PERM_user.manage_staff",
                                "PERM_role.assign",
                                "PERM_role.manage"
                        )
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(problemWriter)
                        .accessDeniedHandler(problemWriter))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(problemWriter).accessDeniedHandler(problemWriter))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${platform.security.issuer-uri:http://localhost:8180/realms/ecommerce-local}") String issuer,
            @Value("${platform.security.jwk-set-uri:http://localhost:8180/realms/ecommerce-local/protocol/openid-connect/certs}") String jwks,
            @Value("${platform.security.audience:user-service}") String audience
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
        OAuth2TokenValidator<Jwt> intended = jwt -> jwt.getAudience().contains(audience)
                && "Bearer".equals(jwt.getClaimAsString("typ"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Access token or audience rejected", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), intended));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Set<GrantedAuthority> authorities = new LinkedHashSet<>();
            Object access = jwt.getClaim("resource_access");
            if (access instanceof Map<?, ?> resources) {
                if (resources.get("user-service") instanceof Map<?, ?> entry
                        && entry.get("roles") instanceof Collection<?> roles) {
                    for (Object role : roles) {
                        if (role instanceof String value && ALLOWED.contains(value)) {
                            authorities.add(new SimpleGrantedAuthority("PERM_" + value));
                        }
                    }
                }
                if (UiPermissionSummary.clientHasRole(resources, PORTAL_ENTRY_CLIENT, PORTAL_ENTRY)
                        || UiPermissionSummary.clientHasRole(resources, RETIRED_PORTAL_ENTRY_CLIENT, PORTAL_ENTRY)) {
                    authorities.add(new SimpleGrantedAuthority("PERM_" + PORTAL_ENTRY));
                }
            }
            return authorities;
        });
        return converter;
    }
}
