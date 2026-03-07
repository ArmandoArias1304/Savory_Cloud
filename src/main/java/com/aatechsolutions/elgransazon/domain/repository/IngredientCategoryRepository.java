package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.IngredientCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for IngredientCategory entity
 */
@Repository
public interface IngredientCategoryRepository extends JpaRepository<IngredientCategory, Long> {

    /**
     * Find categories by name containing (case insensitive)
     */
    List<IngredientCategory> findByNameContainingIgnoreCase(String name);

    /**
     * Find categories by description containing (case insensitive)
     */
    List<IngredientCategory> findByDescriptionContainingIgnoreCase(String description);

    /**
     * Find all active categories ordered by name
     */
    List<IngredientCategory> findByActiveOrderByNameAsc(Boolean active);

    /**
     * Find all categories ordered by name
     */
    List<IngredientCategory> findAllByOrderByNameAsc();

    /**
     * Check if a category exists by name
     */
    boolean existsByName(String name);

    /**
     * Advanced search with multiple filters
     */
    @Query("SELECT c FROM IngredientCategory c " +
           "WHERE (:search IS NULL OR " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:active IS NULL OR c.active = :active) " +
           "ORDER BY c.name ASC")
    List<IngredientCategory> searchWithFilters(@Param("search") String search,
                                               @Param("active") Boolean active);

    /**
     * Count active categories
     */
    long countByActive(Boolean active);

    /**
     * Find all active categories (for dropdowns)
     */
    @Query("SELECT c FROM IngredientCategory c WHERE c.active = true ORDER BY c.name ASC")
    List<IngredientCategory> findAllActive();

    // ========== Multi-Tenant Methods (by Company) ==========

    /**
     * Find all ingredient categories by company
     */
    List<IngredientCategory> findByCompany(Company company);

    /**
     * Find ingredient category by ID and company (for security validation)
     */
    Optional<IngredientCategory> findByIdCategoryAndCompany(Long idCategory, Company company);

    /**
     * Find all active ingredient categories by company ordered by name
     */
    @Query("SELECT c FROM IngredientCategory c WHERE c.company = :company AND c.active = true ORDER BY c.name ASC")
    List<IngredientCategory> findAllActiveByCompany(@Param("company") Company company);

    /**
     * Find all ingredient categories by company ordered by name
     */
    List<IngredientCategory> findByCompanyOrderByNameAsc(Company company);

    /**
     * Check if ingredient category name exists for company
     */
    boolean existsByNameAndCompany(String name, Company company);

    // ========== Migration Helper Methods ==========

    /**
     * Find all ingredient categories without company (for data migration)
     */
    List<IngredientCategory> findByCompanyIsNull();
}
