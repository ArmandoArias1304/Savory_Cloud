package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.*;
import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for Cashier role
 * Handles all cashier operations for order management and payment collection
 */
@Controller
@RequestMapping("/cashier")
@PreAuthorize("hasRole('ROLE_CASHIER')")
@Slf4j
public class CashierController {

    private final CashierOrderServiceImpl cashierOrderService;
    private final OrderService adminOrderService;
    private final RestaurantTableService restaurantTableService;
    private final ItemMenuService itemMenuService;
    private final EmployeeService employeeService;
    private final SystemConfigurationService systemConfigurationService;
    private final CategoryService categoryService;
    private final com.aatechsolutions.elgransazon.domain.repository.OrderRepository orderRepository;
    private final PromotionService promotionService;
    private final BusinessHoursService businessHoursService;
    private final WebSocketNotificationService wsNotificationService;

    public CashierController(
            @Qualifier("cashierOrderService") CashierOrderServiceImpl cashierOrderService,
            @Qualifier("adminOrderService") OrderService adminOrderService,
            RestaurantTableService restaurantTableService,
            ItemMenuService itemMenuService,
            EmployeeService employeeService,
            SystemConfigurationService systemConfigurationService,
            CategoryService categoryService,
            com.aatechsolutions.elgransazon.domain.repository.OrderRepository orderRepository,
            PromotionService promotionService,
            BusinessHoursService businessHoursService,
            WebSocketNotificationService wsNotificationService) {
        this.cashierOrderService = cashierOrderService;
        this.adminOrderService = adminOrderService;
        this.restaurantTableService = restaurantTableService;
        this.itemMenuService = itemMenuService;
        this.employeeService = employeeService;
        this.systemConfigurationService = systemConfigurationService;
        this.categoryService = categoryService;
        this.orderRepository = orderRepository;
        this.promotionService = promotionService;
        this.businessHoursService = businessHoursService;
        this.wsNotificationService = wsNotificationService;
    }

    /**
     * Display cashier dashboard
     */
    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        String username = authentication.getName();
        log.info("Cashier {} accessed dashboard", username);
        
        // Get system configuration
        SystemConfiguration config = systemConfigurationService.getConfiguration();
        
        // Check if restaurant is currently open
        boolean isRestaurantOpen = businessHoursService.isOpenNow();
        
        model.addAttribute("config", config);
        model.addAttribute("username", username);
        model.addAttribute("role", "Cajero");
        model.addAttribute("isRestaurantOpen", isRestaurantOpen);
        log.debug("Restaurant is currently: {}", isRestaurantOpen ? "open" : "closed");
        
        return "cashier/dashboard";
    }

    /**
     * Show list of orders created by current cashier
     * Also shows global unpaid orders (DELIVERED) that can be collected
     */
    @GetMapping("/orders")
    public String listOrders(
            @RequestParam(required = false) Long tableId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) OrderType orderType,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Long globalTableId,
            @RequestParam(required = false) OrderStatus globalStatus,
            @RequestParam(required = false) OrderType globalOrderType,
            @RequestParam(required = false) String globalDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "1") int globalPage,
            Authentication authentication,
            Model model) {
        
        String username = authentication.getName();
        log.debug("Cashier {} displaying orders list with filters - table: {}, status: {}, type: {}, date: {}", 
                  username, tableId, status, orderType, date);

        // Get orders created by current cashier (like waiter)
        List<Order> myOrders = cashierOrderService.findOrdersByCurrentEmployee();

        // Apply filters to myOrders
        if (date != null && !date.isEmpty()) {
            LocalDateTime startDate = LocalDateTime.parse(date + "T00:00:00");
            LocalDateTime endDate = LocalDateTime.parse(date + "T23:59:59");
            myOrders = myOrders.stream()
                .filter(order -> order.getCreatedAt().isAfter(startDate) && order.getCreatedAt().isBefore(endDate))
                .collect(Collectors.toList());
        }

        if (tableId != null) {
            Long finalTableId = tableId;
            myOrders = myOrders.stream()
                .filter(order -> order.getTable() != null && order.getTable().getId().equals(finalTableId))
                .collect(Collectors.toList());
        }

        if (status != null) {
            myOrders = myOrders.stream()
                .filter(order -> order.getStatus() == status)
                .collect(Collectors.toList());
        }

        if (orderType != null) {
            myOrders = myOrders.stream()
                .filter(order -> order.getOrderType() == orderType)
                .collect(Collectors.toList());
        }

        // Sort by creation date (most recent first)
        myOrders = myOrders.stream()
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
            .collect(Collectors.toList());

        // Get global orders (PENDING to PAID) - includes PAID as history
        // Use adminOrderService to get ALL orders (not filtered by employee)
        // EXCLUDE orders created by current cashier (those are already in first table)
        // For PAID orders, only show those collected by current cashier
        List<Order> unpaidOrders = adminOrderService.findAll().stream()
            .filter(o -> {
                // EXCLUDE orders created by current cashier
                if (o.getCreatedBy() != null && o.getCreatedBy().equals(username)) {
                    return false;
                }
                
                // Show all non-PAID orders (created by others)
                if (o.getStatus() == OrderStatus.PENDING ||
                    o.getStatus() == OrderStatus.IN_PREPARATION ||
                    o.getStatus() == OrderStatus.READY ||
                    o.getStatus() == OrderStatus.DELIVERED) {
                    return true;
                }
                
                // For PAID orders, only show if current cashier collected payment
                // (but not created by them - already excluded above)
                if (o.getStatus() == OrderStatus.PAID) {
                    return o.getPaidBy() != null && o.getPaidBy().getUsername().equals(username);
                }
                
                return false;
            })
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
            .collect(Collectors.toList());

        // Apply filters to unpaidOrders (Global filters)
        if (globalDate != null && !globalDate.isEmpty()) {
            LocalDateTime startDate = LocalDateTime.parse(globalDate + "T00:00:00");
            LocalDateTime endDate = LocalDateTime.parse(globalDate + "T23:59:59");
            unpaidOrders = unpaidOrders.stream()
                .filter(order -> order.getCreatedAt().isAfter(startDate) && order.getCreatedAt().isBefore(endDate))
                .collect(Collectors.toList());
        }

        if (globalTableId != null) {
            Long finalGlobalTableId = globalTableId;
            unpaidOrders = unpaidOrders.stream()
                .filter(order -> order.getTable() != null && order.getTable().getId().equals(finalGlobalTableId))
                .collect(Collectors.toList());
        }

        if (globalStatus != null) {
            unpaidOrders = unpaidOrders.stream()
                .filter(order -> order.getStatus() == globalStatus)
                .collect(Collectors.toList());
        }

        if (globalOrderType != null) {
            unpaidOrders = unpaidOrders.stream()
                .filter(order -> order.getOrderType() == globalOrderType)
                .collect(Collectors.toList());
        }

        // ========== Calculate date range for statistics ==========
        // If date filter is applied, use that date; otherwise use today
        LocalDateTime statsStartDate;
        LocalDateTime statsEndDate;
        if (date != null && !date.isEmpty()) {
            statsStartDate = LocalDateTime.parse(date + "T00:00:00");
            statsEndDate = LocalDateTime.parse(date + "T23:59:59");
        } else {
            statsStartDate = java.time.LocalDate.now().atStartOfDay();
            statsEndDate = java.time.LocalDate.now().atTime(23, 59, 59);
        }

        // ========== Calculate statistics (dynamic based on date filter) ==========
        // paidCount: Count of PAID orders collected by current cashier in selected date range
        long paidCount = cashierOrderService.countPaidOrdersByUsernameAndDateRange(username, statsStartDate, statsEndDate);
        
        // Revenue: All orders PAID in date range where paidBy = current cashier (En Caja Hoy)
        // This is what the cashier has collected - regardless of who created the orders
        BigDecimal myTodayRevenue = cashierOrderService.getRevenueByUsernameAndDateRange(username, statsStartDate, statsEndDate);
        
        long myPendingCount = myOrders.stream()
            .filter(o -> o.getStatus() == OrderStatus.PENDING)
            .count();
        
        long myPaidCount = myOrders.stream()
            .filter(o -> o.getStatus() == OrderStatus.PAID)
            .count();
        
        long inPreparationCount = myOrders.stream()
            .filter(o -> o.getStatus() == OrderStatus.IN_PREPARATION)
            .count();
        
        // My Own Revenue: Orders CREATED AND PAID by current cashier in selected date range (Ingresos Propios)
        // This is orders the cashier created AND also collected payment for
        BigDecimal myOwnRevenue = cashierOrderService.getRevenueCreatedAndPaidBySameUserAndDateRange(username, statsStartDate, statsEndDate);
        
        // Statistics for unpaid orders (global) - exclude PAID from total
        long unpaidCount = unpaidOrders.stream()
            .filter(o -> o.getStatus() != OrderStatus.PAID)
            .count();
        BigDecimal unpaidTotal = unpaidOrders.stream()
            .filter(o -> o.getStatus() != OrderStatus.PAID)
            .map(Order::getTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Count of PAID orders (history)
        long paidOrdersCount = unpaidOrders.stream()
            .filter(o -> o.getStatus() == OrderStatus.PAID)
            .count();

        // Get filter data
        List<RestaurantTable> tables = restaurantTableService.findAllOrderByTableNumber();
        OrderStatus[] statuses = OrderStatus.values();
        OrderType[] orderTypes = OrderType.values();

        // Server-side pagination for myOrders
        int pageSize = 15;
        int myTotalElements = myOrders.size();
        int myTotalPages = (int) Math.ceil((double) myTotalElements / pageSize);
        if (myTotalPages == 0) myTotalPages = 1;
        page = Math.max(1, Math.min(page, myTotalPages));
        int myStartIdx = (page - 1) * pageSize;
        int myEndIdx = Math.min(myStartIdx + pageSize, myTotalElements);
        List<Order> pagedMyOrders = myTotalElements > 0 ? myOrders.subList(myStartIdx, myEndIdx) : myOrders;

        // Server-side pagination for unpaidOrders (global)
        int globalTotalElements = unpaidOrders.size();
        int globalTotalPages = (int) Math.ceil((double) globalTotalElements / pageSize);
        if (globalTotalPages == 0) globalTotalPages = 1;
        globalPage = Math.max(1, Math.min(globalPage, globalTotalPages));
        int globalStartIdx = (globalPage - 1) * pageSize;
        int globalEndIdx = Math.min(globalStartIdx + pageSize, globalTotalElements);
        List<Order> pagedUnpaidOrders = globalTotalElements > 0 ? unpaidOrders.subList(globalStartIdx, globalEndIdx) : unpaidOrders;

        model.addAttribute("myOrders", pagedMyOrders);
        model.addAttribute("unpaidOrders", pagedUnpaidOrders);

        // Pagination metadata for myOrders
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", myTotalPages);
        model.addAttribute("totalElements", myTotalElements);
        model.addAttribute("pageSize", pageSize);

        // Pagination metadata for global orders
        model.addAttribute("globalCurrentPage", globalPage);
        model.addAttribute("globalTotalPages", globalTotalPages);
        model.addAttribute("globalTotalElements", globalTotalElements);

        model.addAttribute("tables", tables);
        model.addAttribute("statuses", statuses);
        model.addAttribute("orderTypes", orderTypes);
        model.addAttribute("paidCount", paidCount);
        model.addAttribute("todayRevenue", myTodayRevenue);
        model.addAttribute("myPendingCount", myPendingCount);
        model.addAttribute("myPaidCount", myPaidCount);
        model.addAttribute("inPreparationCount", inPreparationCount);
        model.addAttribute("myOwnRevenue", myOwnRevenue);
        model.addAttribute("unpaidCount", unpaidCount);
        model.addAttribute("unpaidTotal", unpaidTotal);
        model.addAttribute("paidOrdersCount", paidOrdersCount);
        model.addAttribute("selectedTableId", tableId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedOrderType", orderType);
        model.addAttribute("selectedDate", date);
        
        model.addAttribute("selectedGlobalTableId", globalTableId);
        model.addAttribute("selectedGlobalStatus", globalStatus);
        model.addAttribute("selectedGlobalOrderType", globalOrderType);
        model.addAttribute("selectedGlobalDate", globalDate);
        
        model.addAttribute("currentRole", "cashier");

        return "cashier/orders/list";
    }

    /**
     * Show table selection view for DINE_IN orders
     */
    @GetMapping("/orders/select-table")
    public String selectTable(Authentication authentication, Model model) {
        log.debug("Displaying table selection for new order - role: cashier");

        // Get all tables
        List<RestaurantTable> allTables = restaurantTableService.findAllOrderByTableNumber();
        
        // Create maps to store calculated statuses for each table
        Map<Long, Boolean> canBeUsedMap = new HashMap<>();
        Map<Long, Boolean> blockedByReservationMap = new HashMap<>();
        Map<Long, Boolean> usableWithReservationMap = new HashMap<>();
        Map<Long, Boolean> inConflictMap = new HashMap<>();
        Map<Long, Boolean> reservedNowMap = new HashMap<>();
        Map<Long, Long> minutesUntilReservationMap = new HashMap<>();
        
        // Get avg consumption time for display
        Integer avgConsumptionMinutes = systemConfigurationService.getConfiguration().getAverageConsumptionTimeMinutes();
        
        // Count tables by status and availability
        long availableCount = 0;
        long occupiedCount = 0;
        long blockedByReservationCount = 0;
        long usableWithReservationCount = 0;
        long inConflictCount = 0;
        long outOfServiceCount = 0;
        
        for (RestaurantTable table : allTables) {
            boolean canUse = restaurantTableService.canTableBeUsedForOrder(table.getId());
            boolean isBlocked = restaurantTableService.isTableBlockedByReservation(table.getId());
            boolean isUsableWithReservation = restaurantTableService.isTableUsableWithReservation(table.getId());
            boolean isInConflict = restaurantTableService.isTableInConflict(table.getId());
            boolean isReservedNow = restaurantTableService.isTableReservedNow(table.getId());
            Long minutesUntil = restaurantTableService.getMinutesUntilNextReservation(table.getId());
            
            canBeUsedMap.put(table.getId(), canUse);
            blockedByReservationMap.put(table.getId(), isBlocked);
            usableWithReservationMap.put(table.getId(), isUsableWithReservation);
            inConflictMap.put(table.getId(), isInConflict);
            reservedNowMap.put(table.getId(), isReservedNow);
            if (minutesUntil != null) {
                minutesUntilReservationMap.put(table.getId(), minutesUntil);
            }
            
            if (table.getStatus() == TableStatus.AVAILABLE) {
                if (isBlocked) {
                    blockedByReservationCount++;
                } else if (isUsableWithReservation) {
                    usableWithReservationCount++;
                    availableCount++; // Also count as available since it can be used
                } else {
                    availableCount++;
                }
            } else if (table.getStatus() == TableStatus.OCCUPIED) {
                if (isInConflict) {
                    inConflictCount++;
                }
                occupiedCount++;
            } else if (table.getStatus() == TableStatus.OUT_OF_SERVICE) {
                outOfServiceCount++;
            }
        }

        model.addAttribute("tables", allTables);
        model.addAttribute("canBeUsedMap", canBeUsedMap);
        model.addAttribute("blockedByReservationMap", blockedByReservationMap);
        model.addAttribute("usableWithReservationMap", usableWithReservationMap);
        model.addAttribute("inConflictMap", inConflictMap);
        model.addAttribute("reservedNowMap", reservedNowMap);
        model.addAttribute("minutesUntilReservationMap", minutesUntilReservationMap);
        model.addAttribute("avgConsumptionMinutes", avgConsumptionMinutes);
        model.addAttribute("availableCount", availableCount);
        model.addAttribute("occupiedCount", occupiedCount);
        model.addAttribute("blockedByReservationCount", blockedByReservationCount);
        model.addAttribute("usableWithReservationCount", usableWithReservationCount);
        model.addAttribute("inConflictCount", inConflictCount);
        model.addAttribute("outOfServiceCount", outOfServiceCount);
        model.addAttribute("totalCount", allTables.size());
        model.addAttribute("currentRole", "cashier");

        return "cashier/orders/order-table-selection";
    }

    /**
     * Show customer information form before order menu
     */
    @GetMapping("/orders/customer-info")
    public String customerInfoForm(
            @RequestParam(required = false) Long tableId,
            @RequestParam String orderType,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.debug("Displaying customer info form - OrderType: {}, TableId: {}", orderType, tableId);
        
        try {
            OrderType type = OrderType.valueOf(orderType);
            
            // Validate table for DINE_IN orders
            if (type == OrderType.DINE_IN && tableId != null) {
                RestaurantTable table = restaurantTableService.findById(tableId)
                    .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada"));
                
                if (!cashierOrderService.isTableAvailableForOrder(tableId)) {
                    redirectAttributes.addFlashAttribute("errorMessage", 
                        "La mesa seleccionada no está disponible para pedidos");
                    return "redirect:/cashier/orders/select-table";
                }
                
                model.addAttribute("selectedTable", table);  // Cambiar "table" a "selectedTable"
            }
            
            model.addAttribute("orderType", type);
            model.addAttribute("tableId", tableId);
            model.addAttribute("currentRole", "cashier");
            
            return "cashier/orders/order-customer-info";
            
        } catch (IllegalArgumentException e) {
            log.error("Invalid order type: {}", orderType);
            redirectAttributes.addFlashAttribute("errorMessage", "Tipo de orden inválido");
            return "redirect:/cashier/orders/select-table";
        }
    }

    /**
     * Show menu items selection with cart
     */
    @GetMapping("/orders/menu")
    public String menuSelection(
            @RequestParam String orderType,
            @RequestParam(required = false) Long tableId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String customerPhone,
            @RequestParam(required = false) String deliveryAddress,
            @RequestParam(required = false) String deliveryReferences,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.debug("Displaying menu selection - OrderType: {}, TableId: {}", orderType, tableId);
        
        try {
            OrderType type = OrderType.valueOf(orderType);
            
            // Validate customer info for TAKEOUT and DELIVERY
            if (type == OrderType.TAKEOUT || type == OrderType.DELIVERY) {
                if (customerName == null || customerName.trim().isEmpty()) {
                    redirectAttributes.addFlashAttribute("errorMessage", 
                        "El nombre del cliente es requerido para pedidos para pasar a recoger o delivery");
                    return "redirect:/cashier/orders/customer-info?orderType=" + orderType + 
                           (tableId != null ? "&tableId=" + tableId : "");
                }
            }
            
            // Get table info if DINE_IN
            RestaurantTable selectedTable = null;
            if (type == OrderType.DINE_IN && tableId != null) {
                selectedTable = restaurantTableService.findById(tableId)
                    .orElse(null);
            }
            
            // Update availability for all items based on current stock
            itemMenuService.updateAllItemsAvailability();
            
            // Get available menu items
            List<ItemMenu> availableItems = itemMenuService.findAvailableItems();
            
            // Group items by category ID for easier display
            Map<Long, List<ItemMenu>> itemsByCategory = availableItems.stream()
                .collect(Collectors.groupingBy(item -> item.getCategory().getIdCategory()));
            
            // Get all active categories - ONLY those with active items
            List<Category> categories = categoryService.getAllActiveCategories().stream()
                .filter(category -> itemsByCategory.containsKey(category.getIdCategory()))
                .collect(Collectors.toList());
            
            // Get current employee
            String username = authentication.getName();
            Employee employee = employeeService.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Empleado no encontrado"));
            
            // Get system configuration for tax rate
            SystemConfiguration config = systemConfigurationService.getConfiguration();
            
            // Get enabled payment methods based on order type
            Map<PaymentMethodType, Boolean> paymentMethods = type == OrderType.DELIVERY 
                ? config.getDeliveryPaymentMethods() 
                : config.getPaymentMethods();
            List<PaymentMethodType> enabledPaymentMethods = paymentMethods.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
            
            // Validate at least one payment method is enabled
            if (enabledPaymentMethods.isEmpty()) {
                String orderTypeText = type == OrderType.DELIVERY ? "entregas a domicilio" : "el restaurante";
                log.warn("No payment methods enabled for {} in system configuration", orderTypeText);
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "No hay métodos de pago habilitados para " + orderTypeText + ". Por favor contacte al administrador.");
                return "redirect:/cashier/orders";
            }
            
            model.addAttribute("orderType", type);
            model.addAttribute("selectedTable", selectedTable);
            model.addAttribute("customerName", customerName);
            model.addAttribute("customerPhone", customerPhone);
            model.addAttribute("deliveryAddress", deliveryAddress);
            model.addAttribute("deliveryReferences", deliveryReferences);
            model.addAttribute("categories", categories);
            model.addAttribute("itemsByCategory", itemsByCategory);
            model.addAttribute("allItems", availableItems);
            model.addAttribute("employee", employee);
            model.addAttribute("config", config);
            model.addAttribute("taxRate", config.getTaxRate());
            model.addAttribute("enabledPaymentMethods", enabledPaymentMethods);
            model.addAttribute("currentRole", "cashier");
            
            return "cashier/orders/order-menu";
            
        } catch (IllegalArgumentException e) {
            log.error("Invalid order type: {}", orderType);
            redirectAttributes.addFlashAttribute("errorMessage", "Tipo de orden inválido");
            return "redirect:/cashier/orders/select-table";
        }
    }

    /**
     * Create a new order
     */
    @PostMapping("/orders")
    public String createOrder(
            @ModelAttribute("order") Order order,
            BindingResult bindingResult,
            @RequestParam(value = "employeeId", required = true) Long employeeId,
            @RequestParam(value = "tableId", required = false) Long tableId,
            @RequestParam(value = "itemIds", required = false) List<Long> itemIds,
            @RequestParam(value = "quantities", required = false) List<Integer> quantities,
            @RequestParam(value = "comments", required = false) List<String> comments,
            @RequestParam(value = "promotionPrices", required = false) List<String> promotionPrices,
            @RequestParam(value = "promotionIds", required = false) List<String> promotionIds,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        String username = authentication.getName();
        log.info("===== CREATING ORDER =====");
        log.info("Cashier: {}", username);
        log.info("Employee ID (param): {}", employeeId);
        log.info("Order Type: {}", order.getOrderType());
        log.info("Table ID (param): {}", tableId);
        log.info("Table in Order object: {}", order.getTable());
        log.info("Customer Name: {}", order.getCustomerName());
        log.info("Customer Phone: {}", order.getCustomerPhone());
        log.info("Payment Method: {}", order.getPaymentMethod());
        log.info("=========================");

        try {
            // Set employee
            Employee employee = employeeService.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado con ID: " + employeeId));
            order.setEmployee(employee);
            
            // Set table if provided (required for DINE_IN)
            if (tableId != null) {
                RestaurantTable table = restaurantTableService.findById(tableId)
                    .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada con ID: " + tableId));
                order.setTable(table);
            }
            
            // Build order details
            List<OrderDetail> orderDetails = buildOrderDetails(itemIds, quantities, comments, promotionPrices, promotionIds);

            if (orderDetails.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Debe agregar al menos un item al pedido");
                return "redirect:/cashier/orders/menu?orderType=" + order.getOrderType() + 
                       (tableId != null ? "&tableId=" + tableId : "");
            }

            // Set audit fields
            order.setCreatedBy(username);

            // Create the order
            Order createdOrder = cashierOrderService.create(order, orderDetails);

            log.info("Order created successfully by cashier: {}", createdOrder.getOrderNumber());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Pedido creado exitosamente: " + createdOrder.getOrderNumber());

            return "redirect:/cashier/orders";

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Validation error creating order: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/cashier/orders/menu?orderType=" + order.getOrderType() + 
                   (tableId != null ? "&tableId=" + tableId : "");
        } catch (Exception e) {
            log.error("Error creating order", e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error al crear el pedido: " + e.getMessage());
            return "redirect:/cashier/orders/select-table";
        }
    }

    /**
     * View order details
     */
    @GetMapping("/orders/view/{id}")
    public String viewOrder(
            @PathVariable Long id,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.debug("Viewing order details. ID: {}", id);

        return cashierOrderService.findByIdWithDetails(id)
                .map(order -> {
                    model.addAttribute("order", order);
                    model.addAttribute("orderDetails", order.getOrderDetails());
                    model.addAttribute("currentRole", "cashier");
                    return "cashier/orders/view";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Pedido no encontrado");
                    return "redirect:/cashier/orders";
                });
    }

    /**
     * Show edit form for an order (only PENDING status)
     */
    @GetMapping("/orders/edit/{id}")
    public String editOrderForm(
            @PathVariable Long id,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        String username = authentication.getName();
        log.debug("Cashier {} accessing edit form for order {}", username, id);

        try {
            Order order = cashierOrderService.findByIdWithDetails(id)
                    .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

            // Cannot edit PAID or CANCELLED orders
            if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.CANCELLED) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "No se pueden editar pedidos PAGADOS o CANCELADOS");
                return "redirect:/cashier/orders";
            }

            // Get current employee
            Employee employee = employeeService.findByUsername(username)
                    .orElseThrow(() -> new IllegalStateException("Empleado no encontrado"));

            // Get available tables (for DINE_IN orders)
            List<RestaurantTable> availableTables = restaurantTableService.findAllOrderByTableNumber();

            // Get available menu items
            List<ItemMenu> availableItems = itemMenuService.findAvailableItems();
            
            // Convert to simple DTOs to avoid circular reference issues
            List<Map<String, Object>> availableItemsDTO = availableItems.stream()
                    .map(item -> {
                        Map<String, Object> dto = new HashMap<>();
                        dto.put("idItemMenu", item.getIdItemMenu());
                        dto.put("name", item.getName());
                        dto.put("price", item.getPrice());
                        return dto;
                    })
                    .collect(Collectors.toList());

            // Get system configuration for tax rate
            SystemConfiguration config = systemConfigurationService.getConfiguration();

            // Get enabled payment methods based on order type
            Map<PaymentMethodType, Boolean> paymentMethodsMap = order.getOrderType() == OrderType.DELIVERY 
                ? config.getDeliveryPaymentMethods() 
                : config.getPaymentMethods();
            List<PaymentMethodType> enabledPaymentMethods = paymentMethodsMap.entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            
            // Also pass both payment method sets for dynamic updates as Maps with name and displayName
            List<Map<String, String>> regularPaymentMethodsDTO = config.getPaymentMethods().entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .map(method -> {
                        Map<String, String> dto = new HashMap<>();
                        dto.put("name", method.name());
                        dto.put("displayName", method.getDisplayName());
                        return dto;
                    })
                    .collect(Collectors.toList());
            
            List<Map<String, String>> deliveryPaymentMethodsDTO = config.getDeliveryPaymentMethods().entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .map(method -> {
                        Map<String, String> dto = new HashMap<>();
                        dto.put("name", method.name());
                        dto.put("displayName", method.getDisplayName());
                        return dto;
                    })
                    .collect(Collectors.toList());
            
            // Convert order details to simple DTOs
            List<Map<String, Object>> orderDetailsDTO = order.getOrderDetails().stream()
                    .map(detail -> {
                        Map<String, Object> dto = new HashMap<>();
                        dto.put("quantity", detail.getQuantity());
                        dto.put("comments", detail.getComments() != null ? detail.getComments() : "");
                        
                        // Create itemMenu DTO
                        Map<String, Object> itemMenuDTO = new HashMap<>();
                        itemMenuDTO.put("idItemMenu", detail.getItemMenu().getIdItemMenu());
                        itemMenuDTO.put("name", detail.getItemMenu().getName());
                        itemMenuDTO.put("price", detail.getItemMenu().getPrice());
                        
                        dto.put("itemMenu", itemMenuDTO);
                        return dto;
                    })
                    .collect(Collectors.toList());

            model.addAttribute("order", order);
            model.addAttribute("orderDetails", orderDetailsDTO);
            model.addAttribute("employee", employee);
            model.addAttribute("availableTables", availableTables);
            model.addAttribute("availableItems", availableItemsDTO);
            model.addAttribute("taxRate", config.getTaxRate());
            model.addAttribute("orderTypes", OrderType.values());
            model.addAttribute("paymentMethods", enabledPaymentMethods);
            model.addAttribute("regularPaymentMethods", regularPaymentMethodsDTO);
            model.addAttribute("deliveryPaymentMethods", deliveryPaymentMethodsDTO);
            model.addAttribute("formAction", "/cashier/orders/edit/" + id);
            model.addAttribute("currentRole", "cashier");
            
            // Determine if order type can be changed based on current status
            boolean canChangeOrderType = canChangeOrderType(order);
            model.addAttribute("canChangeOrderType", canChangeOrderType);

            return "cashier/orders/form";

        } catch (IllegalArgumentException e) {
            log.error("Error accessing edit form: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/cashier/orders";
        } catch (Exception e) {
            log.error("Error loading edit form for order {}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error al cargar el formulario de edición");
            return "redirect:/cashier/orders";
        }
    }

    /**
     * Update an existing order (only basic info: customer, order type, payment method)
     * Does NOT modify order items or stock - those are managed separately via add-items/delete-item
     */
    @PostMapping("/orders/edit/{id}")
    public String updateOrder(
            @PathVariable Long id,
            @ModelAttribute("order") Order order,
            @RequestParam(value = "tableId", required = false) Long tableId,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        String username = authentication.getName();
        log.info("Cashier {} updating order info (no items) {}", username, id);

        try {
            // Get existing order
            Order existingOrder = cashierOrderService.findByIdWithDetails(id)
                    .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

            // Allow editing if order is not PAID or CANCELLED
            if (existingOrder.getStatus() == OrderStatus.PAID || existingOrder.getStatus() == OrderStatus.CANCELLED) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "No se pueden editar pedidos que ya han sido pagados o cancelados");
                return "redirect:/cashier/orders";
            }
            
            // Validate order type change restrictions ONLY if the order type is actually changing
            if (order.getOrderType() != existingOrder.getOrderType()) {
                if (!canChangeOrderType(existingOrder)) {
                    String statusMessage = getOrderTypeChangeRestrictionMessage(existingOrder);
                    redirectAttributes.addFlashAttribute("errorMessage", statusMessage);
                    return "redirect:/cashier/orders/edit/" + id;
                }
            }
            
            // Validate PAID status restrictions for customer and payment fields
            if (existingOrder.getStatus() == OrderStatus.PAID) {
                // Check if any customer information or payment method is being changed
                boolean customerInfoChanged = 
                    !Objects.equals(order.getCustomerName(), existingOrder.getCustomerName()) ||
                    !Objects.equals(order.getCustomerPhone(), existingOrder.getCustomerPhone()) ||
                    !Objects.equals(order.getDeliveryAddress(), existingOrder.getDeliveryAddress()) ||
                    !Objects.equals(order.getDeliveryReferences(), existingOrder.getDeliveryReferences()) ||
                    !Objects.equals(order.getPaymentMethod(), existingOrder.getPaymentMethod());
                
                if (customerInfoChanged) {
                    redirectAttributes.addFlashAttribute("errorMessage", 
                        "No se puede modificar la información del cliente o método de pago de un pedido PAGADO");
                    return "redirect:/cashier/orders/edit/" + id;
                }
            }

            // Verify payment method is enabled in configuration based on order type
            SystemConfiguration config = systemConfigurationService.getConfiguration();
            if (order.getPaymentMethod() != null && !config.isPaymentMethodEnabledForOrderType(order.getPaymentMethod(), order.getOrderType())) {
                String context = order.getOrderType() == OrderType.DELIVERY ? " para entregas a domicilio" : "";
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "El método de pago seleccionado (" + order.getPaymentMethod().getDisplayName() + ") está deshabilitado" + context);
                return "redirect:/cashier/orders/edit/" + id;
            }

            // Set table if provided
            if (tableId != null) {
                RestaurantTable table = restaurantTableService.findById(tableId)
                        .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada"));
                order.setTable(table);
            } else {
                order.setTable(null);
            }

            // Keep the same employee (creator)
            order.setEmployee(existingOrder.getEmployee());

            // Prepare the order object with updated basic info
            existingOrder.setOrderType(order.getOrderType());
            existingOrder.setTable(order.getTable());
            existingOrder.setCustomerName(order.getCustomerName());
            existingOrder.setCustomerPhone(order.getCustomerPhone());
            existingOrder.setDeliveryAddress(order.getDeliveryAddress());
            existingOrder.setDeliveryReferences(order.getDeliveryReferences());
            existingOrder.setPaymentMethod(order.getPaymentMethod());
            existingOrder.setUpdatedBy(username);

            // Update ONLY basic order info (no items, no stock manipulation)
            // Items are managed separately via add-items and delete-item endpoints
            Order updatedOrder = cashierOrderService.updateOrderInfo(id, existingOrder);

            log.info("Order {} info updated successfully by cashier {}", updatedOrder.getOrderNumber(), username);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Pedido actualizado exitosamente: " + updatedOrder.getOrderNumber());

            return "redirect:/cashier/orders";

        } catch (IllegalArgumentException e) {
            log.error("Validation error updating order: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/cashier/orders/edit/" + id;
        } catch (Exception e) {
            log.error("Error updating order {}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error al actualizar el pedido: " + e.getMessage());
            return "redirect:/cashier/orders";
        }
    }

    /**
     * Change order status (AJAX) - Cashier can only mark DELIVERED as PAID
     */
    @PostMapping("/orders/{id}/change-status")
    @ResponseBody
    public Map<String, Object> changeStatus(
            @PathVariable Long id,
            @RequestParam String newStatus,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Cashier {} changing order {} status to {}", username, id, newStatus);

        Map<String, Object> response = new HashMap<>();
        
        try {
            OrderStatus status = OrderStatus.valueOf(newStatus);
            
            // Get current employee
            Employee currentEmployee = employeeService.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Empleado no encontrado"));
            
            // Get the order to check current state
            Order order = cashierOrderService.findByIdOrThrow(id);
            
            // Cashier can collect payment with ANY payment method (including CASH)
            // ALWAYS update paidBy to reflect who is actually collecting the payment
            if (status == OrderStatus.PAID) {
                order.setPaidBy(currentEmployee);
                orderRepository.save(order);
                log.info("Setting paidBy to cashier: {}", username);
            }
            
            // Now change the status
            Order updated = cashierOrderService.changeStatus(id, status, username);
            
            response.put("success", true);
            response.put("message", "Estado del pedido cambiado a " + status.getDisplayName());
            response.put("order", buildOrderDTO(updated));
        } catch (IllegalArgumentException e) {
            log.error("Invalid status: {}", newStatus);
            response.put("success", false);
            response.put("message", "Estado inválido: " + newStatus);
        } catch (IllegalStateException e) {
            log.error("Error changing order status: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("Error changing order status", e);
            response.put("success", false);
            response.put("message", "Error al cambiar el estado del pedido: " + e.getMessage());
        }

        return response;
    }

    /**
     * Get valid next statuses for an order (AJAX)
     * Cashier can:
     * - Mark READY orders as DELIVERED
     * - Mark DELIVERED orders as PAID (any payment method)
     */
    @GetMapping("/orders/{id}/valid-statuses")
    @ResponseBody
    public Map<String, Object> getValidStatuses(
            @PathVariable Long id,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.debug("Cashier {} getting valid statuses for order {}", username, id);

        Map<String, Object> response = new HashMap<>();
        
        try {
            Order order = cashierOrderService.findByIdOrThrow(id);
            
            // Check if the cashier created this order
            boolean isOrderCreator = order.getCreatedBy().equals(username);
            
            List<Map<String, String>> validStatuses = new ArrayList<>();
            
            // Cashier can mark READY → DELIVERED for TAKEOUT and DINE_IN orders
            // (DELIVERY orders are marked as DELIVERED by delivery drivers)
            if (order.getStatus() == OrderStatus.READY && 
                (order.getOrderType() == OrderType.TAKEOUT || order.getOrderType() == OrderType.DINE_IN)) {
                Map<String, String> deliveredStatus = new HashMap<>();
                deliveredStatus.put("value", OrderStatus.DELIVERED.name());
                deliveredStatus.put("label", OrderStatus.DELIVERED.getDisplayName());
                validStatuses.add(deliveredStatus);
            }
            
            // Cashier can ALWAYS mark DELIVERED orders as PAID (even if they didn't create it)
            if (order.getStatus() == OrderStatus.DELIVERED) {
                Map<String, String> paidStatus = new HashMap<>();
                paidStatus.put("value", OrderStatus.PAID.name());
                paidStatus.put("label", OrderStatus.PAID.getDisplayName());
                validStatuses.add(paidStatus);
            }
            
            response.put("success", true);
            response.put("validStatuses", validStatuses);
            response.put("currentStatus", order.getStatus().name());
            response.put("currentStatusLabel", order.getStatus().getDisplayName());
            response.put("canMarkAsPaid", order.getStatus() == OrderStatus.DELIVERED); // Only DELIVERED can be marked as PAID
            response.put("canMarkAsDelivered", order.getStatus() == OrderStatus.READY && 
                (order.getOrderType() == OrderType.TAKEOUT || order.getOrderType() == OrderType.DINE_IN)); // Can mark as delivered if READY and not DELIVERY
            response.put("isOrderCreator", isOrderCreator); // Tell frontend if cashier created this order
            
        } catch (Exception e) {
            log.error("Error getting valid statuses for order {}", id, e);
            response.put("success", false);
            response.put("message", "Error al obtener estados válidos: " + e.getMessage());
            response.put("validStatuses", new ArrayList<>());
        }

        return response;
    }

    /**
     * Cancel an order (AJAX)
     */
    @PostMapping("/orders/{id}/cancel")
    @ResponseBody
    public Map<String, Object> cancelOrder(
            @PathVariable Long id,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Cashier {} cancelling order {}", username, id);

        Map<String, Object> response = new HashMap<>();
        
        try {
            Order cancelled = cashierOrderService.cancel(id, username);
            response.put("success", true);
            response.put("message", "Pedido " + cancelled.getOrderNumber() + " cancelado exitosamente");
            response.put("order", buildOrderDTO(cancelled));
            
            // Send WebSocket notification to chef/barista so the order disappears from their dashboard
            wsNotificationService.notifyOrderCancelled(cancelled);
            log.info("🔔 WebSocket sent: Order {} cancelled by cashier {}", 
                cancelled.getOrderNumber(), username);
            
            // Analyze items to determine stock return information
            String stockInfo = analyzeStockReturn(cancelled);
            if (stockInfo != null && !stockInfo.isEmpty()) {
                response.put("stockInfo", stockInfo);
            }
        } catch (IllegalStateException e) {
            log.error("Error cancelling order: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("Error cancelling order", e);
            response.put("success", false);
            response.put("message", "Error al cancelar el pedido: " + e.getMessage());
        }

        return response;
    }

    /**
     * Delete a specific item from an order (AJAX)
     */
    @DeleteMapping("/orders/{orderId}/items/{itemId}")
    @ResponseBody
    public Map<String, Object> deleteOrderItem(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Cashier {} deleting item {} from order {}", username, itemId, orderId);

        Map<String, Object> response = new HashMap<>();
        
        try {
            OrderDetail deletedItem = cashierOrderService.deleteOrderItem(orderId, itemId, username);
            
            // Get updated order
            Order order = cashierOrderService.findByIdOrThrow(orderId);
            
            // Send WebSocket notification so chef/barista can remove the item from their view
            wsNotificationService.notifyItemDeleted(order, deletedItem);
            log.info("🔔 WebSocket sent: Item {} deleted from order {} by cashier {}", 
                deletedItem.getItemMenu().getName(), order.getOrderNumber(), username);
            
            // Analyze stock return for this specific item
            String stockInfo = analyzeItemStockReturn(deletedItem);
            
            response.put("success", true);
            response.put("message", String.format("Item '%s' eliminado del pedido exitosamente", 
                    deletedItem.getItemMenu().getName()));
            response.put("stockInfo", stockInfo);
            response.put("orderTotal", order.getTotal());
            response.put("orderStatus", order.getStatus().name());
            response.put("orderStatusLabel", order.getStatus().getDisplayName());
            response.put("remainingItems", order.getOrderDetails().size());
            
        } catch (IllegalArgumentException e) {
            log.error("Item not found: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
        } catch (IllegalStateException e) {
            log.error("Cannot delete item: {}", e.getMessage());
            
            // Check if this is the last item scenario
            if ("LAST_ITEM_CANCEL_ORDER".equals(e.getMessage())) {
                response.put("success", false);
                response.put("isLastItem", true);
                response.put("message", "Este es el último item de la orden. ¿Deseas cancelar la orden completa?");
            } else {
                response.put("success", false);
                response.put("message", e.getMessage());
            }
        } catch (Exception e) {
            log.error("Error deleting item from order", e);
            response.put("success", false);
            response.put("message", "Error al eliminar el item: " + e.getMessage());
        }

        return response;
    }

    /**
     * Display user profile
     *
     * @param authentication Spring Security authentication object
     * @param model Spring MVC model
     * @param redirectAttributes Redirect attributes for error messages
     * @return profile view
     */
    @GetMapping("/profile")
    public String profile(Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        String username = authentication.getName();
        log.info("User {} accessed profile", username);
        
        try {
            Employee employee = employeeService.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
            
            model.addAttribute("employee", employee);
            return "cashier/profile/view";
            
        } catch (Exception e) {
            log.error("Error loading profile for user {}: {}", username, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cargar el perfil");
            return "redirect:/cashier/dashboard";
        }
    }

    /**
     * Display menu items (visual only)
     *
     * @param model Spring MVC model
     * @param redirectAttributes Redirect attributes for error messages
     * @return menu view
     */
    @GetMapping("/menu/view")
    public String viewMenu(Model model, RedirectAttributes redirectAttributes) {
        log.info("Accessed visual menu view");
        
        try {
            // Get system configuration
            SystemConfiguration config = systemConfigurationService.getConfiguration();
            
            // Get available menu items
            List<ItemMenu> availableItems = itemMenuService.findAvailableItems();
            
            // Group items by category ID for easier display
            Map<Long, List<ItemMenu>> itemsByCategory = availableItems.stream()
                .collect(Collectors.groupingBy(item -> item.getCategory().getIdCategory()));
            
            // Get all active categories - ONLY those with active items
            List<Category> categories = categoryService.getAllActiveCategories().stream()
                .filter(category -> itemsByCategory.containsKey(category.getIdCategory()))
                .collect(Collectors.toList());
            
            model.addAttribute("config", config);
            model.addAttribute("categories", categories);
            model.addAttribute("itemsByCategory", itemsByCategory);
            
            return "cashier/menu/view";
            
        } catch (Exception e) {
            log.error("Error loading menu view: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cargar el menú");
            return "redirect:/cashier/dashboard";
        }
    }

    /**
     * Display user reports with charts
     *
     * @param authentication Spring Security authentication object
     * @param model Spring MVC model
     * @param redirectAttributes Redirect attributes for error messages
     * @return reports view
     */
    @GetMapping("/reports/view")
    public String viewReports(Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        String username = authentication.getName();
        log.info("User {} accessed reports view", username);
        
        try {
            Employee employee = employeeService.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
            
            // MULTI-TENANT: Filter orders by company
            Company company = CompanyContext.requireCurrentCompany();
            
            // Get all orders collected by this cashier (PAID orders where paidBy = current cashier)
            List<Order> collectedOrders = orderRepository.findByCompany(company).stream()
                    .filter(order -> order.getStatus() == OrderStatus.PAID)
                    .filter(order -> order.getPaidBy() != null && order.getPaidBy().getIdEmpleado().equals(employee.getIdEmpleado()))
                    .toList();
            
            // Get today's date
            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.LocalDateTime startOfDay = today.atStartOfDay();
            java.time.LocalDateTime endOfDay = today.atTime(java.time.LocalTime.MAX);
            
            // Today's collected orders
            List<Order> todaysCollectedOrders = collectedOrders.stream()
                    .filter(order -> {
                        java.time.LocalDateTime paidAt = order.getUpdatedAt() != null ? order.getUpdatedAt() : order.getCreatedAt();
                        return paidAt.isAfter(startOfDay) && paidAt.isBefore(endOfDay);
                    })
                    .toList();
            
            // Calculate statistics
            // Total collected revenue (only order total, not tips)
            BigDecimal totalRevenue = collectedOrders.stream()
                    .map(Order::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Today's collected revenue
            BigDecimal todayRevenue = todaysCollectedOrders.stream()
                    .map(Order::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Total tips collected
            BigDecimal totalTips = collectedOrders.stream()
                    .map(order -> order.getTip() != null ? order.getTip() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Today's tips
            BigDecimal todayTips = todaysCollectedOrders.stream()
                    .map(order -> order.getTip() != null ? order.getTip() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Average values
            BigDecimal averageOrderValue = collectedOrders.size() > 0
                    ? totalRevenue.divide(BigDecimal.valueOf(collectedOrders.size()), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            
            BigDecimal todayAverageOrderValue = todaysCollectedOrders.size() > 0
                    ? todayRevenue.divide(BigDecimal.valueOf(todaysCollectedOrders.size()), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            
            BigDecimal averageTip = collectedOrders.size() > 0
                    ? totalTips.divide(BigDecimal.valueOf(collectedOrders.size()), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            
            BigDecimal todayAverageTip = todaysCollectedOrders.size() > 0
                    ? todayTips.divide(BigDecimal.valueOf(todaysCollectedOrders.size()), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            
            // Order counts
            int totalOrders = collectedOrders.size();
            int todayOrders = todaysCollectedOrders.size();
            
            // Last 7 days data
            List<String> last7DaysLabels = new ArrayList<>();
            List<Long> last7DaysOrdersData = new ArrayList<>();
            List<BigDecimal> last7DaysRevenueData = new ArrayList<>();
            List<BigDecimal> last7DaysTipsData = new ArrayList<>();
            
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM");
            
            for (int i = 6; i >= 0; i--) {
                java.time.LocalDate date = today.minusDays(i);
                java.time.LocalDateTime dayStart = date.atStartOfDay();
                java.time.LocalDateTime dayEnd = date.atTime(java.time.LocalTime.MAX);
                
                List<Order> dayOrders = collectedOrders.stream()
                        .filter(order -> {
                            java.time.LocalDateTime paidAt = order.getUpdatedAt() != null ? order.getUpdatedAt() : order.getCreatedAt();
                            return paidAt.isAfter(dayStart) && paidAt.isBefore(dayEnd);
                        })
                        .toList();
                
                last7DaysLabels.add(date.format(formatter));
                last7DaysOrdersData.add((long) dayOrders.size());
                
                BigDecimal dayRevenue = dayOrders.stream()
                        .map(Order::getTotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                last7DaysRevenueData.add(dayRevenue);
                
                BigDecimal dayTips = dayOrders.stream()
                        .map(order -> order.getTip() != null ? order.getTip() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                last7DaysTipsData.add(dayTips);
            }
            
            // Add all attributes to model
            model.addAttribute("employee", employee);
            model.addAttribute("totalOrders", totalOrders);
            model.addAttribute("todayOrders", todayOrders);
            model.addAttribute("totalRevenue", totalRevenue);
            model.addAttribute("todayRevenue", todayRevenue);
            model.addAttribute("totalTips", totalTips);
            model.addAttribute("todayTips", todayTips);
            model.addAttribute("averageOrderValue", averageOrderValue);
            model.addAttribute("todayAverageOrderValue", todayAverageOrderValue);
            model.addAttribute("averageTip", averageTip);
            model.addAttribute("todayAverageTip", todayAverageTip);
            
            // Chart data
            model.addAttribute("last7DaysLabels", last7DaysLabels);
            model.addAttribute("last7DaysOrdersData", last7DaysOrdersData);
            model.addAttribute("last7DaysRevenueData", last7DaysRevenueData);
            model.addAttribute("last7DaysTipsData", last7DaysTipsData);
            
            // Status counts - For cashier, we don't track status by employee
            // Set all to 0 since cashier doesn't create orders with different statuses
            model.addAttribute("totalPending", 0);
            model.addAttribute("totalInPreparation", 0);
            model.addAttribute("totalReady", 0);
            model.addAttribute("totalDelivered", 0);
            model.addAttribute("totalPaid", (long) totalOrders);
            model.addAttribute("totalCancelled", 0);
            
            model.addAttribute("todayPending", 0);
            model.addAttribute("todayInPreparation", 0);
            model.addAttribute("todayReady", 0);
            model.addAttribute("todayDelivered", 0);
            model.addAttribute("todayPaid", (long) todayOrders);
            model.addAttribute("todayCancelled", 0);
            
            return "cashier/reports/view";
            
        } catch (Exception e) {
            log.error("Error loading reports for user {}: {}", username, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cargar los reportes");
            return "redirect:/cashier/dashboard";
        }
    }

    // ========== HELPER METHODS ==========

    /**
     * Build order details from form data
     */
    private List<OrderDetail> buildOrderDetails(
            List<Long> itemIds,
            List<Integer> quantities,
            List<String> comments,
            List<String> promotionPrices,
            List<String> promotionIds) {
        
        List<OrderDetail> orderDetails = new ArrayList<>();

        if (itemIds != null && !itemIds.isEmpty()) {
            for (int i = 0; i < itemIds.size(); i++) {
                Long itemId = itemIds.get(i);
                Integer quantity = quantities.get(i);
                String comment = (comments != null && i < comments.size()) ? comments.get(i) : null;
                String promotionIdStr = (promotionIds != null && i < promotionIds.size()) ? promotionIds.get(i) : null;

                ItemMenu item = itemMenuService.findById(itemId)
                        .orElseThrow(() -> new IllegalArgumentException("Item no encontrado: " + itemId));

                OrderDetail.OrderDetailBuilder detailBuilder = OrderDetail.builder()
                        .itemMenu(item)
                        .quantity(quantity)
                        .unitPrice(item.getPrice())
                        .comments(comment);
                
                // Set item status based on whether it requires preparation
                if (Boolean.TRUE.equals(item.getRequiresPreparation())) {
                    detailBuilder.itemStatus(OrderStatus.PENDING);
                } else {
                    detailBuilder.itemStatus(OrderStatus.READY);
                }
                
                // BACKEND VALIDATION: Validate and recalculate promotion price
                if (promotionIdStr != null && !promotionIdStr.trim().isEmpty()) {
                    try {
                        Long promotionId = Long.parseLong(promotionIdStr);
                        
                        // Fetch promotion from database to validate it exists and is active
                        Promotion promotion = promotionService.findById(promotionId)
                            .orElse(null);
                        
                        if (promotion != null && promotion.isValidNow()) {
                            // Validate that the promotion applies to this item
                            boolean promotionAppliesToItem = promotion.getItems().stream()
                                .anyMatch(promotionItem -> promotionItem.getIdItemMenu().equals(itemId));
                            
                            if (promotionAppliesToItem) {
                                // Validate minimum quantity for BUY_X_PAY_Y promotions
                                if (promotion.getPromotionType() == PromotionType.BUY_X_PAY_Y) {
                                    if (quantity < promotion.getBuyQuantity()) {
                                        log.warn("Quantity {} is less than required {} for promotion {}. Applying no promotion.",
                                                quantity, promotion.getBuyQuantity(), promotion.getName());
                                        // Don't apply promotion if minimum quantity not met
                                    } else {
                                        // SECURITY: Recalculate price in backend (don't trust frontend)
                                        BigDecimal calculatedDiscountedTotal = promotion.calculateDiscountedPrice(
                                            item.getPrice(), 
                                            quantity
                                        ).setScale(2, RoundingMode.HALF_UP);
                                        
                                        // For BUY_X_PAY_Y: Calculate price per unit (for display only), rounded to 2 decimals
                                        BigDecimal calculatedPricePerUnit = calculatedDiscountedTotal
                                            .divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
                                        
                                        log.info("CASHIER BACKEND VALIDATION (BUY_X_PAY_Y) - Item: {}, Qty: {}, Promotion: {}, " +
                                                "Original Price/Unit: ${}, Calculated Price/Unit: ${}, " +
                                                "Calculated Total (direct): ${}", 
                                                item.getName(), quantity, promotion.getName(),
                                                item.getPrice(), calculatedPricePerUnit, calculatedDiscountedTotal);
                                        
                                        // Set the VALIDATED promotion data
                                        detailBuilder.appliedPromotionId(promotionId);
                                        detailBuilder.promotionAppliedPrice(calculatedPricePerUnit);
                                        // Set subtotal directly to avoid precision errors from divide/multiply cycle
                                        detailBuilder.subtotal(calculatedDiscountedTotal);
                                    }
                                } else {
                                    // For other promotion types (PERCENTAGE_DISCOUNT, FIXED_AMOUNT_DISCOUNT)
                                    BigDecimal calculatedDiscountedTotal = promotion.calculateDiscountedPrice(
                                        item.getPrice(), 
                                        quantity
                                    ).setScale(2, RoundingMode.HALF_UP);
                                    
                                    BigDecimal calculatedPricePerUnit = calculatedDiscountedTotal
                                        .divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
                                    
                                    log.info("CASHIER BACKEND VALIDATION - Item: {}, Qty: {}, Promotion: {}, " +
                                            "Original Price/Unit: ${}, Calculated Price/Unit: ${}, " +
                                            "Calculated Total: ${}", 
                                            item.getName(), quantity, promotion.getName(),
                                            item.getPrice(), calculatedPricePerUnit, calculatedDiscountedTotal);
                                    
                                    // Set the VALIDATED promotion data
                                    detailBuilder.appliedPromotionId(promotionId);
                                    detailBuilder.promotionAppliedPrice(calculatedPricePerUnit);
                                    // Set subtotal directly to avoid precision errors from divide/multiply cycle
                                    detailBuilder.subtotal(calculatedDiscountedTotal);
                                }
                            } else {
                                log.warn("Promotion {} does not apply to item {}", promotionId, item.getName());
                            }
                        } else {
                            log.warn("Promotion {} is not valid or not active", promotionId);
                        }
                    } catch (NumberFormatException e) {
                        log.warn("Invalid promotion ID format: {}", promotionIdStr);
                    }
                }
                
                OrderDetail detail = detailBuilder.build();
                
                // Only calculate subtotal if not already set (for BUY_X_PAY_Y, it's set directly)
                if (detail.getSubtotal() == null) {
                    detail.calculateSubtotal();
                }
                orderDetails.add(detail);
            }
        }

        return orderDetails;
    }

    /**
     * Build a simple DTO for an order to send in AJAX responses
     */
    private Map<String, Object> buildOrderDTO(Order order) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", order.getIdOrder());
        dto.put("orderNumber", order.getOrderNumber());
        dto.put("status", order.getStatus().name());
        dto.put("statusLabel", order.getStatus().getDisplayName());
        dto.put("orderType", order.getOrderType().name());
        dto.put("orderTypeLabel", order.getOrderType().getDisplayName());
        dto.put("total", order.getTotal());
        dto.put("canBeCancelled", order.getStatus().canBeCancelled());
        
        if (order.getTable() != null) {
            dto.put("tableNumber", order.getTable().getTableNumber());
        }
        
        // Who created the order
        if (order.getEmployee() != null) {
            dto.put("createdBy", order.getEmployee().getFullName());
        }
        
        // Who prepared the order
        if (order.getPreparedBy() != null) {
            dto.put("preparedBy", order.getPreparedBy().getFullName());
        }
        
        // Who collected payment
        if (order.getPaidBy() != null) {
            dto.put("paidBy", order.getPaidBy().getFullName());
        }
        
        return dto;
    }

    /**
     * Analyze items to determine stock return information
     * Returns a message describing how stock was handled
     */
    private String analyzeStockReturn(Order order) {
        if (order.getOrderDetails() == null || order.getOrderDetails().isEmpty()) {
            return null;
        }

        int automaticItems = 0;
        int manualItems = 0;

        for (OrderDetail detail : order.getOrderDetails()) {
            OrderStatus itemStatus = detail.getItemStatus();
            
            // PENDING -> always automatic
            if (itemStatus == OrderStatus.PENDING) {
                automaticItems++;
                continue;
            }
            
            // READY -> check if requires preparation (Chef or Barista)
            if (itemStatus == OrderStatus.READY) {
                if (detail.getItemMenu() != null) {
                    boolean requiresChef = Boolean.TRUE.equals(detail.getItemMenu().getRequiresPreparation());
                    boolean requiresBarista = Boolean.TRUE.equals(detail.getItemMenu().getRequiresBaristaPreparation());
                    
                    // Only count as automatic if NO ONE needed to prepare it
                    if (!requiresChef && !requiresBarista) {
                        automaticItems++;
                    } else {
                        // Chef or Barista prepared it, used ingredients
                        manualItems++;
                    }
                } else {
                    manualItems++;
                }
                continue;
            }
            
            // IN_PREPARATION -> always manual
            if (itemStatus == OrderStatus.IN_PREPARATION) {
                manualItems++;
            }
        }

        // Build appropriate message
        if (automaticItems > 0 && manualItems == 0) {
            return "✅ Stock devuelto automáticamente para todos los items (" + automaticItems + " items)";
        } else if (manualItems > 0 && automaticItems == 0) {
            return "⚠️ Stock debe ser devuelto manualmente para todos los items (" + manualItems + " items)";
        } else if (automaticItems > 0 && manualItems > 0) {
            return "ℹ️ Stock devuelto: " + automaticItems + " items automáticos, " + 
                   manualItems + " items requieren devolución manual";
        }

        return null;
    }

    /**
     * Analyze stock return for a single item
     * Returns a message describing how stock was handled for this specific item
     */
    private String analyzeItemStockReturn(OrderDetail detail) {
        OrderStatus itemStatus = detail.getItemStatus();
        
        // PENDING -> always automatic
        if (itemStatus == OrderStatus.PENDING) {
            return "✅ Stock devuelto automáticamente (item nunca fue preparado)";
        }
        
        // READY -> check if requires preparation (Chef or Barista)
        if (itemStatus == OrderStatus.READY) {
            if (detail.getItemMenu() != null) {
                boolean requiresChef = Boolean.TRUE.equals(detail.getItemMenu().getRequiresPreparation());
                boolean requiresBarista = Boolean.TRUE.equals(detail.getItemMenu().getRequiresBaristaPreparation());
                
                // Only automatic if NO ONE needed to prepare it
                if (!requiresChef && !requiresBarista) {
                    return "✅ Stock devuelto automáticamente (item no requiere preparación)";
                } else {
                    return "⚠️ Stock debe ser devuelto manualmente (chef/barista ya preparó el item)";
                }
            } else {
                return "⚠️ Stock debe ser devuelto manualmente (item ya preparado)";
            }
        }
        
        // IN_PREPARATION -> always manual
        if (itemStatus == OrderStatus.IN_PREPARATION) {
            return "⚠️ Stock debe ser devuelto manualmente (item estaba en preparación)";
        }

        return "ℹ️ Revisar devolución de stock manualmente";
    }
    
    /**
     * Determine if order type can be changed based on current order status
     * Rules:
     * - DELIVERY: Can change if NOT in ON_THE_WAY, DELIVERED, or PAID
     *   AND if no delivery person has been assigned (deliveredBy is null)
     * - DINE_IN/TAKE_OUT: Can change if NOT in DELIVERED or PAID
     */
    private boolean canChangeOrderType(Order order) {
        if (order == null || order.getStatus() == null) {
            return true; // Allow change if no restrictions
        }
        
        OrderStatus status = order.getStatus();
        OrderType orderType = order.getOrderType();
        
        if (orderType == OrderType.DELIVERY) {
            // DELIVERY: Cannot change if a delivery person has been assigned
            if (order.getDeliveredBy() != null) {
                log.warn("Cannot change order type - delivery person already assigned: {}", 
                         order.getDeliveredBy().getFullName());
                return false;
            }
            
            // Also cannot change if in certain statuses
            return status != OrderStatus.ON_THE_WAY && 
                   status != OrderStatus.DELIVERED && 
                   status != OrderStatus.PAID;
        } else {
            // DINE_IN/TAKE_OUT: Cannot change if DELIVERED or PAID
            return status != OrderStatus.DELIVERED && 
                   status != OrderStatus.PAID;
        }
    }
    
    /**
     * Get appropriate error message for order type change restriction
     */
    private String getOrderTypeChangeRestrictionMessage(Order order) {
        OrderStatus status = order.getStatus();
        OrderType orderType = order.getOrderType();
        
        if (orderType == OrderType.DELIVERY) {
            // Check if delivery person is assigned first
            if (order.getDeliveredBy() != null) {
                String deliveryPersonName = order.getDeliveredBy().getFullName();
                return "No se puede cambiar el tipo de pedido porque ya fue aceptado por el repartidor " + 
                       deliveryPersonName + " para entrega.";
            }
            
            if (status == OrderStatus.ON_THE_WAY) {
                return "No se puede cambiar el tipo de pedido porque el pedido está en camino.";
            } else if (status == OrderStatus.DELIVERED) {
                return "No se puede cambiar el tipo de pedido porque el pedido ya fue entregado.";
            } else if (status == OrderStatus.PAID) {
                return "No se puede cambiar el tipo de pedido porque el pedido ya fue pagado.";
            }
        } else {
            if (status == OrderStatus.DELIVERED) {
                return "No se puede cambiar el tipo de pedido porque el pedido ya fue entregado.";
            } else if (status == OrderStatus.PAID) {
                return "No se puede cambiar el tipo de pedido porque el pedido ya fue pagado.";
            }
        }
        
        return "No se puede cambiar el tipo de pedido en el estado actual.";
    }

    /**
     * Display cashiers ranking by sales collected today
     *
     * @param authentication Spring Security authentication object
     * @param model Spring MVC model
     * @param redirectAttributes Redirect attributes for error messages
     * @return ranking view
     */
    @GetMapping("/ranking/view")
    public String viewRanking(Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        log.info("Cashier accessed ranking view");
        
        try {
            // Get system configuration
            SystemConfiguration config = systemConfigurationService.getConfiguration();
            
            // Get today's date range
            LocalDate today = LocalDate.now();
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime endOfDay = today.atTime(LocalTime.MAX);
            
            // Get all employees with CASHIER role
            List<Employee> allCashiers = employeeService.findAll().stream()
                    .filter(emp -> emp.getRoles().stream()
                            .anyMatch(role -> role.getNombreRol().equals("ROLE_CASHIER")))
                    .toList();
            
            // Calculate sales for each cashier (TODAY ONLY) - using paidBy
            List<Map<String, Object>> cashierSales = allCashiers.stream()
                    .map(cashier -> {
                        // Get all PAID orders collected by this cashier TODAY
                        // MULTI-TENANT: Use service instead of repository to filter by company
                        List<Order> todayPaidOrders = adminOrderService.findByStatus(OrderStatus.PAID)
                                .stream()
                                .filter(order -> {
                                    LocalDateTime paidDate = order.getUpdatedAt() != null ? order.getUpdatedAt() : order.getCreatedAt();
                                    return order.getPaidBy() != null &&
                                           order.getPaidBy().getIdEmpleado().equals(cashier.getIdEmpleado()) &&
                                           paidDate != null && 
                                           !paidDate.isBefore(startOfDay) && 
                                           !paidDate.isAfter(endOfDay);
                                })
                                .toList();
                        
                        // Calculate total sales TODAY
                        BigDecimal totalSales = todayPaidOrders.stream()
                                .map(order -> order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        
                        // Calculate total orders TODAY
                        int totalOrders = todayPaidOrders.size();
                        
                        // Get initials
                        String firstName = cashier.getNombre() != null ? cashier.getNombre() : "";
                        String lastName = cashier.getApellido() != null ? cashier.getApellido() : "";
                        String initials = "";
                        if (!firstName.isEmpty()) {
                            initials += firstName.charAt(0);
                        }
                        if (!lastName.isEmpty()) {
                            initials += lastName.charAt(0);
                        }
                        initials = initials.toUpperCase();
                        
                        Map<String, Object> cashierData = new HashMap<>();
                        cashierData.put("employee", cashier);
                        cashierData.put("totalSales", totalSales);
                        cashierData.put("totalOrders", totalOrders);
                        cashierData.put("initials", initials);
                        
                        return cashierData;
                    })
                    .filter(cashierData -> {
                        // Only include cashiers with sales TODAY
                        BigDecimal sales = (BigDecimal) cashierData.get("totalSales");
                        return sales.compareTo(BigDecimal.ZERO) > 0;
                    })
                    .sorted((c1, c2) -> {
                        BigDecimal sales1 = (BigDecimal) c1.get("totalSales");
                        BigDecimal sales2 = (BigDecimal) c2.get("totalSales");
                        return sales2.compareTo(sales1); // Descending order
                    })
                    .limit(5) // Top 5 cashiers
                    .toList();
            
            model.addAttribute("config", config);
            model.addAttribute("waiterRanking", cashierSales); // Using same attribute name for template compatibility
            model.addAttribute("rankingDate", today);
            
            return "cashier/ranking/view";
            
        } catch (Exception e) {
            log.error("Error loading cashier ranking: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cargar el ranking");
            return "redirect:/cashier/dashboard";
        }
    }
}

