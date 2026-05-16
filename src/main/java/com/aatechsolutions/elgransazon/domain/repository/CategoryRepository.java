package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Category;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Category entity
 * Provides CRUD operations and custom queries for categories
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Find a category by its name
     * @param name the category name
     * @return Optional containing the category if found
     */
    Optional<Category> findByName(String name);

    /**
     * Find all active categories ordered by name
     * @return List of active categories
     */
    @Query("SELECT c FROM Category c WHERE c.active = true ORDER BY c.name ASC")
    List<Category> findAllActiveOrderedByName();

    /**
     * Find all categories ordered by name
     * @return List of all categories
     */
    @Query("SELECT c FROM Category c ORDER BY c.name ASC")
    List<Category> findAllOrderedByName();

    /**
     * Check if a category name already exists (case-insensitive)
     * @param name the category name to check
     * @return true if exists, false otherwise
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Count active categories
     * @return number of active categories
     */
    long countByActiveTrue();

    // ========== Multi-Tenant Methods (by Company) ==========

    /**
     * Find a category by name and company
     */
    Optional<Category> findByNameAndCompany(String name, Company company);

    /**
     * Find all active categories by company ordered by name
     */
    @Query("SELECT c FROM Category c WHERE c.company = :company AND c.active = true ORDER BY c.name ASC")
    List<Category> findAllActiveByCompanyOrderedByName(@Param("company") Company company);

    /**
     * Find all categories by company ordered by name
     */
    @Query("SELECT c FROM Category c WHERE c.company = :company ORDER BY c.name ASC")
    List<Category> findAllByCompanyOrderedByName(@Param("company") Company company);

    /**
     * Check if category name exists for a company
     */
    boolean existsByNameIgnoreCaseAndCompany(String name, Company company);

    /**
     * Count active categories by company
     */
    long countByActiveTrueAndCompany(Company company);

    /**
     * Find all categories by company
     */
    List<Category> findByCompany(Company company);

    /**
     * Find category by ID and company (for security validation)
     */
    Optional<Category> findByIdCategoryAndCompany(Long idCategory, Company company);

    // ========== Migration Helper Methods ==========

    /**
     * Find all categories without company (for data migration)
     */
    List<Category> findByCompanyIsNull();

    /**
     * Count categories without company (for data migration)
     */
    long countByCompanyIsNull();
}
