package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.ShopProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for the global shop catalogue. No company scoping: the products
 * are published by the programmer and shared with every restaurant.
 */
@Repository
public interface ShopProductRepository extends JpaRepository<ShopProduct, Long> {

    /**
     * Catalogue for the programmer, including inactive products.
     * Filters are optional (pass null to ignore them).
     */
    @Query("SELECT p FROM ShopProduct p " +
           "WHERE (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "       OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:category IS NULL OR p.category = :category) " +
           "AND (:active IS NULL OR p.active = :active) " +
           "ORDER BY p.featured DESC, p.sortOrder ASC, p.name ASC")
    List<ShopProduct> searchForProgrammer(@Param("search") String search,
                                          @Param("category") String category,
                                          @Param("active") Boolean active);

    /**
     * Published catalogue for restaurants: only active products.
     */
    @Query("SELECT p FROM ShopProduct p " +
           "WHERE p.active = true " +
           "AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "       OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:category IS NULL OR p.category = :category) " +
           "ORDER BY p.featured DESC, p.sortOrder ASC, p.name ASC")
    List<ShopProduct> searchActive(@Param("search") String search,
                                   @Param("category") String category);

    @Query("SELECT DISTINCT p.category FROM ShopProduct p " +
           "WHERE p.category IS NOT NULL AND p.category <> '' " +
           "ORDER BY p.category ASC")
    List<String> findDistinctCategories();

    @Query("SELECT DISTINCT p.category FROM ShopProduct p " +
           "WHERE p.active = true AND p.category IS NOT NULL AND p.category <> '' " +
           "ORDER BY p.category ASC")
    List<String> findDistinctActiveCategories();

    long countByActiveTrue();
}
