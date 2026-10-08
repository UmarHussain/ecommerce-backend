package com.umar.ecommerce.inventory.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI inventoryOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Inventory Service API")
                        .version("Phase 3")
                        .description("""
                                Admin stock setup and adjustments. Available quantity is onHand minus reserved.
                                Reserved is not writable. Setup calls catalog with the caller token and requires catalog.read.
                                That check is point-in-time: later catalog deactivation does not delete stock or history,
                                and adjustments do not reactivate a catalog item. Idempotency-Key is required on setup and adjustment.
                                Same key and payload replays the stored status and body. A different payload is 409 INVENTORY_IDEMPOTENCY_CONFLICT.
                                Stale expectedVersion is 409 INVENTORY_STALE_VERSION. Quantity invariant failures are 409 INVENTORY_STOCK_INVARIANT.
                                """))
                .components(new Components().addSecuritySchemes(
                        "bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                ));
    }
}
