package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
    @Query("SELECT i FROM ItemMenu i WHERE i.category.idCategory = :categoryId ORDER BY i.name ASC")
    List<ItemMenu> findByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Find all items by category ID and company
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.category.idCategory = :categoryId AND i.company = :company ORDER BY i.name ASC")
    List<ItemMenu> findByCategoryIdAndCompany(@Param("categoryId") Long categoryId, @Param("company") Company company);

    /**
     * Find all active items by category ID
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.category.idCategory = :categoryId AND i.active = true ORDER BY i.name ASC")
    List<ItemMenu> findActiveByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Find all active items by category ID and company
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.category.idCategory = :categoryId AND i.active = true AND i.company = :company ORDER BY i.name ASC")
    List<ItemMenu> findActiveByCategoryIdAndCompany(@Param("categoryId") Long categoryId, @Param("company") Company company);

    /**
     * Find all active items
     */
    List<ItemMenu> findByActiveTrue();

    /**
     * Find all available items (active and available)
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.active = true AND i.available = true ORDER BY i.name ASC")
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
    @Query("SELECT COUNT(i) FROM ItemMenu i WHERE i.category.idCategory = :categoryId")
    long countByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Count active items by category
     */
    @Query("SELECT COUNT(i) FROM ItemMenu i WHERE i.category.idCategory = :categoryId AND i.active = true")
    long countActiveByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Count available items by category
     */
    @Query("SELECT COUNT(i) FROM ItemMenu i WHERE i.category.idCategory = :categoryId AND i.active = true AND i.available = true")
    long countAvailableByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Count all available items
     */
    @Query("SELECT COUNT(i) FROM ItemMenu i WHERE i.active = true AND i.available = true")
    long countAvailable();

    /**
     * Count all unavailable items (active but not available)
     */
    @Query("SELECT COUNT(i) FROM ItemMenu i WHERE i.active = true AND i.available = false")
    long countUnavailable();

    /**
     * Find items with low stock (unavailable due to ingredient shortage)
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.active = true AND i.available = false ORDER BY i.name ASC")
    List<ItemMenu> findItemsWithLowStock();

    /**
     * Search items by name (case insensitive)
     */
    @Query("SELECT i FROM ItemMenu i WHERE LOWER(i.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ORDER BY i.name ASC")
    List<ItemMenu> searchByName(@Param("searchTerm") String searchTerm);

    /**
     * Find items by category and availability
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.category.idCategory = :categoryId AND i.active = true AND i.available = :available ORDER BY i.name ASC")
    List<ItemMenu> findByCategoryIdAndAvailability(@Param("categoryId") Long categoryId, 
                                                     @Param("available") Boolean available);

    // ========== Multi-Tenant Methods (by Company) ==========

    /**
     * Find item by name and company
     */
    Optional<ItemMenu> findByNameAndCompany(String name, Company company);

    /**
     * Find all items by company
     */
    List<ItemMenu> findByCompany(Company company);

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
     * Find all available items by company
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.company = :company AND i.active = true AND i.available = true ORDER BY i.name ASC")
    List<ItemMenu> findAvailableItemsByCompany(@Param("company") Company company);

    /**
     * Find all active items by company
     */
    List<ItemMenu> findByActiveTrueAndCompany(Company company);

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
    @Query("SELECT COUNT(i) FROM ItemMenu i WHERE i.company = :company AND i.active = true AND i.available = true")
    long countAvailableByCompany(@Param("company") Company company);

    /**
     * Search items by name and company
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.company = :company AND LOWER(i.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ORDER BY i.name ASC")
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
    long countByCompany(Company company);

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
