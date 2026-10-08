package com.umar.ecommerce.catalog.service;

import com.umar.ecommerce.catalog.exception.ResourceConflictException;

/**
 * Compares the caller's expected version with the row loaded inside the write transaction.
 * JPA {@code @Version} still rejects a race that commits after this check.
 */
final class VersionGuard {

    private VersionGuard() {
    }

    static void requireCurrent(long current, long expected) {
        if (current != expected) {
            throw new ResourceConflictException(
                    ResourceConflictException.STALE_VERSION,
                    "Catalog data changed since it was loaded; reload and review the current values"
            );
        }
    }
}
