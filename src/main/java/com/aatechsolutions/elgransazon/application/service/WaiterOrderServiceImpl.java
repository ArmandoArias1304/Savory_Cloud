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
 * WaiterOrderServiceImpl - Implementation for Waiter role
 * 
 * Restrictions:
 * - Can only see orders created by themselves
 * - Cannot mark CASH payment orders as PAID (only cashier can)
 * - Can only edit/cancel their own orders
 */
@Service("waiterOrderService")
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WaiterOrderServiceImpl implements OrderService {

    private final OrderServiceImpl adminOrderService; // Delegate to admin service for actual operations

    /**
     * Get current authenticated username
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : null;
    }

    /**
     * Validate if waiter can access this order
     */
    private void validateOrderOwnership(Order order) {
        String currentUsername = getCurrentUsername();
        if (currentUsername == null || !order.getCreatedBy().equalsIgnoreCase(currentUsername)) {
            throw new IllegalStateException("No tiene permisos para acceder a este pedido");
        }
    }

    // ========== CRUD Operations (with restrictions) ==========

    @Override
    public Order create(Order order, List<OrderDetail> orderDetails) {
        log.info("Waiter {} creating new order", getCurrentUsername());
        // Delegate to admin service - waiters can create orders
        return adminOrderService.create(order, orderDetails);
    }

    @Override
    public Order update(Long id, Order order, List<OrderDetail> orderDetails) {
        Order existingOrder = findByIdOrThrow(id);
        validateOrderOwnership(existingOrder);
        log.info("Waiter {} updating order {}", getCurrentUsername(), id);
        return adminOrderService.update(id, order, orderDetails);
    }

    @Override
    public Order updateOrderInfo(Long id, Order updatedOrder) {
        Order existingOrder = findByIdOrThrow(id);
        validateOrderOwnership(existingOrder);
        log.info("Waiter {} updating order info (no items) {}", getCurrentUsername(), id);
        return adminOrderService.updateOrderInfo(id, updatedOrder);
    }

    @Override
    public Order cancel(Long id, String username) {
        Order order = findByIdOrThrow(id);
        validateOrderOwnership(order);
        
        // Waiter can cancel orders in PENDING, IN_PREPARATION or READY status
        if (order.getStatus() != OrderStatus.PENDING && 
            order.getStatus() != OrderStatus.IN_PREPARATION && 
            order.getStatus() != OrderStatus.READY) {
            throw new IllegalStateException(
                "Los meseros solo pueden cancelar pedidos en estado PENDIENTE, EN PREPARACIÓN o LISTO. " +
                "Este pedido está en estado: " + order.getStatus().getDisplayName()
            );
        }
        
        log.info("Waiter {} cancelling order {} (status: {})", 
                getCurrentUsername(), id, order.getStatus());
        return adminOrderService.cancel(id, username);
    }

    /**
     * Validate if waiter can change to this status
     * Waiter can only mark as DELIVERED if current status is READY
     * Waiter can mark as PAID if current status is DELIVERED (payment method validation is done in controller)
     */
    private void validateStatusChangeForWaiter(Order order, OrderStatus newStatus) {
        // Waiter can only change READY -> DELIVERED
        if (order.getStatus() == OrderStatus.READY && newStatus == OrderStatus.DELIVERED) {
            return; // Valid
        }
        // Waiter can change DELIVERED -> PAID
        // (CASH payment restriction is validated in the controller)
        if (order.getStatus() == OrderStatus.DELIVERED && newStatus == OrderStatus.PAID) {
            return; // Valid
        }
        throw new IllegalStateException(
            "El mesero solo puede cambiar el estado a ENTREGADO cuando el pedido esté LISTO, " +
            "o a PAGADO cuando esté ENTREGADO"
        );
    }

    @Override
    public Order changeStatus(Long id, OrderStatus newStatus, String username) {
        Order order = findByIdOrThrow(id);
        validateOrderOwnership(order);
        validateStatusChangeForWaiter(order, newStatus);
        log.info("Waiter {} changing order {} status to {}", getCurrentUsername(), id, newStatus);
        return adminOrderService.changeStatus(id, newStatus, username);
    }

    @Override
    public void delete(Long id) {
        Order order = findByIdOrThrow(id);
        validateOrderOwnership(order);
        log.info("Waiter {} deleting order {}", getCurrentUsername(), id);
        adminOrderService.delete(id);
    }

    // ========== Query Operations (filtered by waiter) ==========

    @Override
    public List<Order> findAll() {
        String currentUsername = getCurrentUsername();
        log.debug("Waiter {} fetching their orders", currentUsername);
        // Only return orders created by this waiter
        return adminOrderService.findAll().stream()
                .filter(order -> order.getCreatedBy().equalsIgnoreCase(currentUsername))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Order> findById(Long id) {
        Optional<Order> order = adminOrderService.findById(id);
        if (order.isPresent()) {
            try {
                validateOrderOwnership(order.get());
                return order;
            } catch (IllegalStateException e) {
                log.warn("Waiter {} tried to access order {} they don't own", getCurrentUsername(), id);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Order> findByIdWithDetails(Long id) {
        Optional<Order> order = adminOrderService.findByIdWithDetails(id);
        if (order.isPresent()) {
            try {
                validateOrderOwnership(order.get());
                return order;
            } catch (IllegalStateException e) {
                log.warn("Waiter {} tried to access order {} they don't own", getCurrentUsername(), id);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Order> findByOrderNumber(String orderNumber) {
        Optional<Order> order = adminOrderService.findByOrderNumber(orderNumber);
        if (order.isPresent()) {
            try {
                validateOrderOwnership(order.get());
                return order;
            } catch (IllegalStateException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Order> findByTableId(Long tableId) {
        String currentUsername = getCurrentUsername();
        return adminOrderService.findByTableId(tableId).stream()
                .filter(order -> order.getCreatedBy().equalsIgnoreCase(currentUsername))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Order> findActiveOrderByTableId(Long tableId) {
        Optional<Order> order = adminOrderService.findActiveOrderByTableId(tableId);
        if (order.isPresent()) {
            try {
                validateOrderOwnership(order.get());
                return order;
            } catch (IllegalStateException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Order> findByEmployeeId(Long employeeId) {
        // Waiter can only see their own orders, not other employees
        return findAll();
    }

    @Override
    public List<Order> findByStatus(OrderStatus status) {
        String currentUsername = getCurrentUsername();
        return adminOrderService.findByStatus(status).stream()
                .filter(order -> order.getCreatedBy().equalsIgnoreCase(currentUsername))
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> findByOrderType(OrderType orderType) {
        String currentUsername = getCurrentUsername();
        return adminOrderService.findByOrderType(orderType).stream()
                .filter(order -> order.getCreatedBy().equalsIgnoreCase(currentUsername))
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> findTodaysOrders() {
        String currentUsername = getCurrentUsername();
        return adminOrderService.findTodaysOrders().stream()
                .filter(order -> order.getCreatedBy().equalsIgnoreCase(currentUsername))
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> findActiveOrders() {
        String currentUsername = getCurrentUsername();
        return adminOrderService.findActiveOrders().stream()
                .filter(order -> order.getCreatedBy().equalsIgnoreCase(currentUsername))
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        String currentUsername = getCurrentUsername();
        return adminOrderService.findByDateRange(startDate, endDate).stream()
                .filter(order -> order.getCreatedBy().equalsIgnoreCase(currentUsername))
                .collect(Collectors.toList());
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

    // ========== Statistics (only for waiter's orders) ==========

    @Override
    public long countByStatus(OrderStatus status) {
        return findByStatus(status).size();
    }

    @Override
    public long countTodaysOrders() {
        return findTodaysOrders().size();
    }

    @Override
    public long countTodaysOrdersByStatus(OrderStatus status) {
        String currentUsername = getCurrentUsername();
        return adminOrderService.findTodaysOrders().stream()
                .filter(order -> order.getCreatedBy().equalsIgnoreCase(currentUsername))
                .filter(order -> order.getStatus() == status)
                .count();
    }

    @Override
    public BigDecimal getTodaysRevenue() {
        return findTodaysOrders().stream()
                .filter(order -> order.getStatus() == OrderStatus.PAID)
                .map(Order::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public Order findByIdOrThrow(Long id) {
        Order order = adminOrderService.findByIdOrThrow(id);
        validateOrderOwnership(order);
        return order;
    }

    // ========== New Item Management (delegate to admin with ownership validation) ==========

    @Override
    public Order addItemsToExistingOrder(Long orderId, List<OrderDetail> newItems, String username) {
        Order order = findByIdOrThrow(orderId);
        validateOrderOwnership(order);
        log.info("Waiter {} adding {} items to order {}", getCurrentUsername(), newItems.size(), orderId);
        return adminOrderService.addItemsToExistingOrder(orderId, newItems, username);
    }

    @Override
    public Order changeItemsStatus(Long orderId, List<Long> itemDetailIds, OrderStatus newStatus, String username) {
        Order order = findByIdOrThrow(orderId);
        validateOrderOwnership(order);
        log.info("Waiter {} changing status of {} items in order {} to {}", 
                 getCurrentUsername(), itemDetailIds.size(), orderId, newStatus);
        return adminOrderService.changeItemsStatus(orderId, itemDetailIds, newStatus, username);
    }

    @Override
    public Order acceptOrderItems(Long orderId, List<Long> itemDetailIds, String username) {
        throw new UnsupportedOperationException("El mesero no puede aceptar items de pedidos");
    }

    @Override
    public OrderDetail deleteOrderItem(Long orderId, Long itemDetailId, String username) {
        Order order = findByIdOrThrow(orderId);
        validateOrderOwnership(order);
        log.info("Waiter {} deleting item {} from order {}", getCurrentUsername(), itemDetailId, orderId);
        return adminOrderService.deleteOrderItem(orderId, itemDetailId, username);
    }

    @Override
    public OrderDetailComplement deleteOrderItemComplement(Long orderId, Long itemDetailId, Long complementId, String username) {
        Order order = findByIdOrThrow(orderId);
        validateOrderOwnership(order);
        log.info("Waiter {} deleting complement {} from item {} of order {}", 
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
