package com.umar.ecommerce.inventory.repository;

import com.umar.ecommerce.inventory.entity.StockAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, UUID> {

    Page<StockAdjustment> findByStockItemId(UUID stockItemId, Pageable pageable);
}
