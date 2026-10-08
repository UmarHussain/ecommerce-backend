package com.umar.ecommerce.inventory.catalog;

import java.util.UUID;

public interface CatalogLookupPort {

    CatalogVariantSnapshot load(UUID catalogVariantId, String bearerToken, String correlationId);
}
