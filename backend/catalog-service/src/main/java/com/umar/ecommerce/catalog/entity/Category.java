package com.umar.ecommerce.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Locale;

@Entity
@Table(name = "category")
public class Category extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 120)
    private String slug;

    protected Category() {
    }

    public Category(String name, String slug) {
        updateDetails(name, slug);
    }

    public void updateDetails(String name, String slug) {
        this.name = requireText(name, "name");
        this.slug = normalizeSlug(slug);
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    private static String normalizeSlug(String slug) {
        return requireText(slug, "slug").toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
