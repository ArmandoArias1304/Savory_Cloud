package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Supplier;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import com.aatechsolutions.elgransazon.domain.repository.SupplierRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Service implementation for Supplier management
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Supplier> findAll() {
        log.info("Finding all suppliers");
        Company company = CompanyContext.requireCurrentCompany();
        return supplierRepository.findByCompanyOrderByNameAsc(company);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Supplier> findById(Long id) {
        log.info("Finding supplier by id: {}", id);
        Company company = CompanyContext.requireCurrentCompany();
        return supplierRepository.findByIdSupplierAndCompany(id, company);
    }

    @Override
    @Transactional
    public Supplier create(Supplier supplier) {
        log.info("Creating new supplier: {}", supplier.getName());

        // Set company from context
        Company company = CompanyContext.requireCurrentCompany();
        supplier.setCompany(company);

        // Validate unique name within company
        if (supplierRepository.existsByNameAndCompany(supplier.getName(), company)) {
            log.error("Supplier with name {} already exists", supplier.getName());
            throw new IllegalArgumentException("Ya existe un proveedor con el nombre: " + supplier.getName());
        }

        // Validate unique email if provided within company
        if (supplier.getEmail() != null && !supplier.getEmail().isEmpty() &&
            supplierRepository.existsByEmailAndCompany(supplier.getEmail(), company)) {
            log.error("Supplier with email {} already exists", supplier.getEmail());
            throw new IllegalArgumentException("Ya existe un proveedor con el email: " + supplier.getEmail());
        }

        // Set created by (authenticated user)
        setCreatedBy(supplier);

        Supplier savedSupplier = supplierRepository.save(supplier);
        log.info("Supplier created successfully with id: {}", savedSupplier.getIdSupplier());
        return savedSupplier;
    }

    @Override
    @Transactional
    public Supplier update(Long id, Supplier supplierDetails) {
        log.info("Updating supplier with id: {}", id);

        // Get company from context
        Company company = CompanyContext.requireCurrentCompany();

        Supplier supplier = supplierRepository.findByIdSupplierAndCompany(id, company)
                .orElseThrow(() -> {
                    log.error("Supplier not found with id: {}", id);
                    return new IllegalArgumentException("Proveedor no encontrado con id: " + id);
                });

        // Validate unique name if changed within company
        if (!supplier.getName().equals(supplierDetails.getName()) &&
            supplierRepository.existsByNameAndCompany(supplierDetails.getName(), company)) {
            log.error("Supplier with name {} already exists", supplierDetails.getName());
            throw new IllegalArgumentException("Ya existe un proveedor con el nombre: " + supplierDetails.getName());
        }

        // Validate unique email if changed within company
        if (supplierDetails.getEmail() != null && !supplierDetails.getEmail().isEmpty() &&
            !supplierDetails.getEmail().equals(supplier.getEmail()) &&
            supplierRepository.existsByEmailAndCompany(supplierDetails.getEmail(), company)) {
            log.error("Supplier with email {} already exists", supplierDetails.getEmail());
            throw new IllegalArgumentException("Ya existe un proveedor con el email: " + supplierDetails.getEmail());
        }

        // Update fields
        supplier.setName(supplierDetails.getName());
        supplier.setContactPerson(supplierDetails.getContactPerson());
        supplier.setPhone(supplierDetails.getPhone());
        supplier.setEmail(supplierDetails.getEmail());
        supplier.setAddress(supplierDetails.getAddress());
        supplier.setNotes(supplierDetails.getNotes());
        supplier.setRating(supplierDetails.getRating());
        supplier.setActive(supplierDetails.getActive());

        // Update categories (ManyToMany relationship)
        supplier.getCategories().clear();
        if (supplierDetails.getCategories() != null) {
            supplier.getCategories().addAll(supplierDetails.getCategories());
        }

        Supplier updatedSupplier = supplierRepository.save(supplier);
        log.info("Supplier updated successfully: {}", id);
        return updatedSupplier;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        log.info("Deactivating supplier with id: {}", id);

        // Get company from context
        Company company = CompanyContext.requireCurrentCompany();

        Supplier supplier = supplierRepository.findByIdSupplierAndCompany(id, company)
                .orElseThrow(() -> {
                    log.error("Supplier not found with id: {}", id);
                    return new IllegalArgumentException("Proveedor no encontrado con id: " + id);
                });

        supplier.setActive(false);
        supplierRepository.save(supplier);
        log.info("Supplier deactivated successfully: {}", id);
    }

    @Override
    @Transactional
    public void activate(Long id) {
        log.info("Activating supplier with id: {}", id);

        // Get company from context
        Company company = CompanyContext.requireCurrentCompany();

        Supplier supplier = supplierRepository.findByIdSupplierAndCompany(id, company)
                .orElseThrow(() -> {
                    log.error("Supplier not found with id: {}", id);
                    return new IllegalArgumentException("Proveedor no encontrado con id: " + id);
                });

        supplier.setActive(true);
        supplierRepository.save(supplier);
        log.info("Supplier activated successfully: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Supplier> searchWithFilters(String search, Integer rating, Long categoryId, Boolean active) {
        log.info("Searching suppliers with filters - search: {}, rating: {}, categoryId: {}, active: {}",
                search, rating, categoryId, active);

        // Normalize search string
        String normalizedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        Company company = CompanyContext.requireCurrentCompany();
        List<Supplier> suppliers = supplierRepository.searchWithFiltersAndCompany(normalizedSearch, rating, categoryId, active, company);
        
        // Sort: active suppliers first (alphabetically), then inactive suppliers (alphabetically)
        suppliers.sort(Comparator
                .comparing(Supplier::getActive, Comparator.reverseOrder()) // true (active) first, then false (inactive)
                .thenComparing(Supplier::getName, String.CASE_INSENSITIVE_ORDER)); // then alphabetically
        
        log.info("Found {} suppliers with filters", suppliers.size());
        return suppliers;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Supplier> findByCategoryId(Long categoryId) {
        log.info("Finding suppliers for category ID: {}", categoryId);

        Company company = CompanyContext.requireCurrentCompany();
        List<Supplier> suppliers = supplierRepository.findByCategoriesIdCategoryAndCompany(categoryId, company);

        // Sort: active suppliers first (alphabetically), then inactive suppliers (alphabetically)
        suppliers.sort(Comparator
                .comparing(Supplier::getActive, Comparator.reverseOrder()) // true (active) first, then false (inactive)
                .thenComparing(Supplier::getName, String.CASE_INSENSITIVE_ORDER)); // then alphabetically

        log.info("Found {} suppliers for category ID: {}", suppliers.size(), categoryId);
        return suppliers;
    }

    @Override
    @Transactional(readOnly = true)
    public long getActiveCount() {
        Company company = CompanyContext.requireCurrentCompany();
        return supplierRepository.countByActiveAndCompany(true, company);
    }

    @Override
    @Transactional(readOnly = true)
    public long getInactiveCount() {
        Company company = CompanyContext.requireCurrentCompany();
        return supplierRepository.countByActiveAndCompany(false, company);
    }

    @Override
    @Transactional
    public void permanentDelete(Long id) {
        log.info("Permanently deleting supplier with id: {}", id);

        // Get company from context
        Company company = CompanyContext.requireCurrentCompany();

        Supplier supplier = supplierRepository.findByIdSupplierAndCompany(id, company)
                .orElseThrow(() -> {
                    log.error("Supplier not found with id: {}", id);
                    return new IllegalArgumentException("Proveedor no encontrado con id: " + id);
                });

        // First, clear all category associations (ManyToMany relationship)
        // This removes entries from the join table supplier_ingredient_categories
        supplier.getCategories().clear();
        supplierRepository.save(supplier); // Flush the changes to remove join table entries

        // Now we can safely delete the supplier
        supplierRepository.delete(supplier);
        log.info("Supplier permanently deleted successfully: {}", id);
    }

    /**
     * Set the created by field with the authenticated user
     */
    private void setCreatedBy(Supplier supplier) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() &&
                !"anonymousUser".equals(authentication.getPrincipal())) {

                String username = authentication.getName();
                // MULTI-TENANT: Find employee by username+company
                Company company = CompanyContext.requireCurrentCompany();
                Optional<Employee> employee = employeeRepository.findByUsernameAndCompany(username, company);

                employee.ifPresent(supplier::setCreatedBy);
            }
        } catch (Exception e) {
            log.warn("Could not set created by: {}", e.getMessage());
        }
    }

    @Override
    public List<Supplier> findAllActive() {
        log.info("Finding all active suppliers");
        Company company = CompanyContext.requireCurrentCompany();
        return supplierRepository.findAllActiveByCompany(company);
    }
}
