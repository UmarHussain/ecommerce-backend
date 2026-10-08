package com.umar.ecommerce.inventory.service;

import com.umar.ecommerce.inventory.dto.response.StockAdjustmentResponse;
import com.umar.ecommerce.inventory.dto.response.StockItemResponse;
import com.umar.ecommerce.inventory.entity.StockAdjustment;
import com.umar.ecommerce.inventory.entity.StockItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Structural mapping only. Quantities, reasons, versions, and actor identity
 * are decided by the stock service before these objects are mapped.
 */
@Mapper(config = InventoryMapperConfig.class)
public interface InventoryMapper {

    @Mapping(target = "available", expression = "java(item.available())")
    @Mapping(target = "id", source = "id")
    @Mapping(target = "catalogVariantId", source = "catalogVariantId")
    @Mapping(target = "sku", source = "sku")
    @Mapping(target = "onHand", source = "onHand")
    @Mapping(target = "reserved", source = "reserved")
    @Mapping(target = "version", source = "version")
    @Mapping(target = "productNameSnapshot", source = "productNameSnapshot")
    @Mapping(target = "variantNameSnapshot", source = "variantNameSnapshot")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    StockItemResponse toStockItem(StockItem item);

    @Mapping(target = "stockItemId", source = "stockItem.id")
    @Mapping(target = "reference", source = "referenceText")
    @Mapping(target = "id", source = "id")
    @Mapping(target = "operationType", source = "operationType")
    @Mapping(target = "delta", source = "delta")
    @Mapping(target = "beforeOnHand", source = "beforeOnHand")
    @Mapping(target = "afterOnHand", source = "afterOnHand")
    @Mapping(target = "reservedSnapshot", source = "reservedSnapshot")
    @Mapping(target = "resultingVersion", source = "resultingVersion")
    @Mapping(target = "reasonCode", source = "reasonCode")
    @Mapping(target = "note", source = "note")
    @Mapping(target = "actorIssuer", source = "actorIssuer")
    @Mapping(target = "actorSubject", source = "actorSubject")
    @Mapping(target = "createdAt", source = "createdAt")
    StockAdjustmentResponse toAdjustment(StockAdjustment adjustment);
}
