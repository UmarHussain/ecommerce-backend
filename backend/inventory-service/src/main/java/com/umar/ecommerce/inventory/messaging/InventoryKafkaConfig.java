package com.umar.ecommerce.inventory.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.backoff.FixedBackOff;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableKafka
@ConditionalOnProperty(name = "inventory.messaging.listener-enabled", matchIfMissing = true)
class InventoryKafkaConfig {

    @Bean
    org.apache.kafka.clients.admin.NewTopic checkoutInventoryCommands() {
        return TopicBuilder.name(InventoryChannels.COMMANDS).partitions(3).replicas(1).build();
    }

    @Bean
    org.apache.kafka.clients.admin.NewTopic checkoutInventoryOutcomes() {
        return TopicBuilder.name(InventoryChannels.OUTCOMES).partitions(3).replicas(1).build();
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(2_000L, FixedBackOff.UNLIMITED_ATTEMPTS)));
        return factory;
    }
}

@Configuration
@ConditionalOnProperty(name = "inventory.messaging.dispatch-enabled", matchIfMissing = true)
class OutboxDispatchConfig {

    @Bean(destroyMethod = "shutdown")
    Executor inventoryOutboxExecutor() {
        return new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(32),
                runnable -> {
                    Thread thread = new Thread(runnable, "inventory-outbox");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @Bean
    OutboxDispatchListener outboxDispatchListener(Executor inventoryOutboxExecutor, OutboxPublisher publisher) {
        return new OutboxDispatchListener(inventoryOutboxExecutor, publisher);
    }
}

class OutboxDispatchListener {

    private final Executor executor;
    private final OutboxPublisher publisher;

    OutboxDispatchListener(Executor executor, OutboxPublisher publisher) {
        this.executor = executor;
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReady(OutboxReady ready) {
        try {
            executor.execute(publisher::poll);
        } catch (RejectedExecutionException exception) {
            ready.outboxId();
        }
    }
}
