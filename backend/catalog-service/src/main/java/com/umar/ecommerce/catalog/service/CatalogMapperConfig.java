package com.umar.ecommerce.catalog.service;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.MapperConfig;
import org.mapstruct.ReportingPolicy;

/**
 * Service-local MapStruct defaults. Each service owns its mappers; there is no shared mapper JAR.
 * Constructor injection applies when a mapper depends on another Spring bean.
 */
@MapperConfig(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR
)
public interface CatalogMapperConfig {
}
