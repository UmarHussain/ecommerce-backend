package com.umar.ecommerce.cart.service;

import com.umar.ecommerce.cart.dto.response.CartItemResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;

/**
 * Structural mapping only. Quantity rules, ownership, version, and catalog
 * freshness are decided by the cart services before this mapper runs.
 */
@Mapper(config = CartMapperConfig.class)
public interface CartMapper {

    @Mapping(target = "catalogVariantId", source = "line.catalogVariantId")
    @Mapping(target = "sku", source = "line.sku")
    @Mapping(target = "quantity", source = "line.quantity")
    @Mapping(target = "displayName", source = "line.displayName")
    @Mapping(target = "imageUrl", source = "line.imageUrl")
    @Mapping(target = "unitPrice", source = "line.unitPrice")
    @Mapping(target = "currency", source = "line.currency")
    @Mapping(target = "snapshotAt", source = "line.snapshotAt")
    @Mapping(target = "lineTotal", expression = "java(line.lineTotal())")
    @Mapping(target = "catalogState", source = "catalogState")
    @Mapping(target = "currentUnitPrice", source = "currentUnitPrice")
    CartItemResponse toItem(StoredLine line, CatalogLineState catalogState, BigDecimal currentUnitPrice);
}
