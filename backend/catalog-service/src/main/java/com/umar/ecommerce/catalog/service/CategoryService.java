package com.umar.ecommerce.catalog.service;

import com.umar.ecommerce.catalog.cache.PublicCatalogChanged;
import com.umar.ecommerce.catalog.dto.request.CategoryRequest;
import com.umar.ecommerce.catalog.dto.request.CategoryUpdateRequest;
import com.umar.ecommerce.catalog.dto.response.CategoryResponse;
import com.umar.ecommerce.catalog.dto.response.PageResponse;
import com.umar.ecommerce.catalog.entity.Category;
import com.umar.ecommerce.catalog.exception.ResourceConflictException;
import com.umar.ecommerce.catalog.exception.ResourceNotFoundException;
import com.umar.ecommerce.catalog.repository.CategoryRepository;
import com.umar.ecommerce.catalog.repository.CategorySpecifications;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private static final Set<String> SORT_FIELDS = Set.of("name", "slug", "createdAt", "updatedAt");

    private final CategoryRepository categoryRepository;
    private final CatalogMapper mapper;
    private final ApplicationEventPublisher events;

    public CategoryService(
            CategoryRepository categoryRepository,
            CatalogMapper mapper,
            ApplicationEventPublisher events
    ) {
        this.categoryRepository = categoryRepository;
        this.mapper = mapper;
        this.events = events;
    }

    public List<CategoryResponse> listActiveCategories() {
        return categoryRepository.findAllByActiveTrueOrderByNameAsc()
                .stream()
                .map(mapper::toCategoryResponse)
                .toList();
    }

    public PageResponse<CategoryResponse> searchAdmin(
            String search,
            Boolean active,
            int page,
            int size,
            String sort
    ) {
        CatalogPaging.validatePage(page, size);
        CatalogPaging.SortSelection sortSelection = CatalogPaging.parseSort(sort, SORT_FIELDS);
        Page<Category> categories = categoryRepository.findAll(
                CategorySpecifications.adminSearch(
                        CatalogValidation.normalizeOptionalSearch(search),
                        active
                ),
                PageRequest.of(page, size, CatalogPaging.toSort(sortSelection))
        );
        return PageResponse.from(categories.map(mapper::toCategoryResponse), sortSelection.contract());
    }

    public CategoryResponse getAdmin(UUID id) {
        return mapper.toCategoryResponse(requireCategory(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String slug = CatalogValidation.normalizeSlug(request.slug(), 120);
        ensureSlugAvailable(slug, null);

        Category category = categoryRepository.saveAndFlush(new Category(request.name(), slug));
        events.publishEvent(new PublicCatalogChanged("category-create"));
        return mapper.toCategoryResponse(category);
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryUpdateRequest request) {
        Category category = requireCategory(id);
        VersionGuard.requireCurrent(category.getVersion(), request.expectedVersion());
        String slug = CatalogValidation.normalizeSlug(request.slug(), 120);
        ensureSlugAvailable(slug, id);

        category.updateDetails(request.name(), slug);
        categoryRepository.flush();
        events.publishEvent(new PublicCatalogChanged("category-update"));
        return mapper.toCategoryResponse(category);
    }

    @Transactional
    public CategoryResponse changeStatus(UUID id, boolean active, long expectedVersion) {
        Category category = requireCategory(id);
        VersionGuard.requireCurrent(category.getVersion(), expectedVersion);
        category.changeActiveStatus(active);
        categoryRepository.flush();
        events.publishEvent(new PublicCatalogChanged("category-status"));
        return mapper.toCategoryResponse(category);
    }

    private Category requireCategory(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category '%s' was not found".formatted(id)
                ));
    }

    private void ensureSlugAvailable(String slug, UUID currentId) {
        boolean exists = currentId == null
                ? categoryRepository.existsBySlug(slug)
                : categoryRepository.existsBySlugAndIdNot(slug, currentId);
        if (exists) {
            throw new ResourceConflictException(
                    "Category slug '%s' already exists".formatted(slug)
            );
        }
    }
}
