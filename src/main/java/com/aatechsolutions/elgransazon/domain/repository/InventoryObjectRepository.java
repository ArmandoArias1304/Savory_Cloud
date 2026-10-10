package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.InventoryObject;
import com.aatechsolutions.elgransazon.domain.entity.ObjectCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for InventoryObject entity
 */
@Repository
public interface InventoryObjectRepository extends JpaRepository<InventoryObject, Long> {

    /**
     * Find all objects of a company ordered by name
     */
    List<InventoryObject> findByCompanyOrderByNameAsc(Company company);

    /**
     * Find an object by ID and company (multi-tenant security check)
     */
    Optional<InventoryObject> findByIdObjectAndCompany(Long idObject, Company company);

    /**
     * Check if an object name already exists for a company
     */
    boolean existsByNameAndCompany(String name, Company company);

    /**
     * Search objects of a company with optional filters
     */
    @Query("SELECT o FROM InventoryObject o " +
           "WHERE o.company = :company " +
           "AND (:search IS NULL OR " +
           "LOWER(o.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(o.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(o.brand) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(o.model) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(o.serialNumber) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:categoryId IS NULL OR o.category.idCategory = :categoryId) " +
           "AND (:condition IS NULL OR o.condition = :condition) " +
           "AND (:location IS NULL OR LOWER(o.location) LIKE LOWER(CONCAT('%', :location, '%'))) " +
           "AND (:active IS NULL OR o.active = :active) " +
           "ORDER BY o.name ASC")
    List<InventoryObject> searchWithFiltersAndCompany(@Param("company") Company company,
                                                      @Param("search") String search,
                                                      @Param("categoryId") Long categoryId,
                                                      @Param("condition") ObjectCondition condition,
                                                      @Param("location") String location,
                                                      @Param("active") Boolean active);

    /**
     * Find all objects of a given category (used before deleting the category)
     */
    List<InventoryObject> findByCategoryIdCategoryAndCompany(Long idCategory, Company company);

    /**
     * Count all objects of a company
     */
    long countByCompany(Company company);

    /**
     * Count objects by company and active flag
     */
    long countByCompanyAndActive(Company company, Boolean active);

    /**
     * Count objects by company and condition
     */
    long countByCompanyAndCondition(Company company, ObjectCondition condition);
}
