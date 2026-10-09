package com.umar.ecommerce.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain security(ServerHttpSecurity http, CorsConfigurationSource corsConfigurationSource) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(AccessTokenRules::coarseAuthorities);
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(auth -> auth
                        .pathMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/store/catalog/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/store/catalog/variants/batch").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/store/me", "/api/v1/store/me/**").hasAuthority("PERM_profile.read_own")
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/store/me").hasAuthority("PERM_profile.update_own")
                        .pathMatchers(HttpMethod.POST, "/api/v1/store/me/addresses").hasAuthority("PERM_profile.update_own")
                        .pathMatchers(HttpMethod.PUT, "/api/v1/store/me/addresses/**").hasAuthority("PERM_profile.update_own")
                        .pathMatchers(HttpMethod.DELETE, "/api/v1/store/me/addresses/**").hasAuthority("PERM_profile.update_own")
                        .pathMatchers(HttpMethod.GET, "/api/v1/store/cart").hasAuthority("PERM_cart.read_own")
                        .pathMatchers(HttpMethod.PUT, "/api/v1/store/cart/items/**").hasAuthority("PERM_cart.write_own")
                        .pathMatchers(HttpMethod.DELETE, "/api/v1/store/cart/items/**").hasAuthority("PERM_cart.write_own")
                        .pathMatchers(HttpMethod.DELETE, "/api/v1/store/cart").hasAuthority("PERM_cart.write_own")
                        .pathMatchers(HttpMethod.POST, "/api/v1/store/orders/quotes").hasAuthority("PERM_order.create")
                        .pathMatchers(HttpMethod.POST, "/api/v1/store/orders").hasAuthority("PERM_order.create")
                        .pathMatchers(HttpMethod.GET, "/api/v1/store/orders").hasAuthority("PERM_order.read_own")
                        .pathMatchers(HttpMethod.GET, "/api/v1/store/orders/*").hasAuthority("PERM_order.read_own")
                        .pathMatchers(HttpMethod.POST, "/api/v1/store/orders/*/cancel").hasAuthority("PERM_order.cancel_own")
                        .pathMatchers("/api/v1/admin/**").hasAuthority("PERM_admin.access")
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(
                        new ReactiveJwtAuthenticationConverterAdapter(converter)
                )))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", AccessTokenRules.apiCors());
        return source;
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder(
            @Value("${platform.security.issuer-uri}") String issuer,
            @Value("${platform.security.jwk-set-uri}") String jwks
    ) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwks).build();
        decoder.setJwtValidator(AccessTokenRules.validator(issuer));
        return decoder;
    }
}
