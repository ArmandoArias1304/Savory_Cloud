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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ChefOrderServiceImpl - Implementation for Chef role
 * 
 * Restrictions:
 * - Can only change status from PENDING to IN_PREPARATION
 * - Can only change status from IN_PREPARATION to READY
 * - Can see all PENDING orders to choose which ones to prepare
 * - Can see orders they have accepted (IN_PREPARATION or READY)
 * - Cannot cancel orders
 * - Cannot create new orders
 * - Cannot mark orders as DELIVERED or PAID
 */
@Service("chefOrderService")
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChefOrderServiceImpl implements OrderService {

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

    // ========== CRUD Operations (restricted for chef) ==========

    @Override
    public Order create(Order order, List<OrderDetail> orderDetails) {
        throw new UnsupportedOperationException("El chef no puede crear pedidos");
    }

    @Override
    public Order update(Long id, Order order, List<OrderDetail> orderDetails) {
        throw new UnsupportedOperationException("El chef no puede modificar pedidos");
    }

    @Override
    public Order updateOrderInfo(Long id, Order updatedOrder) {
        throw new UnsupportedOperationException("El chef no puede modificar información de pedidos");
    }

    @Override
    public Order cancel(Long id, String username) {
        throw new UnsupportedOperationException("El chef no puede cancelar pedidos");
    }

    @Override
    public Order changeStatus(Long id, OrderStatus newStatus, String username) {
        // NUEVA LÓGICA: El chef ya NO controla el estado general de la orden
        // Solo controla el estado de items individuales usando changeItemsStatus()
        // El estado de la orden se calcula automáticamente basado en los items
        throw new UnsupportedOperationException(
            "El chef no puede cambiar el estado general de la orden. " +
            "Use el control de items individuales. El estado de la orden se actualiza automáticamente."
        );
    }

    @Override
    public Order addItemsToExistingOrder(Long orderId, List<OrderDetail> newItems, String username) {
        throw new UnsupportedOperationException("El chef no puede agregar items a pedidos");
    }

    @Override
    public Order acceptOrderItems(Long orderId, List<Long> itemDetailIds, String username) {
        throw new UnsupportedOperationException("El chef no puede aceptar items de pedidos");
    }

    @Override
    public Order changeItemsStatus(Long orderId, List<Long> itemDetailIds, OrderStatus newStatus, String username) {
        Order order = findByIdOrThrow(orderId);
        
        // BUG FIX: Prevent modification if order is already cancelled
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("No se puede cambiar el estado de items en una orden CANCELADA.");
        }
        
        String currentUsername = getCurrentUsername();
        
        // Validate that chef can change these items
        for (Long itemDetailId : itemDetailIds) {
            OrderDetail detail = order.getOrderDetails().stream()
                .filter(d -> d.getIdOrderDetail().equals(itemDetailId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    "Item detail no encontrado: " + itemDetailId
                ));
            
            // Check if item is in preparation by another chef
            if (detail.getItemStatus() == OrderStatus.IN_PREPARATION) {
                if (detail.getPreparedBy() != null && 
                    !detail.getPreparedBy().equals(currentUsername)) {
                    throw new IllegalStateException(
                        "Solo el chef que aceptó este item puede cambiar su estado: " + 
                        detail.getItemMenu().getName()
                    );
                }
            }
            
            // Validate status change for this item
            // ALLOWED transitions for chef:
            // 1. PENDING -> IN_PREPARATION (normal acceptance)
            // 2. IN_PREPARATION -> READY (normal completion)
            // 3. PENDING -> READY (skip state when marking all as ready with mixed items)
            OrderStatus itemStatus = detail.getItemStatus();
            boolean isValidTransition = 
                (itemStatus == OrderStatus.PENDING && newStatus == OrderStatus.IN_PREPARATION) ||
                (itemStatus == OrderStatus.IN_PREPARATION && newStatus == OrderStatus.READY) ||
                (itemStatus == OrderStatus.PENDING && newStatus == OrderStatus.READY); // Allow skip
            
            if (!isValidTransition) {
                throw new IllegalStateException(
                    "El chef solo puede cambiar items de PENDIENTE a EN PREPARACIÓN, " +
                    "de EN PREPARACIÓN a LISTO, o de PENDIENTE a LISTO (cuando marca todo como listo)"
                );
            }
        }
        
        log.info("Chef {} changing status of {} items in order {}", 
            currentUsername, itemDetailIds.size(), orderId);
        
        Order updatedOrder = adminOrderService.changeItemsStatus(orderId, itemDetailIds, newStatus, username);

        // If chef is accepting their first items (PENDING -> IN_PREPARATION) and preparedBy is not set
        if (newStatus == OrderStatus.IN_PREPARATION && updatedOrder.getPreparedBy() == null) {
            try {
                Employee currentEmployee = employeeService.findByUsername(currentUsername)
                    .orElseThrow(() -> new IllegalStateException(
                        "Empleado no encontrado: " + currentUsername
                    ));
                
                updatedOrder.setPreparedBy(currentEmployee);
                updatedOrder = orderRepository.save(updatedOrder);
                
                log.info("👨‍🍳 Assigned preparedBy to {} for order {}", 
                    currentUsername, updatedOrder.getOrderNumber());
                
                // Send WebSocket notification that this order was accepted
                // This will hide it from other chefs
                wsNotificationService.notifyOrderAccepted(updatedOrder, currentUsername, "chef");
                log.info("🔔 WebSocket sent: Order {} accepted by chef {}", 
                    updatedOrder.getOrderNumber(), currentUsername);
                
            } catch (Exception e) {
                log.error("Failed to assign preparedBy: {}", e.getMessage(), e);
                // Don't fail the entire operation if this fails
            }
        }

        return updatedOrder;
    }

    /**
     * Change ALL chef items in an order to the next status
     * This is a convenience method for chef to avoid touching the screen many times
     * 
     * NEW Logic:
     * - If ANY item is IN_PREPARATION -> Move ALL chef items to READY (priority action)
     * - Else if all items are PENDING -> Move all to IN_PREPARATION
     * - This allows items to "skip" states when chef marks order as ready
     * 
     * Example: Item A is IN_PREP, Item B (new) is PENDING
     * When chef clicks "Marcar Listo" -> Both A and B go to READY
     * 
     * @param orderId The order ID
     * @param username The chef username
     * @return The updated order
     */
    public Order changeAllChefItemsToNextStatus(Long orderId, String username) {
        Order order = findByIdOrThrow(orderId);
        
        // BUG FIX: Prevent modification if order is already cancelled
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("No se puede cambiar el estado de items en una orden CANCELADA.");
        }
        
        String currentUsername = getCurrentUsername();
        
        // Get all items requiring chef preparation
        List<OrderDetail> chefItems = order.getOrderDetails().stream()
            .filter(detail -> detail.getItemMenu() != null && 
                Boolean.TRUE.equals(detail.getItemMenu().getRequiresPreparation()))
            .collect(Collectors.toList());
        
        if (chefItems.isEmpty()) {
            throw new IllegalStateException("Esta orden no contiene items para el chef");
        }
        
        // Count items in each status
        long pendingCount = chefItems.stream()
            .filter(d -> d.getItemStatus() == OrderStatus.PENDING)
            .count();
        long inPrepCount = chefItems.stream()
            .filter(d -> d.getItemStatus() == OrderStatus.IN_PREPARATION)
            .count();
        
        OrderStatus targetStatus;
        List<Long> itemsToChange = new ArrayList<>();
        
        // PRIORITY 1: If ANY item is IN_PREPARATION, mark ALL actionable chef items as READY
        // This allows new PENDING items to "skip" IN_PREPARATION state.
        // Only PENDING and IN_PREPARATION items are considered: items already in
        // READY, DELIVERED, PAID, CANCELLED or TO_ACCEPT cannot be transitioned by the chef.
        if (inPrepCount > 0) {
            targetStatus = OrderStatus.READY;
            itemsToChange = chefItems.stream()
                .filter(d -> d.getItemStatus() == OrderStatus.PENDING
                    || d.getItemStatus() == OrderStatus.IN_PREPARATION)
                .map(OrderDetail::getIdOrderDetail)
                .collect(Collectors.toList());
            
            log.info("Chef {} marking order {} as ready - moving {} items (PENDING+IN_PREP) to READY", 
                currentUsername, orderId, itemsToChange.size());
        } 
        // PRIORITY 2: If all items are PENDING, move them to IN_PREPARATION
        else if (pendingCount > 0) {
            targetStatus = OrderStatus.IN_PREPARATION;
            itemsToChange = chefItems.stream()
                .filter(d -> d.getItemStatus() == OrderStatus.PENDING)
                .map(OrderDetail::getIdOrderDetail)
                .collect(Collectors.toList());
            
            log.info("Chef {} accepting order {} - moving {} PENDING items to IN_PREPARATION", 
                currentUsername, orderId, itemsToChange.size());
        } 
        // All items already READY
        else {
            throw new IllegalStateException("Todos los items del chef ya están listos");
        }
        
        // Change the items using the existing method
        return changeItemsStatus(orderId, itemsToChange, targetStatus, username);
    }

    @Override
    public void delete(Long id) {
        throw new UnsupportedOperationException("El chef no puede eliminar pedidos");
    }

    // ========== Query Operations (filtered for chef) ==========

    @Override
    public List<Order> findAll() {
        // Chef ONLY sees orders that have at least ONE chef item that is PENDING or IN_PREPARATION
        // If all chef items are READY, the order won't appear
        // This prevents chef from seeing orders when barista items are added but chef items are already done
        Company company = CompanyContext.requireCurrentCompany();
        log.info("🔍 Chef findAll() - Loading orders with PENDING/IN_PREP chef items for company: {}", 
            company.getName());
        
        List<Order> allOrders = orderRepository.findOrdersWithPreparationItemsByCompany(company);
        List<Order> ordersWithPendingChefItems = allOrders.stream()
            .filter(this::hasItemsRequiringPreparation) // Now checks PENDING/IN_PREP status
            .collect(Collectors.toList());
        
        log.info("🔍 Orders visible to chef (with pending items): {}", ordersWithPendingChefItems.size());
        
        return ordersWithPendingChefItems;
    }

    /**
     * Check if an order has at least one item that requires chef preparation
     * AND that item is still PENDING or IN_PREPARATION
     * 
     * @param order The order to check
     * @return true if at least one chef item is PENDING or IN_PREPARATION, false otherwise
     * 
     * IMPORTANT: This now checks both:
     * 1. Item requires chef preparation (requiresPreparation = true)
     * 2. Item status is PENDING or IN_PREPARATION
     * 
     * If all chef items are already READY, the order won't be shown to chef
     */
    private boolean hasItemsRequiringPreparation(Order order) {
        if (order.getOrderDetails() == null || order.getOrderDetails().isEmpty()) {
            log.debug("Order {} has no details", order.getIdOrder());
            return false;
        }
        
        boolean hasPendingChefItems = order.getOrderDetails().stream()
            .anyMatch(detail -> {
                if (detail.getItemMenu() == null) {
                    log.warn("OrderDetail {} has null ItemMenu", detail.getIdOrderDetail());
                    return false;
                }
                
                // Check if item requires chef preparation
                Boolean requiresPrep = detail.getItemMenu().getRequiresPreparation();
                if (!Boolean.TRUE.equals(requiresPrep)) {
                    return false; // Not a chef item
                }
                
                // Check if item is TO_ACCEPT, PENDING or IN_PREPARATION
                // TO_ACCEPT items are shown so chef stays aware of orders that have new items
                // pending validation by cashier/admin, but chef cannot manipulate them.
                OrderStatus itemStatus = detail.getItemStatus();
                boolean isPending = itemStatus == OrderStatus.TO_ACCEPT
                    || itemStatus == OrderStatus.PENDING
                    || itemStatus == OrderStatus.IN_PREPARATION;
                
                log.debug("Order {}, Chef Item '{}': status = {}, isPending = {}", 
                    order.getOrderNumber(), 
                    detail.getItemMenu().getName(),
                    itemStatus,
                    isPending);
                
                return isPending;
            });
        
        log.info("🔍 Order {} hasPendingChefItems: {}", order.getOrderNumber(), hasPendingChefItems);
        return hasPendingChefItems;
    }

    @Override
    public Optional<Order> findById(Long id) {
        // Chef can view any order for history purposes
        // Items will be filtered in the VIEW layer, not here
        return adminOrderService.findById(id);
    }

    @Override
    public Optional<Order> findByIdWithDetails(Long id) {
        // Chef can view any order details for history purposes
        // Items will be filtered in the VIEW layer, not here
        return adminOrderService.findByIdWithDetails(id);
    }

    @Override
    public Optional<Order> findByOrderNumber(String orderNumber) {
        return adminOrderService.findByOrderNumber(orderNumber)
            .filter(order -> 
                order.getStatus() == OrderStatus.PENDING ||
                order.getStatus() == OrderStatus.IN_PREPARATION ||
                order.getStatus() == OrderStatus.READY
            );
    }

    @Override
    public List<Order> findByTableId(Long tableId) {
        // Chef can view all orders by table
        // Items will be filtered in the VIEW layer
        return adminOrderService.findByTableId(tableId).stream()
            .filter(this::hasItemsRequiringPreparation)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<Order> findActiveOrderByTableId(Long tableId) {
        // Chef can view active order by table
        // Items will be filtered in the VIEW layer
        return adminOrderService.findActiveOrderByTableId(tableId)
            .filter(this::hasItemsRequiringPreparation);
    }

    @Override
    public List<Order> findByEmployeeId(Long employeeId) {
        // Chef can view all orders by employee
        // Items will be filtered in the VIEW layer
        return adminOrderService.findByEmployeeId(employeeId).stream()
            .filter(this::hasItemsRequiringPreparation)
            .collect(Collectors.toList());
    }

    @Override
    public List<Order> findByStatus(OrderStatus status) {
        // Chef can view orders in any status
        // Items will be filtered in the VIEW layer
        return adminOrderService.findByStatus(status).stream()
            .filter(this::hasItemsRequiringPreparation)
            .collect(Collectors.toList());
    }

    @Override
    public List<Order> findByOrderType(OrderType orderType) {
        // Chef can view all orders by type
        // Items will be filtered in the VIEW layer
        return adminOrderService.findByOrderType(orderType).stream()
            .filter(this::hasItemsRequiringPreparation)
            .collect(Collectors.toList());
    }

    @Override
    public List<Order> findTodaysOrders() {
        // Chef can view all today's orders
        // Items will be filtered in the VIEW layer
        return adminOrderService.findTodaysOrders().stream()
            .filter(this::hasItemsRequiringPreparation)
            .collect(Collectors.toList());
    }

    @Override
    public List<Order> findActiveOrders() {
        // Chef can view all active orders
        // Items will be filtered in the VIEW layer
        return adminOrderService.findActiveOrders().stream()
            .filter(this::hasItemsRequiringPreparation)
            .collect(Collectors.toList());
    }

    @Override
    public List<Order> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        // Chef can view all orders in date range
        // Items will be filtered in the VIEW layer
        return adminOrderService.findByDateRange(startDate, endDate).stream()
            .filter(this::hasItemsRequiringPreparation)
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

    // ========== Statistics (only for relevant orders) ==========

    @Override
    public long countByStatus(OrderStatus status) {
        if (status != OrderStatus.PENDING && 
            status != OrderStatus.IN_PREPARATION && 
            status != OrderStatus.READY) {
            return 0;
        }
        return adminOrderService.countByStatus(status);
    }

    @Override
    public long countTodaysOrders() {
        return findTodaysOrders().size();
    }

    @Override
    public long countTodaysOrdersByStatus(OrderStatus status) {
        if (status != OrderStatus.PENDING && 
            status != OrderStatus.IN_PREPARATION && 
            status != OrderStatus.READY) {
            return 0;
        }
        return findTodaysOrders().stream()
            .filter(order -> order.getStatus() == status)
            .count();
    }

    @Override
    public BigDecimal getTodaysRevenue() {
        // Chef doesn't need to see revenue
        return BigDecimal.ZERO;
    }

    @Override
    public Order findByIdOrThrow(Long id) {
        return findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado o no accesible"));
    }

    @Override
    public OrderDetail deleteOrderItem(Long orderId, Long itemDetailId, String username) {
        throw new UnsupportedOperationException("El chef no puede eliminar items de pedidos");
    }

    @Override
    public OrderDetailComplement deleteOrderItemComplement(Long orderId, Long itemDetailId, Long complementId, String username) {
        throw new UnsupportedOperationException("El chef no puede eliminar complementos de pedidos");
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
