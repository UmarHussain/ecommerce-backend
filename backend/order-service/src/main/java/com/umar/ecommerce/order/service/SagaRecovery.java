package com.umar.ecommerce.order.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
public class SagaRecovery {

    private final DueOrderQuery dueOrders;
    private final SagaAdvanceService saga;
    private final Clock clock;

    public SagaRecovery(DueOrderQuery dueOrders, SagaAdvanceService saga, Clock clock) {
        this.dueOrders = dueOrders;
        this.saga = saga;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${checkout.saga.poll-delay:1000}")
    public void recover() {
        for (UUID id : dueOrders.due(Instant.now(clock))) {
            saga.deadline(id);
        }
    }
}
