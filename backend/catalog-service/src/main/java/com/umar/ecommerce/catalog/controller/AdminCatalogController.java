package com.umar.ecommerce.catalog.controller;

import com.umar.ecommerce.catalog.dto.request.ActiveStatusRequest;
import com.umar.ecommerce.catalog.dto.request.CategoryRequest;
import com.umar.ecommerce.catalog.dto.request.ProductRequest;
import com.umar.ecommerce.catalog.dto.request.ProductVariantRequest;
import com.umar.ecommerce.catalog.dto.response.CategoryResponse;
import com.umar.ecommerce.catalog.dto.response.ProductResponse;
import com.umar.ecommerce.catalog.dto.response.ProductVariantResponse;
import com.umar.ecommerce.catalog.service.CategoryService;
import com.umar.ecommerce.catalog.service.ProductService;
import com.umar.ecommerce.catalog.service.ProductVariantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/catalog")
@Validated
@Tag(name = "Admin Catalog", description = "Catalog administration operations")
@SecurityRequirement(name = "bearerAuth")
public class AdminCatalogController {

    private final CategoryService categoryService;
    private final ProductService productService;
    private final ProductVariantService variantService;

    public AdminCatalogController(
            CategoryService categoryService,
            ProductService productService,
            ProductVariantService variantService
    ) {
        this.categoryService = categoryService;
        this.productService = productService;
        this.variantService = variantService;
    }

    @PreAuthorize("hasAuthority('PERM_catalog.create')")
    @PostMapping("/categories")
    @Operation(summary = "Create a category")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Category created"),
            @ApiResponse(responseCode = "409", description = "Slug already exists")
    })
    public ResponseEntity<CategoryResponse> createCategory(
            @Valid @RequestBody CategoryRequest request
    ) {
        CategoryResponse response = categoryService.create(request);
        return ResponseEntity.created(
                URI.create("/api/v1/admin/catalog/categories/" + response.id())
        ).body(response);
    }

    @PreAuthorize("hasAuthority('PERM_catalog.update')")
    @PutMapping("/categories/{id}")
    @Operation(summary = "Update a category")
    @ApiResponse(responseCode = "200", description = "Category updated")
    public CategoryResponse updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryRequest request
    ) {
        return categoryService.update(id, request);
    }

    @PreAuthorize("hasAuthority('PERM_catalog.activate')")
    @PatchMapping("/categories/{id}/status")
    @Operation(summary = "Change category activation status")
    @ApiResponse(responseCode = "200", description = "Category status updated")
    public CategoryResponse changeCategoryStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ActiveStatusRequest request
    ) {
        return categoryService.changeStatus(id, request.active());
    }

    @PreAuthorize("hasAuthority('PERM_catalog.create')")
    @PostMapping("/products")
    @Operation(summary = "Create a product")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Product created"),
            @ApiResponse(responseCode = "409", description = "Slug already exists")
    })
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody ProductRequest request
    ) {
        ProductResponse response = productService.create(request);
        return ResponseEntity.created(
                URI.create("/api/v1/admin/catalog/products/" + response.id())
        ).body(response);
    }

    @PreAuthorize("hasAuthority('PERM_catalog.update')")
    @PutMapping("/products/{id}")
    @Operation(summary = "Update a product")
    @ApiResponse(responseCode = "200", description = "Product updated")
    public ProductResponse updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody ProductRequest request
    ) {
        return productService.update(id, request);
    }

    @PreAuthorize("hasAuthority('PERM_catalog.activate')")
    @PatchMapping("/products/{id}/status")
    @Operation(summary = "Change product activation status")
    @ApiResponse(responseCode = "200", description = "Product status updated")
    public ProductResponse changeProductStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ActiveStatusRequest request
    ) {
        return productService.changeStatus(id, request.active());
    }

    @PreAuthorize("hasAuthority('PERM_catalog.create')")
    @PostMapping("/products/{productId}/variants")
    @Operation(summary = "Create a product variant")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Product variant created"),
            @ApiResponse(responseCode = "409", description = "SKU already exists")
    })
    public ResponseEntity<ProductVariantResponse> createVariant(
            @PathVariable UUID productId,
            @Valid @RequestBody ProductVariantRequest request
    ) {
        ProductVariantResponse response = variantService.create(productId, request);
        return ResponseEntity.created(
                URI.create("/api/v1/admin/catalog/variants/" + response.id())
        ).body(response);
    }

    @PreAuthorize("hasAuthority('PERM_catalog.update')")
    @PutMapping("/variants/{variantId}")
    @Operation(summary = "Update a product variant")
    @ApiResponse(responseCode = "200", description = "Product variant updated")
    public ProductVariantResponse updateVariant(
            @PathVariable UUID variantId,
            @Valid @RequestBody ProductVariantRequest request
    ) {
        return variantService.update(variantId, request);
    }

    @PreAuthorize("hasAuthority('PERM_catalog.activate')")
    @PatchMapping("/variants/{variantId}/status")
    @Operation(summary = "Change product variant activation status")
    @ApiResponse(responseCode = "200", description = "Product variant status updated")
    public ProductVariantResponse changeVariantStatus(
            @PathVariable UUID variantId,
            @Valid @RequestBody ActiveStatusRequest request
    ) {
        return variantService.changeStatus(variantId, request.active());
    }
}
