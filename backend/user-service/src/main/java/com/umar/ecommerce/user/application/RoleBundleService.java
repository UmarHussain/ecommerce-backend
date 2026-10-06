package com.umar.ecommerce.user.application;

import com.umar.ecommerce.user.application.port.UserDirectoryPort;
import com.umar.ecommerce.user.domain.PermissionCatalog;
import com.umar.ecommerce.user.domain.RolePolicy;
import com.umar.ecommerce.user.entity.RoleBundle;
import com.umar.ecommerce.user.exception.ConflictException;
import com.umar.ecommerce.user.exception.InvalidRequestException;
import com.umar.ecommerce.user.repository.RoleBundleRepository;
import com.umar.ecommerce.user.web.dto.CreateRoleBundleRequest;
import com.umar.ecommerce.user.web.dto.RoleDefinitionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RoleBundleService {

    private final RoleBundleRepository bundles;
    private final UserDirectoryPort directory;
    private final AccessAuditService audit;

    public RoleBundleService(
            RoleBundleRepository bundles,
            UserDirectoryPort directory,
            AccessAuditService audit
    ) {
        this.bundles = bundles;
        this.directory = directory;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<RoleDefinitionResponse> list() {
        List<RoleDefinitionResponse> result = new ArrayList<>();
        for (var definition : directory.listApplicationRoles()) {
            result.add(new RoleDefinitionResponse(
                    definition.name(),
                    "REALM",
                    null,
                    definition.clientRoles(),
                    null,
                    RolePolicy.isPrivilegedRole(definition.name())
            ));
        }
        for (RoleBundle bundle : bundles.findAll()) {
            result.add(toResponse(bundle));
        }
        return result;
    }

    @Transactional
    public RoleDefinitionResponse create(Actor actor, CreateRoleBundleRequest request, String correlationId) {
        if (RolePolicy.isBuiltin(request.name())) {
            throw new ConflictException("Cannot replace a built-in application role");
        }
        if (bundles.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("A custom role bundle with that name already exists");
        }
        Set<String> permissions = new LinkedHashSet<>(request.permissions());
        Set<String> unknown = RolePolicy.unknownPermissions(permissions);
        if (!unknown.isEmpty()) {
            throw new InvalidRequestException("Unknown permissions: " + String.join(", ", unknown));
        }
        if (permissions.stream().anyMatch(permission -> permission.startsWith("realm-")
                || permission.equals("manage-users")
                || permission.equals("manage-realm")
                || permission.equals("realm-admin"))) {
            throw new InvalidRequestException("Custom bundles cannot reference Keycloak administration roles");
        }
        RoleBundle bundle = bundles.save(new RoleBundle(request.name(), request.description(), permissions));
        audit.record(
                actor,
                null,
                "CREATE_ROLE_BUNDLE",
                Map.of(),
                Map.of("name", bundle.getName(), "permissions", bundle.getPermissions()),
                "SUCCEEDED",
                correlationId
        );
        return toResponse(bundle);
    }

    public Map<String, Set<String>> clientRolesForNames(Set<String> names) {
        Map<String, Set<String>> byClient = new LinkedHashMap<>();
        for (String name : names) {
            bundles.findByNameIgnoreCase(name).ifPresent(bundle -> {
                for (String permission : bundle.getPermissions()) {
                    byClient.computeIfAbsent(PermissionCatalog.owningClient(permission), key -> new LinkedHashSet<>())
                            .add(permission);
                }
            });
        }
        return byClient;
    }

    private static RoleDefinitionResponse toResponse(RoleBundle bundle) {
        return new RoleDefinitionResponse(
                bundle.getName(),
                "CUSTOM_BUNDLE",
                bundle.getDescription(),
                bundle.getPermissions(),
                bundle.getId(),
                RolePolicy.containsPrivilegedPermission(bundle.getPermissions())
        );
    }
}
