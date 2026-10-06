package com.umar.ecommerce.user.application;

import com.umar.ecommerce.user.application.port.UserDirectoryPort;
import com.umar.ecommerce.user.config.KeycloakAdminProperties;
import com.umar.ecommerce.user.domain.OnboardingStatus;
import com.umar.ecommerce.user.domain.OperationStatus;
import com.umar.ecommerce.user.domain.OperationType;
import com.umar.ecommerce.user.domain.RolePolicy;
import com.umar.ecommerce.user.entity.AppUser;
import com.umar.ecommerce.user.entity.IdentityOperation;
import com.umar.ecommerce.user.entity.StaffProfile;
import com.umar.ecommerce.user.exception.DirectoryUnavailableException;
import com.umar.ecommerce.user.exception.ForbiddenOperationException;
import com.umar.ecommerce.user.exception.NotFoundException;
import com.umar.ecommerce.user.repository.StaffProfileRepository;
import com.umar.ecommerce.user.web.dto.CreateUserRequest;
import com.umar.ecommerce.user.web.dto.OnboardStaffRequest;
import com.umar.ecommerce.user.web.dto.OperationResponse;
import com.umar.ecommerce.user.web.dto.PageResponse;
import com.umar.ecommerce.user.web.dto.RoleChangeRequest;
import com.umar.ecommerce.user.web.dto.StaffProfileResponse;
import com.umar.ecommerce.user.web.dto.UserDetailResponse;
import com.umar.ecommerce.user.web.dto.UserSummaryResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class UserAdministrationService {

    private final UserDirectoryPort directory;
    private final ProfileService profiles;
    private final IdentityOperationService operations;
    private final AccessAuditService audit;
    private final RoleAssignmentPolicy policy;
    private final RoleBundleService bundles;
    private final StaffProfileRepository staff;
    private final AdministrationLock lock;
    private final KeycloakAdminProperties keycloak;

    public UserAdministrationService(
            UserDirectoryPort directory,
            ProfileService profiles,
            IdentityOperationService operations,
            AccessAuditService audit,
            RoleAssignmentPolicy policy,
            RoleBundleService bundles,
            StaffProfileRepository staff,
            AdministrationLock lock,
            KeycloakAdminProperties keycloak
    ) {
        this.directory = directory;
        this.profiles = profiles;
        this.operations = operations;
        this.audit = audit;
        this.policy = policy;
        this.bundles = bundles;
        this.staff = staff;
        this.lock = lock;
        this.keycloak = keycloak;
    }

    public PageResponse<UserSummaryResponse> search(String query, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 0);
        List<UserDirectoryPort.Identity> identities = directory.search(query, safePage * safeSize, safeSize);
        List<UserSummaryResponse> items = new ArrayList<>();
        for (UserDirectoryPort.Identity identity : identities) {
            items.add(summarize(identity));
        }
        return new PageResponse<>(items, safePage, safeSize, items.size());
    }

    public UserDetailResponse get(UUID userId) {
        var profile = profiles.getById(userId);
        UserDirectoryPort.Identity identity = directory.findBySubject(profile.subject())
                .orElseThrow(() -> new NotFoundException("Directory identity was not found"));
        return detail(identity, userId);
    }

    public Object createUser(Actor actor, CreateUserRequest request, String idempotencyKey, String correlationId) {
        Set<String> roles = request.roles() == null ? Set.of() : Set.copyOf(request.roles());
        policy.assertAssignable(actor, "new-user", roles);
        String canonical = "CREATE_USER|" + request.email().toLowerCase() + "|" + roles;
        IdentityOperation operation = operations.begin(
                idempotencyKey,
                canonical,
                OperationType.CREATE_USER,
                request.email()
        );
        if (operation.getStatus() == OperationStatus.SUCCEEDED) {
            return profiles.getById(operation.getResultUserId());
        }
        if (operation.getStatus() == OperationStatus.PENDING || operation.getStatus() == OperationStatus.UNCERTAIN) {
            return completeCreate(actor, request, roles, operation, correlationId);
        }
        return OperationResponse.from(operation);
    }

    public Object onboardStaff(
            Actor actor,
            UUID userId,
            OnboardStaffRequest request,
            String idempotencyKey,
            String correlationId
    ) {
        var profile = profiles.getById(userId);
        Set<String> roles = request.roles() == null ? Set.of() : Set.copyOf(request.roles());
        policy.assertAssignable(actor, profile.subject(), roles);
        IdentityOperation operation = operations.begin(
                idempotencyKey,
                "ONBOARD|" + userId + "|" + roles,
                OperationType.ONBOARD_STAFF,
                profile.subject()
        );
        if (operation.getStatus() == OperationStatus.SUCCEEDED) {
            return get(userId);
        }
        return lock.call(() -> applyStaffOnboarding(actor, profile.subject(), userId, request, roles, operation, correlationId));
    }

    public Object assignRoles(
            Actor actor,
            UUID userId,
            RoleChangeRequest request,
            String idempotencyKey,
            String correlationId
    ) {
        var profile = profiles.getById(userId);
        Set<String> roles = Set.copyOf(request.roles());
        policy.assertAssignable(actor, profile.subject(), roles);
        IdentityOperation operation = operations.begin(
                idempotencyKey,
                "ASSIGN|" + userId + "|" + roles,
                OperationType.ASSIGN_ROLES,
                profile.subject()
        );
        if (operation.getStatus() == OperationStatus.SUCCEEDED) {
            return get(userId);
        }
        return lock.call(() -> mutateRoles(actor, profile.subject(), userId, roles, true, operation, correlationId));
    }

    public Object removeRoles(
            Actor actor,
            UUID userId,
            Set<String> roles,
            String idempotencyKey,
            String correlationId
    ) {
        var profile = profiles.getById(userId);
        Set<String> requested = Set.copyOf(roles);
        policy.assertAssignable(actor, profile.subject(), requested);
        IdentityOperation operation = operations.begin(
                idempotencyKey,
                "REMOVE|" + userId + "|" + requested,
                OperationType.REMOVE_ROLES,
                profile.subject()
        );
        if (operation.getStatus() == OperationStatus.SUCCEEDED) {
            return get(userId);
        }
        return lock.call(() -> mutateRoles(actor, profile.subject(), userId, requested, false, operation, correlationId));
    }

    public Object suspendStaff(Actor actor, UUID userId, String idempotencyKey, String correlationId) {
        var profile = profiles.getById(userId);
        IdentityOperation operation = operations.begin(
                idempotencyKey,
                "SUSPEND|" + userId,
                OperationType.SUSPEND_STAFF,
                profile.subject()
        );
        if (operation.getStatus() == OperationStatus.SUCCEEDED) {
            return get(userId);
        }
        return lock.call(() -> {
            Set<String> current = directory.directApplicationRoles(profile.subject());
            if (policy.removesLastPlatformAdmin(current, Set.of(RolePolicy.PLATFORM_ADMIN), directory.countUsersWithRole(RolePolicy.PLATFORM_ADMIN))) {
                operations.fail(operation.getId(), "Cannot suspend the last platform administrator");
                throw new ForbiddenOperationException("Cannot suspend the last platform administrator");
            }
            operations.markInProgress(operation.getId());
            try {
                Set<String> staffRoles = new LinkedHashSet<>(current);
                staffRoles.retainAll(RolePolicy.STAFF_ROLES);
                directory.removeApplicationRoles(profile.subject(), staffRoles);
                staff.findById(userId).ifPresent(StaffProfile::suspend);
                operations.succeed(operation.getId(), userId);
                audit.record(actor, userId, "SUSPEND_STAFF", Map.of("roles", current), Map.of("removed", staffRoles), "SUCCEEDED", correlationId);
                return get(userId);
            } catch (DirectoryUnavailableException exception) {
                operations.uncertain(operation.getId(), exception.getMessage());
                audit.record(actor, userId, "SUSPEND_STAFF", Map.of(), Map.of(), "UNCERTAIN", correlationId);
                return OperationResponse.from(operations.get(operation.getId()));
            }
        });
    }

    public void reconcile(IdentityOperation operation) {
        if (operation.getStatus() != OperationStatus.UNCERTAIN) {
            return;
        }
        operations.markInProgress(operation.getId());
        try {
            Optional<UserDirectoryPort.Identity> identity = switch (operation.getOperationType()) {
                case CREATE_USER -> directory.findByEmail(operation.getTargetIdentity())
                        .or(() -> directory.findByUsername(operation.getTargetIdentity()));
                default -> directory.findBySubject(operation.getTargetIdentity());
            };
            if (identity.isEmpty()) {
                operations.fail(operation.getId(), "Directory has no matching identity; retry the original request");
                return;
            }
            AppUser user = profiles.requireLinked(
                    issuer(),
                    identity.get().subject(),
                    displayName(identity.get()),
                    identity.get().email()
            );
            operations.succeed(operation.getId(), user.getId());
        } catch (DirectoryUnavailableException exception) {
            operations.uncertain(operation.getId(), exception.getMessage());
        }
    }

    private Object completeCreate(
            Actor actor,
            CreateUserRequest request,
            Set<String> roles,
            IdentityOperation operation,
            String correlationId
    ) {
        operations.markInProgress(operation.getId());
        try {
            UserDirectoryPort.Identity identity = directory.findByEmail(request.email())
                    .orElseGet(() -> directory.createUser(new UserDirectoryPort.CreateUserCommand(
                            request.email(),
                            request.email(),
                            request.firstName(),
                            request.lastName(),
                            request.temporaryPassword(),
                            true
                    )));
            applyRoleSet(identity.subject(), roles, true);
            AppUser user = profiles.requireLinked(issuer(), identity.subject(), displayName(identity), identity.email());
            if (request.onboardAsStaff()) {
                profiles.ensureStaffProfile(user.getId(), request.employeeReference(), request.department());
            }
            operations.succeed(operation.getId(), user.getId());
            audit.record(
                    actor,
                    user.getId(),
                    "CREATE_USER",
                    Map.of(),
                    Map.of("email", request.email(), "roles", roles, "existingIdentityReused", true),
                    "SUCCEEDED",
                    correlationId
            );
            return profiles.getById(user.getId());
        } catch (DirectoryUnavailableException exception) {
            operations.uncertain(operation.getId(), exception.getMessage());
            audit.record(actor, null, "CREATE_USER", Map.of(), Map.of("email", request.email()), "UNCERTAIN", correlationId);
            return OperationResponse.from(operations.get(operation.getId()));
        }
    }

    private Object applyStaffOnboarding(
            Actor actor,
            String subject,
            UUID userId,
            OnboardStaffRequest request,
            Set<String> roles,
            IdentityOperation operation,
            String correlationId
    ) {
        operations.markInProgress(operation.getId());
        try {
            applyRoleSet(subject, roles, true);
            profiles.ensureStaffProfile(userId, request.employeeReference(), request.department());
            operations.succeed(operation.getId(), userId);
            audit.record(actor, userId, "ONBOARD_STAFF", Map.of(), Map.of("roles", roles), "SUCCEEDED", correlationId);
            return get(userId);
        } catch (DirectoryUnavailableException exception) {
            operations.uncertain(operation.getId(), exception.getMessage());
            audit.record(actor, userId, "ONBOARD_STAFF", Map.of(), Map.of(), "UNCERTAIN", correlationId);
            return OperationResponse.from(operations.get(operation.getId()));
        }
    }

    private Object mutateRoles(
            Actor actor,
            String subject,
            UUID userId,
            Set<String> roles,
            boolean assign,
            IdentityOperation operation,
            String correlationId
    ) {
        Set<String> current = directory.directApplicationRoles(subject);
        if (!assign && policy.removesLastPlatformAdmin(current, roles, directory.countUsersWithRole(RolePolicy.PLATFORM_ADMIN))) {
            operations.fail(operation.getId(), "Cannot remove PLATFORM_ADMIN from the last platform administrator");
            throw new ForbiddenOperationException("Cannot remove PLATFORM_ADMIN from the last platform administrator");
        }
        operations.markInProgress(operation.getId());
        try {
            applyRoleSet(subject, roles, assign);
            operations.succeed(operation.getId(), userId);
            audit.record(
                    actor,
                    userId,
                    assign ? "ASSIGN_ROLES" : "REMOVE_ROLES",
                    Map.of("roles", current),
                    Map.of("roles", roles),
                    "SUCCEEDED",
                    correlationId
            );
            return get(userId);
        } catch (DirectoryUnavailableException exception) {
            operations.uncertain(operation.getId(), exception.getMessage());
            audit.record(actor, userId, assign ? "ASSIGN_ROLES" : "REMOVE_ROLES", Map.of(), Map.of(), "UNCERTAIN", correlationId);
            return OperationResponse.from(operations.get(operation.getId()));
        }
    }

    private void applyRoleSet(String subject, Set<String> roles, boolean assign) {
        Set<String> builtin = policy.builtinRoles(roles);
        if (assign) {
            directory.assignApplicationRoles(subject, builtin);
        } else {
            directory.removeApplicationRoles(subject, builtin);
        }
        bundles.clientRolesForNames(roles).forEach((client, permissions) -> {
            if (assign) {
                directory.assignClientRoles(subject, client, permissions);
            } else {
                directory.removeClientRoles(subject, client, permissions);
            }
        });
    }

    private UserSummaryResponse summarize(UserDirectoryPort.Identity identity) {
        AppUser user = profiles.requireLinked(issuer(), identity.subject(), displayName(identity), identity.email());
        Optional<StaffProfile> staffProfile = staff.findById(user.getId());
        return new UserSummaryResponse(
                user.getId(),
                identity.subject(),
                identity.email(),
                displayName(identity),
                identity.enabled(),
                staffProfile.isPresent(),
                staffProfile.map(item -> item.getOnboardingStatus().name()).orElse(OnboardingStatus.ACTIVE.name()),
                directory.directApplicationRoles(identity.subject())
        );
    }

    private UserDetailResponse detail(UserDirectoryPort.Identity identity, UUID userId) {
        Optional<StaffProfile> staffProfile = staff.findById(userId);
        return new UserDetailResponse(
                summarize(identity),
                directory.directApplicationRoles(identity.subject()),
                directory.effectiveApplicationRoles(identity.subject()),
                staffProfile.map(item -> new StaffProfileResponse(
                        item.getEmployeeReference(),
                        item.getDepartment(),
                        item.getOnboardingStatus().name()
                )).orElse(null)
        );
    }

    private String issuer() {
        String base = keycloak.getTokenUri();
        int marker = base.indexOf("/protocol/");
        return marker > 0 ? base.substring(0, marker) : "http://localhost:8180/realms/" + keycloak.getRealm();
    }

    private static String displayName(UserDirectoryPort.Identity identity) {
        String name = ((identity.firstName() == null ? "" : identity.firstName()) + " "
                + (identity.lastName() == null ? "" : identity.lastName())).trim();
        if (!name.isBlank()) {
            return name;
        }
        return identity.email() == null || identity.email().isBlank() ? identity.username() : identity.email();
    }
}
