package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Ingredient entity
 */
@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    /**
     * Find ingredient by ID with pessimistic write lock (SELECT FOR UPDATE).
     * This ensures exclusive access to the ingredient during stock updates,
     * preventing race conditions when multiple transactions try to update the same ingredient.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Ingredient i WHERE i.idIngredient = :id")
    Optional<Ingredient> findByIdWithLock(@Param("id") Long id);

    /**
     * Find ingredients by name containing (case insensitive)
     */
    List<Ingredient> findByNameContainingIgnoreCase(String name);

    /**
     * Find ingredients by description containing (case insensitive)
     */
    List<Ingredient> findByDescriptionContainingIgnoreCase(String description);

    /**
     * Find ingredients by storage location containing (case insensitive)
     */
    List<Ingredient> findByStorageLocationContainingIgnoreCase(String storageLocation);

    /**
     * Find ingredients by category
     */
    List<Ingredient> findByCategoryIdCategoryOrderByNameAsc(Long categoryId);

    /**
     * Find ingredients by active status
     */
    List<Ingredient> findByActiveOrderByNameAsc(Boolean active);

    /**
     * Find all active ingredients
     */
    List<Ingredient> findByActiveTrue();

    /**
     * Find all ingredients ordered by name
     */
    List<Ingredient> findAllByOrderByNameAsc();

    /**
     * Check if ingredient exists by name
     */
    boolean existsByName(String name);

    /**
     * Find ingredients with low stock (current stock <= min stock)
     */
    @Query("SELECT i FROM Ingredient i WHERE i.currentStock <= i.minStock AND i.currentStock > 0 AND i.active = true ORDER BY i.currentStock ASC")
    List<Ingredient> findLowStockIngredients();

    /**
     * Find ingredients out of stock (current stock = 0)
     */
    @Query("SELECT i FROM Ingredient i WHERE (i.currentStock IS NULL OR i.currentStock = 0) AND i.active = true ORDER BY i.name ASC")
    List<Ingredient> findOutOfStockIngredients();

    /**
     * Count ingredients with low stock
     */
    @Query("SELECT COUNT(i) FROM Ingredient i WHERE i.currentStock <= i.minStock AND i.currentStock > 0 AND i.active = true")
    long countLowStockIngredients();

    /**
     * Count ingredients out of stock
     */
    @Query("SELECT COUNT(i) FROM Ingredient i WHERE (i.currentStock IS NULL OR i.currentStock = 0) AND i.active = true")
    long countOutOfStockIngredients();

    /**
     * Advanced search with multiple filters
     */
    @Query("SELECT DISTINCT i FROM Ingredient i LEFT JOIN FETCH i.category c " +
           "WHERE (:search IS NULL OR " +
           "LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(i.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(i.storageLocation) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "CAST(i.costPerUnit AS string) LIKE CONCAT('%', :search, '%')) " +
           "AND (:categoryId IS NULL OR i.category.idCategory = :categoryId) " +
           "AND (:active IS NULL OR i.active = :active) " +
           "ORDER BY i.name ASC")
    List<Ingredient> searchWithFilters(@Param("search") String search,
                                      @Param("categoryId") Long categoryId,
                                      @Param("active") Boolean active);

    /**
     * Find ingredients by supplier (through category)
     */
    @Query("SELECT i FROM Ingredient i JOIN i.category c JOIN c.suppliers s " +
           "WHERE s.idSupplier = :supplierId " +
           "ORDER BY i.name ASC")
    List<Ingredient> findBySupplier(@Param("supplierId") Long supplierId);

    /**
     * Find ingredients with stock filters
     */
    @Query("SELECT i FROM Ingredient i " +
           "WHERE (:stockStatus IS NULL OR " +
           "(:stockStatus = 'low-stock' AND i.currentStock <= i.minStock AND i.currentStock > 0) OR " +
           "(:stockStatus = 'out-of-stock' AND (i.currentStock IS NULL OR i.currentStock = 0))) " +
           "AND (:active IS NULL OR i.active = :active) " +
           "ORDER BY i.name ASC")
    List<Ingredient> findByStockStatus(@Param("stockStatus") String stockStatus,
                                       @Param("active") Boolean active);

    /**
     * Count active ingredients
     */
    long countByActive(Boolean active);

    // ========== Multi-Tenant Methods (by Company) ==========

    /**
     * Find all ingredients by company
     */
    List<Ingredient> findByCompany(Company company);

    /**
     * Find ingredient by ID and company (for security validation)
     */
    Optional<Ingredient> findByIdIngredientAndCompany(Long idIngredient, Company company);

    /**
     * Find all active ingredients by company
     */
    List<Ingredient> findByActiveTrueAndCompany(Company company);

    /**
     * Find all ingredients by company ordered by name
     */
    List<Ingredient> findByCompanyOrderByNameAsc(Company company);

    /**
     * Check if ingredient name exists for company
     */
    boolean existsByNameAndCompany(String name, Company company);

    /**
     * Find low stock ingredients by company
     */
    @Query("SELECT i FROM Ingredient i WHERE i.company = :company AND i.currentStock <= i.minStock AND i.currentStock > 0 AND i.active = true ORDER BY i.currentStock ASC")
    List<Ingredient> findLowStockIngredientsByCompany(@Param("company") Company company);

    /**
     * Find out of stock ingredients by company
     */
    @Query("SELECT i FROM Ingredient i WHERE i.company = :company AND (i.currentStock IS NULL OR i.currentStock = 0) AND i.active = true ORDER BY i.name ASC")
    List<Ingredient> findOutOfStockIngredientsByCompany(@Param("company") Company company);

    /**
     * Count low stock ingredients by company
     */
    @Query("SELECT COUNT(i) FROM Ingredient i WHERE i.company = :company AND i.currentStock <= i.minStock AND i.currentStock > 0 AND i.active = true")
    long countLowStockIngredientsByCompany(@Param("company") Company company);

    /**
     * Count out of stock ingredients by company
     */
    @Query("SELECT COUNT(i) FROM Ingredient i WHERE i.company = :company AND (i.currentStock IS NULL OR i.currentStock = 0) AND i.active = true")
    long countOutOfStockIngredientsByCompany(@Param("company") Company company);

    /**
     * Count by active status and company
     */
    long countByActiveAndCompany(Boolean active, Company company);

    /**
     * Advanced search with multiple filters by company
     */
    @Query("SELECT DISTINCT i FROM Ingredient i LEFT JOIN FETCH i.category c " +
           "WHERE i.company = :company " +
           "AND (:search IS NULL OR " +
           "LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(i.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(i.storageLocation) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "CAST(i.costPerUnit AS string) LIKE CONCAT('%', :search, '%')) " +
           "AND (:categoryId IS NULL OR i.category.idCategory = :categoryId) " +
           "AND (:active IS NULL OR i.active = :active) " +
           "ORDER BY i.name ASC")
    List<Ingredient> searchWithFiltersAndCompany(@Param("search") String search,
                                                 @Param("categoryId") Long categoryId,
                                                 @Param("active") Boolean active,
                                                 @Param("company") Company company);

    /**
     * Find ingredients by supplier (through category) and company
     */
    @Query("SELECT i FROM Ingredient i JOIN i.category c JOIN c.suppliers s " +
           "WHERE s.idSupplier = :supplierId AND i.company = :company " +
           "ORDER BY i.name ASC")
    List<Ingredient> findBySupplierAndCompany(@Param("supplierId") Long supplierId, @Param("company") Company company);

    // ========== Migration Helper Methods ==========

    /**
     * Find all ingredients without company (for data migration)
     */
    List<Ingredient> findByCompanyIsNull();
}
