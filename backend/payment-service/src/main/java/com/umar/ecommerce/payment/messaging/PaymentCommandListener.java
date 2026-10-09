package com.umar.ecommerce.payment.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "payment.messaging.listener-enabled", matchIfMissing = true)
public class PaymentCommandListener {

    private final PaymentCommandProcessor processor;

    public PaymentCommandListener(PaymentCommandProcessor processor) {
        this.processor = processor;
    }

    @KafkaListener(topics = PaymentTopics.COMMANDS, groupId = PaymentTopics.GROUP)
    public void onCommand(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        processor.accept(record.value());
        acknowledgment.acknowledge();
    }
}
