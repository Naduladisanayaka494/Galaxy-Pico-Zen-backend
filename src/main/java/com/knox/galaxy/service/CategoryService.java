package com.knox.galaxy.service;

import com.knox.galaxy.dto.CategoryRequest;
import com.knox.galaxy.dto.CategoryResponse;
import com.knox.galaxy.model.Category;
import com.knox.galaxy.repository.CategoryRepository;
import com.knox.galaxy.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/** Product categories. Names are unique per tenant (DB constraint). */
@Service
public class CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categoryRepository.findAllByOrderByNameAsc()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public CategoryResponse create(CategoryRequest req) {
        requireNameAvailable(req.getName(), null);
        Category category = new Category();
        category.setName(req.getName().trim());
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest req) {
        Category category = findOrThrow(id);
        requireNameAvailable(req.getName(), id);
        category.setName(req.getName().trim());
        return toResponse(categoryRepository.save(category));
    }

    /**
     * Products reference categories with {@code ON DELETE SET NULL}, so deleting
     * a category in use silently uncategorises those products. We block it
     * instead — losing the categorisation of a live catalogue is not something
     * the user can undo from the UI.
     */
    @Transactional
    public void delete(Long id) {
        Category category = findOrThrow(id);
        long inUse = productRepository.countByCategoryId(id);
        if (inUse > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "'" + category.getName() + "' is used by " + inUse
                            + " product(s). Reassign them before deleting it.");
        }
        categoryRepository.delete(category);
    }

    private void requireNameAvailable(String name, Long selfId) {
        categoryRepository.findByNameIgnoreCase(name.trim()).ifPresent(existing -> {
            if (!existing.getId().equals(selfId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A category named '" + name.trim() + "' already exists");
            }
        });
    }

    private Category findOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Category " + id + " not found"));
    }

    private CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getName());
    }
}
