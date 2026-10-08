package com.umar.ecommerce.catalog.controller;

import com.umar.ecommerce.catalog.dto.request.ActiveStatusRequest;
import com.umar.ecommerce.catalog.dto.request.CategoryRequest;
import com.umar.ecommerce.catalog.dto.request.CategoryUpdateRequest;
import com.umar.ecommerce.catalog.dto.request.ProductRequest;
import com.umar.ecommerce.catalog.dto.request.ProductUpdateRequest;
import com.umar.ecommerce.catalog.dto.request.ProductVariantRequest;
import com.umar.ecommerce.catalog.dto.request.ProductVariantUpdateRequest;
import com.umar.ecommerce.catalog.dto.response.CategoryResponse;
import com.umar.ecommerce.catalog.dto.response.PageResponse;
import com.umar.ecommerce.catalog.dto.response.ProductResponse;
import com.umar.ecommerce.catalog.dto.response.ProductSummaryResponse;
import com.umar.ecommerce.catalog.dto.response.ProductVariantResponse;
import com.umar.ecommerce.catalog.service.CategoryService;
import com.umar.ecommerce.catalog.service.ProductService;
import com.umar.ecommerce.catalog.service.ProductVariantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
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

    @PreAuthorize("hasAuthority('PERM_catalog.read')")
    @GetMapping("/categories")
    @Operation(summary = "Search categories, including inactive records")
    public PageResponse<CategoryResponse> searchCategories(
            @RequestParam(required = false) String search,
            @Parameter(description = "Omit to include active and inactive categories")
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "name,asc") String sort
    ) {
        return categoryService.searchAdmin(search, active, page, size, sort);
    }

    @PreAuthorize("hasAuthority('PERM_catalog.read')")
    @GetMapping("/categories/{id}")
    @Operation(summary = "Get a category by identifier, including inactive")
    public CategoryResponse getCategory(@PathVariable UUID id) {
        return categoryService.getAdmin(id);
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
            @Valid @RequestBody CategoryUpdateRequest request
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
        return categoryService.changeStatus(id, request.active(), request.expectedVersion());
    }

    @PreAuthorize("hasAuthority('PERM_catalog.read')")
    @GetMapping("/products")
    @Operation(summary = "Search products, including inactive records")
    public PageResponse<ProductSummaryResponse> searchProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId,
            @Parameter(description = "Omit to include active and inactive products")
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "ISO 4217 currency. Required when minPrice or maxPrice is set. Amounts are not converted.")
            @RequestParam(required = false) String currency,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "name,asc") String sort
    ) {
        return productService.searchAdminProducts(
                search, categoryId, active, minPrice, maxPrice, currency, page, size, sort
        );
    }

    @PreAuthorize("hasAuthority('PERM_catalog.read')")
    @GetMapping("/products/{id}")
    @Operation(summary = "Get a product by identifier, including inactive variants")
    public ProductResponse getProduct(@PathVariable UUID id) {
        return productService.getAdminProduct(id);
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
            @Valid @RequestBody ProductUpdateRequest request
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
        return productService.changeStatus(id, request.active(), request.expectedVersion());
    }

    @PreAuthorize("hasAuthority('PERM_catalog.read')")
    @GetMapping("/products/{productId}/variants")
    @Operation(summary = "List up to 100 variants for a product, including inactive")
    public List<ProductVariantResponse> listVariants(@PathVariable UUID productId) {
        return variantService.listAdminVariants(productId);
    }

    @PreAuthorize("hasAuthority('PERM_catalog.read')")
    @GetMapping("/variants/{variantId}")
    @Operation(summary = "Get a variant by identifier, including inactive")
    public ProductVariantResponse getVariant(@PathVariable UUID variantId) {
        return variantService.getAdmin(variantId);
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
            @Valid @RequestBody ProductVariantUpdateRequest request
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
        return variantService.changeStatus(variantId, request.active(), request.expectedVersion());
    }
}
