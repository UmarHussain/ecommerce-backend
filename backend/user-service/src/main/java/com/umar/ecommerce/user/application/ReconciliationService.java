package com.umar.ecommerce.user.application;

import com.umar.ecommerce.user.entity.IdentityOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ReconciliationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReconciliationService.class);

    private final IdentityOperationService operations;
    private final UserAdministrationService administration;

    public ReconciliationService(IdentityOperationService operations, UserAdministrationService administration) {
        this.operations = operations;
        this.administration = administration;
    }

    @Scheduled(fixedDelay = 15_000)
    public void reconcileUncertain() {
        for (IdentityOperation operation : operations.dueUncertain()) {
            try {
                administration.reconcile(operation);
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "identity operation reconciliation failed id={} type={}",
                        operation.getId(),
                        operation.getOperationType()
                );
            }
        }
    }
}
