package com.umar.ecommerce.inventory.catalog;

public class CatalogTechnicalException extends RuntimeException {

    public enum Kind {
        TIMEOUT,
        UNAVAILABLE
    }

    private final Kind kind;

    public CatalogTechnicalException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
