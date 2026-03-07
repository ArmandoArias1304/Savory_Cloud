package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.ItemMenuComboItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for ItemMenuComboItem entity
 */
@Repository
public interface ItemMenuComboItemRepository extends JpaRepository<ItemMenuComboItem, Long> {

    /**
     * Find all combo items for a specific combo menu item
     */
    List<ItemMenuComboItem> findByComboMenuIdItemMenuOrderByDisplayOrderAsc(Long comboMenuId);

    /**
     * Find all combos that contain a specific child item
     */
    List<ItemMenuComboItem> findByChildMenuIdItemMenu(Long childMenuId);

    /**
     * Check if a child item is already part of a combo
     */
    boolean existsByComboMenuIdItemMenuAndChildMenuIdItemMenu(Long comboMenuId, Long childMenuId);

    /**
     * Delete all combo items for a specific combo.
     * Uses bulk JPQL DELETE to execute immediately (bypasses Hibernate action queue ordering
     * which would otherwise try to INSERT new items before DELETEing old ones, causing unique constraint violations).
     */
    @Modifying
    @Query("DELETE FROM ItemMenuComboItem ci WHERE ci.comboMenu.idItemMenu = :comboMenuId")
    void deleteByComboMenuIdItemMenu(@Param("comboMenuId") Long comboMenuId);

    /**
     * Count combo items for a combo
     */
    long countByComboMenuIdItemMenu(Long comboMenuId);

    /**
     * Find combo items with child menu data eagerly loaded
     */
    @Query("SELECT ci FROM ItemMenuComboItem ci " +
           "JOIN FETCH ci.childMenu cm " +
           "WHERE ci.comboMenu.idItemMenu = :comboMenuId " +
           "ORDER BY ci.displayOrder ASC")
    List<ItemMenuComboItem> findByComboMenuIdWithChildDetails(@Param("comboMenuId") Long comboMenuId);

    /**
     * Find all combos that contain a specific child item (with combo data loaded)
     */
    @Query("SELECT ci FROM ItemMenuComboItem ci " +
           "JOIN FETCH ci.comboMenu cm " +
           "WHERE ci.childMenu.idItemMenu = :childMenuId " +
           "AND cm.active = true")
    List<ItemMenuComboItem> findActiveCombosContainingChild(@Param("childMenuId") Long childMenuId);

    /**
     * Delete all combo items where a specific item is a child (delink from combos)
     */
    @Modifying
    @Query("DELETE FROM ItemMenuComboItem ci WHERE ci.childMenu.idItemMenu = :childMenuId")
    void deleteByChildMenuIdItemMenu(@Param("childMenuId") Long childMenuId);
}
