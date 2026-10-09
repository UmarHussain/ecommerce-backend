package com.umar.ecommerce.payment.config;

import com.umar.ecommerce.payment.messaging.PaymentTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    @Bean
    NewTopic paymentCommandsTopic() {
        return TopicBuilder.name(PaymentTopics.COMMANDS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic paymentOutcomesTopic() {
        return TopicBuilder.name(PaymentTopics.OUTCOMES).partitions(3).replicas(1).build();
    }
}
