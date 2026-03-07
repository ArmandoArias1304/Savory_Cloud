package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.ItemMenuComplement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ItemMenuComplement entity
 */
@Repository
public interface ItemMenuComplementRepository extends JpaRepository<ItemMenuComplement, Long> {

    /**
     * Find all complements for a specific menu item
     */
    List<ItemMenuComplement> findByItemMenuIdItemMenu(Long itemMenuId);

    /**
     * Find active complements for a menu item
     */
    List<ItemMenuComplement> findByItemMenuIdItemMenuAndActiveTrue(Long itemMenuId);

    /**
     * Find active complements for a menu item ordered by display order
     */
    List<ItemMenuComplement> findByItemMenuIdItemMenuAndActiveTrueOrderByDisplayOrderAsc(Long itemMenuId);

    /**
     * Find all menu items that use a specific complement
     */
    List<ItemMenuComplement> findByComplementIdComplement(Long complementId);

    /**
     * Check if a relationship between item and complement already exists
     */
    boolean existsByItemMenuIdItemMenuAndComplementIdComplement(Long itemMenuId, Long complementId);

    /**
     * Find specific item-complement relationship
     */
    Optional<ItemMenuComplement> findByItemMenuIdItemMenuAndComplementIdComplement(Long itemMenuId, Long complementId);

    /**
     * Delete all complement associations for a menu item
     */
    void deleteByItemMenuIdItemMenu(Long itemMenuId);

    /**
     * Delete all item associations for a complement
     */
    void deleteByComplementIdComplement(Long complementId);

    /**
     * Find complements with full data loaded
     */
    @Query("SELECT imc FROM ItemMenuComplement imc " +
           "JOIN FETCH imc.complement c " +
           "WHERE imc.itemMenu.idItemMenu = :itemMenuId " +
           "AND imc.active = true " +
           "AND c.active = true " +
           "ORDER BY imc.displayOrder ASC")
    List<ItemMenuComplement> findActiveComplementsForItemMenuWithDetails(@Param("itemMenuId") Long itemMenuId);

    /**
     * Find available complements (active and in stock) for a menu item
     */
    @Query("SELECT imc FROM ItemMenuComplement imc " +
           "JOIN FETCH imc.complement c " +
           "WHERE imc.itemMenu.idItemMenu = :itemMenuId " +
           "AND imc.active = true " +
           "AND c.active = true " +
           "AND c.available = true " +
           "ORDER BY imc.displayOrder ASC")
    List<ItemMenuComplement> findAvailableComplementsForItemMenu(@Param("itemMenuId") Long itemMenuId);
    /**
     * Count complements for a menu item
     */
    long countByItemMenuIdItemMenuAndActiveTrue(Long itemMenuId);

    /**
     * Count menu items using a complement
     */
    long countByComplementIdComplementAndActiveTrue(Long complementId);
}
