package com.umar.ecommerce.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Routes {

    // Bean name must differ from the configuration class bean name ("routes").
    @Bean
    RouteLocator gatewayRoutes(
            RouteLocatorBuilder builder,
            @Value("${USER_SERVICE_URL:http://localhost:8093}") String users,
            @Value("${CATALOG_SERVICE_URL:http://localhost:8094}") String catalog,
            @Value("${INVENTORY_SERVICE_URL:http://localhost:8095}") String inventory,
            @Value("${CART_SERVICE_URL:http://localhost:8096}") String cart,
            @Value("${ORDER_SERVICE_URL:http://localhost:8097}") String orders
    ) {
        return builder.routes()
                .route("public-catalog-product-slug", route -> route
                        .path("/api/v1/store/catalog/products/slug/{slug}")
                        .and().method("GET")
                        .filters(filter -> filter.rewritePath(
                                "/api/v1/store/catalog/products/slug/(?<slug>[^/]+)",
                                "/api/v1/catalog/products/slug/${slug}"))
                        .uri(catalog))
                .route("public-catalog-product-variants", route -> route
                        .path("/api/v1/store/catalog/products/{productId}/variants")
                        .and().method("GET")
                        .filters(filter -> filter.rewritePath(
                                "/api/v1/store/catalog/products/(?<productId>[^/]+)/variants",
                                "/api/v1/catalog/products/${productId}/variants"))
                        .uri(catalog))
                .route("public-catalog-product", route -> route
                        .path("/api/v1/store/catalog/products/{id}")
                        .and().method("GET")
                        .filters(filter -> filter.rewritePath(
                                "/api/v1/store/catalog/products/(?<id>[^/]+)",
                                "/api/v1/catalog/products/${id}"))
                        .uri(catalog))
                .route("public-catalog-products", route -> route
                        .path("/api/v1/store/catalog/products")
                        .and().method("GET")
                        .filters(filter -> filter.rewritePath(
                                "/api/v1/store/catalog/products",
                                "/api/v1/catalog/products"))
                        .uri(catalog))
                .route("public-catalog-variant-batch", route -> route
                        .path("/api/v1/store/catalog/variants/batch")
                        .and().method("POST")
                        .filters(filter -> filter.rewritePath(
                                "/api/v1/store/catalog/variants/batch",
                                "/api/v1/catalog/variants/batch"))
                        .uri(catalog))
                .route("public-catalog-categories", route -> route
                        .path("/api/v1/store/catalog/categories")
                        .and().method("GET")
                        .filters(filter -> filter.rewritePath(
                                "/api/v1/store/catalog/categories",
                                "/api/v1/catalog/categories"))
                        .uri(catalog))
                .route("customer-cart-item", route -> route
                        .path("/api/v1/store/cart/items/{sku}")
                        .and().method("PUT", "DELETE")
                        .filters(filter -> filter.rewritePath(
                                "/api/v1/store/cart/items/(?<sku>[^/]+)",
                                "/api/v1/cart/items/${sku}"))
                        .uri(cart))
                .route("customer-cart", route -> route
                        .path("/api/v1/store/cart")
                        .and().method("GET", "DELETE")
                        .filters(filter -> filter.rewritePath("/api/v1/store/cart", "/api/v1/cart"))
                        .uri(cart))
                .route("customer-order-cancel", route -> route
                        .path("/api/v1/store/orders/{orderId}/cancel")
                        .and().method("POST")
                        .filters(filter -> filter.rewritePath(
                                "/api/v1/store/orders/(?<orderId>[^/]+)/cancel",
                                "/api/v1/orders/${orderId}/cancel"))
                        .uri(orders))
                .route("customer-order-quotes", route -> route
                        .path("/api/v1/store/orders/quotes")
                        .and().method("POST")
                        .filters(filter -> filter.rewritePath(
                                "/api/v1/store/orders/quotes",
                                "/api/v1/orders/quotes"))
                        .uri(orders))
                .route("customer-order", route -> route
                        .path("/api/v1/store/orders/{orderId}")
                        .and().method("GET")
                        .filters(filter -> filter.rewritePath(
                                "/api/v1/store/orders/(?<orderId>[^/]+)",
                                "/api/v1/orders/${orderId}"))
                        .uri(orders))
                .route("customer-orders", route -> route
                        .path("/api/v1/store/orders")
                        .and().method("GET", "POST")
                        .filters(filter -> filter.rewritePath("/api/v1/store/orders", "/api/v1/orders"))
                        .uri(orders))
                .route("customer-me", route -> route
                        .path("/api/v1/store/me")
                        .and().method("GET", "PATCH")
                        .filters(filter -> filter.rewritePath("/api/v1/store/me", "/api/v1/users/me"))
                        .uri(users))
                .route("customer-addresses", route -> route
                        .path("/api/v1/store/me/addresses")
                        .and().method("POST")
                        .filters(filter -> filter.rewritePath(
                                "/api/v1/store/me/addresses",
                                "/api/v1/users/me/addresses"))
                        .uri(users))
                .route("customer-address", route -> route
                        .path("/api/v1/store/me/addresses/{addressId}")
                        .and().method("PUT", "DELETE")
                        .filters(filter -> filter.rewritePath(
                                "/api/v1/store/me/addresses/(?<addressId>[^/]+)",
                                "/api/v1/users/me/addresses/${addressId}"))
                        .uri(users))
                .route("admin-me", route -> route
                        .path("/api/v1/admin/me")
                        .and().method("GET")
                        .uri(users))
                .route("admin-user-roles", route -> route
                        .path("/api/v1/admin/users/{userId}/roles")
                        .and().method("GET", "POST")
                        .uri(users))
                .route("admin-user-role", route -> route
                        .path("/api/v1/admin/users/{userId}/roles/{roleName}")
                        .and().method("DELETE")
                        .uri(users))
                .route("admin-staff-suspend", route -> route
                        .path("/api/v1/admin/users/{userId}/staff/suspend")
                        .and().method("POST")
                        .uri(users))
                .route("admin-staff", route -> route
                        .path("/api/v1/admin/users/{userId}/staff")
                        .and().method("POST")
                        .uri(users))
                .route("admin-user", route -> route
                        .path("/api/v1/admin/users/{userId}")
                        .and().method("GET")
                        .uri(users))
                .route("admin-users", route -> route
                        .path("/api/v1/admin/users")
                        .and().method("GET", "POST")
                        .uri(users))
                .route("admin-roles", route -> route
                        .path("/api/v1/admin/roles")
                        .and().method("GET", "POST")
                        .uri(users))
                .route("admin-operation", route -> route
                        .path("/api/v1/admin/operations/{operationId}")
                        .and().method("GET")
                        .uri(users))
                .route("admin-catalog-categories-read", route -> route
                        .path("/api/v1/admin/catalog/categories")
                        .and().method("GET")
                        .uri(catalog))
                .route("admin-catalog-category-read", route -> route
                        .path("/api/v1/admin/catalog/categories/{id}")
                        .and().method("GET")
                        .uri(catalog))
                .route("admin-catalog-categories", route -> route
                        .path("/api/v1/admin/catalog/categories")
                        .and().method("POST")
                        .uri(catalog))
                .route("admin-catalog-category", route -> route
                        .path("/api/v1/admin/catalog/categories/{id}")
                        .and().method("PUT")
                        .uri(catalog))
                .route("admin-catalog-category-status", route -> route
                        .path("/api/v1/admin/catalog/categories/{id}/status")
                        .and().method("PATCH")
                        .uri(catalog))
                .route("admin-catalog-products-read", route -> route
                        .path("/api/v1/admin/catalog/products")
                        .and().method("GET")
                        .uri(catalog))
                .route("admin-catalog-product-variants-read", route -> route
                        .path("/api/v1/admin/catalog/products/{productId}/variants")
                        .and().method("GET")
                        .uri(catalog))
                .route("admin-catalog-product-read", route -> route
                        .path("/api/v1/admin/catalog/products/{id}")
                        .and().method("GET")
                        .uri(catalog))
                .route("admin-catalog-products", route -> route
                        .path("/api/v1/admin/catalog/products")
                        .and().method("POST")
                        .uri(catalog))
                .route("admin-catalog-product", route -> route
                        .path("/api/v1/admin/catalog/products/{id}")
                        .and().method("PUT")
                        .uri(catalog))
                .route("admin-catalog-product-status", route -> route
                        .path("/api/v1/admin/catalog/products/{id}/status")
                        .and().method("PATCH")
                        .uri(catalog))
                .route("admin-catalog-variants", route -> route
                        .path("/api/v1/admin/catalog/products/{productId}/variants")
                        .and().method("POST")
                        .uri(catalog))
                .route("admin-catalog-variant-read", route -> route
                        .path("/api/v1/admin/catalog/variants/{variantId}")
                        .and().method("GET")
                        .uri(catalog))
                .route("admin-catalog-variant", route -> route
                        .path("/api/v1/admin/catalog/variants/{variantId}")
                        .and().method("PUT")
                        .uri(catalog))
                .route("admin-catalog-variant-status", route -> route
                        .path("/api/v1/admin/catalog/variants/{variantId}/status")
                        .and().method("PATCH")
                        .uri(catalog))
                .route("admin-inventory-stock-items-read", route -> route
                        .path("/api/v1/admin/inventory/stock-items")
                        .and().method("GET")
                        .uri(inventory))
                .route("admin-inventory-stock-adjustments-read", route -> route
                        .path("/api/v1/admin/inventory/stock-items/{id}/adjustments")
                        .and().method("GET")
                        .uri(inventory))
                .route("admin-inventory-stock-adjustments", route -> route
                        .path("/api/v1/admin/inventory/stock-items/{id}/adjustments")
                        .and().method("POST")
                        .uri(inventory))
                .route("admin-inventory-stock-item-read", route -> route
                        .path("/api/v1/admin/inventory/stock-items/{id}")
                        .and().method("GET")
                        .uri(inventory))
                .route("admin-inventory-stock-items", route -> route
                        .path("/api/v1/admin/inventory/stock-items")
                        .and().method("POST")
                        .uri(inventory))
                .build();
    }
}
