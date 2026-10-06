package com.umar.ecommerce.catalog.service;

import com.umar.ecommerce.catalog.dto.request.BatchVariantRequest;
import com.umar.ecommerce.catalog.dto.request.ProductVariantRequest;
import com.umar.ecommerce.catalog.dto.response.BatchVariantResponse;
import com.umar.ecommerce.catalog.dto.response.ProductVariantResponse;
import com.umar.ecommerce.catalog.entity.Product;
import com.umar.ecommerce.catalog.entity.ProductVariant;
import com.umar.ecommerce.catalog.exception.InvalidRequestException;
import com.umar.ecommerce.catalog.exception.ResourceConflictException;
import com.umar.ecommerce.catalog.exception.ResourceNotFoundException;
import com.umar.ecommerce.catalog.repository.ProductRepository;
import com.umar.ecommerce.catalog.repository.ProductVariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductVariantService {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final CatalogMapper mapper;

    public ProductVariantService(
            ProductVariantRepository variantRepository,
            ProductRepository productRepository,
            CatalogMapper mapper
    ) {
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.mapper = mapper;
    }

    public List<ProductVariantResponse> listPublicVariants(UUID productId) {
        productRepository.findByIdAndActiveTrueAndCategory_ActiveTrue(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product '%s' was not found".formatted(productId)
                ));

        return variantRepository
                .findAllByProduct_IdAndActiveTrueAndProduct_ActiveTrueAndProduct_Category_ActiveTrueOrderByPriceAscSkuAsc(
                        productId
                )
                .stream()
                .map(mapper::toVariantResponse)
                .toList();
    }

    public BatchVariantResponse findPublicVariantsBySku(BatchVariantRequest request) {
        if (request == null || request.skus() == null) {
            throw new InvalidRequestException("skus must not be null");
        }
        if (request.skus().isEmpty() || request.skus().size() > 100) {
            throw new InvalidRequestException("skus must contain between 1 and 100 entries");
        }

        LinkedHashSet<String> normalizedSkus = request.skus()
                .stream()
                .map(CatalogValidation::normalizeSku)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

        Map<String, ProductVariant> variantsBySku = new LinkedHashMap<>();
        variantRepository
                .findAllBySkuInAndActiveTrueAndProduct_ActiveTrueAndProduct_Category_ActiveTrue(
                        normalizedSkus
                )
                .forEach(variant -> variantsBySku.put(variant.getSku(), variant));

        List<ProductVariantResponse> variants = normalizedSkus.stream()
                .filter(variantsBySku::containsKey)
                .map(variantsBySku::get)
                .map(mapper::toVariantResponse)
                .toList();
        List<String> missingSkus = normalizedSkus.stream()
                .filter(sku -> !variantsBySku.containsKey(sku))
                .toList();
        return new BatchVariantResponse(variants, missingSkus);
    }

    @Transactional
    public ProductVariantResponse create(UUID productId, ProductVariantRequest request) {
        Product product = requireProduct(productId);
        ValidatedVariantInput input = validate(request);
        ensureSkuAvailable(input.sku(), null);

        ProductVariant variant = variantRepository.saveAndFlush(new ProductVariant(
                product,
                input.sku(),
                input.name(),
                input.price(),
                input.currency(),
                input.imageUrl()
        ));
        return mapper.toVariantResponse(variant);
    }

    @Transactional
    public ProductVariantResponse update(UUID variantId, ProductVariantRequest request) {
        ProductVariant variant = requireVariant(variantId);
        ValidatedVariantInput input = validate(request);
        ensureSkuAvailable(input.sku(), variantId);

        variant.changeSku(input.sku());
        variant.updateDetails(
                input.name(),
                input.price(),
                input.currency(),
                input.imageUrl()
        );
        variantRepository.flush();
        return mapper.toVariantResponse(variant);
    }

    @Transactional
    public ProductVariantResponse changeStatus(UUID variantId, boolean active) {
        ProductVariant variant = requireVariant(variantId);
        variant.changeActiveStatus(active);
        variantRepository.flush();
        return mapper.toVariantResponse(variant);
    }

    private Product requireProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product '%s' was not found".formatted(productId)
                ));
    }

    private ProductVariant requireVariant(UUID variantId) {
        return variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product variant '%s' was not found".formatted(variantId)
                ));
    }

    private void ensureSkuAvailable(String sku, UUID currentId) {
        boolean exists = currentId == null
                ? variantRepository.existsBySku(sku)
                : variantRepository.existsBySkuAndIdNot(sku, currentId);
        if (exists) {
            throw new ResourceConflictException(
                    "Product variant SKU '%s' already exists".formatted(sku)
            );
        }
    }

    private static ValidatedVariantInput validate(ProductVariantRequest request) {
        return new ValidatedVariantInput(
                CatalogValidation.normalizeSku(request.sku()),
                request.name(),
                CatalogValidation.validatePrice(request.price(), "price"),
                CatalogValidation.normalizeCurrency(request.currency()),
                CatalogValidation.normalizeImageUrl(request.imageUrl())
        );
    }

    private record ValidatedVariantInput(
            String sku,
            String name,
            BigDecimal price,
            String currency,
            String imageUrl
    ) {
    }
}
