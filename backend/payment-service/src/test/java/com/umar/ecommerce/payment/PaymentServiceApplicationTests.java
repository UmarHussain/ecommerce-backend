package com.umar.ecommerce.payment;

import com.umar.ecommerce.payment.repository.OutboxEventRepository;
import com.umar.ecommerce.payment.repository.PaymentAttemptRepository;
import com.umar.ecommerce.payment.repository.PaymentRefundRepository;
import com.umar.ecommerce.payment.repository.SimulatorControlRepository;
import com.umar.ecommerce.payment.repository.SimulatorDueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/unused",
        "DATABASE_USERNAME=unused",
        "DATABASE_PASSWORD=unused",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.bootstrap-servers=127.0.0.1:1",
        "spring.kafka.admin.auto-create=false",
        "payment.messaging.listener-enabled=false",
        "payment.outbox.immediate-dispatch=false",
        "spring.task.scheduling.enabled=false",
        "management.health.kafka.enabled=false"
})
class PaymentServiceApplicationTests {

    @MockitoBean
    private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private PaymentAttemptRepository paymentAttemptRepository;
    @MockitoBean
    private PaymentRefundRepository paymentRefundRepository;
    @MockitoBean
    private SimulatorDueRepository simulatorDueRepository;
    @MockitoBean
    private SimulatorControlRepository simulatorControlRepository;
    @MockitoBean
    private OutboxEventRepository outboxEventRepository;
    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void contextLoads() {
    }
}
