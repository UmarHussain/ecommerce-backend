package com.umar.ecommerce.payment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.umar.ecommerce.payment.domain.ChargeScenario;
import com.umar.ecommerce.payment.domain.RefundScenario;
import com.umar.ecommerce.payment.dto.PaymentAttemptResponse;
import com.umar.ecommerce.payment.service.PaymentQueryService;
import com.umar.ecommerce.payment.service.SimulatorAccess;
import com.umar.ecommerce.payment.service.SimulatorControlService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
public class SimulatorControlController {

    private final SimulatorAccess access;
    private final SimulatorControlService controls;
    private final PaymentQueryService payments;

    public SimulatorControlController(
            SimulatorAccess access,
            SimulatorControlService controls,
            PaymentQueryService payments
    ) {
        this.access = access;
        this.controls = controls;
        this.payments = payments;
    }

    @PutMapping("/internal/simulator/outcomes")
    public ResponseEntity<Void> outcomes(
            @RequestHeader(value = SimulatorAccess.HEADER, required = false) String token,
            @RequestBody JsonNode body
    ) {
        access.require(token);
        if (body == null || !body.hasNonNull("chargeOutcome") || !body.hasNonNull("refundOutcome")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        controls.upsert(scenario(body.get("chargeOutcome").asText(), ChargeScenario.class), scenario(body.get("refundOutcome").asText(), RefundScenario.class));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/internal/payments/{operationId}")
    public PaymentAttemptResponse payment(
            @RequestHeader(value = SimulatorAccess.HEADER, required = false) String token,
            @PathVariable UUID operationId
    ) {
        access.require(token);
        return payments.require(operationId);
    }

    private static <E extends Enum<E>> E scenario(String raw, Class<E> type) {
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }
}
