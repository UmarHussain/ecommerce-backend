package com.umar.ecommerce.catalog.controller;

import com.umar.ecommerce.catalog.config.SecurityConfig;
import com.umar.ecommerce.catalog.dto.response.CategoryResponse;
import com.umar.ecommerce.catalog.dto.response.PageResponse;
import com.umar.ecommerce.catalog.dto.response.ProductResponse;
import com.umar.ecommerce.catalog.dto.response.ProductVariantResponse;
import com.umar.ecommerce.catalog.exception.InvalidRequestException;
import com.umar.ecommerce.catalog.exception.ResourceNotFoundException;
import com.umar.ecommerce.catalog.service.CategoryService;
import com.umar.ecommerce.catalog.service.ProductService;
import com.umar.ecommerce.catalog.service.ProductVariantService;
import com.umar.ecommerce.catalog.web.ApiExceptionHandler;
import com.umar.ecommerce.catalog.web.CorrelationIdFilter;
import com.umar.ecommerce.catalog.web.SecurityProblemWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PublicCatalogController.class, AdminCatalogController.class})
@Import({
        SecurityConfig.class,
        SecurityProblemWriter.class,
        ApiExceptionHandler.class,
        CorrelationIdFilter.class
})

class CatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;
    @MockitoBean
    private ProductService productService;
    @MockitoBean
    private ProductVariantService variantService;

    @Test
    void publicCatalogIsAnonymousAndUsesStablePageEnvelope() throws Exception {
        when(productService.searchPublicProducts(
                any(), any(), any(), any(), any(Integer.class), any(Integer.class), any()
        )).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true, true, "name,asc"));

        mockMvc.perform(get("/api/v1/catalog/products")
                        .header(CorrelationIdFilter.HEADER_NAME, "request-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, "request-123"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.sort").value("name,asc"));
    }

    @Test
    void publicSearchForwardsFiltersPaginationAndSort() throws Exception {
        when(productService.searchPublicProducts(
                eq("keyboard"),
                eq("electronics"),
                eq(new BigDecimal("10.0000")),
                eq(new BigDecimal("120.0000")),
                eq(1),
                eq(5),
                eq("slug,desc")
        )).thenReturn(new PageResponse<>(List.of(), 1, 5, 0, 0, false, true, "slug,desc"));

        mockMvc.perform(get("/api/v1/catalog/products")
                        .param("search", "keyboard")
                        .param("category", "electronics")
                        .param("minPrice", "10.0000")
                        .param("maxPrice", "120.0000")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "slug,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.sort").value("slug,desc"));
    }

    @Test
    void publicSearchRejectsInvalidPaginationWithoutQuerying() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/catalog/products").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_VALIDATION_FAILED"));

        verify(productService, never()).searchPublicProducts(
                any(), any(), any(), any(), any(Integer.class), any(Integer.class), any()
        );
    }

    @Test
    void publicSearchRejectsDisallowedSortWithProblemDetails() throws Exception {
        when(productService.searchPublicProducts(
                any(), any(), any(), any(), any(Integer.class), any(Integer.class), eq("price,asc")
        )).thenThrow(new InvalidRequestException(
                "sort field must be one of name, slug, createdAt, updatedAt"
        ));

        mockMvc.perform(get("/api/v1/catalog/products").param("sort", "price,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_INVALID_REQUEST"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void publicProductLookupAndCategoriesAreAnonymous() throws Exception {
        UUID productId = UUID.randomUUID();
        Instant now = Instant.now();
        when(productService.getPublicProduct(productId)).thenReturn(productResponse(productId, now));
        when(productService.getPublicProductBySlug("wireless-headphones"))
                .thenReturn(productResponse(productId, now));
        when(categoryService.listActiveCategories()).thenReturn(List.of(
                new CategoryResponse(UUID.randomUUID(), "Electronics", "electronics", true, now, now, 0)
        ));
        when(variantService.listPublicVariants(productId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/catalog/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("wireless-headphones"));
        mockMvc.perform(get("/api/v1/catalog/products/slug/{slug}", "wireless-headphones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Wireless Headphones"));
        mockMvc.perform(get("/api/v1/catalog/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("electronics"));
        mockMvc.perform(get("/api/v1/catalog/products/{productId}/variants", productId))
                .andExpect(status().isOk());
    }

    @Test
    void missingPublicProductUsesProblemDetails() throws Exception {
        UUID productId = UUID.randomUUID();
        when(productService.getPublicProduct(productId))
                .thenThrow(new ResourceNotFoundException("Product '%s' was not found".formatted(productId)));

        mockMvc.perform(get("/api/v1/catalog/products/{id}", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATALOG_RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void unexpectedFailuresDoNotExposeInternalDetails() throws Exception {
        when(productService.getPublicProduct(any()))
                .thenThrow(new IllegalStateException("jdbc password=super-secret"));

        mockMvc.perform(get("/api/v1/catalog/products/{id}", UUID.randomUUID()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("CATALOG_INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("super-secret")
                )));
    }

    @Test
    void malformedJsonUsesProblemDetails() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/variants/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_MALFORMED_REQUEST"));
    }

    @Test
    void adminCatalogFailsClosedWithoutAdminAuthority() throws Exception {
        String body = """
                {"name":"Electronics","slug":"electronics"}
                """;

        mockMvc.perform(post("/api/v1/admin/catalog/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CATALOG_AUTHENTICATION_REQUIRED"));

        mockMvc.perform(post("/api/v1/admin/catalog/categories")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CATALOG_ACCESS_DENIED"));
    }

    @Test
    void invalidBearerTokensAndMetricsFailClosed() throws Exception {
        mockMvc.perform(get("/api/v1/admin/catalog/products")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CATALOG_AUTHENTICATION_REQUIRED"));

        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CATALOG_AUTHENTICATION_REQUIRED"));
    }

    @Test
    void adminCanCreateUpdateAndChangeStatus() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        CategoryResponse created = new CategoryResponse(
                id, "Electronics", "electronics", true, now, now, 0
        );
        when(categoryService.create(any())).thenReturn(created);
        when(categoryService.update(eq(id), any())).thenReturn(created);
        when(categoryService.changeStatus(eq(id), eq(false))).thenReturn(
                new CategoryResponse(id, "Electronics", "electronics", false, now, now, 1)
        );

        mockMvc.perform(post("/api/v1/admin/catalog/categories")
                        .with(jwt().authorities(new SimpleGrantedAuthority("PERM_catalog.create"), new SimpleGrantedAuthority("PERM_catalog.update"), new SimpleGrantedAuthority("PERM_catalog.activate")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Electronics","slug":"electronics"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/v1/admin/catalog/categories/" + id
                ))
                .andExpect(jsonPath("$.slug").value("electronics"));

        mockMvc.perform(put("/api/v1/admin/catalog/categories/{id}", id)
                        .with(jwt().authorities(new SimpleGrantedAuthority("PERM_catalog.create"), new SimpleGrantedAuthority("PERM_catalog.update"), new SimpleGrantedAuthority("PERM_catalog.activate")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Electronics","slug":"electronics"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/admin/catalog/categories/{id}/status", id)
                        .with(jwt().authorities(new SimpleGrantedAuthority("PERM_catalog.create"), new SimpleGrantedAuthority("PERM_catalog.update"), new SimpleGrantedAuthority("PERM_catalog.activate")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void validationFailuresUseProblemDetailsAndGeneratedCorrelationId() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/variants/batch")
                        .header(CorrelationIdFilter.HEADER_NAME, "invalid header value")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skus\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME))
                .andExpect(jsonPath("$.code").value("CATALOG_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("skus"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    private static ProductResponse productResponse(UUID id, Instant now) {
        return new ProductResponse(
                id,
                "Wireless Headphones",
                "wireless-headphones",
                "Everyday headphones",
                new CategoryResponse(UUID.randomUUID(), "Electronics", "electronics", true, now, now, 0),
                List.of(new ProductVariantResponse(
                        UUID.randomUUID(),
                        id,
                        "HEADPHONES-BLK",
                        "Black",
                        new BigDecimal("79.9900"),
                        "USD",
                        "https://example.test/black.jpg",
                        true,
                        now,
                        now,
                        0
                )),
                true,
                now,
                now,
                0
        );
    }
}
