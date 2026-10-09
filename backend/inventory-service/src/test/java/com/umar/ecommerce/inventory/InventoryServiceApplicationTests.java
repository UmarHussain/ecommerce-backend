package com.umar.ecommerce.inventory;

import com.umar.ecommerce.inventory.repository.InventoryCommandRepository;
import com.umar.ecommerce.inventory.repository.StockAdjustmentRepository;
import com.umar.ecommerce.inventory.repository.StockItemRepository;
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
        "inventory.workers.enabled=false",
        "inventory.messaging.listener-enabled=false",
        "inventory.messaging.dispatch-enabled=false"
})
class InventoryServiceApplicationTests {

    @MockitoBean
    private StockItemRepository stockItemRepository;
    @MockitoBean
    private StockAdjustmentRepository stockAdjustmentRepository;
    @MockitoBean
    private InventoryCommandRepository inventoryCommandRepository;
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
