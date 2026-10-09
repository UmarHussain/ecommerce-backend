package com.umar.ecommerce.payment.messaging;

import com.umar.ecommerce.payment.service.DeadLetterRecorder;
import com.umar.ecommerce.payment.service.PaymentTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PaymentCommandProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentCommandProcessor.class);

    private final CommandParser parser;
    private final PaymentTransactionService transactions;
    private final DeadLetterRecorder deadLetters;

    public PaymentCommandProcessor(
            CommandParser parser,
            PaymentTransactionService transactions,
            DeadLetterRecorder deadLetters
    ) {
        this.parser = parser;
        this.transactions = transactions;
        this.deadLetters = deadLetters;
    }

    public void accept(String raw) {
        ParsedCommand parsed = parser.parse(raw);
        if (parsed.rejected()) {
            LOGGER.warn("Payment command rejected reason={} eventId={}", parsed.reason(), parsed.eventId());
            deadLetters.record(parsed.eventId(), parsed.reason(), parsed.raw());
            return;
        }
        Envelope envelope = parsed.envelope();
        LOGGER.info(
                "Payment command accepted eventType={} eventId={} commandId={}",
                envelope.eventType(),
                envelope.eventId(),
                envelope.commandId()
        );
        transactions.apply(envelope);
    }
}
