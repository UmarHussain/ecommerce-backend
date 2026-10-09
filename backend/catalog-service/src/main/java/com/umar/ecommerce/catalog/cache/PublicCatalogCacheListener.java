package com.umar.ecommerce.catalog.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs only after the catalog write commits. A rollback does not publish
 * this phase, so a failed write leaves the previous cache entries in place.
 * If Redis rejects the eviction, the committed write is still successful and
 * the region TTL is the bound on staleness.
 */
@Component
public class PublicCatalogCacheListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicCatalogCacheListener.class);

    private final PublicCacheEvictor evictor;

    public PublicCatalogCacheListener(PublicCacheEvictor evictor) {
        this.evictor = evictor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(PublicCatalogChanged event) {
        try {
            evictor.evictPublicRegions();
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "catalog cache eviction failed after commit correlationId={} reason={} error={}",
                    MDC.get("correlationId"),
                    event.reason(),
                    exception.toString()
            );
        }
    }
}
