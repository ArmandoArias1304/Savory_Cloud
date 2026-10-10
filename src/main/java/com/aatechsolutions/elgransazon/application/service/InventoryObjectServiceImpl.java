package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.InventoryObject;
import com.aatechsolutions.elgransazon.domain.entity.ObjectCondition;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import com.aatechsolutions.elgransazon.domain.repository.InventoryObjectRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Service implementation for InventoryObject management.
 * Every operation is scoped to the company of the current request (multi-tenant).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryObjectServiceImpl implements InventoryObjectService {

    private final InventoryObjectRepository objectRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    @Transactional(readOnly = true)
    public List<InventoryObject> searchWithFilters(String search, Long categoryId, ObjectCondition condition,
                                                   String location, Boolean active) {
        Company company = CompanyContext.requireCurrentCompany();

        String normalizedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        String normalizedLocation = (location != null && !location.trim().isEmpty()) ? location.trim() : null;

        List<InventoryObject> objects = objectRepository.searchWithFiltersAndCompany(
                company, normalizedSearch, categoryId, condition, normalizedLocation, active);
        log.info("Found {} inventory objects with filters", objects.size());
        return objects;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryObject> findById(Long id) {
        Company company = CompanyContext.requireCurrentCompany();
        return objectRepository.findByIdObjectAndCompany(id, company);
    }

    @Override
    @Transactional
    public InventoryObject create(InventoryObject object) {
        Company company = CompanyContext.requireCurrentCompany();
        object.setCompany(company);

        validateCategoryOfCompany(object, company);

        if (objectRepository.existsByNameAndCompany(object.getName(), company)) {
            log.error("Object with name {} already exists for company {}", object.getName(), company.getIdCompany());
            throw new IllegalArgumentException("Ya existe un objeto con el nombre: " + object.getName());
        }

        setCreatedBy(object);

        InventoryObject saved = objectRepository.save(object);
        log.info("Inventory object created successfully with id: {}", saved.getIdObject());
        return saved;
    }

    @Override
    @Transactional
    public InventoryObject update(Long id, InventoryObject objectDetails) {
        Company company = CompanyContext.requireCurrentCompany();

        InventoryObject object = objectRepository.findByIdObjectAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Objeto no encontrado con id: " + id));

        validateCategoryOfCompany(objectDetails, company);

        if (!object.getName().equals(objectDetails.getName()) &&
            objectRepository.existsByNameAndCompany(objectDetails.getName(), company)) {
            log.error("Object with name {} already exists for company {}", objectDetails.getName(), company.getIdCompany());
            throw new IllegalArgumentException("Ya existe un objeto con el nombre: " + objectDetails.getName());
        }

        object.setName(objectDetails.getName());
        object.setDescription(objectDetails.getDescription());
        object.setCategory(objectDetails.getCategory());
        object.setQuantity(objectDetails.getQuantity());
        object.setUnit(objectDetails.getUnit());
        object.setCondition(objectDetails.getCondition());
        object.setLocation(objectDetails.getLocation());
        object.setResponsible(objectDetails.getResponsible());
        object.setPurchaseCost(objectDetails.getPurchaseCost());
        object.setCurrency(objectDetails.getCurrency() != null ? objectDetails.getCurrency() : "MXN");
        object.setPurchaseDate(objectDetails.getPurchaseDate());
        object.setBrand(objectDetails.getBrand());
        object.setModel(objectDetails.getModel());
        object.setSerialNumber(objectDetails.getSerialNumber());
        object.setNotes(objectDetails.getNotes());
        object.setActive(objectDetails.getActive());

        InventoryObject updated = objectRepository.save(object);
        log.info("Inventory object updated successfully: {}", id);
        return updated;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Company company = CompanyContext.requireCurrentCompany();

        InventoryObject object = objectRepository.findByIdObjectAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Objeto no encontrado con id: " + id));

        object.setActive(false);
        objectRepository.save(object);
        log.info("Inventory object deactivated successfully: {}", id);
    }

    @Override
    @Transactional
    public void activate(Long id) {
        Company company = CompanyContext.requireCurrentCompany();

        InventoryObject object = objectRepository.findByIdObjectAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Objeto no encontrado con id: " + id));

        object.setActive(true);
        objectRepository.save(object);
        log.info("Inventory object activated successfully: {}", id);
    }

    @Override
    @Transactional
    public void hardDelete(Long id) {
        log.warn("Hard deleting inventory object with id: {}", id);
        Company company = CompanyContext.requireCurrentCompany();

        InventoryObject object = objectRepository.findByIdObjectAndCompany(id, company)
                .orElseThrow(() -> new IllegalArgumentException("Objeto no encontrado con id: " + id));

        objectRepository.delete(object);
        log.info("Inventory object '{}' permanently deleted", object.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public long getTotalCount() {
        Company company = CompanyContext.requireCurrentCompany();
        return objectRepository.countByCompany(company);
    }

    @Override
    @Transactional(readOnly = true)
    public long getActiveCount() {
        Company company = CompanyContext.requireCurrentCompany();
        return objectRepository.countByCompanyAndActive(company, true);
    }

    @Override
    @Transactional(readOnly = true)
    public long getInactiveCount() {
        Company company = CompanyContext.requireCurrentCompany();
        return objectRepository.countByCompanyAndActive(company, false);
    }

    @Override
    @Transactional(readOnly = true)
    public long getDamagedCount() {
        Company company = CompanyContext.requireCurrentCompany();
        return objectRepository.countByCompanyAndCondition(company, ObjectCondition.DANADO);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getInventoryValue() {
        Company company = CompanyContext.requireCurrentCompany();
        return objectRepository.findByCompanyOrderByNameAsc(company).stream()
                .filter(o -> Boolean.TRUE.equals(o.getActive()))
                .map(InventoryObject::getTotalValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * The category must belong to the company of the current request
     */
    private void validateCategoryOfCompany(InventoryObject object, Company company) {
        if (object.getCategory() == null || object.getCategory().getIdCategory() == null) {
            throw new IllegalArgumentException("Debe seleccionar una categoría de objetos válida");
        }
        if (object.getCategory().getCompany() != null &&
            !company.getIdCompany().equals(object.getCategory().getCompany().getIdCompany())) {
            throw new IllegalArgumentException("Debe seleccionar una categoría de objetos válida");
        }
    }

    /**
     * Set the created by field with the authenticated user of the current company
     */
    private void setCreatedBy(InventoryObject object) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() &&
                !"anonymousUser".equals(authentication.getPrincipal())) {

                Company company = CompanyContext.requireCurrentCompany();
                Optional<Employee> employee =
                        employeeRepository.findByUsernameAndCompany(authentication.getName(), company);
                employee.ifPresent(object::setCreatedBy);
            }
        } catch (Exception e) {
            log.warn("Could not set created by: {}", e.getMessage());
        }
    }
}
