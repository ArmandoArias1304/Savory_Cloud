package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
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
 * BaristaOrderServiceImpl - Implementation for Barista role
 * 
 * Restrictions:
 * - Can only change status from PENDING to IN_PREPARATION (for barista items)
 * - Can only change status from IN_PREPARATION to READY (for barista items)
 * - Can see all PENDING orders that contain items requiring barista preparation
 * - Can see orders they have accepted (IN_PREPARATION or READY) with barista items
 * - Cannot cancel orders
 * - Cannot create new orders
 * - Cannot mark orders as DELIVERED or PAID
 * - Shares same interface as Chef but filters by requiresBaristaPreparation
 */
@Service("baristaOrderService")
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BaristaOrderServiceImpl implements OrderService {

    private final OrderServiceImpl adminOrderService; // Delegate to admin service for actual operations
    private final OrderRepository orderRepository; // Direct access for optimized queries
    private final EmployeeService employeeService; // To get current employee entity
    private final WebSocketNotificationService wsNotificationService; // WebSocket notifications

    /**
     * Get current authenticated username
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : null;
    }

    /**
     * Check if order contains items that require barista preparation
     * AND those items are still PENDING or IN_PREPARATION
     * 
     * @param order The order to check
     * @return true if at least one barista item is PENDING or IN_PREPARATION, false otherwise
     * 
     * IMPORTANT: This now checks both:
     * 1. Item requires barista preparation (requiresBaristaPreparation = true)
     * 2. Item status is PENDING or IN_PREPARATION
     * 
     * If all barista items are already READY, the order won't be shown to barista
     */
    private boolean hasItemsRequiringBaristaPreparation(Order order) {
        if (order.getOrderDetails() == null || order.getOrderDetails().isEmpty()) {
            return false;
        }
        
        boolean hasPendingBaristaItems = order.getOrderDetails().stream()
            .anyMatch(detail -> {
                if (detail.getItemMenu() == null) {
                    return false;
                }
                
                // Check if item requires barista preparation
                Boolean requiresBaristaPrep = detail.getItemMenu().getRequiresBaristaPreparation();
                if (!Boolean.TRUE.equals(requiresBaristaPrep)) {
                    return false; // Not a barista item
                }
                
                // Check if item is TO_ACCEPT, PENDING or IN_PREPARATION
                // TO_ACCEPT items are shown so barista stays aware of orders that have new items
                // pending validation by cashier/admin, but barista cannot manipulate them.
                OrderStatus itemStatus = detail.getItemStatus();
                boolean isPending = itemStatus == OrderStatus.TO_ACCEPT
                    || itemStatus == OrderStatus.PENDING
                    || itemStatus == OrderStatus.IN_PREPARATION;
                
                log.debug("Order {}, Barista Item '{}': status = {}, isPending = {}", 
                    order.getOrderNumber(), 
                    detail.getItemMenu().getName(),
                    itemStatus,
                    isPending);
                
                return isPending;
            });
        
        log.info("🔍 Order {} hasPendingBaristaItems: {}", order.getOrderNumber(), hasPendingBaristaItems);
        return hasPendingBaristaItems;
    }

    // ========== CRUD Operations (restricted for barista) ==========

    @Override
    public Order create(Order order, List<OrderDetail> orderDetails) {
        throw new UnsupportedOperationException("El barista no puede crear pedidos");
    }

    @Override
    public Order update(Long id, Order order, List<OrderDetail> orderDetails) {
        throw new UnsupportedOperationException("El barista no puede modificar pedidos");
    }

    @Override
    public Order updateOrderInfo(Long id, Order updatedOrder) {
        throw new UnsupportedOperationException("El barista no puede modificar información de pedidos");
    }

    @Override
    public Order cancel(Long id, String username) {
        throw new UnsupportedOperationException("El barista no puede cancelar pedidos");
    }

    @Override
    public Order changeStatus(Long id, OrderStatus newStatus, String username) {
        // NUEVA LÓGICA: El barista ya NO controla el estado general de la orden
        // Solo controla el estado de items individuales usando changeItemsStatus()
        // El estado de la orden se calcula automáticamente basado en los items
        throw new UnsupportedOperationException(
            "El barista no puede cambiar el estado general de la orden. " +
            "Use el control de items individuales. El estado de la orden se actualiza automáticamente."
        );
    }

    @Override
    public Order addItemsToExistingOrder(Long orderId, List<OrderDetail> newItems, String username) {
        throw new UnsupportedOperationException("El barista no puede agregar items a pedidos");
    }

    @Override
    public OrderDetail deleteOrderItem(Long orderId, Long orderDetailId, String username) {
        throw new UnsupportedOperationException("El barista no puede eliminar items de pedidos");
    }

    @Override
    public OrderDetailComplement deleteOrderItemComplement(Long orderId, Long itemDetailId, Long complementId, String username) {
        throw new UnsupportedOperationException("El barista no puede eliminar complementos de pedidos");
    }

    @Override
    public void delete(Long id) {
        throw new UnsupportedOperationException("El barista no puede eliminar pedidos");
    }

    @Override
    public Order acceptOrderItems(Long orderId, List<Long> itemDetailIds, String username) {
        throw new UnsupportedOperationException("El barista no puede aceptar items de pedidos");
    }

    @Override
    public Order changeItemsStatus(Long orderId, List<Long> itemDetailIds, OrderStatus newStatus, String username) {
        // Use repository directly to bypass findByIdOrThrow filter
        // This allows us to handle cancelled items specifically instead of getting "Order not found" error
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada con ID: " + orderId));
        
        // MULTI-TENANT: Validate order belongs to current company
        Company currentCompany = CompanyContext.requireCurrentCompany();
        if (!currentCompany.equals(order.getCompany())) {
            throw new IllegalStateException("Orden no pertenece a esta empresa");
        }
        
        // BUG FIX: Prevent modification if order is already cancelled
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("No se puede cambiar el estado de items en una orden CANCELADA.");
        }

        String currentUsername = getCurrentUsername();
        
        log.info("Barista {} changing status of {} items in order {}", 
            currentUsername, itemDetailIds.size(), orderId);
        
        // Validate that barista can change these items
        for (Long itemDetailId : itemDetailIds) {
            OrderDetail detail = order.getOrderDetails().stream()
                .filter(d -> d.getIdOrderDetail().equals(itemDetailId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    "Item detail no encontrado: " + itemDetailId
                ));
            
            // Check if item requires barista preparation
            if (!Boolean.TRUE.equals(detail.getItemMenu().getRequiresBaristaPreparation())) {
                throw new IllegalStateException(
                    "Este item no requiere preparación por el barista: " + 
                    detail.getItemMenu().getName()
                );
            }
            
            // Validate status change for this item
            OrderStatus itemStatus = detail.getItemStatus();
            
            // Checks for items that are already cancelled (deleted)
            if (itemStatus == OrderStatus.CANCELLED) {
                 // Check if ALL items of current barista are cancelled
                 long activeItems = order.getOrderDetails().stream()
                    .filter(d -> Boolean.TRUE.equals(d.getItemMenu().getRequiresBaristaPreparation()))
                    .filter(d -> d.getItemStatus() != OrderStatus.CANCELLED)
                    .count();

                if (activeItems == 0) {
                     // Same message as Chef when all items are done/invalid
                     throw new IllegalStateException("Todos los items del barista ya están listos o cancelados");
                } else {
                     throw new IllegalStateException("Este item ha sido cancelado y ya no se puede modificar");
                }
            }

            if (!(itemStatus == OrderStatus.PENDING && newStatus == OrderStatus.IN_PREPARATION) &&
                !(itemStatus == OrderStatus.IN_PREPARATION && newStatus == OrderStatus.READY)) {
                throw new IllegalStateException(
                    "El barista solo puede cambiar items de PENDIENTE a EN PREPARACIÓN o de EN PREPARACIÓN a LISTO"
                );
            }
        }
        
        // Delegate to admin service to perform the actual change
        Order updatedOrder = adminOrderService.changeItemsStatus(orderId, itemDetailIds, newStatus, username);
        
        // If barista is accepting their first items (PENDING -> IN_PREPARATION) and preparedByBarista is not set
        if (newStatus == OrderStatus.IN_PREPARATION && updatedOrder.getPreparedByBarista() == null) {
            try {
                Employee currentEmployee = employeeService.findByUsername(currentUsername)
                    .orElseThrow(() -> new IllegalStateException(
                        "Empleado no encontrado: " + currentUsername
                    ));
                
                updatedOrder.setPreparedByBarista(currentEmployee);
                updatedOrder = orderRepository.save(updatedOrder);
                
                log.info("☕ Assigned preparedByBarista to {} for order {}", 
                    currentUsername, updatedOrder.getOrderNumber());
                
                // Send WebSocket notification that this order was accepted
                // This will hide it from other baristas
                wsNotificationService.notifyOrderAccepted(updatedOrder, currentUsername, "barista");
                log.info("🔔 WebSocket sent: Order {} accepted by barista {}", 
                    updatedOrder.getOrderNumber(), currentUsername);
                
            } catch (Exception e) {
                log.error("Failed to assign preparedByBarista: {}", e.getMessage(), e);
                // Don't fail the entire operation if this fails
            }
        }
        
        return updatedOrder;
    }

    // ========== Query Operations (filtered for barista) ==========

    @Override
    @Transactional(readOnly = true)
    public List<Order> findAll() {
        // Show only orders with items requiring barista preparation
        // MULTI-TENANT: Filter by current company
        // OPTIMIZED: Use JOIN FETCH query instead of findByCompany + stream filter
        Company company = CompanyContext.requireCurrentCompany();
        List<Order> orders = orderRepository.findOrdersWithBaristaItemsByCompany(company);
        return orders.stream()
            .filter(this::hasItemsRequiringBaristaPreparation)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(Long id) {
        Optional<Order> order = orderRepository.findById(id);
        // MULTI-TENANT: Validate company
        Company currentCompany = CompanyContext.requireCurrentCompany();
        if (order.isPresent() && !currentCompany.equals(order.get().getCompany())) {
            return Optional.empty();
        }
        // Only show if order contains barista items
        if (order.isPresent() && !hasItemsRequiringBaristaPreparation(order.get())) {
            return Optional.empty();
        }
        return order;
    }

    @Override
    @Transactional(readOnly = true)
    public Order findByIdOrThrow(Long id) {
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada con ID: " + id));
        
        // MULTI-TENANT: Validate company
        Company currentCompany = CompanyContext.requireCurrentCompany();
        if (!currentCompany.equals(order.getCompany())) {
            throw new IllegalArgumentException("Orden no encontrada con ID: " + id);
        }
        
        if (!hasItemsRequiringBaristaPreparation(order)) {
            throw new IllegalArgumentException("Esta orden no contiene bebidas para preparar");
        }
        
        return order;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByOrderNumber(String orderNumber) {
        // MULTI-TENANT: Use company-aware method
        Company currentCompany = CompanyContext.requireCurrentCompany();
        Optional<Order> order = orderRepository.findByOrderNumberAndCompany(orderNumber, currentCompany);
        if (order.isPresent() && !hasItemsRequiringBaristaPreparation(order.get())) {
            return Optional.empty();
        }
        return order;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByStatus(OrderStatus status) {
        String currentUsername = getCurrentUsername();
        // MULTI-TENANT: Filter by current company
        Company company = CompanyContext.requireCurrentCompany();
        
        if (status == OrderStatus.PENDING) {
            // Show all PENDING orders with barista items (available to be accepted)
            List<Order> orders = orderRepository.findByCompany(company).stream()
                .filter(o -> o.getStatus() == status)
                .collect(Collectors.toList());
            return orders.stream()
                .filter(this::hasItemsRequiringBaristaPreparation)
                .collect(Collectors.toList());
        } else if (status == OrderStatus.IN_PREPARATION || status == OrderStatus.READY) {
            // Show only orders accepted by THIS barista
            List<Order> orders = orderRepository.findByCompany(company).stream()
                .filter(o -> o.getStatus() == status)
                .collect(Collectors.toList());
            return orders.stream()
                .filter(this::hasItemsRequiringBaristaPreparation)
                .filter(order -> order.getPreparedByBarista() != null && 
                    order.getPreparedByBarista().getUsername().equalsIgnoreCase(currentUsername))
                .collect(Collectors.toList());
        }
        
        // For other statuses, don't show anything to barista
        return List.of();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByTableId(Long tableId) {
        // MULTI-TENANT: Filter results by company
        Company company = CompanyContext.requireCurrentCompany();
        return orderRepository.findByTableId(tableId).stream()
            .filter(order -> company.equals(order.getCompany()))
            .filter(this::hasItemsRequiringBaristaPreparation)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByOrderType(OrderType orderType) {
        // MULTI-TENANT: Filter results by company
        Company company = CompanyContext.requireCurrentCompany();
        return orderRepository.findByOrderType(orderType).stream()
            .filter(order -> company.equals(order.getCompany()))
            .filter(this::hasItemsRequiringBaristaPreparation)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByEmployeeId(Long employeeId) {
        // MULTI-TENANT: Filter results by company
        Company company = CompanyContext.requireCurrentCompany();
        List<Order> orders = orderRepository.findByCompany(company);
        return orders.stream()
            .filter(this::hasItemsRequiringBaristaPreparation)
            .filter(order -> order.getPreparedByBarista() != null && 
                order.getPreparedByBarista().getIdEmpleado().equals(employeeId))
            .collect(Collectors.toList());
    }


    @Transactional(readOnly = true)
    public List<Order> findByCustomerId(Long customerId) {
        // MULTI-TENANT: Filter results by company
        Company company = CompanyContext.requireCurrentCompany();
        return orderRepository.findByCustomerId(customerId).stream()
            .filter(order -> company.equals(order.getCompany()))
            .filter(this::hasItemsRequiringBaristaPreparation)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findActiveOrderByTableId(Long tableId) {
        Optional<Order> order = orderRepository.findActiveOrderByTableId(tableId);
        // MULTI-TENANT: Validate company
        Company company = CompanyContext.requireCurrentCompany();
        if (order.isPresent() && !company.equals(order.get().getCompany())) {
            return Optional.empty();
        }
        return order.filter(this::hasItemsRequiringBaristaPreparation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findActiveOrders() {
        // MULTI-TENANT: Use company-aware method
        Company company = CompanyContext.requireCurrentCompany();
        List<Order> orders = orderRepository.findActiveOrdersByCompany(company);
        return orders.stream()
            .filter(this::hasItemsRequiringBaristaPreparation)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findTodaysOrders() {
        // MULTI-TENANT: Use company-aware method
        Company company = CompanyContext.requireCurrentCompany();
        List<Order> orders = orderRepository.findTodaysOrdersByCompany(company);
        return orders.stream()
            .filter(this::hasItemsRequiringBaristaPreparation)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByIdWithDetails(Long id) {
        Optional<Order> order = orderRepository.findByIdWithDetails(id);
        
        if (order.isEmpty()) {
            return Optional.empty();
        }
        
        // MULTI-TENANT: Validate company
        Company company = CompanyContext.requireCurrentCompany();
        if (!company.equals(order.get().getCompany())) {
            return Optional.empty();
        }
        
        if (!hasItemsRequiringBaristaPreparation(order.get())) {
            return Optional.empty();
        }
        
        return order;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        // MULTI-TENANT: Use company-aware method
        Company company = CompanyContext.requireCurrentCompany();
        List<Order> orders = orderRepository.findByDateRangeAndCompany(company, startDate, endDate);
        return orders.stream()
            .filter(this::hasItemsRequiringBaristaPreparation)
            .collect(Collectors.toList());
    }

   
    @Transactional(readOnly = true)
    public List<Order> findActiveOrdersByTableId(Long tableId) {
        Optional<Order> order = orderRepository.findActiveOrderByTableId(tableId);
        // MULTI-TENANT: Validate company
        Company company = CompanyContext.requireCurrentCompany();
        if (order.isPresent() && !company.equals(order.get().getCompany())) {
            return List.of();
        }
        if (order.isPresent() && hasItemsRequiringBaristaPreparation(order.get())) {
            return List.of(order.get());
        }
        return List.of();
    }

    // ========== Statistics (filtered for barista) ==========

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(OrderStatus status) {
        return findByStatus(status).size();
    }

    @Override
    @Transactional(readOnly = true)
    public long countTodaysOrders() {
        return findTodaysOrders().size();
    }

    @Override
    @Transactional(readOnly = true)
    public long countTodaysOrdersByStatus(OrderStatus status) {
        return findTodaysOrders().stream()
            .filter(order -> order.getStatus() == status)
            .count();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTodaysRevenue() {
        return BigDecimal.ZERO; // Barista doesn't track revenue
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalIncome() {
        return BigDecimal.ZERO; // Barista doesn't track income
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getIncomeByCategory() {
        return Map.of(); // Barista doesn't track income by category
    }

    @Override
    @Transactional(readOnly = true)
    public List<Object[]> getItemSalesByCategory(Long categoryId) {
        return List.of(); // Barista doesn't track item sales
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveOrder(Long tableId) {
        Optional<Order> order = orderRepository.findActiveOrderByTableId(tableId);
        // MULTI-TENANT: Validate company
        Company company = CompanyContext.requireCurrentCompany();
        if (order.isPresent() && !company.equals(order.get().getCompany())) {
            return false;
        }
        return order.filter(this::hasItemsRequiringBaristaPreparation).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isTableAvailableForOrder(Long tableId) {
        return !hasActiveOrder(tableId);
    }

    @Override
    public String generateOrderNumber() {
        return adminOrderService.generateOrderNumber();
    }

    @Override
    public Map<Long, String> validateStock(List<OrderDetail> orderDetails) {
        return adminOrderService.validateStock(orderDetails);
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
