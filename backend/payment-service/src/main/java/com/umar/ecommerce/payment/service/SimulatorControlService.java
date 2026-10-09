package com.umar.ecommerce.payment.service;

import com.umar.ecommerce.payment.domain.ChargeScenario;
import com.umar.ecommerce.payment.domain.RefundScenario;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;

@Service
public class SimulatorControlService {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public SimulatorControlService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public void upsert(ChargeScenario chargeOutcome, RefundScenario refundOutcome) {
        jdbc.update(
                """
                INSERT INTO simulator_control (id, charge_outcome, refund_outcome, updated_at)
                VALUES (1, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE
                    SET charge_outcome = EXCLUDED.charge_outcome,
                        refund_outcome = EXCLUDED.refund_outcome,
                        updated_at = EXCLUDED.updated_at
                """,
                chargeOutcome.name(),
                refundOutcome.name(),
                Timestamp.from(clock.instant())
        );
    }
}
