package com.umar.ecommerce.payment.service;

import com.umar.ecommerce.payment.repository.SimulatorDueRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Polls {@code simulator_due}. This is the recovery path for delayed and late
 * success. It does not sleep and it does not keep the due time in memory.
 */
@Service
public class DueWorker {

    private final SimulatorDueRepository dues;
    private final ChargeCompletionService completions;
    private final Clock clock;

    public DueWorker(SimulatorDueRepository dues, ChargeCompletionService completions, Clock clock) {
        this.dues = dues;
        this.completions = completions;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${payment.simulator.due-poll-delay:1s}",
            initialDelayString = "${payment.simulator.due-poll-delay:1s}"
    )
    public void poll() {
        List<UUID> dueIds = dues.findDueIds(clock.instant());
        for (UUID dueId : dueIds) {
            completions.complete(dueId);
        }
    }
}
