package com.umar.ecommerce.user.application.port;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Keycloak Admin REST boundary. Never stores credentials. Runtime client uses
 * the least-privilege user-service-admin service account, not realm-admin.
 */
public interface UserDirectoryPort {

    record Identity(
            String subject,
            String username,
            String email,
            String firstName,
            String lastName,
            boolean enabled,
            boolean emailVerified
    ) {
    }

    record RoleDefinition(String name, boolean composite, Set<String> clientRoles) {
    }

    record CreateUserCommand(
            String username,
            String email,
            String firstName,
            String lastName,
            String temporaryPassword,
            boolean enabled
    ) {
    }

    Optional<Identity> findBySubject(String subject);

    Optional<Identity> findByUsername(String username);

    Optional<Identity> findByEmail(String email);

    List<Identity> search(String query, int first, int max);

    Identity createUser(CreateUserCommand command);

    void setEnabled(String subject, boolean enabled);

    Set<String> directApplicationRoles(String subject);

    Set<String> effectiveApplicationRoles(String subject);

    void assignApplicationRoles(String subject, Set<String> roleNames);

    void removeApplicationRoles(String subject, Set<String> roleNames);

    void assignClientRoles(String subject, String clientId, Set<String> roleNames);

    void removeClientRoles(String subject, String clientId, Set<String> roleNames);

    List<RoleDefinition> listApplicationRoles();

    int countUsersWithRole(String roleName);
}
