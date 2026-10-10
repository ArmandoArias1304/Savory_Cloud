package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.InventoryObject;
import com.aatechsolutions.elgransazon.domain.entity.InventoryObjectCategory;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import com.aatechsolutions.elgransazon.domain.repository.InventoryObjectCategoryRepository;
import com.aatechsolutions.elgransazon.domain.repository.InventoryObjectRepository;
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
 * Service implementation for InventoryObjectCategory management.
 * Every operation is scoped to the company of the current request (multi-tenant).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryObjectCategoryServiceImpl implements InventoryObjectCategoryService {

    private final InventoryObjectCategoryRepository categoryRepository;
    private final InventoryObjectRepository objectRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    @Transactional(readOnly = true)
    public List<InventoryObjectCategory> findAll() {
        Company company = CompanyContext.requireCurrentCompany();
        return categoryRepository.findByCompanyOrderByNameAsc(company);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryObjectCategory> findAllActive() {
        Company company = CompanyContext.requireCurrentCompany();
        return categoryRepository.findAllActiveByCompany(company);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryObjectCategory> findById(Long id) {
        Company company = CompanyContext.requireCurrentCompany();
        return categoryRepository.findByIdCategoryAndCompany(id, company);
    }

    @Override
    @Transactional
    public InventoryObjectCategory create(InventoryObjectCategory category) {
        Company company = CompanyContext.requireCurrentCompany();
        category.setCompany(company);

        if (categoryRepository.existsByNameAndCompany(category.getName(), company)) {
            log.error("Object category with name {} already exists for company {}", category.getName(), company.getIdCompany());
            throw new IllegalArgumentException("Ya existe una categoría de objetos con el nombre: " + category.getName());
        }

        setCreatedBy(category);

        InventoryObjectCategory saved = categoryRepository.save(category);
        log.info("Object category created successfully with id: {}", saved.getIdCategory());
        return saved;
    }

    @Override
    @Transactional
    public InventoryObjectCategory update(Long id, InventoryObjectCategory categoryDetails) {
        Company company = CompanyContext.requireCurrentCompany();

        InventoryObjectCategory category = categoryRepository.findByIdCategoryAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Categoría de objetos no encontrada con id: " + id));

        if (!category.getName().equals(categoryDetails.getName()) &&
            categoryRepository.existsByNameAndCompany(categoryDetails.getName(), company)) {
            log.error("Object category with name {} already exists for company {}", categoryDetails.getName(), company.getIdCompany());
            throw new IllegalArgumentException("Ya existe una categoría de objetos con el nombre: " + categoryDetails.getName());
        }

        category.setName(categoryDetails.getName());
        category.setDescription(categoryDetails.getDescription());
        category.setIcon(categoryDetails.getIcon());
        category.setColor(categoryDetails.getColor());
        category.setActive(categoryDetails.getActive());

        InventoryObjectCategory updated = categoryRepository.save(category);
        log.info("Object category updated successfully: {}", id);
        return updated;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Company company = CompanyContext.requireCurrentCompany();

        InventoryObjectCategory category = categoryRepository.findByIdCategoryAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Categoría de objetos no encontrada con id: " + id));

        category.setActive(false);
        categoryRepository.save(category);
        log.info("Object category deactivated successfully: {}", id);
    }

    @Override
    @Transactional
    public void activate(Long id) {
        Company company = CompanyContext.requireCurrentCompany();

        InventoryObjectCategory category = categoryRepository.findByIdCategoryAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Categoría de objetos no encontrada con id: " + id));

        category.setActive(true);
        categoryRepository.save(category);
        log.info("Object category activated successfully: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryObjectCategory> searchWithFilters(String search, Boolean active) {
        String normalizedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        Company company = CompanyContext.requireCurrentCompany();

        List<InventoryObjectCategory> categories =
                categoryRepository.searchWithFiltersAndCompany(company, normalizedSearch, active);
        log.info("Found {} object categories with filters", categories.size());
        return categories;
    }

    @Override
    @Transactional(readOnly = true)
    public long getActiveCount() {
        Company company = CompanyContext.requireCurrentCompany();
        return categoryRepository.countByCompanyAndActive(company, true);
    }

    @Override
    @Transactional(readOnly = true)
    public long getInactiveCount() {
        Company company = CompanyContext.requireCurrentCompany();
        return categoryRepository.countByCompanyAndActive(company, false);
    }

    @Override
    @Transactional
    public void hardDelete(Long id) {
        log.warn("Hard deleting object category with id: {}", id);
        Company company = CompanyContext.requireCurrentCompany();

        InventoryObjectCategory category = categoryRepository.findByIdCategoryAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Categoría de objetos no encontrada con id: " + id));

        List<InventoryObject> objects = objectRepository.findByCategoryIdCategoryAndCompany(id, company);
        if (!objects.isEmpty()) {
            throw new IllegalStateException(
                    "No se puede eliminar la categoría '" + category.getName() +
                    "' porque tiene " + objects.size() + " objeto(s) asignados.");
        }

        categoryRepository.delete(category);
        log.info("Object category '{}' permanently deleted", category.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> checkHardDeleteDependencies(Long id) {
        Company company = CompanyContext.requireCurrentCompany();
        InventoryObjectCategory category = categoryRepository.findByIdCategoryAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Categoría de objetos no encontrada con id: " + id));

        List<InventoryObject> objects = objectRepository.findByCategoryIdCategoryAndCompany(id, company);
        List<String> objectNames = objects.stream().map(InventoryObject::getName).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("categoryName", category.getName());
        result.put("objectCount", objects.size());
        result.put("objectNames", objectNames);
        result.put("canDelete", objects.isEmpty());
        return result;
    }

    /**
     * Set the created by field with the authenticated user of the current company
     */
    private void setCreatedBy(InventoryObjectCategory category) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() &&
                !"anonymousUser".equals(authentication.getPrincipal())) {

                Company company = CompanyContext.requireCurrentCompany();
                Optional<Employee> employee =
                        employeeRepository.findByUsernameAndCompany(authentication.getName(), company);
                employee.ifPresent(category::setCreatedBy);
            }
        } catch (Exception e) {
            log.warn("Could not set created by: {}", e.getMessage());
        }
    }
}
