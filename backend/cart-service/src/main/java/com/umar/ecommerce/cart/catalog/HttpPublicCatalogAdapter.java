package com.umar.ecommerce.cart.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umar.ecommerce.cart.exception.CartProblem;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reads the public catalog batch lookup. The request does not send Authorization,
 * a customer subject, or browser role headers. The batch endpoint is not cached
 * in catalog-service. Retries are applied only by {@link CatalogCallGuard}.
 */
@Component
public class HttpPublicCatalogAdapter {

    static final String CORRELATION_HEADER = "X-Correlation-ID";

    private final RestClient catalog;
    private final ObjectMapper objectMapper;
    private final CatalogCallGuard guard;

    public HttpPublicCatalogAdapter(RestClient catalogRestClient, ObjectMapper objectMapper, CatalogCallGuard guard) {
        this.catalog = catalogRestClient;
        this.objectMapper = objectMapper;
        this.guard = guard;
    }

    public CatalogVariant requireActive(String sku, String correlationId) {
        CatalogVariant variant = findAll(java.util.List.of(sku), correlationId).get(sku);
        if (variant == null) {
            throw new CartProblem(
                    HttpStatus.CONFLICT,
                    CartProblem.SKU_UNAVAILABLE,
                    "SKU is not an active catalog variant"
            );
        }
        return variant;
    }

    public Map<String, CatalogVariant> findAll(Collection<String> skus, String correlationId) {
        if (skus.isEmpty()) {
            return Map.of();
        }
        JsonNode body = guard.execute(() -> post(skus, correlationId));
        Map<String, CatalogVariant> found = new LinkedHashMap<>();
        for (JsonNode node : body.path("variants")) {
            CatalogVariant variant = readVariant(node);
            if (variant != null) {
                found.put(variant.sku(), variant);
            }
        }
        return found;
    }

    private JsonNode post(Collection<String> skus, String correlationId) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of("skus", skus));
            var request = catalog.post().uri("/api/v1/catalog/variants/batch");
            if (correlationId != null && !correlationId.isBlank()) {
                request.header(CORRELATION_HEADER, correlationId);
            }
            String body = request
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw new CatalogTechnicalException(CatalogTechnicalException.Kind.UNAVAILABLE, "empty catalog body");
            }
            return objectMapper.readTree(body);
        } catch (CatalogTechnicalException | CartProblem exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw mapStatus(exception.getStatusCode());
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                throw new CatalogTechnicalException(CatalogTechnicalException.Kind.TIMEOUT, "catalog timeout");
            }
            throw new CatalogTechnicalException(CatalogTechnicalException.Kind.UNAVAILABLE, "catalog connection failed");
        } catch (Exception exception) {
            throw new CatalogTechnicalException(CatalogTechnicalException.Kind.UNAVAILABLE, "catalog response could not be read");
        }
    }

    private static RuntimeException mapStatus(HttpStatusCode status) {
        int code = status.value();
        if (code == 502 || code == 503 || code == 504) {
            if (code == 504) {
                return new CatalogTechnicalException(CatalogTechnicalException.Kind.TIMEOUT, "catalog 504");
            }
            return new CatalogTechnicalException(CatalogTechnicalException.Kind.UNAVAILABLE, "catalog " + code);
        }
        if (code == 400) {
            return new CartProblem(HttpStatus.BAD_REQUEST, CartProblem.VALIDATION_FAILED, "Catalog rejected the SKU lookup");
        }
        if (code == 429) {
            return new CartProblem(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    CartProblem.CATALOG_UNAVAILABLE,
                    "Catalog asked the cart to slow down; automatic retry of 429 is not enabled"
            );
        }
        return new CartProblem(HttpStatus.SERVICE_UNAVAILABLE, CartProblem.CATALOG_UNAVAILABLE, "Catalog is unavailable");
    }

    private static CatalogVariant readVariant(JsonNode node) {
        String sku = text(node, "sku");
        String id = text(node, "id");
        if (sku.isBlank() || id.isBlank()) {
            return null;
        }
        try {
            return new CatalogVariant(
                    UUID.fromString(id),
                    sku,
                    text(node, "name"),
                    new BigDecimal(text(node, "price")),
                    text(node, "currency"),
                    blankToNull(text(node, "imageUrl"))
            );
        } catch (RuntimeException exception) {
            return null;
        }
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

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
