package com.umar.ecommerce.user.application;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Serializes last-admin and privileged role mutations so two concurrent
 * removals cannot drop the last PLATFORM_ADMIN.
 */
@Component
public class AdministrationLock {

    private static final long LOCK_KEY = 481_516_234L;
    private final DataSource dataSource;

    public AdministrationLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public <T> T call(LockedWork<T> work) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT pg_advisory_lock(" + LOCK_KEY + ")");
            try {
                return work.get();
            } finally {
                statement.execute("SELECT pg_advisory_unlock(" + LOCK_KEY + ")");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not acquire administration lock", exception);
        }
    }

    @FunctionalInterface
    public interface LockedWork<T> {
        T get();
    }
}
