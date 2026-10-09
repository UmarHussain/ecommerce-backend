package com.umar.ecommerce.payment.service;

import com.umar.ecommerce.payment.domain.DueAction;
import com.umar.ecommerce.payment.domain.PaymentDecisions;
import com.umar.ecommerce.payment.entity.PaymentAttempt;
import com.umar.ecommerce.payment.entity.SimulatorDue;
import com.umar.ecommerce.payment.repository.PaymentAttemptRepository;
import com.umar.ecommerce.payment.repository.SimulatorDueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class ChargeCompletionService {

    private final SimulatorDueRepository dues;
    private final PaymentAttemptRepository attempts;
    private final OutcomeWriter outcomes;
    private final Clock clock;

    public ChargeCompletionService(
            SimulatorDueRepository dues,
            PaymentAttemptRepository attempts,
            OutcomeWriter outcomes,
            Clock clock
    ) {
        this.dues = dues;
        this.attempts = attempts;
        this.outcomes = outcomes;
        this.clock = clock;
    }

    @Transactional
    public void complete(UUID dueId) {
        SimulatorDue due = dues.lockById(dueId).orElse(null);
        if (due == null || due.isCompleted() || due.getDueAt().isAfter(clock.instant())) {
            return;
        }
        if (due.getAction() == DueAction.COMPLETE_CHARGE) {
            PaymentAttempt attempt = attempts.lockById(due.getOperationId()).orElse(null);
            if (attempt != null && PaymentDecisions.dueCompletionSucceeds(attempt.getStatus())) {
                attempt.succeed(clock.instant());
                outcomes.delayedSuccess(attempt);
            }
        }
        due.complete();
    }
}
