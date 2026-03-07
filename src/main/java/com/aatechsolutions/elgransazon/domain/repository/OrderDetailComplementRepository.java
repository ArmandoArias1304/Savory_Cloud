package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.OrderDetailComplement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Repository for OrderDetailComplement entity
 */
@Repository
public interface OrderDetailComplementRepository extends JpaRepository<OrderDetailComplement, Long> {

    /**
     * Find all complements for a specific order detail
     */
    List<OrderDetailComplement> findByOrderDetailIdOrderDetail(Long orderDetailId);

    /**
     * Find all complements for an order (through order details)
     */
    @Query("SELECT odc FROM OrderDetailComplement odc " +
           "JOIN odc.orderDetail od " +
           "WHERE od.order.idOrder = :orderId")
    List<OrderDetailComplement> findByOrderId(@Param("orderId") Long orderId);

    /**
     * Find complements with full data loaded for an order detail
     */
    @Query("SELECT odc FROM OrderDetailComplement odc " +
           "JOIN FETCH odc.complement c " +
           "WHERE odc.orderDetail.idOrderDetail = :orderDetailId")
    List<OrderDetailComplement> findByOrderDetailIdWithComplement(@Param("orderDetailId") Long orderDetailId);

    /**
     * Delete all complements for an order detail
     */
    void deleteByOrderDetailIdOrderDetail(Long orderDetailId);

    /**
     * Calculate total complement cost for an order detail
     */
    @Query("SELECT COALESCE(SUM(odc.subtotal), 0) FROM OrderDetailComplement odc " +
           "WHERE odc.orderDetail.idOrderDetail = :orderDetailId")
    BigDecimal calculateComplementsTotalForOrderDetail(@Param("orderDetailId") Long orderDetailId);

    /**
     * Calculate total complement cost for an entire order
     */
    @Query("SELECT COALESCE(SUM(odc.subtotal), 0) FROM OrderDetailComplement odc " +
           "JOIN odc.orderDetail od " +
           "WHERE od.order.idOrder = :orderId")
    BigDecimal calculateComplementsTotalForOrder(@Param("orderId") Long orderId);

    /**
     * Find complements that need stock deduction (stockDeducted = false)
     */
    @Query("SELECT odc FROM OrderDetailComplement odc " +
           "JOIN odc.orderDetail od " +
           "WHERE od.order.idOrder = :orderId " +
           "AND odc.stockDeducted = false")
    List<OrderDetailComplement> findComplementsNeedingStockDeduction(@Param("orderId") Long orderId);

    /**
     * Find complements that have stock deducted (for returns)
     */
    @Query("SELECT odc FROM OrderDetailComplement odc " +
           "JOIN odc.orderDetail od " +
           "WHERE od.order.idOrder = :orderId " +
           "AND odc.stockDeducted = true")
    List<OrderDetailComplement> findComplementsWithStockDeducted(@Param("orderId") Long orderId);

    /**
     * Count complements in an order
     */
    @Query("SELECT COUNT(odc) FROM OrderDetailComplement odc " +
           "JOIN odc.orderDetail od " +
           "WHERE od.order.idOrder = :orderId")
    long countComplementsInOrder(@Param("orderId") Long orderId);

    /**
     * Check if order detail has any complements
     */
    boolean existsByOrderDetailIdOrderDetail(Long orderDetailId);

    /**
     * Find usage count for a specific complement (for reporting)
     */
    @Query("SELECT COUNT(odc) FROM OrderDetailComplement odc " +
           "WHERE odc.complement.idComplement = :complementId")
    long countUsageOfComplement(@Param("complementId") Long complementId);

    /**
     * Find total revenue from a specific complement
     */
    @Query("SELECT COALESCE(SUM(odc.subtotal), 0) FROM OrderDetailComplement odc " +
           "WHERE odc.complement.idComplement = :complementId")
    BigDecimal calculateRevenueFromComplement(@Param("complementId") Long complementId);
}
