package com.umar.ecommerce.catalog.repository;

import com.umar.ecommerce.catalog.entity.Category;
import com.umar.ecommerce.catalog.entity.Product;
import com.umar.ecommerce.catalog.entity.ProductVariant;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> publicCatalog(
            String search,
            String categorySlug,
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Product, Category> category = root.join("category", JoinType.INNER);

            predicates.add(builder.isTrue(root.get("active")));
            predicates.add(builder.isTrue(category.get("active")));

            if (categorySlug != null) {
                predicates.add(builder.equal(category.get("slug"), categorySlug));
            }

            boolean hasPriceFilter = minPrice != null || maxPrice != null;
            if (search != null) {
                String pattern = containsPattern(search);
                Predicate productTextMatch = builder.or(
                        like(builder, root.get("name"), pattern),
                        like(builder, root.get("slug"), pattern)
                );
                Predicate variantTextMatch = matchingVariantExists(
                        root,
                        query,
                        builder,
                        pattern,
                        minPrice,
                        maxPrice
                );

                if (hasPriceFilter) {
                    Predicate productWithMatchingPrice = builder.and(
                            productTextMatch,
                            matchingVariantExists(root, query, builder, null, minPrice, maxPrice)
                    );
                    predicates.add(builder.or(productWithMatchingPrice, variantTextMatch));
                } else {
                    predicates.add(builder.or(productTextMatch, variantTextMatch));
                }
                query.distinct(true);
            } else if (hasPriceFilter) {
                predicates.add(matchingVariantExists(
                        root,
                        query,
                        builder,
                        null,
                        minPrice,
                        maxPrice
                ));
                query.distinct(true);
            }

            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate matchingVariantExists(
            Root<Product> productRoot,
            CriteriaQuery<?> query,
            CriteriaBuilder builder,
            String searchPattern,
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<ProductVariant> variant = subquery.from(ProductVariant.class);
        Join<ProductVariant, Product> variantProduct = variant.join("product", JoinType.INNER);
        List<Predicate> predicates = new ArrayList<>();

        predicates.add(builder.equal(variantProduct.get("id"), productRoot.get("id")));
        predicates.add(builder.isTrue(variant.get("active")));

        if (searchPattern != null) {
            predicates.add(builder.or(
                    like(builder, variant.get("sku"), searchPattern),
                    like(builder, variant.get("name"), searchPattern)
            ));
        }
        if (minPrice != null) {
            predicates.add(builder.greaterThanOrEqualTo(variant.get("price"), minPrice));
        }
        if (maxPrice != null) {
            predicates.add(builder.lessThanOrEqualTo(variant.get("price"), maxPrice));
        }

        subquery.select(builder.literal(1))
                .where(predicates.toArray(Predicate[]::new));
        return builder.exists(subquery);
    }

    private static Predicate like(
            CriteriaBuilder builder,
            jakarta.persistence.criteria.Expression<String> expression,
            String pattern
    ) {
        return builder.like(builder.lower(expression), pattern, '\\');
    }

    private static String containsPattern(String value) {
        String escaped = value.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
