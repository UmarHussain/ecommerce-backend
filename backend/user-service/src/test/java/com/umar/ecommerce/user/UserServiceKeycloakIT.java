package com.umar.ecommerce.user;

import com.umar.ecommerce.user.application.port.UserDirectoryPort;
import com.umar.ecommerce.user.config.KeycloakAdminProperties;
import com.umar.ecommerce.user.domain.RolePolicy;
import com.umar.ecommerce.user.infrastructure.keycloak.KeycloakAdminClient;
import com.umar.ecommerce.user.infrastructure.keycloak.KeycloakUserDirectoryAdapter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live Keycloak Admin adapter checks. Requires Keycloak on localhost:8180 and
 * {@code KEYCLOAK_ADMIN_CLIENT_SECRET}. Skipped otherwise so offline {@code mvn test}
 * stays green. {@code make security-check} is the primary issued-token gate.
 */
class UserServiceKeycloakIT {

    private static UserDirectoryPort directory;

    @BeforeAll
    static void connect() {
        assumeTrue(keycloakUp(), "Keycloak is not reachable at localhost:8180");
        String secret = System.getenv("KEYCLOAK_ADMIN_CLIENT_SECRET");
        assumeTrue(secret != null && !secret.isBlank(), "KEYCLOAK_ADMIN_CLIENT_SECRET is not set");
        KeycloakAdminProperties properties = new KeycloakAdminProperties();
        properties.setTokenUri("http://localhost:8180/realms/ecommerce-local/protocol/openid-connect/token");
        properties.setAdminBaseUri("http://localhost:8180/admin/realms/ecommerce-local");
        properties.setClientSecret(secret);
        directory = new KeycloakUserDirectoryAdapter(new KeycloakAdminClient(properties));
    }

    @Test
    void findsSeededCustomerAndListsApplicationRoles() {
        var identity = directory.findByUsername("customer@example.test");
        assertThat(identity).isPresent();
        assertThat(identity.get().email()).isEqualTo("customer@example.test");
        assertThat(directory.effectiveApplicationRoles(identity.get().subject())).contains(RolePolicy.CUSTOMER);
        assertThat(directory.listApplicationRoles().stream().map(UserDirectoryPort.RoleDefinition::name))
                .contains(RolePolicy.CUSTOMER, RolePolicy.USER_ADMIN, RolePolicy.PLATFORM_ADMIN);
        assertThat(directory.countUsersWithRole(RolePolicy.PLATFORM_ADMIN)).isGreaterThanOrEqualTo(1);
    }

    private static boolean keycloakUp() {
        try {
            HttpResponse<Void> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build()
                    .send(
                            HttpRequest.newBuilder(URI.create("http://localhost:8180/realms/ecommerce-local/.well-known/openid-configuration"))
                                    .timeout(Duration.ofSeconds(2))
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.discarding()
                    );
            return response.statusCode() == 200;
        } catch (Exception exception) {
            return false;
        }
    }
}
