package com.umar.ecommerce.catalog.controller;

import com.umar.ecommerce.catalog.dto.request.BatchVariantRequest;
import com.umar.ecommerce.catalog.dto.response.BatchVariantResponse;
import com.umar.ecommerce.catalog.dto.response.CategoryResponse;
import com.umar.ecommerce.catalog.dto.response.PageResponse;
import com.umar.ecommerce.catalog.dto.response.ProductResponse;
import com.umar.ecommerce.catalog.dto.response.ProductSummaryResponse;
import com.umar.ecommerce.catalog.dto.response.ProductVariantResponse;
import com.umar.ecommerce.catalog.service.CategoryService;
import com.umar.ecommerce.catalog.service.ProductService;
import com.umar.ecommerce.catalog.service.ProductVariantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog")
@Validated
@Tag(name = "Public Catalog", description = "Active public catalog browsing")
public class PublicCatalogController {

    private final ProductService productService;
    private final ProductVariantService variantService;
    private final CategoryService categoryService;

    public PublicCatalogController(
            ProductService productService,
            ProductVariantService variantService,
            CategoryService categoryService
    ) {
        this.productService = productService;
        this.variantService = variantService;
        this.categoryService = categoryService;
    }

    @GetMapping("/products")
    @Operation(summary = "Search active products")
    @ApiResponse(responseCode = "200", description = "Product page returned")
    public PageResponse<ProductSummaryResponse> searchProducts(
            @RequestParam(required = false) String search,
            @Parameter(description = "Active category slug")
            @RequestParam(name = "category", required = false) String categorySlug,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Allowlisted field and direction, for example name,asc")
            @RequestParam(defaultValue = "name,asc") String sort
    ) {
        return productService.searchPublicProducts(
                search,
                categorySlug,
                minPrice,
                maxPrice,
                page,
                size,
                sort
        );
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "Get an active product by identifier")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product returned"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public ProductResponse getProduct(@PathVariable UUID id) {
        return productService.getPublicProduct(id);
    }

    @GetMapping("/products/slug/{slug}")
    @Operation(summary = "Get an active product by slug")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product returned"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public ProductResponse getProductBySlug(@PathVariable String slug) {
        return productService.getPublicProductBySlug(slug);
    }

    @GetMapping("/categories")
    @Operation(summary = "List active categories")
    @ApiResponse(responseCode = "200", description = "Categories returned")
    public List<CategoryResponse> listCategories() {
        return categoryService.listActiveCategories();
    }

    @GetMapping("/products/{productId}/variants")
    @Operation(summary = "List active variants for an active product")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Variants returned"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public List<ProductVariantResponse> listProductVariants(
            @PathVariable UUID productId
    ) {
        return variantService.listPublicVariants(productId);
    }

    @PostMapping("/variants/batch")
    @Operation(summary = "Find active variants by SKU")
    @ApiResponse(responseCode = "200", description = "Batch lookup completed")
    public BatchVariantResponse findVariantsBySku(
            @Valid @RequestBody BatchVariantRequest request
    ) {
        return variantService.findPublicVariantsBySku(request);
    }
}
