package com.umar.ecommerce.inventory.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

@Service
public class ReservationExpiry {

    private final ReservationTransactionService transactions;
    private final Clock clock;
    private final int batch;

    public ReservationExpiry(
            ReservationTransactionService transactions,
            Clock clock,
            @Value("${inventory.expiry.batch-size:20}") int batch
    ) {
        this.transactions = transactions;
        this.clock = clock;
        this.batch = batch;
    }

    public void expireDue() {
        for (UUID id : transactions.dueIds(clock.instant(), batch)) {
            transactions.expireIfDue(id);
        }
    }
}
