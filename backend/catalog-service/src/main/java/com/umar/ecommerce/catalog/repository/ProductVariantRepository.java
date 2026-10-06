package com.umar.ecommerce.catalog.repository;

import com.umar.ecommerce.catalog.entity.ProductVariant;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    Optional<ProductVariant> findBySku(String sku);

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, UUID id);

    @EntityGraph(attributePaths = {"product", "product.category"})
    List<ProductVariant>
    findAllBySkuInAndActiveTrueAndProduct_ActiveTrueAndProduct_Category_ActiveTrue(
            Collection<String> skus
    );

    List<ProductVariant>
    findAllByProduct_IdAndActiveTrueAndProduct_ActiveTrueAndProduct_Category_ActiveTrueOrderByPriceAscSkuAsc(
            UUID productId
    );

    List<ProductVariant>
    findAllByProduct_IdInAndActiveTrueAndProduct_ActiveTrueAndProduct_Category_ActiveTrueOrderByProduct_IdAscPriceAscSkuAsc(
            Collection<UUID> productIds
    );

    List<ProductVariant> findAllByProduct_IdOrderByPriceAscSkuAsc(UUID productId);
}
