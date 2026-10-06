package com.umar.ecommerce.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "product")
public class Product extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 160)
    private String slug;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    protected Product() {
    }

    public Product(String name, String slug, String description, Category category) {
        updateDetails(name, slug, description);
        changeCategory(category);
    }

    public void updateDetails(String name, String slug, String description) {
        this.name = requireText(name, "name");
        this.slug = normalizeSlug(slug);
        this.description = normalizeNullableText(description);
    }

    public void changeCategory(Category category) {
        this.category = Objects.requireNonNull(category, "category must not be null");
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    private static String normalizeSlug(String slug) {
        return requireText(slug, "slug").toLowerCase(Locale.ROOT);
    }

    private static String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
