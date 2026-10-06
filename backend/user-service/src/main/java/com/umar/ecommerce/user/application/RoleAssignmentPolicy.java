package com.umar.ecommerce.user.application;

import com.umar.ecommerce.user.domain.RolePolicy;
import com.umar.ecommerce.user.entity.RoleBundle;
import com.umar.ecommerce.user.exception.ForbiddenOperationException;
import com.umar.ecommerce.user.exception.InvalidRequestException;
import com.umar.ecommerce.user.repository.RoleBundleRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Component
public class RoleAssignmentPolicy {

    private final RoleBundleRepository bundles;

    public RoleAssignmentPolicy(RoleBundleRepository bundles) {
        this.bundles = bundles;
    }

    public void assertAssignable(Actor actor, String targetSubject, Set<String> roles) {
        for (String role : roles) {
            Optional<RoleBundle> bundle = bundles.findByNameIgnoreCase(role);
            boolean privileged = RolePolicy.isPrivilegedRole(role)
                    || bundle.map(item -> RolePolicy.containsPrivilegedPermission(item.getPermissions())).orElse(false);
            if (privileged && !actor.canManageRoles()) {
                throw new ForbiddenOperationException(
                        "USER_ADMIN cannot assign PLATFORM_ADMIN, USER_ADMIN, or privileged custom bundles"
                );
            }
            if (RolePolicy.isBuiltin(role)) {
                if (!privileged && !RolePolicy.USER_ADMIN_ASSIGNABLE.contains(role) && !actor.canManageRoles()) {
                    throw new ForbiddenOperationException("Role " + role + " is not on the USER_ADMIN allowlist");
                }
                continue;
            }
            if (bundle.isEmpty()) {
                throw new InvalidRequestException("Unknown application role or bundle: " + role);
            }
        }
        if (actor.subject().equals(targetSubject) && containsPrivileged(roles) && !actor.canManageRoles()) {
            throw new ForbiddenOperationException("A user administrator cannot elevate itself");
        }
    }

    public Set<String> builtinRoles(Set<String> names) {
        Set<String> builtin = new LinkedHashSet<>();
        for (String name : names) {
            if (RolePolicy.isBuiltin(name)) {
                builtin.add(name);
            }
        }
        return builtin;
    }

    public boolean containsPrivileged(Set<String> roles) {
        for (String role : roles) {
            if (RolePolicy.isPrivilegedRole(role)) {
                return true;
            }
            Optional<RoleBundle> bundle = bundles.findByNameIgnoreCase(role);
            if (bundle.isPresent() && RolePolicy.containsPrivilegedPermission(bundle.get().getPermissions())) {
                return true;
            }
        }
        return false;
    }

    public boolean removesLastPlatformAdmin(Set<String> currentRoles, Set<String> rolesToRemove, int platformAdminCount) {
        return currentRoles.contains(RolePolicy.PLATFORM_ADMIN)
                && rolesToRemove.contains(RolePolicy.PLATFORM_ADMIN)
                && platformAdminCount <= 1;
    }
}
