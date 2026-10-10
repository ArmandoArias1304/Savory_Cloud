package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.InventoryObjectCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for InventoryObjectCategory entity
 */
@Repository
public interface InventoryObjectCategoryRepository extends JpaRepository<InventoryObjectCategory, Long> {

    /**
     * Find all object categories of a company ordered by name
     */
    List<InventoryObjectCategory> findByCompanyOrderByNameAsc(Company company);

    /**
     * Find an object category by ID and company (multi-tenant security check)
     */
    Optional<InventoryObjectCategory> findByIdCategoryAndCompany(Long idCategory, Company company);

    /**
     * Find all active object categories of a company ordered by name
     */
    @Query("SELECT c FROM InventoryObjectCategory c " +
           "WHERE c.company = :company AND c.active = true ORDER BY c.name ASC")
    List<InventoryObjectCategory> findAllActiveByCompany(@Param("company") Company company);

    /**
     * Check if an object category name already exists for a company
     */
    boolean existsByNameAndCompany(String name, Company company);

    /**
     * Search object categories of a company with optional filters
     */
    @Query("SELECT c FROM InventoryObjectCategory c " +
           "WHERE c.company = :company " +
           "AND (:search IS NULL OR " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:active IS NULL OR c.active = :active) " +
           "ORDER BY c.name ASC")
    List<InventoryObjectCategory> searchWithFiltersAndCompany(@Param("company") Company company,
                                                             @Param("search") String search,
                                                             @Param("active") Boolean active);

    /**
     * Count active object categories of a company
     */
    long countByCompanyAndActive(Company company, Boolean active);
}
