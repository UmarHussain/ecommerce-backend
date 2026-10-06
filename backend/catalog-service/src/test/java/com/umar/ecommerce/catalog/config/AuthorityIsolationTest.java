package com.umar.ecommerce.catalog.config;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.junit.jupiter.api.Assertions.*;
class AuthorityIsolationTest {
 @Test void ignoresOtherClientsAndUnknownPermissions() {
  Jwt jwt = Jwt.withTokenValue("fixture").header("alg","RS256").subject("customer")
   .claim("realm_access",Map.of("roles",List.of("PLATFORM_ADMIN")))
   .claim("resource_access",Map.of(
    "untrusted-client",Map.of("roles",List.of("catalog.create")),
    "catalog-service",Map.of("roles",List.of("catalog.read","realm-admin","user.create"))))
   .build();
  var token = new SecurityConfig().jwtAuthenticationConverter().convert(jwt);
  assertNotNull(token);
  assertEquals(Set.of("PERM_catalog.read"),new HashSet<>(token.getAuthorities().stream().map(a -> a.getAuthority()).toList()));
 }
}
