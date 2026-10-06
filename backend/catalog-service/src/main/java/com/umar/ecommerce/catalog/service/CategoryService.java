package com.umar.ecommerce.catalog.service;

import com.umar.ecommerce.catalog.dto.request.CategoryRequest;
import com.umar.ecommerce.catalog.dto.response.CategoryResponse;
import com.umar.ecommerce.catalog.entity.Category;
import com.umar.ecommerce.catalog.exception.ResourceConflictException;
import com.umar.ecommerce.catalog.exception.ResourceNotFoundException;
import com.umar.ecommerce.catalog.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CatalogMapper mapper;

    public CategoryService(CategoryRepository categoryRepository, CatalogMapper mapper) {
        this.categoryRepository = categoryRepository;
        this.mapper = mapper;
    }

    public List<CategoryResponse> listActiveCategories() {
        return categoryRepository.findAllByActiveTrueOrderByNameAsc()
                .stream()
                .map(mapper::toCategoryResponse)
                .toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String slug = CatalogValidation.normalizeSlug(request.slug(), 120);
        ensureSlugAvailable(slug, null);

        Category category = categoryRepository.saveAndFlush(new Category(request.name(), slug));
        return mapper.toCategoryResponse(category);
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = requireCategory(id);
        String slug = CatalogValidation.normalizeSlug(request.slug(), 120);
        ensureSlugAvailable(slug, id);

        category.updateDetails(request.name(), slug);
        categoryRepository.flush();
        return mapper.toCategoryResponse(category);
    }

    @Transactional
    public CategoryResponse changeStatus(UUID id, boolean active) {
        Category category = requireCategory(id);
        category.changeActiveStatus(active);
        categoryRepository.flush();
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
