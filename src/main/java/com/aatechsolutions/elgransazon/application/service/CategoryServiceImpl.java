package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Category;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.repository.CategoryRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuRepository;
import com.aatechsolutions.elgransazon.domain.repository.OrderDetailRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of CategoryService
 * Handles business logic for category operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ItemMenuRepository itemMenuRepository;
    private final OrderDetailRepository orderDetailRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Category> getAllCategories() {
        log.debug("Fetching all categories");
        Company company = CompanyContext.requireCurrentCompany();
        return categoryRepository.findAllByCompanyOrderedByName(company);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> getAllActiveCategories() {
        log.debug("Fetching all active categories");
        Company company = CompanyContext.requireCurrentCompany();
        return categoryRepository.findAllActiveByCompanyOrderedByName(company);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> getCategoryById(Long id) {
        log.debug("Fetching category with id: {}", id);
        Company company = CompanyContext.requireCurrentCompany();
        return categoryRepository.findByIdCategoryAndCompany(id, company);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> getCategoryByName(String name) {
        log.debug("Fetching category with name: {}", name);
        Company company = CompanyContext.requireCurrentCompany();
        return categoryRepository.findByNameAndCompany(name, company);
    }

    @Override
    public Category createCategory(Category category) {
        log.info("Creating new category: {}", category.getName());

        // Set company from context
        Company company = CompanyContext.requireCurrentCompany();
        category.setCompany(company);

        // Validate that category name doesn't already exist for this company
        if (categoryRepository.existsByNameIgnoreCaseAndCompany(category.getName(), company)) {
            log.error("Category name already exists: {}", category.getName());
            throw new IllegalArgumentException("Category with name '" + category.getName() + "' already exists");
        }

        // Set default values if not provided
        if (category.getActive() == null) {
            category.setActive(true);
        }

        Category savedCategory = categoryRepository.save(category);
        log.info("Category created successfully with id: {}", savedCategory.getIdCategory());
        return savedCategory;
    }

    @Override
    public Category updateCategory(Long id, Category category) {
        log.info("Updating category with id: {}", id);

        Company company = CompanyContext.requireCurrentCompany();
        
        Category existingCategory = categoryRepository.findByIdCategoryAndCompany(id, company)
                .orElseThrow(() -> {
                    log.error("Category not found with id: {}", id);
                    return new IllegalArgumentException("Category not found with id: " + id);
                });

        // Check if name is being changed and if the new name already exists for this company
        if (!existingCategory.getName().equalsIgnoreCase(category.getName())) {
            if (categoryRepository.existsByNameIgnoreCaseAndCompany(category.getName(), company)) {
                log.error("Category name already exists: {}", category.getName());
                throw new IllegalArgumentException("Category with name '" + category.getName() + "' already exists");
            }
        }

        // Update fields
        existingCategory.setName(category.getName());
        existingCategory.setDescription(category.getDescription());
        existingCategory.setActive(category.getActive());
        existingCategory.setIcon(category.getIcon());

        Category updatedCategory = categoryRepository.save(existingCategory);
        log.info("Category updated successfully: {}", updatedCategory.getIdCategory());
        return updatedCategory;
    }

    @Override
    public void deleteCategory(Long id) {
        log.info("Soft deleting category with id: {}", id);

        Company company = CompanyContext.requireCurrentCompany();
        
        Category category = categoryRepository.findByIdCategoryAndCompany(id, company)
                .orElseThrow(() -> {
                    log.error("Category not found with id: {}", id);
                    return new IllegalArgumentException("Category not found with id: " + id);
                });

        category.setActive(false);
        categoryRepository.save(category);
        log.info("Category soft deleted successfully: {}", id);
    }

    @Override
    public void permanentlyDeleteCategory(Long id) {
        log.warn("Permanently deleting category with id: {}", id);

        Company company = CompanyContext.requireCurrentCompany();
        
        Category category = categoryRepository.findByIdCategoryAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + id));

        // Check if category has items with order history
        List<ItemMenu> items = itemMenuRepository.findByCategoryId(id);
        if (!items.isEmpty()) {
            boolean hasOrders = items.stream()
                    .anyMatch(item -> orderDetailRepository.existsByItemMenuIdItemMenu(item.getIdItemMenu()));
            if (hasOrders) {
                throw new IllegalStateException(
                        "No se puede eliminar la categoría '" + category.getName() +
                        "' porque contiene items con historial de ventas.");
            }
        }

        categoryRepository.deleteById(id);
        log.info("Category permanently deleted: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> checkHardDeleteDependencies(Long id) {
        Company company = CompanyContext.requireCurrentCompany();
        
        Category category = categoryRepository.findByIdCategoryAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + id));

        List<ItemMenu> items = itemMenuRepository.findByCategoryId(id);
        List<String> itemNames = items.stream().map(ItemMenu::getName).collect(Collectors.toList());
        boolean hasOrders = items.stream()
                .anyMatch(item -> orderDetailRepository.existsByItemMenuIdItemMenu(item.getIdItemMenu()));

        Map<String, Object> result = new HashMap<>();
        result.put("categoryName", category.getName());
        result.put("itemCount", items.size());
        result.put("itemNames", itemNames);
        result.put("hasOrders", hasOrders);
        result.put("canDelete", !hasOrders && items.isEmpty());
        return result;
    }

    @Override
    public void activateCategory(Long id) {
        log.info("Activating category with id: {}", id);

        Company company = CompanyContext.requireCurrentCompany();
        
        Category category = categoryRepository.findByIdCategoryAndCompany(id, company)
                .orElseThrow(() -> {
                    log.error("Category not found with id: {}", id);
                    return new IllegalArgumentException("Category not found with id: " + id);
                });

        category.setActive(true);
        categoryRepository.save(category);
        log.info("Category activated successfully: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean categoryNameExists(String name) {
        log.debug("Checking if category name exists: {}", name);
        // MULTI-TENANT: Require company context - no fallback to global data
        Company company = CompanyContext.requireCurrentCompany();
        return categoryRepository.existsByNameIgnoreCaseAndCompany(name, company);
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveCategories() {
        log.debug("Counting active categories");
        // MULTI-TENANT: Require company context - no fallback to global data
        Company company = CompanyContext.requireCurrentCompany();
        return categoryRepository.countByActiveTrueAndCompany(company);
    }
}
