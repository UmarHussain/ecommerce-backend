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
import java.util.UUID;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> publicCatalog(
            String search,
            String categorySlug,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String currency
    ) {
        return catalog(
                search,
                categorySlug,
                null,
                true,
                Boolean.TRUE,
                true,
                minPrice,
                maxPrice,
                currency
        );
    }

    /**
     * Admin search. {@code active} null means both states. Price bounds compare only variants
     * stored in {@code currency}; amounts are not converted. Inactive variants count so staff
     * can find a product by a hidden price.
     */
    public static Specification<Product> adminCatalog(
            String search,
            UUID categoryId,
            Boolean active,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String currency
    ) {
        return catalog(
                search,
                null,
                categoryId,
                false,
                active,
                false,
                minPrice,
                maxPrice,
                currency
        );
    }

    private static Specification<Product> catalog(
            String search,
            String categorySlug,
            UUID categoryId,
            boolean requireActiveChain,
            Boolean productActive,
            boolean activeVariantsOnly,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String currency
    ) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Product, Category> category = root.join("category", JoinType.INNER);

            if (requireActiveChain) {
                predicates.add(builder.isTrue(root.get("active")));
                predicates.add(builder.isTrue(category.get("active")));
            } else if (productActive != null) {
                predicates.add(builder.equal(root.get("active"), productActive));
            }

            if (categorySlug != null) {
                predicates.add(builder.equal(category.get("slug"), categorySlug));
            }
            if (categoryId != null) {
                predicates.add(builder.equal(category.get("id"), categoryId));
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
                        maxPrice,
                        currency,
                        activeVariantsOnly
                );

                if (hasPriceFilter) {
                    Predicate productWithMatchingPrice = builder.and(
                            productTextMatch,
                            matchingVariantExists(
                                    root, query, builder, null, minPrice, maxPrice, currency, activeVariantsOnly
                            )
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
                        maxPrice,
                        currency,
                        activeVariantsOnly
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
            BigDecimal maxPrice,
            String currency,
            boolean activeVariantsOnly
    ) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<ProductVariant> variant = subquery.from(ProductVariant.class);
        Join<ProductVariant, Product> variantProduct = variant.join("product", JoinType.INNER);
        List<Predicate> predicates = new ArrayList<>();

        predicates.add(builder.equal(variantProduct.get("id"), productRoot.get("id")));
        if (activeVariantsOnly) {
            predicates.add(builder.isTrue(variant.get("active")));
        }
        if (currency != null) {
            predicates.add(builder.equal(variant.get("currency"), currency));
        }

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
