package com.umar.ecommerce.cart.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI cartOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cart Service API")
                        .version("Phase 4")
                        .description(
                                "Own-cart reads and absolute quantity commands. "
                                        + "The gateway exposes these as /api/v1/store/cart. "
                                        + "expectedVersion is required on every mutation. "
                                        + "DELETE sends it as a query parameter. "
                                        + "Display prices are snapshots and are not checkout prices."
                        ))
                .components(new Components().addSecuritySchemes(
                        "bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                ));
    }
}
