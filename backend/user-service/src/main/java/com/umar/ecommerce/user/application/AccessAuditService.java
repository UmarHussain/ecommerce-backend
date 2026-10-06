package com.umar.ecommerce.user.application;

import com.umar.ecommerce.user.entity.AccessAudit;
import com.umar.ecommerce.user.repository.AccessAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class AccessAuditService {

    private final AccessAuditRepository repository;

    public AccessAuditService(AccessAuditRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(
            Actor actor,
            UUID targetUserId,
            String action,
            Map<String, Object> before,
            Map<String, Object> after,
            String outcome,
            String correlationId
    ) {
        repository.save(new AccessAudit(
                actor.issuer(),
                actor.subject(),
                targetUserId,
                action,
                before,
                after,
                outcome,
                correlationId
        ));
    }
}
