package com.umar.ecommerce.catalog.service;

import com.umar.ecommerce.catalog.dto.response.CategoryResponse;
import com.umar.ecommerce.catalog.dto.response.ProductResponse;
import com.umar.ecommerce.catalog.dto.response.ProductSummaryResponse;
import com.umar.ecommerce.catalog.dto.response.ProductVariantResponse;
import com.umar.ecommerce.catalog.dto.response.VariantPriceResponse;
import com.umar.ecommerce.catalog.entity.Category;
import com.umar.ecommerce.catalog.entity.Product;
import com.umar.ecommerce.catalog.entity.ProductVariant;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CatalogMapper {

    public CategoryResponse toCategoryResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.isActive(),
                category.getCreatedAt(),
                category.getUpdatedAt(),
                category.getVersion()
        );
    }

    public ProductVariantResponse toVariantResponse(ProductVariant variant) {
        return new ProductVariantResponse(
                variant.getId(),
                variant.getProduct().getId(),
                variant.getSku(),
                variant.getName(),
                variant.getPrice(),
                variant.getCurrency(),
                variant.getImageUrl(),
                variant.isActive(),
                variant.getCreatedAt(),
                variant.getUpdatedAt(),
                variant.getVersion()
        );
    }

    public VariantPriceResponse toVariantPriceResponse(ProductVariant variant) {
        return new VariantPriceResponse(
                variant.getId(),
                variant.getSku(),
                variant.getName(),
                variant.getPrice(),
                variant.getCurrency(),
                variant.getImageUrl()
        );
    }

    public ProductResponse toProductResponse(
            Product product,
            List<ProductVariant> variants
    ) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                toCategoryResponse(product.getCategory()),
                variants.stream().map(this::toVariantResponse).toList(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getVersion()
        );
    }

    public ProductSummaryResponse toProductSummaryResponse(
            Product product,
            List<ProductVariant> variants
    ) {
        return new ProductSummaryResponse(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                toCategoryResponse(product.getCategory()),
                variants.stream().map(this::toVariantPriceResponse).toList(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getVersion()
        );
    }
}
