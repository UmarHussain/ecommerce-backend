package com.umar.ecommerce.cart;

import com.umar.ecommerce.cart.repository.CartRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(properties = {
        "DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/unused",
        "DATABASE_USERNAME=unused",
        "DATABASE_PASSWORD=unused",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "spring.task.scheduling.enabled=false"
})
class CartServiceApplicationTests {

    @MockitoBean
    private CartRepository cartRepository;
    @MockitoBean
    private EntityManager entityManager;
    @MockitoBean
    private PlatformTransactionManager platformTransactionManager;
    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void contextLoads() {
    }
}
