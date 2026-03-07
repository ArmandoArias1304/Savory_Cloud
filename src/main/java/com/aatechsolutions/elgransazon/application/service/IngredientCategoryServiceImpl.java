package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.IngredientCategory;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import com.aatechsolutions.elgransazon.domain.repository.IngredientCategoryRepository;
import com.aatechsolutions.elgransazon.domain.repository.IngredientRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service implementation for IngredientCategory management
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngredientCategoryServiceImpl implements IngredientCategoryService {

    private final IngredientCategoryRepository categoryRepository;
    private final EmployeeRepository employeeRepository;
    private final IngredientRepository ingredientRepository;

    @Override
    @Transactional(readOnly = true)
    public List<IngredientCategory> findAll() {
        log.info("Finding all ingredient categories");
        Company company = CompanyContext.requireCurrentCompany();
        return categoryRepository.findByCompanyOrderByNameAsc(company);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IngredientCategory> findAllActive() {
        log.info("Finding all active ingredient categories");
        Company company = CompanyContext.requireCurrentCompany();
        return categoryRepository.findAllActiveByCompany(company);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IngredientCategory> findById(Long id) {
        log.info("Finding ingredient category by id: {}", id);
        Company company = CompanyContext.requireCurrentCompany();
        return categoryRepository.findByIdCategoryAndCompany(id, company);
    }

    @Override
    @Transactional
    public IngredientCategory create(IngredientCategory category) {
        log.info("Creating new ingredient category: {}", category.getName());

        // Set company from context
        Company company = CompanyContext.requireCurrentCompany();
        category.setCompany(company);

        // Validate unique name within company
        if (categoryRepository.existsByNameAndCompany(category.getName(), company)) {
            log.error("Category with name {} already exists for company {}", category.getName(), company.getIdCompany());
            throw new IllegalArgumentException("Ya existe una categoría con el nombre: " + category.getName());
        }

        // Set created by (authenticated user)
        setCreatedBy(category);

        IngredientCategory savedCategory = categoryRepository.save(category);
        log.info("Ingredient category created successfully with id: {}", savedCategory.getIdCategory());
        return savedCategory;
    }

    @Override
    @Transactional
    public IngredientCategory update(Long id, IngredientCategory categoryDetails) {
        log.info("Updating ingredient category with id: {}", id);

        // Get company from context
        Company company = CompanyContext.requireCurrentCompany();

        IngredientCategory category = categoryRepository.findByIdCategoryAndCompany(id, company)
                .orElseThrow(() -> {
                    log.error("Category not found with id: {} for company: {}", id, company.getIdCompany());
                    return new IllegalArgumentException("Categoría no encontrada con id: " + id);
                });

        // Validate unique name if changed (within company)
        if (!category.getName().equals(categoryDetails.getName()) &&
            categoryRepository.existsByNameAndCompany(categoryDetails.getName(), company)) {
            log.error("Category with name {} already exists for company {}", categoryDetails.getName(), company.getIdCompany());
            throw new IllegalArgumentException("Ya existe una categoría con el nombre: " + categoryDetails.getName());
        }

        // Update fields
        category.setName(categoryDetails.getName());
        category.setDescription(categoryDetails.getDescription());
        category.setIcon(categoryDetails.getIcon());
        category.setColor(categoryDetails.getColor());
        category.setActive(categoryDetails.getActive());

        // Update suppliers (ManyToMany relationship)
        category.getSuppliers().clear();
        if (categoryDetails.getSuppliers() != null) {
            category.getSuppliers().addAll(categoryDetails.getSuppliers());
        }

        IngredientCategory updatedCategory = categoryRepository.save(category);
        log.info("Ingredient category updated successfully: {}", id);
        return updatedCategory;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        log.info("Deactivating ingredient category with id: {}", id);

        // Get company from context
        Company company = CompanyContext.requireCurrentCompany();

        IngredientCategory category = categoryRepository.findByIdCategoryAndCompany(id, company)
                .orElseThrow(() -> {
                    log.error("Category not found with id: {} for company: {}", id, company.getIdCompany());
                    return new IllegalArgumentException("Categoría no encontrada con id: " + id);
                });

        category.setActive(false);
        categoryRepository.save(category);
        log.info("Ingredient category deactivated successfully: {}", id);
    }

    @Override
    @Transactional
    public void activate(Long id) {
        log.info("Activating ingredient category with id: {}", id);

        // Get company from context
        Company company = CompanyContext.requireCurrentCompany();

        IngredientCategory category = categoryRepository.findByIdCategoryAndCompany(id, company)
                .orElseThrow(() -> {
                    log.error("Category not found with id: {} for company: {}", id, company.getIdCompany());
                    return new IllegalArgumentException("Categoría no encontrada con id: " + id);
                });

        category.setActive(true);
        categoryRepository.save(category);
        log.info("Ingredient category activated successfully: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IngredientCategory> searchWithFilters(String search, Boolean active) {
        log.info("Searching ingredient categories with filters - search: {}, active: {}", search, active);

        // Normalize search string
        String normalizedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        List<IngredientCategory> categories = categoryRepository.searchWithFilters(normalizedSearch, active);
        
        // Filter by company - require company context
        Company company = CompanyContext.requireCurrentCompany();
        categories = categories.stream()
                .filter(c -> c.getCompany() != null && c.getCompany().getIdCompany().equals(company.getIdCompany()))
                .collect(Collectors.toList());
        
        log.info("Found {} categories with filters", categories.size());
        return categories;
    }

    @Override
    @Transactional(readOnly = true)
    public long getActiveCount() {
        Company company = CompanyContext.requireCurrentCompany();
        return categoryRepository.findAllActiveByCompany(company).size();
    }

    @Override
    @Transactional(readOnly = true)
    public long getInactiveCount() {
        Company company = CompanyContext.requireCurrentCompany();
        return categoryRepository.findByCompanyOrderByNameAsc(company).stream()
                .filter(c -> !c.getActive()).count();
    }

    @Override
    @Transactional
    public void hardDelete(Long id) {
        log.warn("Hard deleting ingredient category with id: {}", id);

        // Get company from context
        Company company = CompanyContext.requireCurrentCompany();

        IngredientCategory category = categoryRepository.findByIdCategoryAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada con id: " + id));

        List<Ingredient> ingredients = ingredientRepository.findByCategoryIdCategoryOrderByNameAsc(id);
        if (!ingredients.isEmpty()) {
            throw new IllegalStateException(
                    "No se puede eliminar la categoría '" + category.getName() +
                    "' porque tiene " + ingredients.size() + " ingrediente(s) asignados.");
        }

        // Clear supplier associations
        category.getSuppliers().clear();
        categoryRepository.save(category);

        categoryRepository.delete(category);
        log.info("Ingredient category '{}' permanently deleted", category.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> checkHardDeleteDependencies(Long id) {
        Company company = CompanyContext.requireCurrentCompany();
        IngredientCategory category = categoryRepository.findByIdCategoryAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada con id: " + id));

        List<Ingredient> ingredients = ingredientRepository.findByCategoryIdCategoryOrderByNameAsc(id);
        List<String> ingredientNames = ingredients.stream().map(Ingredient::getName).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("categoryName", category.getName());
        result.put("ingredientCount", ingredients.size());
        result.put("ingredientNames", ingredientNames);
        result.put("canDelete", ingredients.isEmpty());
        return result;
    }

    /**
     * Set the created by field with the authenticated user
     */
    private void setCreatedBy(IngredientCategory category) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() &&
                !"anonymousUser".equals(authentication.getPrincipal())) {

                String username = authentication.getName();
                // MULTI-TENANT: Find employee by username+company
                Company company = CompanyContext.requireCurrentCompany();
                Optional<Employee> employee = employeeRepository.findByUsernameAndCompany(username, company);

                employee.ifPresent(category::setCreatedBy);
            }
        } catch (Exception e) {
            log.warn("Could not set created by: {}", e.getMessage());
        }
    }
}
