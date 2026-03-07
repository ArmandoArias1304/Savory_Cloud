package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.OrderDetail;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for OrderDetail entity
 */
@Repository
public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {

    /**
     * Find all order details by order
     */
    List<OrderDetail> findByOrder(Order order);

    /**
     * Find all order details by order ID
     */
    @Query("SELECT od FROM OrderDetail od WHERE od.order.idOrder = :orderId")
    List<OrderDetail> findByOrderId(@Param("orderId") Long orderId);

    /**
     * Find all order details by item menu
     */
    List<OrderDetail> findByItemMenu(ItemMenu itemMenu);

    /**
     * Find all order details by item menu ID
     */
    @Query("SELECT od FROM OrderDetail od WHERE od.itemMenu.idItemMenu = :itemMenuId")
    List<OrderDetail> findByItemMenuId(@Param("itemMenuId") Long itemMenuId);

    /**
     * Delete all order details by order
     */
    void deleteByOrder(Order order);

    /**
     * Get income grouped by menu category (only PAID orders)
     * Returns: [categoryId, categoryName, totalSales]
     */
    @Query("SELECT im.category.idCategory, im.category.name, COALESCE(SUM(od.subtotal), 0) " +
           "FROM OrderDetail od " +
           "JOIN od.itemMenu im " +
           "WHERE od.order.status = 'PAID' " +
           "GROUP BY im.category.idCategory, im.category.name " +
           "ORDER BY SUM(od.subtotal) DESC")
    List<Object[]> getIncomeByMenuCategory();

    /**
     * Get items sold by category (only PAID orders)
     * Returns: [itemName, totalQuantity, totalSales]
     */
    @Query("SELECT od.itemMenu.name, SUM(od.quantity), COALESCE(SUM(od.subtotal), 0) " +
           "FROM OrderDetail od " +
           "WHERE od.order.status = 'PAID' " +
           "AND od.itemMenu.category.idCategory = :categoryId " +
           "GROUP BY od.itemMenu.idItemMenu, od.itemMenu.name " +
           "ORDER BY SUM(od.quantity) DESC")
    List<Object[]> getItemSalesByCategory(@Param("categoryId") Long categoryId);

    /**
     * Check if any order detail references a specific menu item
     */
    boolean existsByItemMenuIdItemMenu(Long itemMenuId);

    /**
     * Count order details for a specific menu item
     */
    long countByItemMenuIdItemMenu(Long itemMenuId);
}
