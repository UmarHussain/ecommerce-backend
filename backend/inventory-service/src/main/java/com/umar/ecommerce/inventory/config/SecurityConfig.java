package com.umar.ecommerce.inventory.config;

import com.umar.ecommerce.inventory.web.SecurityProblemWriter;
import org.springframework.beans.factory.annotation.Value;
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
public class SecurityConfig {

    private static final Set<String> INVENTORY_ROLES = Set.of("inventory.read", "inventory.adjust");

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            SecurityProblemWriter problemWriter
    ) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/circuitbreakers",
                                "/actuator/circuitbreakers/**",
                                "/actuator/retries",
                                "/actuator/retries/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/inventory/stock-items").hasAuthority("PERM_inventory.read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/inventory/stock-items/*/adjustments").hasAuthority("PERM_inventory.read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/inventory/stock-items/*").hasAuthority("PERM_inventory.read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/inventory/stock-items").hasAuthority("PERM_inventory.adjust")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/inventory/stock-items/*/adjustments").hasAuthority("PERM_inventory.adjust")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(problemWriter)
                        .accessDeniedHandler(problemWriter))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(problemWriter)
                        .accessDeniedHandler(problemWriter))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${platform.security.issuer-uri:http://localhost:8180/realms/ecommerce-local}") String issuer,
            @Value("${platform.security.jwk-set-uri:http://localhost:8180/realms/ecommerce-local/protocol/openid-connect/certs}") String jwks,
            @Value("${platform.security.audience:inventory-service}") String audience
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
        OAuth2TokenValidator<Jwt> intended = jwt -> jwt.getAudience().contains(audience)
                && "Bearer".equals(jwt.getClaimAsString("typ"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Access token or audience rejected", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                intended
        ));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Set<GrantedAuthority> authorities = new LinkedHashSet<>();
            Object access = jwt.getClaim("resource_access");
            if (access instanceof Map<?, ?> resources
                    && resources.get("inventory-service") instanceof Map<?, ?> entry
                    && entry.get("roles") instanceof Collection<?> roles) {
                for (Object role : roles) {
                    if (role instanceof String value && INVENTORY_ROLES.contains(value)) {
                        authorities.add(new SimpleGrantedAuthority("PERM_" + value));
                    }
                }
            }
            return authorities;
        });
        return converter;
    }
}
