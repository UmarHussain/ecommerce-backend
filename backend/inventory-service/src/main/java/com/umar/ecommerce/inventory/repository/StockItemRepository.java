package com.umar.ecommerce.inventory.repository;

import com.umar.ecommerce.inventory.entity.StockItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StockItemRepository extends JpaRepository<StockItem, UUID>, JpaSpecificationExecutor<StockItem> {

    boolean existsByCatalogVariantId(UUID catalogVariantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from StockItem item where item.id = :id")
    Optional<StockItem> lockById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from StockItem item where item.catalogVariantId = :catalogVariantId")
    Optional<StockItem> lockByCatalogVariantId(@Param("catalogVariantId") UUID catalogVariantId);
}
