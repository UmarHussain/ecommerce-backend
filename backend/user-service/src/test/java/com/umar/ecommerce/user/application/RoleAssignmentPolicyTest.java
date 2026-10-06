package com.umar.ecommerce.user.application;

import com.umar.ecommerce.user.domain.RolePolicy;
import com.umar.ecommerce.user.entity.RoleBundle;
import com.umar.ecommerce.user.exception.ForbiddenOperationException;
import com.umar.ecommerce.user.repository.RoleBundleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoleAssignmentPolicyTest {

    private RoleBundleRepository bundles;
    private RoleAssignmentPolicy policy;

    @BeforeEach
    void setUp() {
        bundles = mock(RoleBundleRepository.class);
        policy = new RoleAssignmentPolicy(bundles);
        when(bundles.findByNameIgnoreCase(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
    }

    @Test
    void userAdminCannotAssignPlatformAdmin() {
        Actor actor = new Actor("iss", "admin-1", "user-admin@example.test", "UA", Set.of("PERM_role.assign"));
        assertThatThrownBy(() -> policy.assertAssignable(actor, "other", Set.of(RolePolicy.PLATFORM_ADMIN)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void userAdminCannotElevateItself() {
        Actor actor = new Actor("iss", "admin-1", "user-admin@example.test", "UA", Set.of("PERM_role.assign"));
        assertThatThrownBy(() -> policy.assertAssignable(actor, "admin-1", Set.of(RolePolicy.PLATFORM_ADMIN)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void userAdminCanAssignCatalogEditor() {
        Actor actor = new Actor("iss", "admin-1", "user-admin@example.test", "UA", Set.of("PERM_role.assign"));
        policy.assertAssignable(actor, "other", Set.of(RolePolicy.CATALOG_EDITOR));
    }

    @Test
    void platformAdminCanAssignPrivilegedRole() {
        Actor actor = new Actor("iss", "plat-1", "platform-admin@example.test", "PA", Set.of("PERM_role.manage"));
        policy.assertAssignable(actor, "other", Set.of(RolePolicy.USER_ADMIN));
    }

    @Test
    void customPrivilegedBundleIsRejectedForUserAdmin() {
        RoleBundle bundle = new RoleBundle("SecurityLead", "x", Set.of("role.manage", "catalog.read"));
        when(bundles.findByNameIgnoreCase("SecurityLead")).thenReturn(Optional.of(bundle));
        Actor actor = new Actor("iss", "admin-1", "user-admin@example.test", "UA", Set.of("PERM_role.assign"));
        assertThatThrownBy(() -> policy.assertAssignable(actor, "other", Set.of("SecurityLead")))
                .isInstanceOf(ForbiddenOperationException.class);
    }
}
