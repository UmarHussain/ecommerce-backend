package com.umar.ecommerce.catalog.cache;

/**
 * Published inside a catalog write transaction. The after-commit listener
 * evicts public regions only when that transaction commits.
 */
public record PublicCatalogChanged(String reason) {
}
