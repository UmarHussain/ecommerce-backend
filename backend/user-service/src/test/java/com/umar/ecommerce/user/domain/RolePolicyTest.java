package com.umar.ecommerce.user.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RolePolicyTest {

    @Test
    void userAdminAllowlistExcludesPrivilegedRoles() {
        assertThat(RolePolicy.USER_ADMIN_ASSIGNABLE)
                .contains(RolePolicy.CATALOG_EDITOR, RolePolicy.INVENTORY_MANAGER)
                .doesNotContain(RolePolicy.USER_ADMIN, RolePolicy.PLATFORM_ADMIN);
    }

    @Test
    void unknownPermissionsAreRejected() {
        assertThat(RolePolicy.unknownPermissions(Set.of("catalog.read", "invented.permission")))
                .containsExactly("invented.permission");
    }

    @Test
    void privilegedPermissionsAreDetected() {
        assertThat(RolePolicy.containsPrivilegedPermission(Set.of("catalog.read", "role.manage"))).isTrue();
        assertThat(RolePolicy.containsPrivilegedPermission(Set.of("catalog.read"))).isFalse();
    }
}
