package com.umar.ecommerce.payment.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PaymentCommandListenerTest {

    private final PaymentCommandProcessor processor = mock(PaymentCommandProcessor.class);
    private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
    private final PaymentCommandListener listener = new PaymentCommandListener(processor);

    @Test
    void acknowledgesOnlyAfterTheCommandIsAccepted() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(PaymentTopics.COMMANDS, 0, 0L, "order", "{}");
        listener.onCommand(record, acknowledgment);
        var order = inOrder(processor, acknowledgment);
        order.verify(processor).accept("{}");
        order.verify(acknowledgment).acknowledge();
    }

    @Test
    void doesNotAcknowledgeWhenProcessingFails() {
        doThrow(new RuntimeException("dead-letter insert failed")).when(processor).accept("{}");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(PaymentTopics.COMMANDS, 0, 1L, "order", "{}");
        assertThatThrownBy(() -> listener.onCommand(record, acknowledgment))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("dead-letter insert failed");
        verify(acknowledgment, never()).acknowledge();
    }
}
