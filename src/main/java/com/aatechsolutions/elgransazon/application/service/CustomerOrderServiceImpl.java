package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
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
 * CustomerOrderServiceImpl - Implementation for Customer role
 * 
 * Restrictions:
 * - Can only create TAKEOUT and DELIVERY orders (no DINE_IN)
 * - Can only see their own orders
 * - Cannot edit, cancel, or change status of orders
 * - Read-only access to their order history
 */
@Service("customerOrderService")
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CustomerOrderServiceImpl implements OrderService {

    private final OrderServiceImpl adminOrderService; // Delegate to admin service for actual operations
    private final com.aatechsolutions.elgransazon.domain.repository.OrderRepository orderRepository;
    private final com.aatechsolutions.elgransazon.domain.repository.CustomerRepository customerRepository;
    private final DateTimeService dateTimeService;
    private final SystemConfigurationService systemConfigurationService;

    /**
     * Returns true when the company has enabled "require manual acceptance" of customer orders.
     */
    private boolean isCustomerAcceptanceRequired() {
        try {
            SystemConfiguration cfg = systemConfigurationService.getConfiguration();
            return cfg != null && Boolean.TRUE.equals(cfg.getRequireCustomerOrderAcceptance());
        } catch (Exception e) {
            log.warn("Failed to read requireCustomerOrderAcceptance flag: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get current authenticated customer email
     */
    private String getCurrentCustomerEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : null;
    }

    /**
     * Validate if customer can access this order
     */
    private void validateOrderOwnership(Order order) {
        String currentEmail = getCurrentCustomerEmail();
        
        // Check if order belongs to current customer
        if (currentEmail == null || order.getCustomer() == null || 
            !order.getCustomer().getEmail().equalsIgnoreCase(currentEmail)) {
            throw new IllegalStateException("No tiene permisos para acceder a este pedido");
        }
        
        // MULTI-TENANT: Validate order belongs to current company context
        Company currentCompany = CompanyContext.requireCurrentCompany();
        if (order.getCompany() == null || 
            !currentCompany.getIdCompany().equals(order.getCompany().getIdCompany())) {
            throw new IllegalStateException("No tiene permisos para acceder a este pedido");
        }
    }

    // ========== CRUD Operations (with restrictions) ==========

    @Override
    public Order create(Order order, List<OrderDetail> orderDetails) {
        log.info("Customer {} creating new order", getCurrentCustomerEmail());
        
        // Validate order type: customers can only create TAKEOUT and DELIVERY orders
        if (order.getOrderType() == OrderType.DINE_IN) {
            throw new IllegalStateException("Los clientes no pueden crear pedidos para comer aquí (DINE_IN). Solo TAKEOUT o DELIVERY.");
        }
        
        // Ensure table and employee are null (customers don't use tables or employees)
        order.setTable(null);
        order.setEmployee(null);
        
        // Get customer and set it in the order
        String customerEmail = getCurrentCustomerEmail();
        if (customerEmail == null) {
            throw new IllegalStateException("Debe iniciar sesión para crear un pedido");
        }
        
        // MULTI-TENANT: Get company from context first
        Company company = CompanyContext.requireCurrentCompany();
        
        Customer customer = customerRepository.findByEmailIgnoreCaseAndCompany(customerEmail, company)
                .orElseThrow(() -> new IllegalStateException("Cliente no encontrado"));
        
        order.setCustomer(customer);
        
        // Set customer info from Customer entity
        order.setCustomerName(customer.getFullName());
        order.setCustomerPhone(customer.getPhone());
        
        // Set company in order
        order.setCompany(company);
        
        // For DELIVERY orders, validate address is provided
        if (order.getOrderType() == OrderType.DELIVERY) {
            if (order.getDeliveryAddress() == null || order.getDeliveryAddress().trim().isEmpty()) {
                throw new IllegalStateException("Debe seleccionar una dirección de entrega de sus direcciones guardadas");
            }
        }
        
        // Set createdBy to customer email
        order.setCreatedBy(customerEmail);
        
        // Delegate to admin service - choose deferred variant when manual acceptance is enabled
        if (isCustomerAcceptanceRequired()) {
            log.info("requireCustomerOrderAcceptance=TRUE -> creating order in TO_ACCEPT mode (no stock deduction yet)");
            return adminOrderService.createDeferredAcceptance(order, orderDetails);
        }
        return adminOrderService.create(order, orderDetails);
    }

    @Override
    public Order update(Long id, Order order, List<OrderDetail> orderDetails) {
        throw new UnsupportedOperationException("Los clientes no pueden editar pedidos");
    }

    @Override
    public Order updateOrderInfo(Long id, Order updatedOrder) {
        throw new UnsupportedOperationException("Los clientes no pueden editar información de pedidos");
    }

    /**
     * Check if an item can be cancelled/deleted by the customer
     * Rules:
     * - Items requiring preparation (Chef or Barista): only cancellable if PENDING
     * - Items NOT requiring preparation: only cancellable if READY (their auto-assigned state)
     */
    private boolean canItemBeCancelledByCustomer(OrderDetail detail) {
        boolean requiresChef = Boolean.TRUE.equals(detail.getItemMenu().getRequiresPreparation());
        boolean requiresBarista = Boolean.TRUE.equals(detail.getItemMenu().getRequiresBaristaPreparation());
        OrderStatus itemStatus = detail.getItemStatus();

        // TO_ACCEPT items have not been accepted by staff and no stock has been deducted -
        // customers can always cancel them.
        if (itemStatus == OrderStatus.TO_ACCEPT) {
            return true;
        }

        if (requiresChef || requiresBarista) {
            // Items with preparation: only cancellable if PENDING (not yet started)
            return itemStatus == OrderStatus.PENDING;
        } else {
            // Items without preparation: only cancellable if READY (their default state)
            return itemStatus == OrderStatus.READY;
        }
    }
    
    /**
     * Get a descriptive reason why an item cannot be cancelled
     */
    private String getItemCancelBlockReason(OrderDetail detail) {
        boolean requiresChef = Boolean.TRUE.equals(detail.getItemMenu().getRequiresPreparation());
        boolean requiresBarista = Boolean.TRUE.equals(detail.getItemMenu().getRequiresBaristaPreparation());
        OrderStatus itemStatus = detail.getItemStatus();
        String itemName = detail.getItemMenu().getName();
        
        if (requiresChef || requiresBarista) {
            // Items with preparation
            if (itemStatus == OrderStatus.IN_PREPARATION) {
                return String.format("'%s' ya está en preparación", itemName);
            } else if (itemStatus == OrderStatus.READY) {
                return String.format("'%s' ya está listo", itemName);
            } else if (itemStatus == OrderStatus.DELIVERED) {
                return String.format("'%s' ya fue entregado", itemName);
            }
        } else {
            // Items without preparation
            if (itemStatus == OrderStatus.DELIVERED) {
                return String.format("'%s' ya fue entregado", itemName);
            } else if (itemStatus != OrderStatus.READY) {
                return String.format("'%s' no está en estado válido para cancelar", itemName);
            }
        }
        return String.format("'%s' no puede ser cancelado (estado: %s)", itemName, itemStatus.getDisplayName());
    }

    @Override
    public Order cancel(Long id, String username) {
        log.info("Customer {} cancelling order {}", getCurrentCustomerEmail(), id);
        
        // Get the order and validate ownership
        Order order = findByIdOrThrow(id);
        validateOrderOwnership(order);
        
        // Validate order is not already in a final state
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Este pedido ya está cancelado");
        }
        if (order.getStatus() == OrderStatus.PAID) {
            throw new IllegalStateException("No se puede cancelar un pedido que ya fue pagado");
        }
        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw new IllegalStateException("No se puede cancelar un pedido que ya fue entregado");
        }
        if (order.getStatus() == OrderStatus.ON_THE_WAY) {
            throw new IllegalStateException("No se puede cancelar un pedido que está en camino");
        }
        
        // NEW VALIDATION: Check ALL items can be cancelled according to customer rules
        // - Items with preparation (Chef/Barista): must be PENDING
        // - Items without preparation: must be READY
        List<String> blockedReasons = new java.util.ArrayList<>();
        
        for (OrderDetail detail : order.getOrderDetails()) {
            if (!canItemBeCancelledByCustomer(detail)) {
                blockedReasons.add(getItemCancelBlockReason(detail));
            }
        }
        
        if (!blockedReasons.isEmpty()) {
            String reasons = String.join("; ", blockedReasons);
            throw new IllegalStateException(
                "No se puede cancelar el pedido porque: " + reasons + 
                ". Solo puede cancelar cuando los items que requieren preparación estén PENDIENTES " +
                "y los items listos para llevar estén en estado LISTO."
            );
        }
        
        // All items passed validation - delegate to admin service
        return adminOrderService.cancel(id, username);
    }

    @Override
    public Order changeStatus(Long id, OrderStatus newStatus, String username) {
        throw new UnsupportedOperationException("Los clientes no pueden cambiar el estado de los pedidos");
    }

    @Override
    public Order addItemsToExistingOrder(Long orderId, List<OrderDetail> newItems, String username) {
        log.info("Customer {} adding items to order {}", getCurrentCustomerEmail(), orderId);
        
        // Get the order and validate ownership
        Order order = findByIdOrThrow(orderId);
        validateOrderOwnership(order);
        
        // Validate order can accept new items - customers have stricter rules than staff
        // (e.g. TAKEOUT cannot accept new items once DELIVERED on the customer side).
        if (!order.canCustomerAcceptNewItems()) {
            throw new IllegalStateException(
                String.format("No se pueden agregar items a este pedido. Tipo: %s, Estado: %s",
                    order.getOrderType().getDisplayName(),
                    order.getStatus().getDisplayName())
            );
        }
        
        // Delegate to admin service - choose deferred variant when manual acceptance is enabled
        if (isCustomerAcceptanceRequired()) {
            log.info("requireCustomerOrderAcceptance=TRUE -> adding items in TO_ACCEPT mode (no stock deduction yet)");
            return adminOrderService.addItemsToExistingOrderDeferredAcceptance(orderId, newItems, username);
        }
        return adminOrderService.addItemsToExistingOrder(orderId, newItems, username);
    }

    @Override
    public Order changeItemsStatus(Long orderId, List<Long> itemDetailIds, OrderStatus newStatus, String username) {
        throw new UnsupportedOperationException("Los clientes no pueden cambiar el estado de los items");
    }

    @Override
    public Order acceptOrderItems(Long orderId, List<Long> itemDetailIds, String username) {
        throw new UnsupportedOperationException("Los clientes no pueden aceptar items de pedidos");
    }

    @Override
    public OrderDetail deleteOrderItem(Long orderId, Long itemDetailId, String username) {
        log.info("Customer {} deleting item {} from order {}", getCurrentCustomerEmail(), itemDetailId, orderId);
        
        // Get the order and validate ownership
        Order order = findByIdOrThrow(orderId);
        validateOrderOwnership(order);
        
        // Validate order is not in final states
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("No se pueden eliminar items de un pedido cancelado");
        }
        if (order.getStatus() == OrderStatus.PAID) {
            throw new IllegalStateException("No se pueden eliminar items de un pedido pagado");
        }
        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw new IllegalStateException("No se pueden eliminar items de un pedido entregado");
        }
        
        // Validate DELIVERY orders in ON_THE_WAY state
        if (order.getOrderType() == OrderType.DELIVERY && order.getStatus() == OrderStatus.ON_THE_WAY) {
            throw new IllegalStateException("No se pueden eliminar items de un pedido de DELIVERY que está EN CAMINO");
        }
        
        // Find the item to delete
        OrderDetail itemToDelete = order.getOrderDetails().stream()
                .filter(detail -> detail.getIdOrderDetail().equals(itemDetailId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Item no encontrado en el pedido"));
        
        // Validate the item can be deleted according to customer rules
        if (!canItemBeCancelledByCustomer(itemToDelete)) {
            String reason = getItemCancelBlockReason(itemToDelete);
            throw new IllegalStateException("No se puede eliminar el item: " + reason);
        }
        
        // Check if this is the last item - if so, cancel the order instead
        if (order.getOrderDetails().size() == 1) {
            // Signal that this should cancel the order
            throw new IllegalStateException("LAST_ITEM_CANCEL_ORDER");
        }
        
        // Delegate to admin service for the actual deletion
        return adminOrderService.deleteOrderItem(orderId, itemDetailId, username);
    }

    @Override
    public OrderDetailComplement deleteOrderItemComplement(Long orderId, Long itemDetailId, Long complementId, String username) {
        log.info("Customer attempting to delete complement {} from item {} in order {}", complementId, itemDetailId, orderId);
        
        // Find the order and validate ownership
        Order order = findByIdOrThrow(orderId);
        validateOrderOwnership(order);
        
        // Only allow customers to delete complements from their PENDING orders
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException(
                "Solo puedes eliminar complementos de pedidos en estado PENDIENTE. " +
                "Estado actual: " + order.getStatus().getDisplayName()
            );
        }
        
        // Delegate to admin service for the actual deletion
        return adminOrderService.deleteOrderItemComplement(orderId, itemDetailId, complementId, username);
    }

    @Override
    public void delete(Long id) {
        throw new UnsupportedOperationException("Los clientes no pueden eliminar pedidos");
    }

    // ========== Query Operations (filtered by customer) ==========

    @Override
    @Transactional(readOnly = true)
    public List<Order> findAll() {
        String currentEmail = getCurrentCustomerEmail();
        log.debug("Customer {} fetching their orders", currentEmail);
        
        // MULTI-TENANT: Filter by customer email AND company context
        Company company = CompanyContext.requireCurrentCompany();
        return orderRepository.findByCustomerEmailAndCompany(currentEmail, company);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(Long id) {
        Optional<Order> order = adminOrderService.findById(id);
        if (order.isPresent()) {
            try {
                validateOrderOwnership(order.get());
                return order;
            } catch (IllegalStateException e) {
                log.warn("Customer {} tried to access order {} they don't own", getCurrentCustomerEmail(), id);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByIdWithDetails(Long id) {
        Optional<Order> order = adminOrderService.findByIdWithDetails(id);
        if (order.isPresent()) {
            try {
                validateOrderOwnership(order.get());
                return order;
            } catch (IllegalStateException e) {
                log.warn("Customer {} tried to access order {} they don't own", getCurrentCustomerEmail(), id);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public List<Order> findByTableId(Long tableId) {
        // Customers don't use tables
        return List.of();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findActiveOrderByTableId(Long tableId) {
        // Customers don't use tables
        return Optional.empty();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByEmployeeId(Long employeeId) {
        // Customers don't query by employee
        return List.of();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByStatus(OrderStatus status) {
        return findAll().stream()
                .filter(order -> order.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByOrderType(OrderType orderType) {
        return findAll().stream()
                .filter(order -> order.getOrderType() == orderType)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findTodaysOrders() {
        return findAll().stream()
                .filter(order -> order.getCreatedAt().toLocalDate().equals(dateTimeService.todayLocal()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findActiveOrders() {
        return findAll().stream()
                .filter(order -> order.getStatus() != OrderStatus.CANCELLED && 
                               order.getStatus() != OrderStatus.DELIVERED && 
                               order.getStatus() != OrderStatus.PAID)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return findAll().stream()
                .filter(order -> !order.getCreatedAt().isBefore(startDate) && 
                               !order.getCreatedAt().isAfter(endDate))
                .collect(Collectors.toList());
    }

    // ========== Validation Operations (delegate to admin) ==========

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> validateStock(List<OrderDetail> orderDetails) {
        return adminOrderService.validateStock(orderDetails);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveOrder(Long tableId) {
        // Customers don't use tables
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isTableAvailableForOrder(Long tableId) {
        // Customers don't use tables
        return false;
    }

    // ========== Order Number Generation (delegate) ==========

    @Override
    public String generateOrderNumber() {
        return adminOrderService.generateOrderNumber();
    }

    // ========== Statistics (only for customer's orders) ==========

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
        // Customers can't see revenue
        return BigDecimal.ZERO;
    }

    @Override
    @Transactional(readOnly = true)
    public Order findByIdOrThrow(Long id) {
        Order order = adminOrderService.findByIdOrThrow(id);
        validateOrderOwnership(order);
        return order;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalIncome() {
        return adminOrderService.getTotalIncome();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getIncomeByCategory() {
        return adminOrderService.getIncomeByCategory();
    }

    @Override
    @Transactional(readOnly = true)
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
