package com.umar.ecommerce.payment.controller;

import com.umar.ecommerce.payment.config.PaymentSimulatorProperties;
import com.umar.ecommerce.payment.config.SecurityConfig;
import com.umar.ecommerce.payment.domain.AttemptStatus;
import com.umar.ecommerce.payment.domain.ChargeScenario;
import com.umar.ecommerce.payment.domain.RefundScenario;
import com.umar.ecommerce.payment.dto.PaymentAttemptResponse;
import com.umar.ecommerce.payment.service.PaymentQueryService;
import com.umar.ecommerce.payment.service.SimulatorAccess;
import com.umar.ecommerce.payment.service.SimulatorControlService;
import com.umar.ecommerce.payment.web.CorrelationIdFilter;
import com.umar.ecommerce.payment.web.SecurityProblemWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SimulatorControlController.class)
@Import({SecurityConfig.class, SecurityProblemWriter.class, CorrelationIdFilter.class, SimulatorAccess.class})
@EnableConfigurationProperties(PaymentSimulatorProperties.class)
@TestPropertySource(properties = {
        "payment.simulator.control-enabled=true",
        "payment.simulator.control-token=local-control-token",
        "payment.simulator.charge-outcome=SUCCESS",
        "payment.simulator.refund-outcome=SUCCESS",
        "payment.simulator.delay=2s"
})
class SimulatorControlAccessTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SimulatorControlService controls;

    @MockitoBean
    private PaymentQueryService payments;

    @Test
    void wrongTokenAndUnknownRoutesDoNotOpenTheSimulator() throws Exception {
        mvc.perform(put("/internal/simulator/outcomes")
                        .contentType("application/json")
                        .content("{\"chargeOutcome\":\"DECLINE\",\"refundOutcome\":\"SUCCESS\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/internal/simulator/outcomes")
                        .header("X-Simulator-Control", "nope")
                        .contentType("application/json")
                        .content("{\"chargeOutcome\":\"DECLINE\",\"refundOutcome\":\"SUCCESS\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/payments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PAYMENT_AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(controls, payments);
    }

    @Test
    void matchingTokenReachesTheControlPlane() throws Exception {
        UUID operationId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(payments.require(operationId)).thenReturn(new PaymentAttemptResponse(
                operationId,
                operationId,
                new BigDecimal("25.00"),
                "USD",
                AttemptStatus.SUCCEEDED,
                1L,
                Instant.EPOCH,
                Instant.EPOCH
        ));
        mvc.perform(put("/internal/simulator/outcomes")
                        .header("X-Simulator-Control", "local-control-token")
                        .contentType("application/json")
                        .content("{\"chargeOutcome\":\"DECLINE\",\"refundOutcome\":\"REFUND_FAILURE\"}"))
                .andExpect(status().isNoContent());
        verify(controls).upsert(ChargeScenario.DECLINE, RefundScenario.REFUND_FAILURE);
        mvc.perform(get("/internal/payments/" + operationId)
                        .header("X-Simulator-Control", "local-control-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }
}
