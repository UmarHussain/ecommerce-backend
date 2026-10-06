package com.umar.ecommerce.catalog.service;

import com.umar.ecommerce.catalog.dto.request.ProductRequest;
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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
            int page,
            int size,
            String sort
    ) {
        validatePage(page, size);
        BigDecimal normalizedMinPrice = validateOptionalPrice(minPrice, "minPrice");
        BigDecimal normalizedMaxPrice = validateOptionalPrice(maxPrice, "maxPrice");
        if (normalizedMinPrice != null
                && normalizedMaxPrice != null
                && normalizedMaxPrice.compareTo(normalizedMinPrice) < 0) {
            throw new InvalidRequestException("maxPrice must be greater than or equal to minPrice");
        }

        SortSelection sortSelection = parseSort(sort);
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(sortSelection.direction(), sortSelection.field())
        );
        Page<Product> products = productRepository.findAll(
                ProductSpecifications.publicCatalog(
                        CatalogValidation.normalizeOptionalSearch(search),
                        CatalogValidation.normalizeOptionalCategorySlug(categorySlug),
                        normalizedMinPrice,
                        normalizedMaxPrice
                ),
                pageRequest
        );

        List<UUID> productIds = products.stream().map(Product::getId).toList();
        Map<UUID, List<ProductVariant>> variantsByProduct = productIds.isEmpty()
                ? Map.of()
                : variantRepository
                        .findAllByProduct_IdInAndActiveTrueAndProduct_ActiveTrueAndProduct_Category_ActiveTrueOrderByProduct_IdAscPriceAscSkuAsc(
                                productIds
                        )
                        .stream()
                        .collect(Collectors.groupingBy(
                                variant -> variant.getProduct().getId(),
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

        Page<ProductSummaryResponse> responsePage = products.map(product ->
                mapper.toProductSummaryResponse(
                        product,
                        variantsByProduct.getOrDefault(product.getId(), List.of())
                )
        );
        return PageResponse.from(responsePage, sortSelection.contract());
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
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = requireProduct(id);
        String slug = CatalogValidation.normalizeSlug(request.slug(), 160);
        ensureSlugAvailable(slug, id);
        Category category = requireCategory(request.categoryId());

        product.updateDetails(request.name(), slug, request.description());
        product.changeCategory(category);
        productRepository.flush();
        return adminProductResponse(product);
    }

    @Transactional
    public ProductResponse changeStatus(UUID id, boolean active) {
        Product product = requireProduct(id);
        product.changeActiveStatus(active);
        productRepository.flush();
        return adminProductResponse(product);
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

    private static void validatePage(int page, int size) {
        if (page < 0) {
            throw new InvalidRequestException("page must be zero or greater");
        }
        if (size < 1 || size > 100) {
            throw new InvalidRequestException("size must be between 1 and 100");
        }
    }

    private static BigDecimal validateOptionalPrice(BigDecimal price, String fieldName) {
        return price == null ? null : CatalogValidation.validatePrice(price, fieldName);
    }

    private static SortSelection parseSort(String sort) {
        String value = sort == null || sort.isBlank() ? "name,asc" : sort.trim();
        String[] parts = value.split(",", -1);
        if (parts.length != 2) {
            throw new InvalidRequestException(
                    "sort must use the format field,direction, for example name,asc"
            );
        }

        String field = parts[0].trim();
        String directionValue = parts[1].trim().toLowerCase(Locale.ROOT);
        if (!SORT_FIELDS.contains(field)) {
            throw new InvalidRequestException(
                    "sort field must be one of name, slug, createdAt, updatedAt"
            );
        }
        if (!directionValue.equals("asc") && !directionValue.equals("desc")) {
            throw new InvalidRequestException("sort direction must be asc or desc");
        }

        Sort.Direction direction = directionValue.equals("asc")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return new SortSelection(field, direction, field + "," + directionValue);
    }

    private static ResourceNotFoundException productNotFound(String identifier) {
        return new ResourceNotFoundException(
                "Product '%s' was not found".formatted(identifier)
        );
    }

    private record SortSelection(
            String field,
            Sort.Direction direction,
            String contract
    ) {
    }
}
