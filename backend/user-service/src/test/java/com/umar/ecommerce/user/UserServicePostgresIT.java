package com.umar.ecommerce.user;

import com.umar.ecommerce.user.application.Actor;
import com.umar.ecommerce.user.application.ProfileService;
import com.umar.ecommerce.user.application.port.UserDirectoryPort;
import com.umar.ecommerce.user.repository.AppUserRepository;
import com.umar.ecommerce.user.web.dto.ProfileResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@Testcontainers
class UserServicePostgresIT {

    private static final String USER_NAME = "user_app";
    private static final String USER_PASSWORD = "test_" + UUID.randomUUID().toString().replace("-", "");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16.10-alpine")
    ).withDatabaseName("user_test");

    static {
        POSTGRES.start();
        bootstrapSchema();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> USER_NAME);
        registry.add("spring.datasource.password", () -> USER_PASSWORD);
        registry.add("spring.datasource.hikari.schema", () -> "user");
        registry.add("spring.flyway.default-schema", () -> "user");
        registry.add("spring.flyway.schemas", () -> "user");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "user");
        registry.add("platform.keycloak.client-secret", () -> "unused");
        registry.add("spring.task.scheduling.enabled", () -> "false");
    }

    @MockitoBean
    private UserDirectoryPort directory;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ProfileService profiles;
    @Autowired
    private AppUserRepository users;

    @Test
    void migratesAsUserRoleAndCreatesUniqueIdentityLink() {
        assertThat(jdbcTemplate.queryForObject("select current_user", String.class)).isEqualTo(USER_NAME);
        assertThat(jdbcTemplate.queryForObject("select count(*) from flyway_schema_history where success", Integer.class))
                .isGreaterThanOrEqualTo(1);
        Actor actor = new Actor(
                "http://localhost:8180/realms/ecommerce-local",
                "subject-one",
                "one@example.test",
                "One",
                Set.of("PERM_profile.read_own")
        );
        ProfileResponse first = profiles.initializeOwnProfile(actor);
        ProfileResponse second = profiles.initializeOwnProfile(actor);
        assertThat(first.id()).isEqualTo(second.id());
        assertThat(users.findByIssuerAndSubject(actor.issuer(), actor.subject())).isPresent();
        assertThat(jdbcTemplate.queryForObject("select count(*) from app_user", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from customer_profile", Integer.class)).isEqualTo(1);
    }

    private static void bootstrapSchema() {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        ); Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + USER_NAME + " LOGIN PASSWORD '" + USER_PASSWORD + "'");
            statement.execute("CREATE SCHEMA \"user\" AUTHORIZATION " + USER_NAME);
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
