package com.umar.ecommerce.catalog.repository;

import com.umar.ecommerce.catalog.entity.Category;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CategorySpecifications {

    private CategorySpecifications() {
    }

    public static Specification<Category> adminSearch(String search, Boolean active) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (active != null) {
                predicates.add(builder.equal(root.get("active"), active));
            }
            if (search != null) {
                String escaped = search.toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                String pattern = "%" + escaped + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), pattern, '\\'),
                        builder.like(builder.lower(root.get("slug")), pattern, '\\')
                ));
            }
            if (predicates.isEmpty()) {
                return builder.conjunction();
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
