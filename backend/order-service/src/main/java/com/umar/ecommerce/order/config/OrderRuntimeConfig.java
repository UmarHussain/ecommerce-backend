package com.umar.ecommerce.order.config;

import com.umar.ecommerce.order.messaging.CheckoutTopics;
import com.umar.ecommerce.order.remote.CheckoutCallGuard;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class OrderRuntimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    Duration quoteTtl(@Value("${checkout.quote-ttl:10m}") Duration quoteTtl) {
        return quoteTtl;
    }

    @Bean
    Duration commandTimeout(@Value("${checkout.command-timeout:15s}") Duration commandTimeout) {
        return commandTimeout;
    }

    @Bean
    RestClient cartRestClient(RestClient.Builder builder, @Value("${CART_SERVICE_URL:http://localhost:8096}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }

    @Bean
    RestClient catalogRestClient(RestClient.Builder builder, @Value("${CATALOG_SERVICE_URL:http://localhost:8094}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }

    @Bean
    RestClient userRestClient(RestClient.Builder builder, @Value("${USER_SERVICE_URL:http://localhost:8093}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }

    @Bean
    RestClientCustomizer checkoutTimeouts() {
        return builder -> {
            var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofMilliseconds(300)).build())
                    .build();
            var http = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofMilliseconds(800)).build())
                    .build();
            builder.requestFactory(new HttpComponentsClientHttpRequestFactory(http));
        };
    }

    @Bean
    CheckoutCallGuard checkoutCallGuard(CircuitBreakerRegistry breakers, RetryRegistry retries) {
        return new CheckoutCallGuard(breakers.circuitBreaker("checkoutUpstream"), retries.retry("checkoutUpstream"));
    }

    @Bean
    KafkaAdmin.NewTopics checkoutTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(CheckoutTopics.INVENTORY_COMMANDS).partitions(3).replicas(1).build(),
                TopicBuilder.name(CheckoutTopics.INVENTORY_OUTCOMES).partitions(3).replicas(1).build(),
                TopicBuilder.name(CheckoutTopics.PAYMENT_COMMANDS).partitions(3).replicas(1).build(),
                TopicBuilder.name(CheckoutTopics.PAYMENT_OUTCOMES).partitions(3).replicas(1).build(),
                TopicBuilder.name(CheckoutTopics.CART_COMMANDS).partitions(3).replicas(1).build(),
                TopicBuilder.name(CheckoutTopics.CART_OUTCOMES).partitions(3).replicas(1).build()
        );
    }
}
