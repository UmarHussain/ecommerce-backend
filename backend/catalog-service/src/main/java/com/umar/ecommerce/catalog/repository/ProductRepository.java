package com.umar.ecommerce.catalog.repository;

import com.umar.ecommerce.catalog.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository
        extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlug(String slug);

    @EntityGraph(attributePaths = "category")
    Optional<Product> findByIdAndActiveTrueAndCategory_ActiveTrue(UUID id);

    @EntityGraph(attributePaths = "category")
    Optional<Product> findBySlugAndActiveTrueAndCategory_ActiveTrue(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(@Nullable Specification<Product> specification, Pageable pageable);
}
