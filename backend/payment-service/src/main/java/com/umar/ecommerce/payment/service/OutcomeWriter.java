package com.umar.ecommerce.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.umar.ecommerce.payment.entity.PaymentAttempt;
import com.umar.ecommerce.payment.entity.PaymentRefund;
import com.umar.ecommerce.payment.messaging.Envelope;
import com.umar.ecommerce.payment.messaging.PaymentEventTypes;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

@Component
public class OutcomeWriter {

    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutcomeWriter(OutboxAppender outbox, ObjectMapper objectMapper, Clock clock) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void charge(Envelope cause, PaymentAttempt attempt, String eventType) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", attempt.getOrderId().toString());
        payload.put("paymentOperationId", attempt.getOperationId().toString());
        payload.put("amount", attempt.getAmount());
        payload.put("currency", attempt.getCurrency());
        payload.put("status", attempt.getStatus().name());
        outbox.append(fromCause(cause, attempt.getOrderId(), attempt.getVersion(), eventType, payload));
    }

    public void chargeConflict(Envelope cause, PaymentAttempt attempt) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", attempt.getOrderId().toString());
        payload.put("paymentOperationId", attempt.getOperationId().toString());
        payload.put("reason", "PAYLOAD_MISMATCH");
        outbox.append(fromCause(cause, attempt.getOrderId(), attempt.getVersion(), PaymentEventTypes.CONFLICT, payload));
    }

    public void delayedSuccess(PaymentAttempt attempt) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", attempt.getOrderId().toString());
        payload.put("paymentOperationId", attempt.getOperationId().toString());
        payload.put("amount", attempt.getAmount());
        payload.put("currency", attempt.getCurrency());
        payload.put("status", attempt.getStatus().name());
        Envelope envelope = new Envelope(
                UUID.randomUUID(),
                PaymentEventTypes.SUCCEEDED,
                PaymentEventTypes.VERSION,
                attempt.getOrderId().toString(),
                attempt.getVersion(),
                attempt.getSagaId(),
                attempt.getOperationId(),
                attempt.getCorrelationId(),
                attempt.getRequestEventId(),
                clock.instant(),
                payload
        );
        outbox.append(envelope);
    }

    public void refund(Envelope cause, PaymentRefund refund, String eventType) {
        ObjectNode payload = refundPayload(
                refund.getOrderId(),
                refund.getChargeOperationId(),
                refund.getOperationId(),
                refund.getAmount(),
                refund.getCurrency(),
                null
        );
        outbox.append(fromCause(cause, refund.getOrderId(), 0L, eventType, payload));
    }

    public void refundConflict(Envelope cause, UUID orderId, UUID chargeOperationId, UUID refundOperationId) {
        ObjectNode payload = refundPayload(orderId, chargeOperationId, refundOperationId, null, null, "PAYLOAD_MISMATCH");
        outbox.append(fromCause(cause, orderId, 0L, PaymentEventTypes.REFUND_CONFLICT, payload));
    }

    public void refundFailedWithoutRow(
            Envelope cause,
            UUID orderId,
            UUID chargeOperationId,
            UUID refundOperationId,
            BigDecimal amount,
            String currency
    ) {
        ObjectNode payload = refundPayload(orderId, chargeOperationId, refundOperationId, amount, currency, "NOT_APPLIED");
        outbox.append(fromCause(cause, orderId, 0L, PaymentEventTypes.REFUND_FAILED, payload));
    }

    private ObjectNode refundPayload(
            UUID orderId,
            UUID chargeOperationId,
            UUID refundOperationId,
            BigDecimal amount,
            String currency,
            String reason
    ) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", orderId.toString());
        if (chargeOperationId != null) {
            payload.put("paymentOperationId", chargeOperationId.toString());
        }
        payload.put("refundOperationId", refundOperationId.toString());
        if (amount != null) {
            payload.put("amount", amount);
        }
        if (currency != null) {
            payload.put("currency", currency);
        }
        if (reason != null) {
            payload.put("reason", reason);
        }
        return payload;
    }

    private Envelope fromCause(Envelope cause, UUID orderId, long version, String eventType, ObjectNode payload) {
        return new Envelope(
                UUID.randomUUID(),
                eventType,
                PaymentEventTypes.VERSION,
                orderId.toString(),
                version,
                cause.sagaId(),
                cause.commandId(),
                cause.correlationId(),
                cause.eventId(),
                clock.instant(),
                payload
        );
    }
}
