package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * CashierOrderServiceImpl - Implementation for Cashier role
 * 
 * Characteristics:
 * - Can create, edit, and cancel orders
 * - Can see only orders they created (like waiter)
 * - Can collect payment with ANY payment method (including CASH)
 * - Can change status: READY -> DELIVERED and DELIVERED -> PAID
 * - In "My Orders" view, sees orders they created
 */
@Service("cashierOrderService")
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CashierOrderServiceImpl implements OrderService {

    private final OrderServiceImpl adminOrderService; // Delegate to admin service for actual operations

    /**
     * Get current authenticated username
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : null;
    }

    /**
     * Validate if cashier can change to this status
     * Cashier can:
     * - Change READY -> DELIVERED (mark order as delivered)
     * - Change DELIVERED -> PAID (collect payment)
     */
    private void validateStatusChangeForCashier(Order order, OrderStatus newStatus) {
        // Cashier can change READY -> DELIVERED
        if (order.getStatus() == OrderStatus.READY && newStatus == OrderStatus.DELIVERED) {
            return; // Valid
        }
        
        // Cashier can change DELIVERED -> PAID
        if (order.getStatus() == OrderStatus.DELIVERED && newStatus == OrderStatus.PAID) {
            return; // Valid
        }
        
        throw new IllegalStateException(
            "El cajero solo puede marcar pedidos LISTOS como ENTREGADOS o pedidos ENTREGADOS como PAGADOS"
        );
    }

    // ========== CRUD Operations ==========

    @Override
    public Order create(Order order, List<OrderDetail> orderDetails) {
        log.info("Cashier {} creating new order", getCurrentUsername());
        // Cashiers can create orders
        return adminOrderService.create(order, orderDetails);
    }

    @Override
    public Order update(Long id, Order order, List<OrderDetail> orderDetails) {
        log.info("Cashier {} updating order {}", getCurrentUsername(), id);
        // Cashiers can update orders (no ownership restriction)
        return adminOrderService.update(id, order, orderDetails);
    }

    @Override
    public Order updateOrderInfo(Long id, Order updatedOrder) {
        log.info("Cashier {} updating order info (no items) {}", getCurrentUsername(), id);
        // Cashiers can update order info without touching items/stock
        return adminOrderService.updateOrderInfo(id, updatedOrder);
    }

    @Override
    public Order cancel(Long id, String username) {
        Order order = findByIdOrThrow(id);
        
        // Cashier can cancel orders in PENDING, IN_PREPARATION or READY status
        if (order.getStatus() != OrderStatus.PENDING && 
            order.getStatus() != OrderStatus.IN_PREPARATION && 
            order.getStatus() != OrderStatus.READY) {
            throw new IllegalStateException(
                "Los cajeros solo pueden cancelar pedidos en estado PENDIENTE, EN PREPARACIÓN o LISTO. " +
                "Este pedido está en estado: " + order.getStatus().getDisplayName()
            );
        }
        
        log.info("Cashier {} cancelling order {} (status: {})", 
                getCurrentUsername(), id, order.getStatus());
        return adminOrderService.cancel(id, username);
    }

    @Override
    public Order changeStatus(Long id, OrderStatus newStatus, String username) {
        Order order = findByIdOrThrow(id);
        validateStatusChangeForCashier(order, newStatus);
        log.info("Cashier {} changing order {} status to {}", getCurrentUsername(), id, newStatus);
        return adminOrderService.changeStatus(id, newStatus, username);
    }

    @Override
    public void delete(Long id) {
        log.info("Cashier {} deleting order {}", getCurrentUsername(), id);
        adminOrderService.delete(id);
    }

    // ========== Query Operations (cashier sees only their created orders) ==========

    @Override
    public List<Order> findAll() {
        // Cashier can only see orders created by themselves (like waiter)
        String currentUsername = getCurrentUsername();
        log.debug("Cashier {} fetching their orders", currentUsername);
        return adminOrderService.findAll().stream()
                .filter(order -> order.getCreatedBy().equalsIgnoreCase(currentUsername))
                .collect(Collectors.toList());
    }
    
    /**
     * Find orders created by current cashier employee
     * Used for "My Orders" view
     */
    public List<Order> findOrdersByCurrentEmployee() {
        return findAll();
    }

    @Override
    public Optional<Order> findById(Long id) {
        // Cashier can view any order
        return adminOrderService.findById(id);
    }

    @Override
    public Optional<Order> findByIdWithDetails(Long id) {
        // Cashier can view any order details
        return adminOrderService.findByIdWithDetails(id);
    }

    @Override
    public Optional<Order> findByOrderNumber(String orderNumber) {
        return adminOrderService.findByOrderNumber(orderNumber);
    }

    @Override
    public List<Order> findByTableId(Long tableId) {
        return adminOrderService.findByTableId(tableId);
    }

    @Override
    public Optional<Order> findActiveOrderByTableId(Long tableId) {
        return adminOrderService.findActiveOrderByTableId(tableId);
    }

    @Override
    public List<Order> findByEmployeeId(Long employeeId) {
        return adminOrderService.findByEmployeeId(employeeId);
    }

    @Override
    public List<Order> findByStatus(OrderStatus status) {
        return adminOrderService.findByStatus(status);
    }

    @Override
    public List<Order> findByOrderType(OrderType orderType) {
        return adminOrderService.findByOrderType(orderType);
    }

    @Override
    public List<Order> findTodaysOrders() {
        return adminOrderService.findTodaysOrders();
    }

    @Override
    public List<Order> findActiveOrders() {
        return adminOrderService.findActiveOrders();
    }

    @Override
    public List<Order> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return adminOrderService.findByDateRange(startDate, endDate);
    }

    // ========== Validation Operations (delegate to admin) ==========

    @Override
    public Map<Long, String> validateStock(List<OrderDetail> orderDetails) {
        return adminOrderService.validateStock(orderDetails);
    }

    @Override
    public boolean hasActiveOrder(Long tableId) {
        return adminOrderService.hasActiveOrder(tableId);
    }

    @Override
    public boolean isTableAvailableForOrder(Long tableId) {
        return adminOrderService.isTableAvailableForOrder(tableId);
    }

    // ========== Order Number Generation (delegate) ==========

    @Override
    public String generateOrderNumber() {
        return adminOrderService.generateOrderNumber();
    }

    // ========== Statistics ==========

    @Override
    public long countByStatus(OrderStatus status) {
        return adminOrderService.countByStatus(status);
    }

    @Override
    public long countTodaysOrders() {
        return adminOrderService.countTodaysOrders();
    }

    @Override
    public long countTodaysOrdersByStatus(OrderStatus status) {
        return adminOrderService.countTodaysOrdersByStatus(status);
    }

    @Override
    public BigDecimal getTodaysRevenue() {
        return adminOrderService.getTodaysRevenue();
    }

    @Override
    public Order findByIdOrThrow(Long id) {
        return adminOrderService.findByIdOrThrow(id);
    }

    /**
     * Find orders paid by current cashier
     * Used for "My Orders" view
     */
    public List<Order> findOrdersPaidByCurrentCashier() {
        String currentUsername = getCurrentUsername();
        log.debug("Cashier {} fetching orders they collected payment for", currentUsername);
        
        return adminOrderService.findAll().stream()
            .filter(order -> 
                order.getPaidBy() != null && 
                order.getPaidBy().getUsername().equalsIgnoreCase(currentUsername)
            )
            .sorted((o1, o2) -> o2.getUpdatedAt().compareTo(o1.getUpdatedAt()))
            .collect(Collectors.toList());
    }

    // ========== New Item Management (delegate to admin) ==========

    @Override
    public Order addItemsToExistingOrder(Long orderId, List<OrderDetail> newItems, String username) {
        log.info("Cashier {} adding {} items to order {}", getCurrentUsername(), newItems.size(), orderId);
        return adminOrderService.addItemsToExistingOrder(orderId, newItems, username);
    }

    @Override
    public Order changeItemsStatus(Long orderId, List<Long> itemDetailIds, OrderStatus newStatus, String username) {
        log.info("Cashier {} changing status of {} items in order {} to {}", 
                 getCurrentUsername(), itemDetailIds.size(), orderId, newStatus);
        return adminOrderService.changeItemsStatus(orderId, itemDetailIds, newStatus, username);
    }

    @Override
    public OrderDetail deleteOrderItem(Long orderId, Long itemDetailId, String username) {
        log.info("Cashier {} deleting item {} from order {}", getCurrentUsername(), itemDetailId, orderId);
        return adminOrderService.deleteOrderItem(orderId, itemDetailId, username);
    }

    @Override
    public OrderDetailComplement deleteOrderItemComplement(Long orderId, Long itemDetailId, Long complementId, String username) {
        log.info("Cashier {} deleting complement {} from item {} of order {}", 
                getCurrentUsername(), complementId, itemDetailId, orderId);
        return adminOrderService.deleteOrderItemComplement(orderId, itemDetailId, complementId, username);
    }

    @Override
    public BigDecimal getTotalIncome() {
        return adminOrderService.getTotalIncome();
    }

    @Override
    public Map<String, BigDecimal> getIncomeByCategory() {
        return adminOrderService.getIncomeByCategory();
    }

    @Override
    public List<Object[]> getItemSalesByCategory(Long categoryId) {
        return adminOrderService.getItemSalesByCategory(categoryId);
    }

    // ========== Statistics by Date Range (delegate to admin) ==========

    @Override
    public long countPaidOrdersByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return adminOrderService.countPaidOrdersByDateRange(startDate, endDate);
    }

    @Override
    public long countPaidOrdersByUsernameAndDateRange(String username, LocalDateTime startDate, LocalDateTime endDate) {
        return adminOrderService.countPaidOrdersByUsernameAndDateRange(username, startDate, endDate);
    }

    @Override
    public long countByStatusAndDateRange(OrderStatus status, LocalDateTime startDate, LocalDateTime endDate) {
        return adminOrderService.countByStatusAndDateRange(status, startDate, endDate);
    }

    @Override
    public BigDecimal getRevenueByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return adminOrderService.getRevenueByDateRange(startDate, endDate);
    }

    @Override
    public BigDecimal getRevenueByUsernameAndDateRange(String username, LocalDateTime startDate, LocalDateTime endDate) {
        return adminOrderService.getRevenueByUsernameAndDateRange(username, startDate, endDate);
    }

    @Override
    public BigDecimal getRevenueCreatedByUserPaidByOthersAndDateRange(String username, LocalDateTime startDate, LocalDateTime endDate) {
        return adminOrderService.getRevenueCreatedByUserPaidByOthersAndDateRange(username, startDate, endDate);
    }

    @Override
    public BigDecimal getRevenueCreatedAndPaidBySameUserAndDateRange(String username, LocalDateTime startDate, LocalDateTime endDate) {
        return adminOrderService.getRevenueCreatedAndPaidBySameUserAndDateRange(username, startDate, endDate);
    }
}
