package com.umar.ecommerce.catalog.config;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
 @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter, com.umar.ecommerce.catalog.web.SecurityProblemWriter problemWriter) throws Exception {
  return http.csrf(c -> c.disable())
   .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a -> a
    .requestMatchers("/actuator/health", "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
    .requestMatchers(HttpMethod.GET, "/api/v1/catalog/**").permitAll()
    .requestMatchers(HttpMethod.POST, "/api/v1/catalog/variants/batch").permitAll()
    .requestMatchers(HttpMethod.POST, "/api/v1/admin/catalog/**").hasAuthority("PERM_catalog.create")
    .requestMatchers(HttpMethod.PUT, "/api/v1/admin/catalog/**").hasAuthority("PERM_catalog.update")
    .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/catalog/**").hasAuthority("PERM_catalog.activate")
    .anyRequest().denyAll())
   .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter)).authenticationEntryPoint(problemWriter).accessDeniedHandler(problemWriter))
   .exceptionHandling(e -> e.authenticationEntryPoint(problemWriter).accessDeniedHandler(problemWriter))
   .build();
 }
 @Bean JwtDecoder jwtDecoder(
  @Value("${platform.security.issuer-uri:http://localhost:8180/realms/ecommerce-local}") String issuer,
  @Value("${platform.security.jwk-set-uri:http://localhost:8180/realms/ecommerce-local/protocol/openid-connect/certs}") String jwks,
  @Value("${platform.security.audience:catalog-service}") String audience) {
  NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
  OAuth2TokenValidator<Jwt> intended = jwt -> jwt.getAudience().contains(audience) && "Bearer".equals(jwt.getClaimAsString("typ"))
    ? OAuth2TokenValidatorResult.success()
    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Access token or audience rejected", null));
  decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), intended));
  return decoder;
 }
 @Bean JwtAuthenticationConverter jwtAuthenticationConverter() {
  JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
  converter.setJwtGrantedAuthoritiesConverter(jwt -> {
   Set<GrantedAuthority> authorities = new LinkedHashSet<>();
   Object access = jwt.getClaim("resource_access");
   if (access instanceof Map<?,?> resources) {
    for (String client : List.of("catalog-service")) {
     if (resources.get(client) instanceof Map<?,?> entry && entry.get("roles") instanceof Collection<?> roles) {
      for (Object role : roles) if (role instanceof String value && Set.of("catalog.read","catalog.create","catalog.update","catalog.activate").contains(value))
       authorities.add(new SimpleGrantedAuthority("PERM_" + value));
     }
    }
   }
   return authorities;
  });
  return converter;
 }
}
