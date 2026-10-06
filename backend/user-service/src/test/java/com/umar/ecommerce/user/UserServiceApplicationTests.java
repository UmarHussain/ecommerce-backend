package com.umar.ecommerce.user;

import com.umar.ecommerce.user.application.port.UserDirectoryPort;
import com.umar.ecommerce.user.repository.AccessAuditRepository;
import com.umar.ecommerce.user.repository.AppUserRepository;
import com.umar.ecommerce.user.repository.CustomerAddressRepository;
import com.umar.ecommerce.user.repository.CustomerProfileRepository;
import com.umar.ecommerce.user.repository.IdentityOperationRepository;
import com.umar.ecommerce.user.repository.RoleBundleRepository;
import com.umar.ecommerce.user.repository.StaffProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/unused",
        "DATABASE_USERNAME=unused",
        "DATABASE_PASSWORD=unused",
        "spring.task.scheduling.enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
class UserServiceApplicationTests {

    @MockitoBean private AppUserRepository appUserRepository;
    @MockitoBean private CustomerProfileRepository customerProfileRepository;
    @MockitoBean private CustomerAddressRepository customerAddressRepository;
    @MockitoBean private StaffProfileRepository staffProfileRepository;
    @MockitoBean private IdentityOperationRepository identityOperationRepository;
    @MockitoBean private AccessAuditRepository accessAuditRepository;
    @MockitoBean private RoleBundleRepository roleBundleRepository;
    @MockitoBean private UserDirectoryPort userDirectoryPort;
    @MockitoBean private com.umar.ecommerce.user.application.AdministrationLock administrationLock;

    @Test
    void contextLoads() {
    }
}
