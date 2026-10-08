package com.umar.ecommerce.catalog.service;

import com.umar.ecommerce.catalog.dto.response.CategoryResponse;
import com.umar.ecommerce.catalog.dto.response.ProductResponse;
import com.umar.ecommerce.catalog.dto.response.ProductSummaryResponse;
import com.umar.ecommerce.catalog.dto.response.ProductVariantResponse;
import com.umar.ecommerce.catalog.dto.response.VariantPriceResponse;
import com.umar.ecommerce.catalog.entity.Category;
import com.umar.ecommerce.catalog.entity.Product;
import com.umar.ecommerce.catalog.entity.ProductVariant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * Structural response mapping only. Callers choose the variant list so a public response cannot
 * pick up inactive variants that happen to be loaded on the product. This mapper does not load
 * relationships, apply versions, or change activation.
 */
@Mapper(componentModel = "spring", config = CatalogMapperConfig.class)
public interface CatalogMapper {

    CategoryResponse toCategoryResponse(Category category);

    @Named("variantResponse")
    @Mapping(target = "productId", source = "product.id")
    ProductVariantResponse toVariantResponse(ProductVariant variant);

    @Named("variantPrice")
    VariantPriceResponse toVariantPriceResponse(ProductVariant variant);

    @Mapping(target = "id", source = "product.id")
    @Mapping(target = "name", source = "product.name")
    @Mapping(target = "slug", source = "product.slug")
    @Mapping(target = "description", source = "product.description")
    @Mapping(target = "category", source = "product.category")
    @Mapping(target = "variants", source = "variants", qualifiedByName = "variantResponse")
    @Mapping(target = "active", source = "product.active")
    @Mapping(target = "createdAt", source = "product.createdAt")
    @Mapping(target = "updatedAt", source = "product.updatedAt")
    @Mapping(target = "version", source = "product.version")
    ProductResponse toProductResponse(Product product, List<ProductVariant> variants);

    @Mapping(target = "id", source = "product.id")
    @Mapping(target = "name", source = "product.name")
    @Mapping(target = "slug", source = "product.slug")
    @Mapping(target = "description", source = "product.description")
    @Mapping(target = "category", source = "product.category")
    @Mapping(target = "variants", source = "variants", qualifiedByName = "variantPrice")
    @Mapping(target = "active", source = "product.active")
    @Mapping(target = "createdAt", source = "product.createdAt")
    @Mapping(target = "updatedAt", source = "product.updatedAt")
    @Mapping(target = "version", source = "product.version")
    ProductSummaryResponse toProductSummaryResponse(Product product, List<ProductVariant> variants);
}
