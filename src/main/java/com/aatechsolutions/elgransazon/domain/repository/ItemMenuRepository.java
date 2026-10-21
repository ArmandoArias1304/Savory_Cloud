package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ItemMenu entity
 */
@Repository
public interface ItemMenuRepository extends JpaRepository<ItemMenu, Long> {

    /**
     * Find item by name
     */
    Optional<ItemMenu> findByName(String name);

    /**
     * Find all items by category ID
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.category.idCategory = :categoryId AND i.internalOnly = false ORDER BY i.name ASC")
    List<ItemMenu> findByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Find all items by category ID and company
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.category.idCategory = :categoryId AND i.company = :company AND i.internalOnly = false ORDER BY i.name ASC")
    List<ItemMenu> findByCategoryIdAndCompany(@Param("categoryId") Long categoryId, @Param("company") Company company);

    /**
     * Find all active items by category ID
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.category.idCategory = :categoryId AND i.active = true AND i.internalOnly = false ORDER BY i.name ASC")
    List<ItemMenu> findActiveByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Find all active items by category ID and company
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.category.idCategory = :categoryId AND i.active = true AND i.company = :company AND i.internalOnly = false ORDER BY i.name ASC")
    List<ItemMenu> findActiveByCategoryIdAndCompany(@Param("categoryId") Long categoryId, @Param("company") Company company);

    /**
     * Find all active sellable items (internal production items excluded).
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.active = true AND i.internalOnly = false ORDER BY i.name ASC")
    List<ItemMenu> findByActiveTrue();

    /**
     * Find all available items (active and available)
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.active = true AND i.available = true AND i.internalOnly = false ORDER BY i.name ASC")
    List<ItemMenu> findAvailableItems();

    /**
     * Find all items ordered by name
     */
    @Query("SELECT i FROM ItemMenu i ORDER BY i.name ASC")
    List<ItemMenu> findAllOrderByName();

    /**
     * Find all items ordered by category and name
     */
    @Query("SELECT i FROM ItemMenu i ORDER BY i.category.name ASC, i.name ASC")
    List<ItemMenu> findAllOrderByCategoryAndName();

    /**
     * Check if item name exists
     */
    boolean existsByName(String name);

    /**
     * Check if item name exists excluding a specific id (for updates)
     */
    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM ItemMenu i " +
           "WHERE i.name = :name AND i.idItemMenu <> :excludeId")
    boolean existsByNameAndIdNot(@Param("name") String name, @Param("excludeId") Long excludeId);

    /**
     * Count items by category
     */
    @Query("SELECT COUNT(i) FROM ItemMenu i WHERE i.category.idCategory = :categoryId AND i.internalOnly = false")
    long countByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Count active items by category
     */
    @Query("SELECT COUNT(i) FROM ItemMenu i WHERE i.category.idCategory = :categoryId AND i.active = true AND i.internalOnly = false")
    long countActiveByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Count available items by category
     */
    @Query("SELECT COUNT(i) FROM ItemMenu i WHERE i.category.idCategory = :categoryId AND i.active = true AND i.available = true AND i.internalOnly = false")
    long countAvailableByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Count all available items
     */
    @Query("SELECT COUNT(i) FROM ItemMenu i WHERE i.active = true AND i.available = true AND i.internalOnly = false")
    long countAvailable();

    /**
     * Count all unavailable items (active but not available)
     */
    @Query("SELECT COUNT(i) FROM ItemMenu i WHERE i.active = true AND i.available = false AND i.internalOnly = false")
    long countUnavailable();

    /**
     * Find items with low stock (unavailable due to ingredient shortage)
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.active = true AND i.available = false AND i.internalOnly = false ORDER BY i.name ASC")
    List<ItemMenu> findItemsWithLowStock();

    /**
     * Search items by name (case insensitive)
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.internalOnly = false AND LOWER(i.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ORDER BY i.name ASC")
    List<ItemMenu> searchByName(@Param("searchTerm") String searchTerm);

    /**
     * Find items by category and availability
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.category.idCategory = :categoryId AND i.active = true AND i.available = :available AND i.internalOnly = false ORDER BY i.name ASC")
    List<ItemMenu> findByCategoryIdAndAvailability(@Param("categoryId") Long categoryId, 
                                                     @Param("available") Boolean available);

    // ========== Multi-Tenant Methods (by Company) ==========

    /**
     * Find item by name and company
     */
    Optional<ItemMenu> findByNameAndCompany(String name, Company company);

    /**
     * Find all sellable items of a company (internal production items excluded).
     *
     * <p>This is the selector source for combos, size variants and order lookups, so internal
     * items must never appear here: they are not sold and cannot be part of a combo.</p>
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.company = :company AND i.internalOnly = false ORDER BY i.name ASC")
    List<ItemMenu> findByCompany(@Param("company") Company company);

    /**
     * Find all items by company ordered by name
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.company = :company ORDER BY i.name ASC")
    List<ItemMenu> findAllByCompanyOrderByName(@Param("company") Company company);

    /**
     * Find all items by company ordered by category and name
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.company = :company ORDER BY i.category.name ASC, i.name ASC")
    List<ItemMenu> findAllByCompanyOrderByCategoryAndName(@Param("company") Company company);

    /**
     * Find all items by company ordered by category and name, with parentItem eagerly loaded.
     * Used in the admin list to avoid N+1 queries when displaying the parent badge.
     */
    @Query("SELECT i FROM ItemMenu i LEFT JOIN FETCH i.parentItem WHERE i.company = :company ORDER BY i.category.name ASC, i.name ASC")
    List<ItemMenu> findAllByCompanyOrderByCategoryAndNameWithParent(@Param("company") Company company);

    /**
     * All items of a company with their parent (size variant) eagerly loaded, no
     * ordering guarantee and NO side effects (unlike findAllOrderByCategoryAndName,
     * which recalculates availability and writes). Used by the printed menu (carta)
     * generator, which applies its own filters and ordering in memory.
     */
    @Query("SELECT i FROM ItemMenu i LEFT JOIN FETCH i.parentItem WHERE i.company = :company AND i.internalOnly = false ORDER BY i.name ASC")
    List<ItemMenu> findAllByCompanyWithParentForMenu(@Param("company") Company company);

    /**
     * Search menu items of a company with optional filters, paginated (20 per page in the admin list).
     * All filter parameters are optional: pass null to ignore them.
     */
    @Query(value = """
            SELECT i FROM ItemMenu i LEFT JOIN FETCH i.parentItem
            WHERE i.company = :company
              AND i.internalOnly = false
              AND (:name IS NULL OR LOWER(i.name) LIKE LOWER(CONCAT('%', :name, '%')))
              AND (:categoryId IS NULL OR i.category.idCategory = :categoryId)
              AND (:minPrice IS NULL OR i.price >= :minPrice)
              AND (:maxPrice IS NULL OR i.price <= :maxPrice)
              AND (:active IS NULL OR i.active = :active)
              AND (:available IS NULL OR i.available = :available)
            ORDER BY i.category.name ASC, i.name ASC
            """,
            countQuery = """
            SELECT COUNT(i) FROM ItemMenu i
            WHERE i.company = :company
              AND i.internalOnly = false
              AND (:name IS NULL OR LOWER(i.name) LIKE LOWER(CONCAT('%', :name, '%')))
              AND (:categoryId IS NULL OR i.category.idCategory = :categoryId)
              AND (:minPrice IS NULL OR i.price >= :minPrice)
              AND (:maxPrice IS NULL OR i.price <= :maxPrice)
              AND (:active IS NULL OR i.active = :active)
              AND (:available IS NULL OR i.available = :available)
            """)
    Page<ItemMenu> searchPageByCompany(@Param("company") Company company,
                                       @Param("name") String name,
                                       @Param("categoryId") Long categoryId,
                                       @Param("minPrice") BigDecimal minPrice,
                                       @Param("maxPrice") BigDecimal maxPrice,
                                       @Param("active") Boolean active,
                                       @Param("available") Boolean available,
                                       Pageable pageable);

    /**
     * Find all available items by company
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.company = :company AND i.active = true AND i.available = true AND i.internalOnly = false ORDER BY i.name ASC")
    List<ItemMenu> findAvailableItemsByCompany(@Param("company") Company company);

    /**
     * Find all active <b>sellable</b> items by company.
     *
     * <p>This is the single source of the operational menus (client, home, waiter, cashier,
     * chef, delivery and order screens). Internal production items are filtered out here so
     * they can never reach a menu, an order or a promotion picker.</p>
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.company = :company AND i.active = true " +
           "AND i.internalOnly = false ORDER BY i.name ASC")
    List<ItemMenu> findByActiveTrueAndCompany(@Param("company") Company company);

    /**
     * Internal production items of a company (buffet dishes, staff meals), ordered by name.
     * These items are never sold: they only exist to discount their recipe when produced.
     */
    List<ItemMenu> findByCompanyAndInternalOnlyTrueOrderByNameAsc(Company company);

    /**
     * Internal production item by id, scoped to its company.
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.idItemMenu = :id AND i.company = :company " +
           "AND i.internalOnly = true")
    Optional<ItemMenu> findInternalByIdAndCompany(@Param("id") Long id,
                                                  @Param("company") Company company);

    /**
     * Check if item name exists for company
     */
    boolean existsByNameAndCompany(String name, Company company);

    /**
     * Check if item name exists for company excluding specific id
     */
    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM ItemMenu i " +
           "WHERE i.name = :name AND i.company = :company AND i.idItemMenu <> :excludeId")
    boolean existsByNameAndCompanyAndIdNot(@Param("name") String name, 
                                            @Param("company") Company company, 
                                            @Param("excludeId") Long excludeId);

    /**
     * Find item by ID and company (for security validation)
     */
    Optional<ItemMenu> findByIdItemMenuAndCompany(Long idItemMenu, Company company);

    /**
     * Count available items by company
     */
    @Query("SELECT COUNT(i) FROM ItemMenu i WHERE i.company = :company AND i.active = true AND i.available = true AND i.internalOnly = false")
    long countAvailableByCompany(@Param("company") Company company);

    /**
     * Search items by name and company
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.company = :company AND i.internalOnly = false AND LOWER(i.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ORDER BY i.name ASC")
    List<ItemMenu> searchByNameAndCompany(@Param("searchTerm") String searchTerm, @Param("company") Company company);

    /**
     * Find all items by company ordered by name (alias for findAllByCompanyOrderByName)
     */
    List<ItemMenu> findByCompanyOrderByName(Company company);

    // ========== Migration Helper Methods ==========

    /**
     * Find all items without company (for data migration)
     */
    List<ItemMenu> findByCompanyIsNull();

    /**
     * Count items without company (for data migration)
     */
    long countByCompanyIsNull();

    /**
     * Count all menu items by company
     */
    @Query("SELECT COUNT(i) FROM ItemMenu i WHERE i.company = :company AND i.internalOnly = false")
    long countByCompany(@Param("company") Company company);

    /**
     * Count active menu items by company
     */
    @Query("SELECT COUNT(i) FROM ItemMenu i WHERE i.company = :company AND i.active = true AND i.internalOnly = false")
    long countActiveByCompany(@Param("company") Company company);

    /**
     * Count unavailable menu items by company (active but no stock)
     */
    @Query("SELECT COUNT(i) FROM ItemMenu i WHERE i.company = :company AND i.active = true AND i.available = false AND i.internalOnly = false")
    long countUnavailableByCompany(@Param("company") Company company);

    // ========== Size / Self-Reference Queries ==========

    /**
     * Find all size variants (children) of a given parent item, ordered by price ascending.
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.parentItem.idItemMenu = :parentId ORDER BY i.price ASC")
    List<ItemMenu> findSizeItemsByParentId(@Param("parentId") Long parentId);

    /**
     * Count how many size variants a parent item has.
     */
    @Query("SELECT COUNT(i) FROM ItemMenu i WHERE i.parentItem.idItemMenu = :parentId")
    long countSizeItemsByParentId(@Param("parentId") Long parentId);

    /**
     * Find all "free" items for a company (no parent assigned), ordered by name.
     * Used when selecting which items can have sizes added to them.
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.company = :company AND i.parentItem IS NULL ORDER BY i.name ASC")
    List<ItemMenu> findFreeItemsByCompany(@Param("company") Company company);
}
