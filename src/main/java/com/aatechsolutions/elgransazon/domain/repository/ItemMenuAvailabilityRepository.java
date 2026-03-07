package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.DayOfWeek;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenuAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ItemMenuAvailability entity
 * Manages the days when menu items are available
 */
@Repository
public interface ItemMenuAvailabilityRepository extends JpaRepository<ItemMenuAvailability, Long> {

    /**
     * Find all availability records for a specific menu item
     */
    @Query("SELECT ima FROM ItemMenuAvailability ima WHERE ima.itemMenu.idItemMenu = :itemMenuId ORDER BY ima.dayOfWeek")
    List<ItemMenuAvailability> findByItemMenuId(@Param("itemMenuId") Long itemMenuId);

    /**
     * Find availability record for a specific item and day
     */
    @Query("SELECT ima FROM ItemMenuAvailability ima WHERE ima.itemMenu.idItemMenu = :itemMenuId AND ima.dayOfWeek = :dayOfWeek")
    Optional<ItemMenuAvailability> findByItemMenuIdAndDayOfWeek(@Param("itemMenuId") Long itemMenuId, @Param("dayOfWeek") DayOfWeek dayOfWeek);

    /**
     * Check if an item is available on a specific day
     */
    @Query("SELECT CASE WHEN COUNT(ima) > 0 THEN true ELSE false END FROM ItemMenuAvailability ima " +
           "WHERE ima.itemMenu.idItemMenu = :itemMenuId AND ima.dayOfWeek = :dayOfWeek")
    boolean isAvailableOnDay(@Param("itemMenuId") Long itemMenuId, @Param("dayOfWeek") DayOfWeek dayOfWeek);

    /**
     * Get all days when an item is available
     */
    @Query("SELECT ima.dayOfWeek FROM ItemMenuAvailability ima WHERE ima.itemMenu.idItemMenu = :itemMenuId ORDER BY ima.dayOfWeek")
    List<DayOfWeek> findAvailableDaysByItemMenuId(@Param("itemMenuId") Long itemMenuId);

    /**
     * Delete all availability records for a specific item
     */
    @Modifying
    @Query("DELETE FROM ItemMenuAvailability ima WHERE ima.itemMenu.idItemMenu = :itemMenuId")
    void deleteByItemMenuId(@Param("itemMenuId") Long itemMenuId);

    /**
     * Delete a specific availability record
     */
    @Modifying
    @Query("DELETE FROM ItemMenuAvailability ima WHERE ima.itemMenu.idItemMenu = :itemMenuId AND ima.dayOfWeek = :dayOfWeek")
    void deleteByItemMenuIdAndDayOfWeek(@Param("itemMenuId") Long itemMenuId, @Param("dayOfWeek") DayOfWeek dayOfWeek);

    /**
     * Count how many days an item is available
     */
    @Query("SELECT COUNT(ima) FROM ItemMenuAvailability ima WHERE ima.itemMenu.idItemMenu = :itemMenuId")
    long countByItemMenuId(@Param("itemMenuId") Long itemMenuId);

    /**
     * Find all items available on a specific day
     */
    @Query("SELECT ima.itemMenu.idItemMenu FROM ItemMenuAvailability ima WHERE ima.dayOfWeek = :dayOfWeek")
    List<Long> findItemMenuIdsAvailableOnDay(@Param("dayOfWeek") DayOfWeek dayOfWeek);

    /**
     * Check if any availability records exist for an item
     */
    @Query("SELECT CASE WHEN COUNT(ima) > 0 THEN true ELSE false END FROM ItemMenuAvailability ima " +
           "WHERE ima.itemMenu.idItemMenu = :itemMenuId")
    boolean existsByItemMenuId(@Param("itemMenuId") Long itemMenuId);
}
