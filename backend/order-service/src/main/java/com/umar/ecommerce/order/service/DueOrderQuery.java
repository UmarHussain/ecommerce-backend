package com.umar.ecommerce.order.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DueOrderQuery {

    private final EntityManager entityManager;

    public DueOrderQuery(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<UUID> due(Instant now) {
        @SuppressWarnings("unchecked")
        List<Object> ids = entityManager.createNativeQuery("""
                SELECT id FROM customer_order
                WHERE deadline_at IS NOT NULL AND deadline_at <= :now
                  AND saga_step NOT IN ('COMPLETED', 'MANUAL_REVIEW')
                ORDER BY deadline_at
                LIMIT 20
                """)
                .setParameter("now", now)
                .getResultList();
        return ids.stream().map(id -> UUID.fromString(id.toString())).toList();
    }
}
