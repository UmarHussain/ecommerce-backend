package com.umar.ecommerce.user.infrastructure.keycloak;

import com.umar.ecommerce.user.application.port.UserDirectoryPort;
import com.umar.ecommerce.user.domain.RolePolicy;
import com.umar.ecommerce.user.exception.ConflictException;
import com.umar.ecommerce.user.exception.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class KeycloakUserDirectoryAdapter implements UserDirectoryPort {

    private final KeycloakAdminClient client;
    private final ConcurrentHashMap<String, String> clientIds = new ConcurrentHashMap<>();

    public KeycloakUserDirectoryAdapter(KeycloakAdminClient client) {
        this.client = client;
    }

    @Override
    public Optional<Identity> findBySubject(String subject) {
        try {
            return Optional.of(toIdentity(userBySubject(subject)));
        } catch (NotFoundException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Identity> findByUsername(String username) {
        return findExact("username", username);
    }

    @Override
    public Optional<Identity> findByEmail(String email) {
        return findExact("email", email);
    }

    @Override
    public List<Identity> search(String query, int first, int max) {
        String path = "/users?first=" + first + "&max=" + max;
        if (query != null && !query.isBlank()) {
            path += "&search=" + url(query);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> found = client.get(path, List.class);
        List<Identity> identities = new ArrayList<>();
        for (Map<String, Object> row : found) {
            identities.add(toIdentity(row));
        }
        return identities;
    }

    @Override
    public Identity createUser(CreateUserCommand command) {
        Optional<Identity> existing = findByEmail(command.email());
        if (existing.isPresent()) {
            throw new ConflictException("A Keycloak identity with that email already exists");
        }
        existing = findByUsername(command.username());
        if (existing.isPresent()) {
            throw new ConflictException("A Keycloak identity with that username already exists");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", command.username());
        body.put("email", command.email());
        body.put("firstName", command.firstName());
        body.put("lastName", command.lastName());
        body.put("enabled", command.enabled());
        body.put("emailVerified", false);
        if (command.temporaryPassword() != null && !command.temporaryPassword().isBlank()) {
            body.put("credentials", List.of(Map.of(
                    "type", "password",
                    "value", command.temporaryPassword(),
                    "temporary", true
            )));
            body.put("requiredActions", List.of("UPDATE_PASSWORD"));
        }
        client.postForLocation("/users", body);
        return findByUsername(command.username())
                .or(() -> findByEmail(command.email()))
                .orElseThrow(() -> new ConflictException(
                        "USER_DIRECTORY_UNCERTAIN",
                        "Keycloak accepted user creation but the identity is not yet readable"
                ));
    }

    @Override
    public void setEnabled(String subject, boolean enabled) {
        Map<String, Object> user = userBySubject(subject);
        user.put("enabled", enabled);
        client.put("/users/" + user.get("id"), user);
    }

    @Override
    public Set<String> directApplicationRoles(String subject) {
        String id = keycloakUserId(subject);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> roles = client.get("/users/" + id + "/role-mappings/realm", List.class);
        return names(roles);
    }

    @Override
    public Set<String> effectiveApplicationRoles(String subject) {
        String id = keycloakUserId(subject);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> roles = client.get("/users/" + id + "/role-mappings/realm/composite", List.class);
        Set<String> names = names(roles);
        names.retainAll(RolePolicy.BUILTIN);
        return names;
    }

    @Override
    public void assignApplicationRoles(String subject, Set<String> roleNames) {
        if (roleNames.isEmpty()) {
            return;
        }
        String id = keycloakUserId(subject);
        client.post("/users/" + id + "/role-mappings/realm", roleRepresentations(roleNames));
    }

    @Override
    public void removeApplicationRoles(String subject, Set<String> roleNames) {
        if (roleNames.isEmpty()) {
            return;
        }
        String id = keycloakUserId(subject);
        client.delete("/users/" + id + "/role-mappings/realm", roleRepresentations(roleNames));
    }

    @Override
    public void assignClientRoles(String subject, String clientId, Set<String> roleNames) {
        if (roleNames.isEmpty()) {
            return;
        }
        String userId = keycloakUserId(subject);
        String clientUuid = clientUuid(clientId);
        client.post(
                "/users/" + userId + "/role-mappings/clients/" + clientUuid,
                clientRoleRepresentations(clientUuid, roleNames)
        );
    }

    @Override
    public void removeClientRoles(String subject, String clientId, Set<String> roleNames) {
        if (roleNames.isEmpty()) {
            return;
        }
        String userId = keycloakUserId(subject);
        String clientUuid = clientUuid(clientId);
        client.delete(
                "/users/" + userId + "/role-mappings/clients/" + clientUuid,
                clientRoleRepresentations(clientUuid, roleNames)
        );
    }

    @Override
    public List<RoleDefinition> listApplicationRoles() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> roles = client.get("/roles?max=100", List.class);
        List<RoleDefinition> definitions = new ArrayList<>();
        for (Map<String, Object> role : roles) {
            String name = string(role.get("name"));
            if (!RolePolicy.isBuiltin(name)) {
                continue;
            }
            Set<String> composites = new LinkedHashSet<>();
            if (Boolean.TRUE.equals(role.get("composite"))) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = client.get("/roles/" + url(name) + "/composites", List.class);
                for (Map<String, Object> child : children) {
                    if (Boolean.TRUE.equals(child.get("clientRole"))) {
                        composites.add(string(child.get("name")));
                    }
                }
            }
            definitions.add(new RoleDefinition(name, Boolean.TRUE.equals(role.get("composite")), composites));
        }
        return definitions;
    }

    @Override
    public int countUsersWithRole(String roleName) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> users = client.get("/roles/" + url(roleName) + "/users?max=200", List.class);
        return users.size();
    }

    private Optional<Identity> findExact(String field, String value) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> found = client.get(
                "/users?" + field + "=" + url(value) + "&exact=true&max=5",
                List.class
        );
        return firstIdentity(found);
    }

    private Optional<Identity> firstIdentity(List<Map<String, Object>> found) {
        if (found == null || found.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toIdentity(found.getFirst()));
    }

    private Identity toIdentity(Map<String, Object> row) {
        return new Identity(
                string(row.get("id")),
                string(row.get("username")),
                string(row.get("email")),
                string(row.get("firstName")),
                string(row.get("lastName")),
                !Boolean.FALSE.equals(row.get("enabled")),
                Boolean.TRUE.equals(row.get("emailVerified"))
        );
    }

    private Map<String, Object> userBySubject(String subject) {
        @SuppressWarnings("unchecked")
        Map<String, Object> user = client.get("/users/" + url(subject), Map.class);
        if (user == null || user.get("id") == null) {
            throw new NotFoundException("Directory identity was not found");
        }
        return user;
    }

    private String keycloakUserId(String subject) {
        return string(userBySubject(subject).get("id"));
    }

    private List<Map<String, Object>> roleRepresentations(Set<String> roleNames) {
        List<Map<String, Object>> representations = new ArrayList<>();
        for (String name : roleNames) {
            @SuppressWarnings("unchecked")
            Map<String, Object> role = client.get("/roles/" + url(name), Map.class);
            representations.add(role);
        }
        return representations;
    }

    private List<Map<String, Object>> clientRoleRepresentations(String clientUuid, Set<String> roleNames) {
        List<Map<String, Object>> representations = new ArrayList<>();
        for (String name : roleNames) {
            @SuppressWarnings("unchecked")
            Map<String, Object> role = client.get("/clients/" + clientUuid + "/roles/" + url(name), Map.class);
            representations.add(role);
        }
        return representations;
    }

    private String clientUuid(String clientId) {
        return clientIds.computeIfAbsent(clientId, id -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> clients = client.get("/clients?clientId=" + url(id), List.class);
            if (clients == null || clients.isEmpty()) {
                throw new NotFoundException("Keycloak client " + id + " was not found");
            }
            return string(clients.getFirst().get("id"));
        });
    }

    private static Set<String> names(List<Map<String, Object>> roles) {
        Set<String> names = new LinkedHashSet<>();
        if (roles == null) {
            return names;
        }
        for (Map<String, Object> role : roles) {
            String name = string(role.get("name"));
            if (!name.isBlank() && !name.startsWith("default-roles-")) {
                names.add(name);
            }
        }
        return names;
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String url(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }
}
