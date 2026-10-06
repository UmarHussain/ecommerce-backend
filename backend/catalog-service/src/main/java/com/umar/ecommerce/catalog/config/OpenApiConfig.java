package com.umar.ecommerce.catalog.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI catalogOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Catalog Service API")
                        .version("Phase 2")
                        .description(
                                "Public catalog browsing and protected catalog administration. "
                                        + "Public list endpoints return a stable page envelope with items "
                                        + "and pagination metadata. Error responses use RFC 7807 Problem "
                                        + "Details with timestamp, status, code, path, correlationId, "
                                        + "and optional fieldErrors. Admin operations require a bearer JWT."
                        ))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        )
                        .addSchemas(
                                "CatalogProblemDetail",
                                new ObjectSchema()
                                        .description("RFC 7807 Problem Details envelope")
                                        .addProperty("type", new StringSchema())
                                        .addProperty("title", new StringSchema())
                                        .addProperty("status", new IntegerSchema())
                                        .addProperty("detail", new StringSchema())
                                        .addProperty("instance", new StringSchema())
                                        .addProperty("timestamp", new StringSchema().format("date-time"))
                                        .addProperty("code", new StringSchema())
                                        .addProperty("path", new StringSchema())
                                        .addProperty("correlationId", new StringSchema())
                                        .addProperty("fieldErrors", new ObjectSchema())
                        ));
    }
}
