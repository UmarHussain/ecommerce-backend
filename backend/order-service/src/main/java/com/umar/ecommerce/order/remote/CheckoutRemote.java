package com.umar.ecommerce.order.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umar.ecommerce.order.exception.OrderProblem;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class CheckoutRemote {

    private final RestClient cart;
    private final RestClient catalog;
    private final RestClient users;
    private final ObjectMapper objectMapper;
    private final CheckoutCallGuard guard;

    public CheckoutRemote(
            RestClient cartRestClient,
            RestClient catalogRestClient,
            RestClient userRestClient,
            ObjectMapper objectMapper,
            CheckoutCallGuard guard
    ) {
        this.cart = cartRestClient;
        this.catalog = catalogRestClient;
        this.users = userRestClient;
        this.objectMapper = objectMapper;
        this.guard = guard;
    }

    public CheckoutSnapshot.CartSnapshotView cart(String bearer, String correlationId) {
        JsonNode body = guard.execute(() -> get(cart, "/api/v1/cart", bearer, correlationId));
        List<CheckoutSnapshot.CartLine> lines = new ArrayList<>();
        for (JsonNode item : body.path("items")) {
            lines.add(new CheckoutSnapshot.CartLine(
                    UUID.fromString(item.path("catalogVariantId").asText()),
                    item.path("sku").asText(),
                    item.path("quantity").asInt(),
                    item.path("displayName").asText(),
                    new BigDecimal(item.path("unitPrice").asText("0")),
                    item.path("currency").asText(),
                    item.path("catalogState").asText()
            ));
        }
        return new CheckoutSnapshot.CartSnapshotView(body.path("version").asLong(), List.copyOf(lines));
    }

    public List<CheckoutSnapshot.CatalogLine> catalog(List<String> skus, String correlationId) {
        JsonNode body = guard.execute(() -> post(catalog, "/api/v1/catalog/variants/batch", Map.of("skus", skus), null, correlationId));
        List<CheckoutSnapshot.CatalogLine> lines = new ArrayList<>();
        for (JsonNode variant : body.path("variants")) {
            lines.add(new CheckoutSnapshot.CatalogLine(
                    UUID.fromString(variant.path("id").asText()),
                    variant.path("sku").asText(),
                    variant.path("name").asText(),
                    new BigDecimal(variant.path("price").asText("0")),
                    variant.path("currency").asText()
            ));
        }
        return List.copyOf(lines);
    }

    public CheckoutSnapshot.OwnedAddress address(String bearer, UUID addressId, String correlationId) {
        JsonNode body = guard.execute(() -> get(users, "/api/v1/users/me", bearer, correlationId));
        for (JsonNode address : body.path("addresses")) {
            if (addressId.toString().equals(address.path("id").asText())) {
                return new CheckoutSnapshot.OwnedAddress(
                        addressId,
                        text(address, "label"),
                        address.path("line1").asText(),
                        text(address, "line2"),
                        address.path("city").asText(),
                        text(address, "region"),
                        address.path("postalCode").asText(),
                        address.path("countryCode").asText()
                );
            }
        }
        throw new OrderProblem(HttpStatus.NOT_FOUND, OrderProblem.NOT_FOUND, "Address was not found");
    }

    private JsonNode get(RestClient client, String path, String bearer, String correlationId) {
        try {
            String body = client.get().uri(path)
                    .header("Authorization", bearer)
                    .header("X-Correlation-ID", correlationId)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(body);
        } catch (RestClientResponseException exception) {
            throw technical(exception.getStatusCode().value());
        } catch (ResourceAccessException exception) {
            throw new OrderUpstreamException(true);
        } catch (OrderUpstreamException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new OrderUpstreamException(false);
        }
    }

    private JsonNode post(RestClient client, String path, Object payload, String bearer, String correlationId) {
        try {
            var request = client.post().uri(path).header("Content-Type", "application/json").header("X-Correlation-ID", correlationId);
            if (bearer != null) {
                request.header("Authorization", bearer);
            }
            String body = request.body(objectMapper.writeValueAsString(payload)).retrieve().body(String.class);
            return objectMapper.readTree(body);
        } catch (RestClientResponseException exception) {
            throw technical(exception.getStatusCode().value());
        } catch (ResourceAccessException exception) {
            throw new OrderUpstreamException(true);
        } catch (OrderUpstreamException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new OrderUpstreamException(false);
        }
    }

    private static OrderUpstreamException technical(int status) {
        return new OrderUpstreamException(status == 408 || status == 504);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return value.asText();
    }

    public static final class OrderUpstreamException extends RuntimeException {
        private final boolean timeout;

        public OrderUpstreamException(boolean timeout) {
            this.timeout = timeout;
        }

        public boolean timeout() {
            return timeout;
        }
    }
}
