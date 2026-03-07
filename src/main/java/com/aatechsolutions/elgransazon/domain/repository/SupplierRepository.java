package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Supplier entity
 */
@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    /**
     * Find suppliers by name containing (case insensitive)
     */
    List<Supplier> findByNameContainingIgnoreCase(String name);

    /**
     * Find suppliers by contact person containing (case insensitive)
     */
    List<Supplier> findByContactPersonContainingIgnoreCase(String contactPerson);

    /**
     * Find suppliers by email containing (case insensitive)
     */
    List<Supplier> findByEmailContainingIgnoreCase(String email);

    /**
     * Find suppliers by rating
     */
    List<Supplier> findByRatingOrderByNameAsc(Integer rating);

    /**
     * Find suppliers by active status
     */
    List<Supplier> findByActiveOrderByNameAsc(Boolean active);

    /**
     * Find suppliers by ingredient category
     * Used to get suppliers for a specific ingredient
     */
    List<Supplier> findByCategoriesIdCategory(Long categoryId);

    /**
     * Check if a supplier exists by name
     */
    boolean existsByName(String name);

    /**
     * Check if a supplier exists by email
     */
    boolean existsByEmail(String email);

    /**
     * Advanced search with multiple filters
     */
    @Query("SELECT DISTINCT s FROM Supplier s LEFT JOIN FETCH s.categories " +
           "WHERE (:search IS NULL OR " +
           "LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.contactPerson) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:rating IS NULL OR s.rating = :rating) " +
           "AND (:categoryId IS NULL OR EXISTS (SELECT 1 FROM s.categories c WHERE c.idCategory = :categoryId)) " +
           "AND (:active IS NULL OR s.active = :active) " +
           "ORDER BY s.name ASC")
    List<Supplier> searchWithFilters(@Param("search") String search,
                                     @Param("rating") Integer rating,
                                     @Param("categoryId") Long categoryId,
                                     @Param("active") Boolean active);

    /**
     * Find all suppliers ordered by name
     */
    List<Supplier> findAllByOrderByNameAsc();

    /**
     * Count active suppliers
     */
    long countByActive(Boolean active);

    /**
     * Find all active suppliers ordered by name
     */
    @Query("SELECT s FROM Supplier s WHERE s.active = true ORDER BY s.name ASC")
    List<Supplier> findAllActive();

    // ========== Multi-Tenant Methods (by Company) ==========

    /**
     * Find all suppliers by company
     */
    List<Supplier> findByCompany(Company company);

    /**
     * Find supplier by ID and company (for security validation)
     */
    Optional<Supplier> findByIdSupplierAndCompany(Long idSupplier, Company company);

    /**
     * Find all active suppliers by company ordered by name
     */
    @Query("SELECT s FROM Supplier s WHERE s.company = :company AND s.active = true ORDER BY s.name ASC")
    List<Supplier> findAllActiveByCompany(@Param("company") Company company);

    /**
     * Find all suppliers by company ordered by name
     */
    List<Supplier> findByCompanyOrderByNameAsc(Company company);

    /**
     * Check if supplier name exists for company
     */
    boolean existsByNameAndCompany(String name, Company company);

    /**
     * Check if supplier email exists for company
     */
    boolean existsByEmailAndCompany(String email, Company company);

    /**
     * Advanced search with multiple filters and company
     */
    @Query("SELECT DISTINCT s FROM Supplier s LEFT JOIN FETCH s.categories " +
           "WHERE s.company = :company " +
           "AND (:search IS NULL OR " +
           "LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.contactPerson) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:rating IS NULL OR s.rating = :rating) " +
           "AND (:categoryId IS NULL OR EXISTS (SELECT 1 FROM s.categories c WHERE c.idCategory = :categoryId)) " +
           "AND (:active IS NULL OR s.active = :active) " +
           "ORDER BY s.name ASC")
    List<Supplier> searchWithFiltersAndCompany(@Param("search") String search,
                                               @Param("rating") Integer rating,
                                               @Param("categoryId") Long categoryId,
                                               @Param("active") Boolean active,
                                               @Param("company") Company company);

    /**
     * Find suppliers by ingredient category and company
     */
    List<Supplier> findByCategoriesIdCategoryAndCompany(Long categoryId, Company company);

    /**
     * Count suppliers by active status and company
     */
    long countByActiveAndCompany(boolean active, Company company);

    // ========== Migration Helper Methods ==========

    /**
     * Find all suppliers without company (for data migration)
     */
    List<Supplier> findByCompanyIsNull();
}
