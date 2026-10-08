package com.umar.ecommerce.catalog.service;

import com.umar.ecommerce.catalog.dto.request.ProductRequest;
import com.umar.ecommerce.catalog.dto.request.ProductUpdateRequest;
import com.umar.ecommerce.catalog.dto.response.PageResponse;
import com.umar.ecommerce.catalog.dto.response.ProductResponse;
import com.umar.ecommerce.catalog.dto.response.ProductSummaryResponse;
import com.umar.ecommerce.catalog.entity.Category;
import com.umar.ecommerce.catalog.entity.Product;
import com.umar.ecommerce.catalog.entity.ProductVariant;
import com.umar.ecommerce.catalog.exception.InvalidRequestException;
import com.umar.ecommerce.catalog.exception.ResourceConflictException;
import com.umar.ecommerce.catalog.exception.ResourceNotFoundException;
import com.umar.ecommerce.catalog.repository.CategoryRepository;
import com.umar.ecommerce.catalog.repository.ProductRepository;
import com.umar.ecommerce.catalog.repository.ProductSpecifications;
import com.umar.ecommerce.catalog.repository.ProductVariantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private static final Set<String> SORT_FIELDS =
            Set.of("name", "slug", "createdAt", "updatedAt");

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CategoryRepository categoryRepository;
    private final CatalogMapper mapper;

    public ProductService(
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            CategoryRepository categoryRepository,
            CatalogMapper mapper
    ) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.categoryRepository = categoryRepository;
        this.mapper = mapper;
    }

    public PageResponse<ProductSummaryResponse> searchPublicProducts(
            String search,
            String categorySlug,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String currency,
            int page,
            int size,
            String sort
    ) {
        PriceFilter prices = priceFilter(minPrice, maxPrice, currency);
        return search(
                ProductSpecifications.publicCatalog(
                        CatalogValidation.normalizeOptionalSearch(search),
                        CatalogValidation.normalizeOptionalCategorySlug(categorySlug),
                        prices.min(),
                        prices.max(),
                        prices.currency()
                ),
                true,
                page,
                size,
                sort
        );
    }

    public PageResponse<ProductSummaryResponse> searchAdminProducts(
            String search,
            UUID categoryId,
            Boolean active,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String currency,
            int page,
            int size,
            String sort
    ) {
        PriceFilter prices = priceFilter(minPrice, maxPrice, currency);
        return search(
                ProductSpecifications.adminCatalog(
                        CatalogValidation.normalizeOptionalSearch(search),
                        categoryId,
                        active,
                        prices.min(),
                        prices.max(),
                        prices.currency()
                ),
                false,
                page,
                size,
                sort
        );
    }

    public ProductResponse getPublicProduct(UUID id) {
        Product product = productRepository.findByIdAndActiveTrueAndCategory_ActiveTrue(id)
                .orElseThrow(() -> productNotFound(id.toString()));
        return publicProductResponse(product);
    }

    public ProductResponse getPublicProductBySlug(String slug) {
        String normalizedSlug = CatalogValidation.normalizeSlug(slug, 160);
        Product product = productRepository
                .findBySlugAndActiveTrueAndCategory_ActiveTrue(normalizedSlug)
                .orElseThrow(() -> productNotFound(normalizedSlug));
        return publicProductResponse(product);
    }

    public ProductResponse getAdminProduct(UUID id) {
        return adminProductResponse(requireProduct(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        String slug = CatalogValidation.normalizeSlug(request.slug(), 160);
        ensureSlugAvailable(slug, null);
        Category category = requireCategory(request.categoryId());

        Product product = productRepository.saveAndFlush(new Product(
                request.name(),
                slug,
                request.description(),
                category
        ));
        return mapper.toProductResponse(product, List.of());
    }

    @Transactional
    public ProductResponse update(UUID id, ProductUpdateRequest request) {
        Product product = requireProduct(id);
        VersionGuard.requireCurrent(product.getVersion(), request.expectedVersion());
        String slug = CatalogValidation.normalizeSlug(request.slug(), 160);
        ensureSlugAvailable(slug, id);
        Category category = requireCategory(request.categoryId());

        product.updateDetails(request.name(), slug, request.description());
        product.changeCategory(category);
        productRepository.flush();
        return adminProductResponse(product);
    }

    @Transactional
    public ProductResponse changeStatus(UUID id, boolean active, long expectedVersion) {
        Product product = requireProduct(id);
        VersionGuard.requireCurrent(product.getVersion(), expectedVersion);
        product.changeActiveStatus(active);
        productRepository.flush();
        return adminProductResponse(product);
    }

    private PageResponse<ProductSummaryResponse> search(
            org.springframework.data.jpa.domain.Specification<Product> specification,
            boolean activeVariantsOnly,
            int page,
            int size,
            String sort
    ) {
        CatalogPaging.validatePage(page, size);
        CatalogPaging.SortSelection sortSelection = CatalogPaging.parseSort(sort, SORT_FIELDS);
        Page<Product> products = productRepository.findAll(
                specification,
                PageRequest.of(page, size, CatalogPaging.toSort(sortSelection))
        );

        List<UUID> productIds = products.stream().map(Product::getId).toList();
        Map<UUID, List<ProductVariant>> variantsByProduct = variantsFor(productIds, activeVariantsOnly);
        Page<ProductSummaryResponse> responsePage = products.map(product ->
                mapper.toProductSummaryResponse(
                        product,
                        variantsByProduct.getOrDefault(product.getId(), List.of())
                )
        );
        return PageResponse.from(responsePage, sortSelection.contract());
    }

    private Map<UUID, List<ProductVariant>> variantsFor(List<UUID> productIds, boolean activeOnly) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        List<ProductVariant> variants = activeOnly
                ? variantRepository
                .findAllByProduct_IdInAndActiveTrueAndProduct_ActiveTrueAndProduct_Category_ActiveTrueOrderByProduct_IdAscPriceAscSkuAsc(
                        productIds
                )
                : variantRepository.findAllByProduct_IdInOrderByProduct_IdAscPriceAscSkuAsc(productIds);
        return variants.stream().collect(Collectors.groupingBy(
                variant -> variant.getProduct().getId(),
                LinkedHashMap::new,
                Collectors.toList()
        ));
    }

    private ProductResponse publicProductResponse(Product product) {
        List<ProductVariant> variants = variantRepository
                .findAllByProduct_IdAndActiveTrueAndProduct_ActiveTrueAndProduct_Category_ActiveTrueOrderByPriceAscSkuAsc(
                        product.getId()
                );
        return mapper.toProductResponse(product, variants);
    }

    private ProductResponse adminProductResponse(Product product) {
        List<ProductVariant> variants =
                variantRepository.findAllByProduct_IdOrderByPriceAscSkuAsc(product.getId());
        return mapper.toProductResponse(product, variants);
    }

    private Product requireProduct(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> productNotFound(id.toString()));
    }

    private Category requireCategory(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category '%s' was not found".formatted(id)
                ));
    }

    private void ensureSlugAvailable(String slug, UUID currentId) {
        boolean exists = currentId == null
                ? productRepository.existsBySlug(slug)
                : productRepository.existsBySlugAndIdNot(slug, currentId);
        if (exists) {
            throw new ResourceConflictException(
                    "Product slug '%s' already exists".formatted(slug)
            );
        }
    }

    /**
     * Price bounds compare the stored numeric amount of variants in one currency.
     * Amounts are never converted, so a bound without a currency is rejected.
     */
    private static PriceFilter priceFilter(BigDecimal minPrice, BigDecimal maxPrice, String currency) {
        BigDecimal normalizedMinPrice = minPrice == null ? null : CatalogValidation.validatePrice(minPrice, "minPrice");
        BigDecimal normalizedMaxPrice = maxPrice == null ? null : CatalogValidation.validatePrice(maxPrice, "maxPrice");
        boolean bounded = normalizedMinPrice != null || normalizedMaxPrice != null;
        String normalizedCurrency = currency == null || currency.isBlank()
                ? null
                : CatalogValidation.normalizeCurrency(currency);
        if (bounded && normalizedCurrency == null) {
            throw new InvalidRequestException(
                    "currency is required when minPrice or maxPrice is set; "
                            + "price filters compare the stored amount in that currency and do not convert"
            );
        }
        if (normalizedMinPrice != null
                && normalizedMaxPrice != null
                && normalizedMaxPrice.compareTo(normalizedMinPrice) < 0) {
            throw new InvalidRequestException("maxPrice must be greater than or equal to minPrice");
        }
        return new PriceFilter(normalizedMinPrice, normalizedMaxPrice, bounded ? normalizedCurrency : null);
    }

    private static ResourceNotFoundException productNotFound(String identifier) {
        return new ResourceNotFoundException(
                "Product '%s' was not found".formatted(identifier)
        );
    }

    private record PriceFilter(BigDecimal min, BigDecimal max, String currency) {
    }
}
