package com.umar.ecommerce.inventory.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umar.ecommerce.inventory.exception.InventoryProblem;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

/**
 * Reads the protected catalog admin APIs with the caller's access token.
 * Tokens are forwarded and never logged. There is no retry.
 */
@Component
public class HttpCatalogLookupAdapter implements CatalogLookupPort {

    static final String CORRELATION_HEADER = "X-Correlation-ID";

    private final RestClient catalog;
    private final ObjectMapper objectMapper;

    public HttpCatalogLookupAdapter(RestClient catalogRestClient, ObjectMapper objectMapper) {
        this.catalog = catalogRestClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public CatalogVariantSnapshot load(UUID catalogVariantId, String bearerToken, String correlationId) {
        JsonNode variant = get("/api/v1/admin/catalog/variants/" + catalogVariantId, bearerToken, correlationId);
        UUID productId = requiredUuid(variant, "productId");
        JsonNode product = get("/api/v1/admin/catalog/products/" + productId, bearerToken, correlationId);
        JsonNode category = product.path("category");
        String sku = text(variant, "sku");
        if (sku.isBlank()) {
            throw unavailable("Catalog did not return a SKU for the variant");
        }
        return new CatalogVariantSnapshot(
                requiredUuid(variant, "id"),
                productId,
                sku,
                text(variant, "name"),
                text(product, "name"),
                variant.path("active").asBoolean(false),
                product.path("active").asBoolean(false),
                category.path("active").asBoolean(false)
        );
    }

    private JsonNode get(String path, String bearerToken, String correlationId) {
        try {
            String body = catalog.get()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .header(CORRELATION_HEADER, correlationId == null ? "" : correlationId)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw unavailable("Catalog returned an empty response");
            }
            return objectMapper.readTree(body);
        } catch (InventoryProblem problem) {
            throw problem;
        } catch (RestClientResponseException exception) {
            throw mapStatus(exception.getStatusCode());
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                throw new InventoryProblem(
                        HttpStatus.GATEWAY_TIMEOUT,
                        InventoryProblem.CATALOG_TIMEOUT,
                        "Catalog did not respond in time"
                );
            }
            throw unavailable("Catalog is unavailable");
        } catch (Exception exception) {
            throw unavailable("Catalog response could not be read");
        }
    }

    private static InventoryProblem mapStatus(HttpStatusCode status) {
        int code = status.value();
        if (code == 401) {
            return new InventoryProblem(
                    HttpStatus.UNAUTHORIZED,
                    InventoryProblem.AUTHENTICATION_REQUIRED,
                    "Catalog rejected the caller token"
            );
        }
        if (code == 403) {
            return new InventoryProblem(
                    HttpStatus.FORBIDDEN,
                    InventoryProblem.CATALOG_FORBIDDEN,
                    "Catalog denied this caller"
            );
        }
        if (code == 404) {
            return new InventoryProblem(
                    HttpStatus.NOT_FOUND,
                    InventoryProblem.CATALOG_VARIANT_NOT_FOUND,
                    "Catalog has no variant with that identifier"
            );
        }
        return unavailable("Catalog is unavailable");
    }

    private static InventoryProblem unavailable(String message) {
        return new InventoryProblem(HttpStatus.SERVICE_UNAVAILABLE, InventoryProblem.CATALOG_UNAVAILABLE, message);
    }

    private static boolean isTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String name = current.getClass().getSimpleName();
            if (current instanceof java.net.SocketTimeoutException
                    || name.contains("Timeout")
                    || name.contains("ConnectionRequest")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static UUID requiredUuid(JsonNode node, String field) {
        String value = text(node, field);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw unavailable("Catalog response was missing " + field);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }
}
