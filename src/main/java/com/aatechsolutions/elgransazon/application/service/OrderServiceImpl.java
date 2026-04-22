package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.*;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OrderServiceImpl - Business logic implementation for Orders management
 * This is the ADMIN implementation with full access
 * 
 * Handles:
 * - Order creation with stock validation
 * - Table state management (status driven by order lifecycle)
 * - Reservation integration (orders can complete PENDING reservations)
 * - Stock deduction and return
 * - Order status transitions
 * - Order cancellation
 * - Statistics and reports
 * 
 * Table Status Flow:
 * - Create order with AVAILABLE table → Table becomes OCCUPIED
 * - Create order with PENDING reservation → Reservation becomes COMPLETED, Table becomes OCCUPIED
 * - Pay/Cancel order → Table becomes AVAILABLE
 */
@Service("adminOrderService")
@Slf4j
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final ItemMenuRepository itemMenuRepository;
    private final ItemMenuService itemMenuService;
    private final IngredientStockService ingredientStockService;
    private final ComplementStockService complementStockService;
    private final SystemConfigurationService systemConfigurationService;
    private final RestaurantTableService restaurantTableService;
    private final WebSocketNotificationService wsNotificationService;
    private final EmployeeMonthlyStatsService monthlyStatsService;
    private final DailyOrderCounterService dailyOrderCounterService;
    private final OrderDetailComplementRepository orderDetailComplementRepository;
    private final ComplementRepository complementRepository;
    private final ReservationService reservationService;
    private final DateTimeService dateTimeService;

    // Injected by Spring after construction — used to refresh stale in-session entities
    // after REQUIRES_NEW sub-transactions update ingredient stock in isolated transactions.
    @PersistenceContext
    private EntityManager entityManager;

    // Constructor with @Lazy for ReservationService to break circular dependency
    public OrderServiceImpl(
            OrderRepository orderRepository,
            OrderDetailRepository orderDetailRepository,
            RestaurantTableRepository restaurantTableRepository,
            ItemMenuRepository itemMenuRepository,
            @Lazy ItemMenuService itemMenuService,
            IngredientStockService ingredientStockService,
            ComplementStockService complementStockService,
            @Lazy SystemConfigurationService systemConfigurationService,
            RestaurantTableService restaurantTableService,
            WebSocketNotificationService wsNotificationService,
            EmployeeMonthlyStatsService monthlyStatsService,
            DailyOrderCounterRepository dailyOrderCounterRepository,
            DailyOrderCounterService dailyOrderCounterService,
            OrderDetailComplementRepository orderDetailComplementRepository,
            ComplementRepository complementRepository,
            @Lazy ReservationService reservationService,
            DateTimeService dateTimeService) {
        this.orderRepository = orderRepository;
        this.orderDetailRepository = orderDetailRepository;
        this.restaurantTableRepository = restaurantTableRepository;
        this.itemMenuRepository = itemMenuRepository;
        this.itemMenuService = itemMenuService;
        this.ingredientStockService = ingredientStockService;
        this.complementStockService = complementStockService;
        this.systemConfigurationService = systemConfigurationService;
        this.restaurantTableService = restaurantTableService;
        this.wsNotificationService = wsNotificationService;
        this.monthlyStatsService = monthlyStatsService;
        this.dailyOrderCounterService = dailyOrderCounterService;
        this.orderDetailComplementRepository = orderDetailComplementRepository;
        this.complementRepository = complementRepository;
        this.reservationService = reservationService;
        this.dateTimeService = dateTimeService;
    }

    @Override
    public Order create(Order order, List<OrderDetail> orderDetails) {
        log.info("Creating new order - Type: {}, Table: {}", 
                 order.getOrderType(), 
                 order.getTable() != null ? order.getTable().getTableNumber() : "N/A");
        
        // 0. Set company from context (multi-tenant)
        Company company = CompanyContext.requireCurrentCompany();
        order.setCompany(company);
        log.debug("Order assigned to company: {} (ID: {})", company.getName(), company.getIdCompany());
        
        // 1. Validate table requirement based on order type
        validateTableRequirement(order);
        
        // 2. Validate table availability (only if table is provided)
        RestaurantTable table = order.getTable();
        if (table != null) {
            // Use RestaurantTableService to check if table can be used
            // This considers: table status (AVAILABLE only), reservation blocking, and active orders
            if (!restaurantTableService.canTableBeUsedForOrder(table.getId())) {
                throw new IllegalStateException(
                    String.format("La mesa #%d no está disponible, está bloqueada por una reservación próxima, o ya tiene un pedido activo", 
                                  table.getTableNumber())
                );
            }
        }

        // 3. Validate customer information based on order type
        validateCustomerInformation(order);

        // 4. Validate payment method is enabled (considering order type for DELIVERY)
        validatePaymentMethod(order.getPaymentMethod(), order.getOrderType());

        // 5. Validate items are active
        validateItemsActive(orderDetails);

        // 6. Validate stock availability for all items (optimistic pre-check, no lock).
        // This catches obvious stock shortfalls cheaply before we generate an order number
        // or acquire any locks.  Under concurrency a race may allow this check to pass for
        // two threads at the same time; the authoritative check happens inside the
        // REQUIRES_NEW+PESSIMISTIC_WRITE lock in deductStockForItem / deductStockForComplements.
        Map<Long, String> stockErrors = validateStock(orderDetails);
        if (!stockErrors.isEmpty()) {
            throw new IllegalStateException(
                "¡Lo sentimos! No tenemos suficiente stock de los siguientes items: " + 
                String.join(", ", stockErrors.values()) + 
                ". ¡Te invitamos a seguir descubriendo las deliciosas opciones de nuestro menú!"
            );
        }
        
        // 7. Validate item availability schedule (day and time) - AFTER stock validation
        validateItemsAvailability(orderDetails);

        // 6. Generate unique order number
        String orderNumber = generateOrderNumber();
        order.setOrderNumber(orderNumber);

        // 7. Get tax rate from system configuration
        BigDecimal taxRate = getTaxRate();
        order.setTaxRate(taxRate);

        // Normalize deliveryCost: only meaningful for DELIVERY; force ZERO otherwise.
        if (order.getOrderType() != OrderType.DELIVERY || order.getDeliveryCost() == null) {
            order.setDeliveryCost(order.getOrderType() == OrderType.DELIVERY && order.getDeliveryCost() != null
                    ? order.getDeliveryCost()
                    : BigDecimal.ZERO);
        }

        // 8. Process order details and deduct stock
        for (OrderDetail detail : orderDetails) {
            ItemMenu item = itemMenuRepository.findById(detail.getItemMenu().getIdItemMenu())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Item de menú no encontrado: " + detail.getItemMenu().getIdItemMenu()
                ));

            // Set current price if not set
            if (detail.getUnitPrice() == null) {
                detail.setUnitPrice(item.getPrice());
            }

            // Calculate subtotal
            detail.calculateSubtotal();

            // Initialize item status - respect pre-set status for combo items
            if (detail.getItemStatus() == null) {
                detail.setItemStatus(OrderStatus.PENDING);
            }
            detail.setIsNewItem(false); // Initial items are not "new"
            detail.setAddedAt(LocalDateTime.now());
            
            // Auto-advance to READY ONLY if item requires NO preparation at all (neither chef nor barista)
            // Items requiring barista preparation MUST start as PENDING and be changed manually
            // Skip auto-advance for items that already have a status set (e.g., combo items)
            boolean requiresChefPreparation = Boolean.TRUE.equals(item.getRequiresPreparation());
            boolean requiresBaristaPreparation = Boolean.TRUE.equals(item.getRequiresBaristaPreparation());
            boolean isComboParent = Boolean.TRUE.equals(item.getIsCombo());
            
            if (isComboParent) {
                // Combo parent always stays READY - it's just a price container
                detail.setItemStatus(OrderStatus.READY);
                log.info("Combo parent '{}' set to READY (no preparation needed, children handle preparation)", item.getName());
            } else if (!requiresChefPreparation && !requiresBaristaPreparation) {
                // Item requires NO preparation (e.g., bottled drinks, pre-packaged items)
                detail.setItemStatus(OrderStatus.READY);
                log.info("Item '{}' auto-advanced to READY (no preparation required)", item.getName());
            } else {
                // Item requires preparation (chef or barista), starts as PENDING
                log.info("Item '{}' set to PENDING (requires {} preparation)", 
                    item.getName(), 
                    requiresBaristaPreparation ? "barista" : "chef");
            }

            // Deduct stock from ingredients (authoritative: runs under PESSIMISTIC_WRITE lock in REQUIRES_NEW)
            try {
                deductStockForItem(item, detail.getQuantity());
            } catch (IllegalStateException ex) {
                // Stock ran out between the pre-check and the lock; surface as user-friendly message
                if (ex.getMessage() != null && ex.getMessage().startsWith("Stock insuficiente")) {
                    throw new IllegalStateException(
                        "¡Lo sentimos! No tenemos suficiente stock de los siguientes items: " +
                        item.getName() +
                        ". ¡Te invitamos a seguir descubriendo las deliciosas opciones de nuestro menú!", ex);
                }
                throw ex;
            }
            
            // Deduct stock for selected complements
            deductStockForComplements(detail);

            // Refresh the item entity so updateAvailability() reads current ingredient
            // stock from DB rather than stale values from the outer session's 1st-level
            // cache (which pre-dates the REQUIRES_NEW sub-transactions that just ran).
            entityManager.refresh(item);
            item.updateAvailability();
            itemMenuRepository.save(item);

            // Add to order
            order.addOrderDetail(detail);
        }

        // 9. Calculate order totals
        order.recalculateAmounts();

        // 10. Occupy table and handle reservation if applicable (only for DINE_IN)
        if (table != null && order.getOrderType() == OrderType.DINE_IN) {
            // Table must be AVAILABLE at this point (validated in step 2)
            // Change table status to OCCUPIED
            table.setStatus(TableStatus.OCCUPIED);
            table.setUpdatedBy(order.getCreatedBy());
            restaurantTableRepository.save(table);
            log.info("Table #{} changed to OCCUPIED", table.getTableNumber());
            
            // Check if there's a PENDING reservation for this table that should be completed
            // If the order has a reservationId, complete that specific reservation
            // Otherwise, check if there's an active PENDING reservation due now
            Long reservationIdToComplete = order.getReservationId();
            if (reservationIdToComplete != null) {
                // Complete the specific reservation linked to this order
                try {
                    reservationService.markAsCompleted(reservationIdToComplete, order.getCreatedBy());
                    log.info("Reservation #{} marked as COMPLETED (linked to order)", reservationIdToComplete);
                } catch (Exception e) {
                    log.warn("Could not complete reservation #{}: {}", reservationIdToComplete, e.getMessage());
                }
            } else {
                // Check if there's a PENDING reservation that is due now (time has arrived)
                reservationService.findFirstActivePendingReservationForTable(table.getId())
                    .ifPresent(reservation -> {
                        try {
                            reservationService.markAsCompleted(reservation.getId(), order.getCreatedBy());
                            order.setReservationId(reservation.getId()); // Link the reservation to the order
                            log.info("Reservation #{} auto-completed (customer arrived at reserved time)", 
                                     reservation.getId());
                        } catch (Exception e) {
                            log.warn("Could not auto-complete reservation #{}: {}", 
                                     reservation.getId(), e.getMessage());
                        }
                    });
            }
        }

        // 11. Save order
        Order savedOrder = orderRepository.save(order);
        log.info("Order created successfully: {} (Type: {}) with total: ${}", 
                 savedOrder.getOrderNumber(), 
                 savedOrder.getOrderType().getDisplayName(),
                 savedOrder.getTotal());

        // 12. Send WebSocket notification for new order
        try {
            wsNotificationService.notifyNewOrder(savedOrder);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for new order: {}", savedOrder.getOrderNumber(), e);
        }

        // 13. Auto-advance to READY if ALL items don't require preparation
        autoAdvanceOrderIfNoPreparationNeeded(savedOrder);

        return savedOrder;
    }

    @Override
    public Order update(Long id, Order updatedOrder, List<OrderDetail> newOrderDetails) {
        log.info("Updating order with ID: {}", id);

        Order existingOrder = findByIdOrThrow(id);

        // Cannot update PAID or CANCELLED orders
        if (existingOrder.getStatus() == OrderStatus.PAID || existingOrder.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException(
                "No se pueden modificar pedidos PAGADOS o CANCELADOS. Estado actual: " + 
                existingOrder.getStatus().getDisplayName()
            );
        }

        // Store old table reference before updating
        RestaurantTable oldTable = existingOrder.getTable();
        RestaurantTable newTable = updatedOrder.getTable();
        OrderType oldOrderType = existingOrder.getOrderType();
        OrderType newOrderType = updatedOrder.getOrderType();

        // Validate table requirement for the new order type
        validateTableRequirement(updatedOrder);

        // Validate new table availability if table is changing
        if (newTable != null) {
            // If table is different from current, validate new table availability
            if (oldTable == null || !oldTable.getId().equals(newTable.getId())) {
                if (!restaurantTableService.canTableBeUsedForOrder(newTable.getId())) {
                    throw new IllegalStateException(
                        String.format("La mesa #%d no está disponible, está bloqueada por una reservación próxima, o ya tiene un pedido activo", 
                                      newTable.getTableNumber())
                    );
                }
            }
        }

        // Validate customer information based on order type
        validateCustomerInformation(updatedOrder);

        // Capture existing state to preserve status and preparer info
        Map<Long, Queue<OrderDetail>> previousStateMap = new HashMap<>();
        for (OrderDetail detail : existingOrder.getOrderDetails()) {
            Long itemId = detail.getItemMenu().getIdItemMenu();
            previousStateMap.putIfAbsent(itemId, new LinkedList<>());
            previousStateMap.get(itemId).add(detail);
        }

        // Return stock for old items
        returnStockForOrder(existingOrder);

        // Validate items are active
        validateItemsActive(newOrderDetails);

        // Validate stock for new items (FIRST PRIORITY - material constraint)
        Map<Long, String> stockErrors = validateStock(newOrderDetails);
        if (!stockErrors.isEmpty()) {
            throw new IllegalStateException(
                "¡Lo sentimos! No tenemos suficiente stock de los siguientes items: " + 
                String.join(", ", stockErrors.values()) + 
                ". ¡Te invitamos a seguir descubriendo las deliciosas opciones de nuestro menú!"
            );
        }
        
        // Validate item availability schedule - AFTER stock validation
        validateItemsAvailability(newOrderDetails);

        // Clear existing details
        orderDetailRepository.deleteByOrder(existingOrder);
        existingOrder.getOrderDetails().clear();

        // Add new details and deduct stock
        for (OrderDetail newDetail : newOrderDetails) {
            ItemMenu item = itemMenuRepository.findById(newDetail.getItemMenu().getIdItemMenu())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Item de menú no encontrado: " + newDetail.getItemMenu().getIdItemMenu()
                ));

            // Set current price
            newDetail.setUnitPrice(item.getPrice());
            newDetail.calculateSubtotal();

            // Restore state if match found
            Long itemId = item.getIdItemMenu();
            if (previousStateMap.containsKey(itemId) && !previousStateMap.get(itemId).isEmpty()) {
                OrderDetail oldDetail = previousStateMap.get(itemId).poll();
                
                // Restore status and responsible info
                newDetail.setItemStatus(oldDetail.getItemStatus());
                newDetail.setPreparedBy(oldDetail.getPreparedBy());
                newDetail.setIsNewItem(oldDetail.getIsNewItem());
                newDetail.setAddedAt(oldDetail.getAddedAt());
                
                log.info("Restored state for item '{}': status={}, preparedBy={}", 
                        item.getName(), oldDetail.getItemStatus(), oldDetail.getPreparedBy());
            } else {
                // Initialize as new/pending if no match found
                newDetail.setItemStatus(OrderStatus.PENDING);
                
                // Auto-advance logic (same as create)
                boolean requiresChefPreparation = Boolean.TRUE.equals(item.getRequiresPreparation());
                boolean requiresBaristaPreparation = Boolean.TRUE.equals(item.getRequiresBaristaPreparation());
                
                if (!requiresChefPreparation && !requiresBaristaPreparation) {
                    newDetail.setItemStatus(OrderStatus.READY);
                }
            }

            // Deduct stock
            deductStockForItem(item, newDetail.getQuantity());
            
            // Deduct stock for complements
            deductStockForComplements(newDetail);

            // Update item availability
            item.updateAvailability();
            itemMenuRepository.save(item);

            // Add to order
            existingOrder.addOrderDetail(newDetail);
        }

        // Update basic fields
        existingOrder.setOrderType(newOrderType);
        existingOrder.setCustomerName(updatedOrder.getCustomerName());
        existingOrder.setCustomerPhone(updatedOrder.getCustomerPhone());
        existingOrder.setDeliveryAddress(updatedOrder.getDeliveryAddress());
        existingOrder.setDeliveryReferences(updatedOrder.getDeliveryReferences());
        existingOrder.setPaymentMethod(updatedOrder.getPaymentMethod());

        // Delivery cost: clear when leaving DELIVERY; otherwise apply incoming value (default 0).
        if (newOrderType != OrderType.DELIVERY) {
            existingOrder.setDeliveryCost(BigDecimal.ZERO);
        } else {
            existingOrder.setDeliveryCost(updatedOrder.getDeliveryCost() != null
                    ? updatedOrder.getDeliveryCost()
                    : BigDecimal.ZERO);
        }

        // Handle table changes
        handleTableChange(existingOrder, oldTable, newTable, oldOrderType, newOrderType, 
                         updatedOrder.getUpdatedBy());

        // Recalculate totals
        existingOrder.recalculateAmounts();

        // Save updated order
        Order savedOrder = orderRepository.save(existingOrder);
        log.info("Order updated successfully: {}", savedOrder.getOrderNumber());

        return savedOrder;
    }

    @Override
    public Order updateOrderInfo(Long id, Order updatedOrder) {
        log.info("Updating order INFO (without items) for ID: {}", id);

        Order existingOrder = findByIdOrThrow(id);

        // Cannot update PAID or CANCELLED orders
        if (existingOrder.getStatus() == OrderStatus.PAID || existingOrder.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException(
                "No se pueden modificar pedidos PAGADOS o CANCELADOS. Estado actual: " + 
                existingOrder.getStatus().getDisplayName()
            );
        }

        // Store old references for table handling
        RestaurantTable oldTable = existingOrder.getTable();
        RestaurantTable newTable = updatedOrder.getTable();
        OrderType oldOrderType = existingOrder.getOrderType();
        OrderType newOrderType = updatedOrder.getOrderType();

        // Validate table requirement for the new order type
        validateTableRequirement(updatedOrder);

        // Validate new table availability if table is changing
        if (newTable != null) {
            if (oldTable == null || !oldTable.getId().equals(newTable.getId())) {
                if (!restaurantTableService.canTableBeUsedForOrder(newTable.getId())) {
                    throw new IllegalStateException(
                        String.format("La mesa #%d no está disponible, está bloqueada por una reservación próxima, o ya tiene un pedido activo", 
                                      newTable.getTableNumber())
                    );
                }
            }
        }

        // Validate customer information based on order type
        validateCustomerInformation(updatedOrder);

        // Update basic fields ONLY (NO items, NO stock manipulation)
        existingOrder.setOrderType(newOrderType);
        existingOrder.setCustomerName(updatedOrder.getCustomerName());
        existingOrder.setCustomerPhone(updatedOrder.getCustomerPhone());
        existingOrder.setDeliveryAddress(updatedOrder.getDeliveryAddress());
        existingOrder.setDeliveryReferences(updatedOrder.getDeliveryReferences());
        existingOrder.setDeliveryLatitude(updatedOrder.getDeliveryLatitude());
        existingOrder.setDeliveryLongitude(updatedOrder.getDeliveryLongitude());
        existingOrder.setPaymentMethod(updatedOrder.getPaymentMethod());
        existingOrder.setUpdatedBy(updatedOrder.getUpdatedBy());

        // Delivery cost: clear when leaving DELIVERY; otherwise apply incoming value (default 0).
        if (newOrderType != OrderType.DELIVERY) {
            existingOrder.setDeliveryCost(BigDecimal.ZERO);
        } else {
            existingOrder.setDeliveryCost(updatedOrder.getDeliveryCost() != null
                    ? updatedOrder.getDeliveryCost()
                    : BigDecimal.ZERO);
        }

        // Handle table changes (release old table, assign new table)
        handleTableChange(existingOrder, oldTable, newTable, oldOrderType, newOrderType, 
                         updatedOrder.getUpdatedBy());

        // Recalculate totals so deliveryCost / orderType change is reflected in subtotal/tax/total
        existingOrder.recalculateAmounts();

        // Save updated order
        Order savedOrder = orderRepository.save(existingOrder);
        log.info("Order INFO updated successfully (no items changed): {}", savedOrder.getOrderNumber());

        return savedOrder;
    }

    @Override
    public Order cancel(Long id, String cancelledBy) {
        log.info("Cancelling order with ID: {}", id);

        Order order = findByIdOrThrow(id);

        // Check if order can be cancelled
        if (!order.getStatus().canBeCancelled()) {
            throw new IllegalStateException(
                String.format("No se puede cancelar un pedido con estado: %s. " +
                             "Solo se pueden cancelar pedidos en estados: PENDING, IN_PREPARATION, READY",
                              order.getStatus().getDisplayName())
            );
        }

        // IMPORTANT: Check if any items have been DELIVERED
        // Even if the order status is PENDING (due to new items added), 
        // we cannot cancel if some items were already delivered
        boolean hasDeliveredItems = order.getOrderDetails().stream()
            .anyMatch(detail -> detail.getItemStatus() == OrderStatus.DELIVERED);
        
        if (hasDeliveredItems) {
            throw new IllegalStateException(
                "No se puede cancelar este pedido porque ya tiene items que fueron entregados. " +
                "Si desea cancelar los items pendientes, debe hacerlo individualmente."
            );
        }

        OrderStatus currentStatus = order.getStatus();
        
        // Analyze items individually to determine stock return strategy
        List<OrderDetail> itemsToReturnAutomatically = new ArrayList<>();
        List<OrderDetail> itemsToReturnManually = new ArrayList<>();
        
        for (OrderDetail detail : order.getOrderDetails()) {
            if (shouldReturnStockAutomatically(detail)) {
                itemsToReturnAutomatically.add(detail);
            } else {
                itemsToReturnManually.add(detail);
            }
        }
        
        // Return stock automatically for eligible items (including complements)
        if (!itemsToReturnAutomatically.isEmpty()) {
            for (OrderDetail detail : itemsToReturnAutomatically) {
                ItemMenu item = detail.getItemMenu();
                returnStockForItem(item, detail.getQuantity());
                // Also return complement stock for items that qualify for automatic return
                returnStockForComplements(detail);
            }
            log.info("Stock returned automatically for {} items (including complements) in order: {}", 
                     itemsToReturnAutomatically.size(), order.getOrderNumber());
        }
        
        // Log items that need manual stock return
        if (!itemsToReturnManually.isEmpty()) {
            String manualItems = itemsToReturnManually.stream()
                .map(d -> d.getItemMenu().getName())
                .collect(java.util.stream.Collectors.joining(", "));
            log.warn("Order {} has {} items that need MANUAL stock return (complements included): {}", 
                     order.getOrderNumber(), itemsToReturnManually.size(), manualItems);
        }

        // Update order status to CANCELLED
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedBy(cancelledBy);
        order.setUpdatedAt(java.time.LocalDateTime.now());

        // Free table if applicable (DINE_IN only)
        if (order.getOrderType() == OrderType.DINE_IN) {
            RestaurantTable table = order.getTable();
            if (table != null && table.getStatus() == TableStatus.OCCUPIED) {
                // Change table status back to AVAILABLE
                table.setStatus(TableStatus.AVAILABLE);
                table.setUpdatedBy(cancelledBy);
                restaurantTableRepository.save(table);
                log.info("Table #{} freed and marked as AVAILABLE after order cancellation", 
                         table.getTableNumber());
            }
        }

        Order cancelledOrder = orderRepository.save(order);
        log.info("Order cancelled successfully: {} (was in {} status)", 
                 cancelledOrder.getOrderNumber(), currentStatus.getDisplayName());
        
        // Send WebSocket notification for order cancellation
        // Use notifyOrderCancelled to send ORDER_CANCELLED notification to all relevant roles
        try {
            wsNotificationService.notifyOrderCancelled(cancelledOrder);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for order cancellation: {}", 
                cancelledOrder.getOrderNumber(), e);
        }
        
        return cancelledOrder;
    }

    @Override
    public Order changeStatus(Long id, OrderStatus newStatus, String updatedBy) {
        log.info("Changing order status. ID: {}, New Status: {}", id, newStatus);

        Order order = findByIdOrThrow(id);
        OrderStatus oldStatus = order.getStatus();
        OrderType orderType = order.getOrderType();

        // Validate status transition
        if (oldStatus == OrderStatus.CANCELLED) {
            throw new IllegalStateException("No se puede cambiar el estado de un pedido cancelado");
        }

        if (oldStatus == OrderStatus.PAID) {
            throw new IllegalStateException("No se puede cambiar el estado de un pedido ya pagado");
        }

        // Validate ON_THE_WAY is only for DELIVERY orders
        if (newStatus == OrderStatus.ON_THE_WAY && orderType != OrderType.DELIVERY) {
            throw new IllegalStateException(
                "El estado 'EN CAMINO' solo es válido para pedidos de tipo ENTREGA A DOMICILIO"
            );
        }

        // Validate transition is allowed
        if (!OrderStatus.isValidTransition(oldStatus, newStatus, orderType)) {
            throw new IllegalStateException(
                String.format("Transición de estado inválida: %s -> %s para pedido tipo %s",
                    oldStatus.getDisplayName(),
                    newStatus.getDisplayName(),
                    orderType.getDisplayName())
            );
        }

        // Update status
        order.setStatus(newStatus);
        order.setUpdatedBy(updatedBy);
        order.setUpdatedAt(LocalDateTime.now()); // Explicitly set updatedAt

        // Track timestamp when order is marked as READY
        if (newStatus == OrderStatus.READY) {
            order.setPreparedAt(LocalDateTime.now());
        }

        // Track timestamp when order is marked as DELIVERED
        if (newStatus == OrderStatus.DELIVERED) {
            order.setDeliveredAt(LocalDateTime.now());
        }

        // NUEVA LÓGICA: Ya NO actualizamos items automáticamente cuando cambia el estado de la orden
        // Los items solo se actualizan mediante changeItemsStatus() por chef/barista
        // El estado de la orden se recalcula automáticamente en changeItemsStatus()
        
        // EXCEPCIÓN: Solo actualizamos items SIN preparación específica
        if (newStatus == OrderStatus.IN_PREPARATION || 
            newStatus == OrderStatus.READY || 
            newStatus == OrderStatus.DELIVERED) {
            
            for (OrderDetail detail : order.getOrderDetails()) {
                ItemMenu itemMenu = detail.getItemMenu();
                
                // Check if item requires specific preparation by chef or barista
                boolean requiresChefPreparation = itemMenu != null && 
                    Boolean.TRUE.equals(itemMenu.getRequiresPreparation());
                boolean requiresBaristaPreparation = itemMenu != null && 
                    Boolean.TRUE.equals(itemMenu.getRequiresBaristaPreparation());
                
                // ONLY update items that DON'T require ANY specific preparation
                // UNLESS newStatus is DELIVERED, then we force update all items to DELIVERED
                boolean isDeliveredUpdate = (newStatus == OrderStatus.DELIVERED);

                if ((!requiresChefPreparation && !requiresBaristaPreparation) || isDeliveredUpdate) {
                    // Item doesn't require specific preparation - auto-advance with order
                    if (detail.getItemStatus() == null || 
                        detail.getItemStatus().ordinal() < newStatus.ordinal()) {
                        detail.setItemStatus(newStatus);
                        
                        // Remove "New" badge when delivered
                        if (isDeliveredUpdate) {
                            detail.setIsNewItem(false);
                        }
                        
                        log.debug("Item '{}' auto-advanced to {} (forced={})", 
                            itemMenu != null ? itemMenu.getName() : "unknown", newStatus, isDeliveredUpdate);
                    }
                }
            }
        }

        // NOTE: preparedBy and paidBy should be set in the controller BEFORE calling this method
        // This is just a fallback in case they weren't set
        
        // Track who prepared the order (when status changes to READY) - Fallback only
        if (newStatus == OrderStatus.READY && order.getPreparedBy() == null) {
            // This shouldn't happen if controller set it properly when changing to IN_PREPARATION
            log.warn("Order {} marked as READY but preparedBy is null - using fallback", order.getOrderNumber());
            order.setPreparedBy(order.getEmployee()); // Fallback to order creator
        }

        // Track who collected payment (when status changes to PAID) - Fallback only
        if (newStatus == OrderStatus.PAID && order.getPaidBy() == null) {
            // This shouldn't happen if controller set it properly
            log.warn("Order {} marked as PAID but paidBy is null - using fallback", order.getOrderNumber());
            order.setPaidBy(order.getEmployee()); // Fallback to order creator - FIXED: was setPreparedBy
        }

        // Track timestamp when order is marked as PAID (only set once; never overwritten by later updates).
        // This is the authoritative "paidAt" used by all revenue/sales/CFDI reports.
        if (newStatus == OrderStatus.PAID && order.getPaidAt() == null) {
            order.setPaidAt(LocalDateTime.now());
        }

        // If order is marked as PAID, free the table
        // NOTE: Table is NOT freed when DELIVERED - only when PAID
        if (newStatus == OrderStatus.PAID && orderType == OrderType.DINE_IN) {
            RestaurantTable table = order.getTable();
            if (table != null && table.getStatus() == TableStatus.OCCUPIED) {
                // Change table status back to AVAILABLE
                table.setStatus(TableStatus.AVAILABLE);
                table.setUpdatedBy(updatedBy);
                restaurantTableRepository.save(table);
                log.info("Table #{} freed and marked as AVAILABLE after order payment", 
                         table.getTableNumber());
            }
        }

        // If order is marked as PAID, update employee monthly statistics
        if (newStatus == OrderStatus.PAID) {
            try {
                // Use paidAt (authoritative payment timestamp) so monthly stats are bucketed correctly
                // even when the order was created in a different month than the one it was paid in.
                LocalDateTime paidDate = order.getPaidAt() != null ? order.getPaidAt() : LocalDateTime.now();
                Integer month = paidDate.getMonthValue();
                Integer year = paidDate.getYear();
                
                // Update waiter statistics (sales)
                if (order.getEmployee() != null && order.getEmployee().hasRole(Role.WAITER)) {
                    BigDecimal salesAmount = order.getTotal(); // Total without tip
                    monthlyStatsService.updateWaiterSales(order.getEmployee(), salesAmount, month, year);
                    log.info("Updated waiter {} monthly sales: +${} for {}/{}", 
                            order.getEmployee().getUsername(), salesAmount, month, year);
                }
                
                // Update chef statistics (orders count)
                if (order.getPreparedBy() != null && order.getPreparedBy().hasRole(Role.CHEF)) {
                    monthlyStatsService.updateChefOrders(order.getPreparedBy(), month, year);
                    log.info("Updated chef {} monthly orders count for {}/{}", 
                            order.getPreparedBy().getUsername(), month, year);
                }
                
                // Update barista statistics (orders count)
                if (order.getPreparedByBarista() != null && order.getPreparedByBarista().hasRole(Role.BARISTA)) {
                    monthlyStatsService.updateBaristaOrders(order.getPreparedByBarista(), month, year);
                    log.info("Updated barista {} monthly orders count for {}/{}", 
                            order.getPreparedByBarista().getUsername(), month, year);
                }
                
                // Update cashier statistics (sales) - track who collected the payment
                if (order.getPaidBy() != null && order.getPaidBy().hasRole(Role.CASHIER)) {
                    BigDecimal cashierSalesAmount = order.getTotal(); // Total without tip
                    monthlyStatsService.updateCashierSales(order.getPaidBy(), cashierSalesAmount, month, year);
                    log.info("Updated cashier {} monthly sales: +${} for {}/{}", 
                            order.getPaidBy().getUsername(), cashierSalesAmount, month, year);
                }
            } catch (Exception e) {
                // Don't fail the order if stats update fails
                log.error("Failed to update employee monthly statistics for order {}: {}", 
                         order.getOrderNumber(), e.getMessage(), e);
            }
        }

        Order savedOrder = orderRepository.save(order);
        log.info("Order status changed: {} -> {}", oldStatus, newStatus);

        // Send WebSocket notification for status change
        try {
            String statusMessage = String.format("Estado cambiado: %s → %s", 
                oldStatus.getDisplayName(), newStatus.getDisplayName());
            wsNotificationService.notifyOrderStatusChange(savedOrder, statusMessage);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for status change: {}", savedOrder.getOrderNumber(), e);
        }

        return savedOrder;
    }

    @Override
    public Order addItemsToExistingOrder(Long orderId, List<OrderDetail> newItems, String username) {
        log.info("Adding {} new items to order ID: {}", newItems.size(), orderId);

        Order order = findByIdOrThrow(orderId);

        // Validate that order can accept new items
        if (!order.canAcceptNewItems()) {
            throw new IllegalStateException(
                String.format("No se pueden agregar items a este pedido. Tipo: %s, Estado: %s.",
                              order.getOrderType().getDisplayName(),
                              order.getStatus().getDisplayName())
            );
        }

        // Validate items are active
        validateItemsActive(newItems);

        // Validate stock for new items (FIRST PRIORITY - material constraint)
        Map<Long, String> stockErrors = validateStock(newItems);
        if (!stockErrors.isEmpty()) {
            throw new IllegalStateException(
                "¡Lo sentimos! No tenemos suficiente stock de los siguientes items: " + 
                String.join(", ", stockErrors.values()) + 
                ". ¡Te invitamos a seguir descubriendo las deliciosas opciones de nuestro menú!"
            );
        }
        
        // Validate item availability schedule - AFTER stock validation
        validateItemsAvailability(newItems);

        // Process each new item
        for (OrderDetail detail : newItems) {
            ItemMenu item = itemMenuRepository.findById(detail.getItemMenu().getIdItemMenu())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Item de menú no encontrado: " + detail.getItemMenu().getIdItemMenu()
                ));

            // Set current price if not set
            if (detail.getUnitPrice() == null) {
                detail.setUnitPrice(item.getPrice());
            }

            // Calculate subtotal
            detail.calculateSubtotal();

            // Mark as new item
            detail.markAsNew();
            
            // Initialize as PENDING by default
            detail.setItemStatus(OrderStatus.PENDING);
            
            // Auto-advance to READY ONLY if item requires NO preparation at all (neither chef nor barista)
            // Items requiring barista preparation MUST start as PENDING and be changed manually
            boolean requiresChefPreparation = Boolean.TRUE.equals(item.getRequiresPreparation());
            boolean requiresBaristaPreparation = Boolean.TRUE.equals(item.getRequiresBaristaPreparation());
            
            if (!requiresChefPreparation && !requiresBaristaPreparation) {
                // Item requires NO preparation (e.g., bottled drinks, pre-packaged items)
                detail.setItemStatus(OrderStatus.READY);
                log.info("New item '{}' auto-advanced to READY (no preparation required)", item.getName());
            } else {
                // Item requires preparation (chef or barista), starts as PENDING
                log.info("New item '{}' set to PENDING (requires {} preparation)", 
                    item.getName(), 
                    requiresBaristaPreparation ? "barista" : "chef");
            }

            // Deduct stock from ingredients
            deductStockForItem(item, detail.getQuantity());
            
            // Deduct stock for complements
            deductStockForComplements(detail);

            // Set order reference for the owning side of the relationship
            detail.setOrder(order);
            
            // CRITICAL FIX: Explicitly save each new OrderDetail to ensure it gets its own
            // INSERT statement. This avoids issues with Hibernate's PersistentBag collection
            // management where multiple new entities could be deduplicated during flush.
            OrderDetail savedDetail = orderDetailRepository.save(detail);
            order.getOrderDetails().add(savedDetail);
            
            log.debug("Saved new OrderDetail id={} for item '{}' qty={}",
                savedDetail.getIdOrderDetail(), item.getName(), savedDetail.getQuantity());
        }

        // Recalculate order totals
        order.recalculateAmounts();

        // Update order status based on items
        order.updateStatusFromItems();

        // Set audit fields
        order.setUpdatedBy(username);
        order.setUpdatedAt(LocalDateTime.now());

        Order savedOrder = orderRepository.save(order);
        log.info("Added {} new items to order {}. New total: {}", 
                 newItems.size(), 
                 savedOrder.getOrderNumber(),
                 savedOrder.getFormattedTotal());

        // Send WebSocket notification for items added to existing order
        try {
            // Pass the actual new items list to detect what type of items were added
            List<OrderDetail> newOrderDetails = savedOrder.getOrderDetails().stream()
                .skip(savedOrder.getOrderDetails().size() - newItems.size())
                .collect(Collectors.toList());
            wsNotificationService.notifyItemsAdded(savedOrder, newOrderDetails);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for items added to order: {}", 
                savedOrder.getOrderNumber(), e);
        }

        return savedOrder;
    }

    @Override
    public Order changeItemsStatus(Long orderId, List<Long> itemDetailIds, OrderStatus newStatus, String username) {
        log.info("Changing status of {} items in order ID: {} to {}", 
                 itemDetailIds.size(), orderId, newStatus);

        Order order = findByIdOrThrow(orderId);

        // Detect what type of items are being changed (chef or barista items)
        boolean changingChefItems = false;
        boolean changingBaristaItems = false;

        // Find and update each item
        for (Long itemDetailId : itemDetailIds) {
            OrderDetail detail = order.getOrderDetails().stream()
                .filter(d -> d.getIdOrderDetail().equals(itemDetailId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    "Item detail no encontrado en esta orden: " + itemDetailId
                ));

            // Detect item type
            if (detail.getItemMenu() != null) {
                if (Boolean.TRUE.equals(detail.getItemMenu().getRequiresPreparation())) {
                    changingChefItems = true;
                }
                if (Boolean.TRUE.equals(detail.getItemMenu().getRequiresBaristaPreparation())) {
                    changingBaristaItems = true;
                }
            }

            // Validate status transition for this item
            OrderStatus oldItemStatus = detail.getItemStatus();
            
            // Basic validation: can't go backwards
            if (oldItemStatus == OrderStatus.DELIVERED) {
                throw new IllegalStateException(
                    "No se puede cambiar el estado de un item ya entregado: " + detail.getItemMenu().getName()
                );
            }

            // Set new status
            detail.setItemStatus(newStatus);

            // Track who prepared the item when it becomes IN_PREPARATION
            if (newStatus == OrderStatus.IN_PREPARATION && detail.getPreparedBy() == null) {
                detail.setPreparedBy(username);
            }

            // Remove "new item" badge when item reaches READY status
            // This ensures the badge only shows for items that are still being prepared
            if (newStatus == OrderStatus.READY && Boolean.TRUE.equals(detail.getIsNewItem())) {
                detail.setIsNewItem(false);
                log.info("Item '{}' marked as no longer new (READY status reached)", 
                         detail.getItemMenu().getName());
            }

            log.info("Item '{}' status changed: {} -> {}", 
                     detail.getItemMenu().getName(), oldItemStatus, newStatus);
        }

        // Update order's overall status based on all items
        OrderStatus oldOrderStatus = order.getStatus();
        order.updateStatusFromItems();
        OrderStatus newOrderStatus = order.getStatus();

        // Track timestamp when order becomes READY
        if (oldOrderStatus != OrderStatus.READY && newOrderStatus == OrderStatus.READY) {
            order.setPreparedAt(LocalDateTime.now());
        }

        // Set audit fields
        order.setUpdatedBy(username);
        order.setUpdatedAt(LocalDateTime.now());

        Order savedOrder = orderRepository.save(order);
        log.info("Order {} status recalculated: {} -> {}", 
                 savedOrder.getOrderNumber(), 
                 oldOrderStatus,
                 newOrderStatus);

        // Send WebSocket notification if order status changed automatically
        if (oldOrderStatus != newOrderStatus) {
            try {
                String statusChangeMessage = String.format(
                    "Pedido #%s cambió automáticamente de %s a %s", 
                    savedOrder.getOrderNumber(),
                    oldOrderStatus.getDisplayName(),
                    newOrderStatus.getDisplayName()
                );
                
                // Determine which role made the change
                String roleWhoChanged = null;
                if (changingChefItems && !changingBaristaItems) {
                    roleWhoChanged = "chef";
                } else if (changingBaristaItems && !changingChefItems) {
                    roleWhoChanged = "barista";
                }
                // If both or none, roleWhoChanged stays null (notify all)
                
                wsNotificationService.notifyOrderStatusChange(savedOrder, statusChangeMessage, roleWhoChanged);
                log.info("🔔 WebSocket: Order status auto-update notification sent - {} ({} -> {}) - Role: {}", 
                    savedOrder.getOrderNumber(), oldOrderStatus, newOrderStatus, roleWhoChanged);
            } catch (Exception e) {
                log.error("Failed to send WebSocket notification for order status change: {}", 
                    savedOrder.getOrderNumber(), e);
            }
        }

        return savedOrder;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findAll() {
        Company company = CompanyContext.requireCurrentCompany();
        return orderRepository.findAllByCompanyOrderByCreatedAtDesc(company);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(Long id) {
        Company company = CompanyContext.requireCurrentCompany();
        return orderRepository.findByIdOrderAndCompany(id, company);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByIdWithDetails(Long id) {
        Company company = CompanyContext.requireCurrentCompany();
        return orderRepository.findByIdWithDetailsAndCompany(id, company);
    }

    @Override
    @Transactional(readOnly = true)
    public Order findByIdOrThrow(Long id) {
        Company company = CompanyContext.requireCurrentCompany();
        return orderRepository.findByIdOrderAndCompany(id, company)
            .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado con ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByOrderNumber(String orderNumber) {
        Company company = CompanyContext.requireCurrentCompany();
        return orderRepository.findByOrderNumberAndCompany(orderNumber, company);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByTableId(Long tableId) {
        Company company = CompanyContext.requireCurrentCompany();
        return orderRepository.findByTableIdAndCompany(tableId, company);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findActiveOrderByTableId(Long tableId) {
        Company company = CompanyContext.requireCurrentCompany();
        return orderRepository.findActiveOrderByTableIdAndCompany(tableId, company);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByEmployeeId(Long employeeId) {
        Company company = CompanyContext.requireCurrentCompany();
        return orderRepository.findByEmployeeIdAndCompany(employeeId, company);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByStatus(OrderStatus status) {
        Company company = CompanyContext.requireCurrentCompany();
        return orderRepository.findByCompany(company).stream()
            .filter(o -> o.getStatus() == status)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByOrderType(OrderType orderType) {
        Company company = CompanyContext.requireCurrentCompany();
        return orderRepository.findByCompany(company).stream()
            .filter(o -> o.getOrderType() == orderType)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findTodaysOrders() {
        Company company = CompanyContext.requireCurrentCompany();
        return orderRepository.findTodaysOrdersByCompany(company);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findActiveOrders() {
        Company company = CompanyContext.requireCurrentCompany();
        return orderRepository.findActiveOrdersByCompany(company);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        Company company = CompanyContext.requireCurrentCompany();
        return orderRepository.findByDateRangeAndCompany(company, startDate, endDate);
    }

    @Override
    public void delete(Long id) {
        log.info("Deleting order with ID: {}", id);
        
        Order order = findByIdOrThrow(id);
        
        // Only allow deletion if order is CANCELLED
        if (order.getStatus() != OrderStatus.CANCELLED) {
            throw new IllegalStateException(
                "Solo se pueden eliminar pedidos cancelados. Estado actual: " + 
                order.getStatus().getDisplayName()
            );
        }

        orderRepository.deleteById(id);
        log.info("Order deleted successfully: {}", order.getOrderNumber());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> validateStock(List<OrderDetail> orderDetails) {
        Map<Long, String> errors = new HashMap<>();

        for (OrderDetail detail : orderDetails) {
            ItemMenu item = itemMenuRepository.findById(detail.getItemMenu().getIdItemMenu())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Item de menú no encontrado: " + detail.getItemMenu().getIdItemMenu()
                ));

            if (!item.hasEnoughStock(detail.getQuantity())) {
                errors.put(item.getIdItemMenu(), item.getName());
            }
            
            // Validate stock for selected complements
            if (detail.getSelectedComplements() != null) {
                for (OrderDetailComplement odc : detail.getSelectedComplements()) {
                    Complement complement = odc.getComplement();
                    if (complement != null) {
                        // For sauces, multiply by parent item quantity (sauces are per-serving)
                        int effectiveQty = odc.getQuantity();
                        if (Boolean.TRUE.equals(complement.getIsSauce())) {
                            effectiveQty = odc.getQuantity() * detail.getQuantity();
                        }
                        if (!complement.hasEnoughStock(effectiveQty)) {
                            // Use negative ID to distinguish complement errors from item errors
                            errors.put(-complement.getIdComplement(), complement.getName());
                        }
                    }
                }
            }
        }

        return errors;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveOrder(Long tableId) {
        return orderRepository.findActiveOrderByTableId(tableId).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isTableAvailableForOrder(Long tableId) {
        // Delegate to RestaurantTableService which has the complete logic including:
        // 1. Table status must be AVAILABLE
        // 2. Table must not be blocked by a PENDING reservation within avg consumption time
        // 3. Table must not have an active order
        return restaurantTableService.canTableBeUsedForOrder(tableId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateOrderNumber() {
        LocalDate today = dateTimeService.todayLocal();
        Company company = CompanyContext.requireCurrentCompany();
        
        String datePrefix = String.format("ORD-%04d%02d%02d-", 
                                         today.getYear(), 
                                         today.getMonthValue(), 
                                         today.getDayOfMonth());

        // Two-step anti-deadlock counter update:
        //
        // Step 1 – INSERT IGNORE (no-op when row exists).
        //   Initialises last_sequence from MAX(existing sequences) to survive
        //   manual counter-table wipes. INSERT IGNORE never upgrades to an
        //   exclusive lock on a duplicate key, avoiding the S→X deadlock that
        //   INSERT … ON DUPLICATE KEY UPDATE causes under high concurrency.
        //
        // Step 2 – Atomic row-level UPDATE.
        //   LAST_INSERT_ID(last_sequence + 1) both writes the new value and
        //   captures it in this session's LAST_INSERT_ID(), which is then read
        //   by getLastInsertId() — strictly per-connection, so concurrent
        //   transactions each see only their own assigned sequence.
        // Phase 1: ensure the row exists in its own committed transaction.
        // Fast path (row present): returns immediately. Slow path: reads MAX from
        // orders (read-only, no INSERT pressure) then does INSERT IGNORE VALUES
        // (no cross-table SELECT → no gap-lock contention with other inserts).
        // Phase 2: atomic increment in the same REQUIRES_NEW transaction.
        // Combining both phases into one sub-transaction reduces connection pool
        // pressure: 2 connections per thread (outer + sub-tx) instead of 3.
        long nextSequence = ensureAndIncrementWithRetry(today, company.getIdCompany(), datePrefix + "%");

        return String.format("%s%03d", datePrefix, nextSequence);
    }

    /** Retries ensureAndIncrement up to 5 times on transient deadlock. */
    private long ensureAndIncrementWithRetry(LocalDate date, Long companyId, String prefix) {
        CannotAcquireLockException last = null;
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                return dailyOrderCounterService.ensureAndIncrement(date, companyId, prefix);
            } catch (CannotAcquireLockException e) {
                last = e;
                log.warn("counter deadlock (attempt {}): {}", attempt, e.getMessage());
                if (attempt < 5) {
                    try { Thread.sleep(5L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
        throw last;
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(OrderStatus status) {
        Company company = CompanyContext.requireCurrentCompany();
        return orderRepository.countByStatusAndCompany(status, company);
    }

    @Override
    @Transactional(readOnly = true)
    public long countTodaysOrders() {
        Company company = CompanyContext.requireCurrentCompany();
        return orderRepository.countTodaysOrdersByCompany(company);
    }

    @Override
    @Transactional(readOnly = true)
    public long countTodaysOrdersByStatus(OrderStatus status) {
        // Count today's orders filtered by status
        List<Order> todaysOrders = findTodaysOrders();
        return todaysOrders.stream()
            .filter(order -> order.getStatus() == status)
            .count();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTodaysRevenue() {
        Company company = CompanyContext.requireCurrentCompany();
        BigDecimal revenue = orderRepository.getTodaysRevenueByCompany(company);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }

    // ========== Statistics by Date Range (for dynamic cards) ==========

    @Override
    @Transactional(readOnly = true)
    public long countPaidOrdersByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        Company company = CompanyContext.requireCurrentCompany();
        return orderRepository.countPaidByDateRangeAndCompany(startDate, endDate, company);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPaidOrdersByUsernameAndDateRange(String username, LocalDateTime startDate, LocalDateTime endDate) {
        Company company = CompanyContext.requireCurrentCompany();
        return orderRepository.countPaidByUsernameAndDateRangeAndCompany(username, startDate, endDate, company);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatusAndDateRange(OrderStatus status, LocalDateTime startDate, LocalDateTime endDate) {
        Company company = CompanyContext.requireCurrentCompany();
        return orderRepository.countByStatusAndDateRangeAndCompany(status, startDate, endDate, company);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getRevenueByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        Company company = CompanyContext.requireCurrentCompany();
        BigDecimal revenue = orderRepository.getRevenueByDateRangeAndCompany(startDate, endDate, company);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getRevenueByUsernameAndDateRange(String username, LocalDateTime startDate, LocalDateTime endDate) {
        Company company = CompanyContext.requireCurrentCompany();
        BigDecimal revenue = orderRepository.getRevenueByUsernameAndDateRangeAndCompany(username, startDate, endDate, company);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getRevenueCreatedByUserPaidByOthersAndDateRange(String username, LocalDateTime startDate, LocalDateTime endDate) {
        Company company = CompanyContext.requireCurrentCompany();
        BigDecimal revenue = orderRepository.getRevenueCreatedByUserPaidByOthersAndDateRangeAndCompany(username, startDate, endDate, company);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getRevenueCreatedAndPaidBySameUserAndDateRange(String username, LocalDateTime startDate, LocalDateTime endDate) {
        Company company = CompanyContext.requireCurrentCompany();
        BigDecimal revenue = orderRepository.getRevenueCreatedAndPaidBySameUserAndDateRangeAndCompany(username, startDate, endDate, company);
        return revenue != null ? revenue : BigDecimal.ZERO;
    }

    // ========== PRIVATE HELPER METHODS ==========

    /**
     * Deduct stock for a menu item.
     * Uses IngredientStockService with REQUIRES_NEW transactions and pessimistic locking
     * to prevent the @Version optimistic-lock conflict that occurs when multiple concurrent
     * orders modify the same Ingredient row inside the outer transaction's session.
     * Mirrors the pattern already used by returnStockForItem.
     */
    private void deductStockForItem(ItemMenu item, Integer quantity) {
        log.debug("Deducting stock for item: {} (quantity: {})", item.getName(), quantity);

        for (ItemIngredient itemIngredient : item.getIngredients()) {
            BigDecimal quantityToDeduct = itemIngredient.getQuantity()
                .multiply(BigDecimal.valueOf(quantity));
            Long ingredientId = itemIngredient.getIngredient().getIdIngredient();

            // Uses REQUIRES_NEW + SELECT FOR UPDATE — serialises concurrent deductions
            // without leaving a stale @Version in the outer session.
            ingredientStockService.deductStockWithRetry(ingredientId, quantityToDeduct, itemIngredient.getUnit());
        }
    }

    /**
     * Deduct stock for complements of an order detail
     * Called after the order detail is created with its complements
     * Uses ComplementStockService with REQUIRES_NEW transactions for concurrent updates
     * 
     * NOTE: We do NOT save the OrderDetailComplement here because:
     * 1. The OrderDetail hasn't been persisted yet (it's transient)
     * 2. OrderDetail has CascadeType.ALL for complements, so they'll be saved automatically
     *    when the Order is saved via orderRepository.save(order)
     * We just mark stockDeducted=true and update complement availability.
     */
    private void deductStockForComplements(OrderDetail detail) {
        if (detail.getSelectedComplements() == null || detail.getSelectedComplements().isEmpty()) {
            return;
        }
        
        log.debug("Deducting stock for {} complements on item: {}", 
                 detail.getSelectedComplements().size(), 
                 detail.getItemMenu().getName());
        
        for (OrderDetailComplement odc : detail.getSelectedComplements()) {
            try {
                // Use the service with pessimistic locking for each ingredient
                Complement complement = odc.getComplement();
                if (complement.getIngredients() != null && !complement.getIngredients().isEmpty()) {
                    // For sauces, multiply by parent item quantity (sauces are per-serving)
                    // For non-sauces, use the complement quantity as-is
                    int effectiveQty = odc.getQuantity();
                    if (Boolean.TRUE.equals(complement.getIsSauce())) {
                        effectiveQty = odc.getQuantity() * detail.getQuantity();
                    }
                    complementStockService.deductStockForComplement(
                        new ArrayList<>(complement.getIngredients()), 
                        effectiveQty
                    );
                }
                
                // Mark as deducted (will be persisted via cascade when Order is saved)
                odc.setStockDeducted(true);
                // DO NOT save odc here - it will be cascaded from OrderDetail -> Order
                
                // Refresh complement so updateAvailability() reads current ingredient
                // stock from DB (not stale values from the outer session's 1st-level cache).
                entityManager.refresh(complement);
                complement.updateAvailability();
                complementRepository.save(complement);
                
                log.debug("Stock deducted for complement: {} (quantity: {}, effective: {})", 
                         complement.getName(), odc.getQuantity(),
                         Boolean.TRUE.equals(complement.getIsSauce()) ? odc.getQuantity() * detail.getQuantity() : odc.getQuantity());
            } catch (IllegalStateException e) {
                log.error("Error deducting stock for complement {}: {}", 
                         odc.getComplement().getName(), e.getMessage());
                // Wrap in user-friendly format (same as items) so frontend can parse the name
                if (e.getMessage() != null && e.getMessage().startsWith("Stock insuficiente")) {
                    throw new IllegalStateException(
                        "¡Lo sentimos! No tenemos suficiente stock de los siguientes items: " +
                        odc.getComplement().getName() +
                        ". ¡Te invitamos a seguir descubriendo las deliciosas opciones de nuestro menú!", e);
                }
                throw e;
            }
        }
    }

    /**
     * Return stock for an order (when cancelling or updating)
     */
    private void returnStockForOrder(Order order) {
        log.info("Returning stock for order: {}", order.getOrderNumber());

        for (OrderDetail detail : order.getOrderDetails()) {
            ItemMenu item = itemMenuRepository.findById(detail.getItemMenu().getIdItemMenu())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Item de menú no encontrado: " + detail.getItemMenu().getIdItemMenu()
                ));

            returnStockForItem(item, detail.getQuantity());
            
            // Return stock for complements
            returnStockForComplements(detail);

            // Update item availability
            item.updateAvailability();
            itemMenuRepository.save(item);
        }
    }

    /**
     * Return stock for a menu item
     * When returning stock from cancelled orders or deleted items:
     * - If the returned stock would exceed maxStock, update maxStock to match
     * - This ensures stock is never "lost" when orders are cancelled after manual restocking
     * - Uses IngredientStockService with REQUIRES_NEW transactions for concurrent updates
     */
    private void returnStockForItem(ItemMenu item, Integer quantity) {
        log.debug("Returning stock for item: {} (quantity: {})", item.getName(), quantity);

        for (ItemIngredient itemIngredient : item.getIngredients()) {
            BigDecimal quantityToReturn = itemIngredient.getQuantity()
                .multiply(BigDecimal.valueOf(quantity));
            
            Long ingredientId = itemIngredient.getIngredient().getIdIngredient();
            
            // Use dedicated service with REQUIRES_NEW transaction for retry mechanism
            ingredientStockService.returnStockWithRetry(ingredientId, quantityToReturn, itemIngredient.getUnit());
        }
    }

    /**
     * Return stock for complements of an order detail
     * Called when deleting items or cancelling orders
     * Uses ComplementStockService with REQUIRES_NEW transactions for concurrent updates
     */
    private void returnStockForComplements(OrderDetail detail) {
        if (detail.getSelectedComplements() == null || detail.getSelectedComplements().isEmpty()) {
            return;
        }
        
        log.debug("Returning stock for {} complements on item: {}", 
                 detail.getSelectedComplements().size(), 
                 detail.getItemMenu().getName());
        
        for (OrderDetailComplement odc : detail.getSelectedComplements()) {
            try {
                // Only return if stock was previously deducted
                if (!Boolean.TRUE.equals(odc.getStockDeducted())) {
                    log.debug("Skipping stock return for complement {} - not deducted", 
                             odc.getComplement().getName());
                    continue;
                }
                
                // Use the service with pessimistic locking for each ingredient
                Complement complement = odc.getComplement();
                if (complement.getIngredients() != null && !complement.getIngredients().isEmpty()) {
                    // For sauces, multiply by parent item quantity (sauces are per-serving)
                    // For non-sauces, use the complement quantity as-is
                    int effectiveQty = odc.getQuantity();
                    if (Boolean.TRUE.equals(complement.getIsSauce())) {
                        effectiveQty = odc.getQuantity() * detail.getQuantity();
                    }
                    complementStockService.returnStockForComplement(
                        new ArrayList<>(complement.getIngredients()), 
                        effectiveQty
                    );
                }
                
                // Mark as returned
                odc.setStockDeducted(false);
                orderDetailComplementRepository.save(odc);
                
                // Update complement availability
                complement.updateAvailability();
                complementRepository.save(complement);
                
                log.debug("Stock returned for complement: {} (quantity: {}, effective: {})", 
                         complement.getName(), odc.getQuantity(),
                         Boolean.TRUE.equals(complement.getIsSauce()) ? odc.getQuantity() * detail.getQuantity() : odc.getQuantity());
            } catch (IllegalStateException e) {
                log.error("Error returning stock for complement {}: {}", 
                         odc.getComplement().getName(), e.getMessage());
                // Log but don't throw - stock return errors shouldn't block cancellation
            }
        }
    }

    /**
     * Get tax rate from system configuration
     * MULTI-TENANT: Uses SystemConfigurationService which filters by company
     */
    private BigDecimal getTaxRate() {
        SystemConfiguration config = systemConfigurationService.getConfiguration();
        if (config == null) {
            throw new IllegalStateException("No se encontró la configuración del sistema");
        }
        return config.getTaxRate();
    }

    /**
     * Automatically advance order to READY status if ALL items don't require ANY preparation
     * This allows orders with ONLY items requiring no preparation (bottled drinks, pre-packaged) to skip chef AND barista
     * 
     * @param order The order to check and potentially auto-advance
     */
    private void autoAdvanceOrderIfNoPreparationNeeded(Order order) {
        // Only auto-advance if order is currently PENDING
        if (order.getStatus() != OrderStatus.PENDING) {
            return;
        }

        // Check if ALL items don't require ANY preparation (neither chef nor barista)
        // Items requiring barista preparation CANNOT auto-advance
        boolean allItemsReady = order.getOrderDetails().stream()
            .allMatch(detail -> detail.getItemMenu() != null 
                && Boolean.FALSE.equals(detail.getItemMenu().getRequiresPreparation())
                && Boolean.FALSE.equals(detail.getItemMenu().getRequiresBaristaPreparation()));

        if (allItemsReady && !order.getOrderDetails().isEmpty()) {
            log.info("Order {} contains ONLY items that don't require ANY preparation. Auto-advancing to READY status.", 
                     order.getOrderNumber());
            
            // Change order status to READY
            order.setStatus(OrderStatus.READY);
            order.setPreparedAt(LocalDateTime.now());
            
            // Update all item statuses to READY
            for (OrderDetail detail : order.getOrderDetails()) {
                detail.setItemStatus(OrderStatus.READY);
            }
            
            // Save changes
            Order savedOrder = orderRepository.save(order);
            
            log.info("Order {} auto-advanced to READY (items: {})", 
                     order.getOrderNumber(),
                     order.getOrderDetails().stream()
                         .map(d -> d.getItemMenu().getName())
                         .collect(java.util.stream.Collectors.joining(", ")));
            
            // Send WebSocket notification for status change
            try {
                String message = "Pedido #" + savedOrder.getOrderNumber() + " está listo (sin preparación requerida)";
                wsNotificationService.notifyOrderStatusChange(savedOrder, message);
                log.info("WebSocket notification sent for auto-advanced order: {}", savedOrder.getOrderNumber());
            } catch (Exception e) {
                log.error("Failed to send WebSocket notification for auto-advanced order: {}", 
                         savedOrder.getOrderNumber(), e);
            }
        }
    }

    /**
     * Validate table requirement based on order type
     * DINE_IN: Table is required
     * TAKEOUT: Table is optional
     * DELIVERY: Table should not be assigned
     */
    private void validateTableRequirement(Order order) {
        OrderType orderType = order.getOrderType();
        RestaurantTable table = order.getTable();
        
        if (orderType == OrderType.DINE_IN) {
            // DINE_IN requires a table
            if (table == null) {
                throw new IllegalArgumentException("Se requiere asignar una mesa para pedidos 'Para comer aquí'");
            }
        } else if (orderType == OrderType.DELIVERY) {
            // DELIVERY should not have a table
            if (table != null) {
                log.warn("Table assigned to DELIVERY order will be ignored");
                order.setTable(null); // Remove table for delivery orders
            }
        } else if (orderType == OrderType.TAKEOUT) {
            // TAKEOUT is optional - can have table or not
            if (table != null) {
                log.info("Table #{} assigned to TAKEOUT order", table.getTableNumber());
            }
        }
    }

    /**
     * Handle table changes when updating an order
     * Frees old table and occupies new table as needed
     * 
     * Simplified logic:
     * - Tables only have statuses: AVAILABLE, OCCUPIED, OUT_OF_SERVICE
     * - OCCUPIED tables have an active order
     * - AVAILABLE tables can receive new orders (unless blocked by reservation)
     */
    private void handleTableChange(Order order, RestaurantTable oldTable, RestaurantTable newTable, 
                                   OrderType oldOrderType, OrderType newOrderType, String username) {
        
        log.info("Handling table change - Old: {}, New: {}, OldType: {}, NewType: {}", 
                 oldTable != null ? "Table #" + oldTable.getTableNumber() : "null",
                 newTable != null ? "Table #" + newTable.getTableNumber() : "null",
                 oldOrderType, newOrderType);
        
        boolean oldWasDineIn = oldOrderType == OrderType.DINE_IN;
        boolean newIsDineIn = newOrderType == OrderType.DINE_IN;
        
        // Case 1: Old order had a table and was DINE_IN -> free the old table
        if (oldTable != null && oldWasDineIn) {
            // Check if we're changing to a different table or changing order type
            if (newTable == null || !oldTable.getId().equals(newTable.getId()) || !newIsDineIn) {
                log.info("Freeing old table #{} (was {})", oldTable.getTableNumber(), oldTable.getStatus());
                
                // Refresh table from DB to get latest status
                RestaurantTable tableToFree = restaurantTableRepository.findById(oldTable.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada: " + oldTable.getId()));
                
                // Only change status if table is OCCUPIED
                if (tableToFree.getStatus() == TableStatus.OCCUPIED) {
                    tableToFree.setStatus(TableStatus.AVAILABLE);
                    tableToFree.setUpdatedBy(username);
                    RestaurantTable savedOldTable = restaurantTableRepository.save(tableToFree);
                    restaurantTableRepository.flush();
                    log.info("Table #{} freed successfully - Status: {}", 
                             savedOldTable.getTableNumber(), savedOldTable.getStatus());
                }
            }
        }
        
        // Case 2: New order has a table and is DINE_IN -> occupy the new table
        if (newTable != null && newIsDineIn) {
            // Only occupy if it's a different table or we're changing from non-DINE_IN to DINE_IN
            if (oldTable == null || !oldTable.getId().equals(newTable.getId()) || !oldWasDineIn) {
                log.info("Occupying new table #{}", newTable.getTableNumber());
                
                // Refresh table from DB to get latest status
                RestaurantTable tableToOccupy = restaurantTableRepository.findById(newTable.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada: " + newTable.getId()));
                
                log.info("Table #{} current status before occupying: {}", 
                         tableToOccupy.getTableNumber(), tableToOccupy.getStatus());
                
                // Only AVAILABLE tables can be occupied
                if (tableToOccupy.getStatus() == TableStatus.AVAILABLE) {
                    tableToOccupy.setStatus(TableStatus.OCCUPIED);
                    tableToOccupy.setUpdatedBy(username);
                    RestaurantTable savedNewTable = restaurantTableRepository.save(tableToOccupy);
                    restaurantTableRepository.flush();
                    log.info("Table #{} occupied successfully - Status: {}", 
                             savedNewTable.getTableNumber(), savedNewTable.getStatus());
                } else {
                    // Table is OCCUPIED or OUT_OF_SERVICE
                    throw new IllegalArgumentException(
                        "La mesa " + tableToOccupy.getTableNumber() + " está " + 
                        tableToOccupy.getStatusDisplayName() + " y no se puede asignar"
                    );
                }
            }
        }
        
        // Update order's table reference
        order.setTable(newTable);
    }

    /**
     * Validate customer information based on order type
     * DINE_IN: Customer information is optional
     * DELIVERY: Customer name, phone and address are required
     * TAKEOUT: Customer name and phone are required, address is optional
     */
    private void validateCustomerInformation(Order order) {
        OrderType orderType = order.getOrderType();
        
        if (orderType == OrderType.DELIVERY) {
            // DELIVERY requires: name, phone, address
            if (order.getCustomerName() == null || order.getCustomerName().trim().isEmpty()) {
                throw new IllegalArgumentException("El nombre del cliente es requerido para pedidos a domicilio");
            }
            if (order.getCustomerPhone() == null || order.getCustomerPhone().trim().isEmpty()) {
                throw new IllegalArgumentException("El teléfono del cliente es requerido para pedidos a domicilio");
            }
            if (order.getDeliveryAddress() == null || order.getDeliveryAddress().trim().isEmpty()) {
                throw new IllegalArgumentException("La dirección de entrega es requerida para pedidos a domicilio");
            }
        } else if (orderType == OrderType.TAKEOUT) {
            // TAKEOUT requires: name, phone (address optional)
            if (order.getCustomerName() == null || order.getCustomerName().trim().isEmpty()) {
                throw new IllegalArgumentException("El nombre del cliente es requerido para pedidos para pasar a recoger");
            }
            if (order.getCustomerPhone() == null || order.getCustomerPhone().trim().isEmpty()) {
                throw new IllegalArgumentException("El teléfono del cliente es requerido para pedidos para pasar a recoger");
            }
        }
        // DINE_IN: No validation needed, customer info is optional
        
        // Set default values for empty fields to avoid null issues
        if (order.getCustomerName() == null) {
            order.setCustomerName("");
        }
        if (order.getCustomerPhone() == null) {
            order.setCustomerPhone("");
        }
        if (order.getDeliveryAddress() == null) {
            order.setDeliveryAddress("");
        }
    }

    /**
     * Validate payment method is enabled based on order type
     * For DELIVERY orders, uses deliveryPaymentMethods configuration
     * For other orders (DINE_IN, TAKEOUT), uses paymentMethods configuration
     * MULTI-TENANT: Uses SystemConfigurationService which filters by company
     */
    private void validatePaymentMethod(PaymentMethodType paymentMethod, OrderType orderType) {
        SystemConfiguration config = systemConfigurationService.getConfiguration();
        if (config == null) {
            throw new IllegalStateException("No se encontró la configuración del sistema");
        }

        boolean isEnabled = config.isPaymentMethodEnabledForOrderType(paymentMethod, orderType);
        
        if (!isEnabled) {
            String orderTypeText = orderType == OrderType.DELIVERY ? "entregas a domicilio" : "el restaurante";
            throw new IllegalStateException(
                String.format("El método de pago '%s' no está habilitado para %s", 
                              paymentMethod.getDisplayName(), orderTypeText)
            );
        }
    }

    /**
     * Validate that all items in the order are active
     */
    private void validateItemsActive(List<OrderDetail> orderDetails) {
        log.info("Validating active status for {} items in order", orderDetails.size());
        for (OrderDetail detail : orderDetails) {
            Long itemId = detail.getItemMenu().getIdItemMenu();
            ItemMenu item = itemMenuRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Item de menú no encontrado: " + itemId
                ));

            // Force refresh from DB to ensure we have the latest status
            // (though findById usually returns the current state in transaction)
            log.info("Validating item '{}' (ID: {}). Active status in DB: {}", 
                     item.getName(), itemId, item.getActive());

            if (!Boolean.TRUE.equals(item.getActive())) {
                log.warn("Blocking order due to inactive item: {}", item.getName());
                throw new IllegalStateException(
                    "El item '" + item.getName() + "' está desactivado y no puede ser incluido en el pedido."
                );
            }
        }
    }
    
    /**
     * Validate that all items are available according to their schedule (day and time)
     * This validation happens AFTER stock validation to prioritize material constraints
     */
    private void validateItemsAvailability(List<OrderDetail> orderDetails) {
        log.info("Validating availability schedule for {} items in order", orderDetails.size());
        for (OrderDetail detail : orderDetails) {
            Long itemId = detail.getItemMenu().getIdItemMenu();
            ItemMenu item = detail.getItemMenu();
            
            if (!itemMenuService.isItemAvailableNow(itemId)) {
                // Build a message showing only today's schedule
                java.time.DayOfWeek javaDow = dateTimeService.todayLocal().getDayOfWeek();
                DayOfWeek todayDay = DayOfWeek.valueOf(javaDow.name());
                String todayName = todayDay.getDisplayName();
                
                String scheduleMsg = "";
                if (item.getAvailabilityDays() != null) {
                    var todayAvail = item.getAvailabilityDays().stream()
                        .filter(a -> a.getDayOfWeek() == todayDay)
                        .findFirst()
                        .orElse(null);
                    
                    if (todayAvail != null && todayAvail.getStartTime() != null && todayAvail.getEndTime() != null) {
                        scheduleMsg = "Hoy (" + todayName + ") disponible de " + todayAvail.getStartTime() + " a " + todayAvail.getEndTime() + ".";
                    } else if (todayAvail != null) {
                        scheduleMsg = "Hoy (" + todayName + ") está disponible pero fuera de horario.";
                    } else {
                        scheduleMsg = "No disponible hoy (" + todayName + "). Disponible: " + item.getAvailabilityDescription();
                    }
                }
                
                log.warn("Blocking order due to item not available now: {} - {}", item.getName(), scheduleMsg);
                throw new IllegalStateException(
                    "El item '" + item.getName() + "' no está disponible en este momento. " + scheduleMsg
                );
            }
        }
    }

    /**
     * Determine if stock should be returned automatically for an item
     * PENDING -> automatic return (never touched)
     * READY + NO requires preparation (Chef or Barista) -> automatic return (auto-advanced, never touched)
     * READY + requires preparation -> manual return (chef/barista prepared it, used ingredients)
     * IN_PREPARATION -> manual return (working on it, may have used ingredients)
     */
    private boolean shouldReturnStockAutomatically(OrderDetail detail) {
        OrderStatus itemStatus = detail.getItemStatus();
        
        // PENDING -> always return automatically (never touched)
        if (itemStatus == OrderStatus.PENDING) {
            return true;
        }
        
        // READY -> depends if someone prepared it or not
        if (itemStatus == OrderStatus.READY) {
            // If item does NOT require preparation (neither Chef nor Barista) -> it was marked READY automatically
            // No ingredients were used, can return automatically
            if (detail.getItemMenu() != null) {
                boolean requiresChef = Boolean.TRUE.equals(detail.getItemMenu().getRequiresPreparation());
                boolean requiresBarista = Boolean.TRUE.equals(detail.getItemMenu().getRequiresBaristaPreparation());
                
                // Only return automatically if NO ONE needs to prepare it
                return !requiresChef && !requiresBarista;
            }
            return false;
        }
        
        // IN_PREPARATION -> requires manual return (working on it)
        return false;
    }

    /**
     * Delete a specific item from an order
     * Only allows deleting items that are not DELIVERED
     * Returns stock automatically if item is PENDING or (READY and !requiresPreparation)
     */
    @Override
    @Transactional
    public OrderDetail deleteOrderItem(Long orderId, Long itemDetailId, String username) {
        log.info("Deleting item {} from order {} by user {}", itemDetailId, orderId, username);

        // Find order
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado con ID: " + orderId));

        // Validate order is not in final states
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("No se pueden eliminar items de un pedido CANCELADO");
        }
        if (order.getStatus() == OrderStatus.PAID) {
            throw new IllegalStateException("No se pueden eliminar items de un pedido PAGADO");
        }
        
        // Validate DELIVERY orders in ON_THE_WAY or superior states
        if (order.getOrderType() == OrderType.DELIVERY) {
            if (order.getStatus() == OrderStatus.ON_THE_WAY) {
                throw new IllegalStateException("No se pueden eliminar items de un pedido de DELIVERY que está EN CAMINO");
            }
            if (order.getStatus() == OrderStatus.DELIVERED) {
                throw new IllegalStateException("No se pueden eliminar items de un pedido de DELIVERY que ya fue ENTREGADO");
            }
        }

        // Find the item detail in the order
        OrderDetail itemToDelete = order.getOrderDetails().stream()
                .filter(detail -> detail.getIdOrderDetail().equals(itemDetailId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Item no encontrado en el pedido"));

        // ===== COMBO PROTECTION: Prevent deleting individual combo children =====
        if (itemToDelete.isComboChild() && !Boolean.TRUE.equals(itemToDelete.getItemMenu().getIsCombo())) {
            throw new IllegalStateException(
                "No se puede eliminar un item individual del combo. " +
                "Para eliminar este item, debe eliminar el combo completo."
            );
        }

        // ===== COMBO PARENT: Collect all combo children for group deletion =====
        List<OrderDetail> comboChildrenToDelete = new ArrayList<>();
        if (itemToDelete.getComboGroupId() != null && Boolean.TRUE.equals(itemToDelete.getItemMenu().getIsCombo())) {
            String comboGroupId = itemToDelete.getComboGroupId();
            comboChildrenToDelete = order.getOrderDetails().stream()
                .filter(d -> comboGroupId.equals(d.getComboGroupId()) && !d.getIdOrderDetail().equals(itemDetailId))
                .collect(java.util.stream.Collectors.toList());
            log.info("Combo parent deletion - will also delete {} child items (groupId: {})", 
                comboChildrenToDelete.size(), comboGroupId);
        }

        // Validate item is not DELIVERED
        if (itemToDelete.getItemStatus() == OrderStatus.DELIVERED) {
            throw new IllegalStateException("No se puede eliminar un item que ya fue ENTREGADO");
        }

        // Check if this is the last item(s) in the order (account for combo children being deleted too)
        int totalItemsToDelete = 1 + comboChildrenToDelete.size();
        if (order.getOrderDetails().size() <= totalItemsToDelete) {
            throw new IllegalStateException("LAST_ITEM_CANCEL_ORDER");
        }

        // Check if stock should be returned automatically
        boolean returnStockAuto = shouldReturnStockAutomatically(itemToDelete);
        
        if (returnStockAuto) {
            // Return stock automatically
            ItemMenu itemMenu = itemToDelete.getItemMenu();
            int quantity = itemToDelete.getQuantity();
            
            log.info("Returning stock automatically for item '{}' (quantity: {})", 
                    itemMenu.getName(), quantity);
            
            try {
                returnStockForItem(itemMenu, quantity);
                
                // Return stock for complements
                returnStockForComplements(itemToDelete);
                
                log.info("Stock returned successfully for item '{}'", itemMenu.getName());
            } catch (Exception e) {
                log.error("Error returning stock for item '{}': {}", itemMenu.getName(), e.getMessage());
                throw new IllegalStateException("Error al devolver el stock: " + e.getMessage());
            }
        } else {
            log.info("Stock must be returned MANUALLY for item '{}' (status: {}, requiresPrep: {})", 
                    itemToDelete.getItemMenu().getName(),
                    itemToDelete.getItemStatus(),
                    itemToDelete.getItemMenu().getRequiresPreparation());
        }

        // Remove item from order
        order.getOrderDetails().remove(itemToDelete);

        // Remove combo children if this was a combo parent
        if (!comboChildrenToDelete.isEmpty()) {
            for (OrderDetail child : comboChildrenToDelete) {
                // Return stock for combo children
                if (shouldReturnStockAutomatically(child)) {
                    returnStockForItem(child.getItemMenu(), child.getQuantity());
                    returnStockForComplements(child);
                }
                order.getOrderDetails().remove(child);
                log.info("Removed combo child '{}' from order {}", 
                    child.getItemMenu().getName(), order.getOrderNumber());
            }
        }

        // Recalculate order amounts (subtotal, tax, and total)
        // This uses the Order entity's built-in calculation methods:
        // - calculateSubtotal(): sums all item subtotals
        // - calculateTaxAmount(): applies tax rate from system config
        // - calculateTotal(): subtotal + tax
        order.recalculateAmounts();

        // Recalculate order status based on remaining items
        if (!order.getOrderDetails().isEmpty()) {
            OrderStatus newOrderStatus = order.calculateStatusFromItems();
            order.setStatus(newOrderStatus);
            log.info("Order status recalculated to: {}", newOrderStatus);
        } else {
            log.warn("Order {} now has no items after deletion", orderId);
            // Keep current status if no items remain
        }

        // Save changes
        orderRepository.save(order);
        
        log.info("Item '{}' deleted from order {}. New subtotal: {}, New total: {}, New status: {}", 
                itemToDelete.getItemMenu().getName(), 
                order.getOrderNumber(),
                order.getFormattedSubtotal(),
                order.getFormattedTotal(),
                order.getStatus());

        // Send WebSocket notification for item deletion
        // This will notify chef/barista based on item type
        try {
            wsNotificationService.notifyItemDeleted(order, itemToDelete);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for item deletion: {}", e.getMessage());
        }

        return itemToDelete;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalIncome() {
        log.info("Calculating total income from all PAID orders");
        Company company = CompanyContext.requireCurrentCompany();
        BigDecimal totalIncome = orderRepository.getTotalIncomeByCompany(company);
        return totalIncome != null ? totalIncome : BigDecimal.ZERO;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getIncomeByCategory() {
        log.info("Getting income grouped by menu category");
        
        List<Object[]> results = orderDetailRepository.getIncomeByMenuCategory();
        Map<String, BigDecimal> incomeMap = new java.util.LinkedHashMap<>();

        for (Object[] row : results) {
            String categoryName = (String) row[1];
            BigDecimal totalIncome = (BigDecimal) row[2];
            incomeMap.put(categoryName, totalIncome != null ? totalIncome : BigDecimal.ZERO);
        }

        return incomeMap;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Object[]> getItemSalesByCategory(Long categoryId) {
        log.info("Getting items sold for category ID: {}", categoryId);
        return orderDetailRepository.getItemSalesByCategory(categoryId);
    }

    /**
     * Delete a specific complement from an order detail
     * Only allows deleting complements when the parent item is not DELIVERED
     * Returns stock automatically following same rules as deleteOrderItem
     */
    @Override
    @Transactional
    public OrderDetailComplement deleteOrderItemComplement(Long orderId, Long itemDetailId, Long complementId, String username) {
        log.info("Deleting complement {} from item {} of order {} by user {}", 
                complementId, itemDetailId, orderId, username);

        // Find order
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado con ID: " + orderId));

        // Validate order is not in final states
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("No se pueden eliminar complementos de un pedido CANCELADO");
        }
        if (order.getStatus() == OrderStatus.PAID) {
            throw new IllegalStateException("No se pueden eliminar complementos de un pedido PAGADO");
        }
        
        // Validate DELIVERY orders in ON_THE_WAY or superior states
        if (order.getOrderType() == OrderType.DELIVERY) {
            if (order.getStatus() == OrderStatus.ON_THE_WAY) {
                throw new IllegalStateException("No se pueden eliminar complementos de un pedido de DELIVERY que está EN CAMINO");
            }
            if (order.getStatus() == OrderStatus.DELIVERED) {
                throw new IllegalStateException("No se pueden eliminar complementos de un pedido de DELIVERY que ya fue ENTREGADO");
            }
        }

        // Find the item detail in the order
        OrderDetail itemDetail = order.getOrderDetails().stream()
                .filter(detail -> detail.getIdOrderDetail().equals(itemDetailId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Item no encontrado en el pedido"));

        // Validate item is not DELIVERED
        if (itemDetail.getItemStatus() == OrderStatus.DELIVERED) {
            throw new IllegalStateException("No se puede eliminar un complemento de un item que ya fue ENTREGADO");
        }

        // Find the complement in the item
        OrderDetailComplement complementToDelete = itemDetail.getSelectedComplements().stream()
                .filter(odc -> odc.getIdOrderDetailComplement().equals(complementId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Complemento no encontrado en el item"));

        // Check if stock should be returned automatically (same logic as items)
        boolean returnStockAuto = shouldReturnStockAutomatically(itemDetail);
        
        if (returnStockAuto) {
            // Return stock automatically for this complement
            try {
                if (Boolean.TRUE.equals(complementToDelete.getStockDeducted())) {
                    Complement complement = complementToDelete.getComplement();
                    if (complement.getIngredients() != null && !complement.getIngredients().isEmpty()) {
                        // For sauces, multiply by parent item quantity (sauces are per-serving)
                        int effectiveQty = complementToDelete.getQuantity();
                        if (Boolean.TRUE.equals(complement.getIsSauce())) {
                            effectiveQty = complementToDelete.getQuantity() * itemDetail.getQuantity();
                        }
                        complementStockService.returnStockForComplement(
                            new ArrayList<>(complement.getIngredients()), 
                            effectiveQty
                        );
                    }
                    
                    // Update complement availability
                    complement.updateAvailability();
                    complementRepository.save(complement);
                    
                    log.info("Stock returned automatically for complement '{}' (quantity: {}, effective: {})", 
                            complement.getName(), complementToDelete.getQuantity(),
                            Boolean.TRUE.equals(complement.getIsSauce()) ? complementToDelete.getQuantity() * itemDetail.getQuantity() : complementToDelete.getQuantity());
                }
            } catch (Exception e) {
                log.error("Error returning stock for complement '{}': {}", 
                        complementToDelete.getComplement().getName(), e.getMessage());
                throw new IllegalStateException("Error al devolver el stock del complemento: " + e.getMessage());
            }
        } else {
            log.info("Stock must be returned MANUALLY for complement '{}' (item status: {})", 
                    complementToDelete.getComplement().getName(),
                    itemDetail.getItemStatus());
        }

        // Remove complement from item
        itemDetail.getSelectedComplements().remove(complementToDelete);
        orderDetailComplementRepository.delete(complementToDelete);

        // Recalculate order amounts (since complement subtotal is no longer part of the total)
        order.recalculateAmounts();

        // Save changes
        orderRepository.save(order);
        
        log.info("Complement '{}' deleted from item '{}' in order {}. New subtotal: {}, New total: {}", 
                complementToDelete.getComplement().getName(),
                itemDetail.getItemMenu().getName(),
                order.getOrderNumber(),
                order.getFormattedSubtotal(),
                order.getFormattedTotal());

        return complementToDelete;
    }
}
