package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.*;
import com.aatechsolutions.elgransazon.domain.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Controller for Order (Pedidos) management
 * Handles CRUD operations for customer orders
 * Uses different OrderService implementations based on user role
 */
@Controller
@RequestMapping("/{role}/orders")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_WAITER', 'ROLE_CHEF', 'ROLE_BARISTA', 'ROLE_DELIVERY', 'ROLE_CASHIER')")
@Slf4j
public class OrderController {

    private final Map<String, OrderService> orderServices;
    private final OrderService chefOrderService; // Direct reference for chef-specific methods
    
    // Guard to prevent duplicate order submissions per user (concurrent rapid clicks)
    private final Set<String> activeOrderSubmissions = ConcurrentHashMap.newKeySet();

    private final RestaurantTableService restaurantTableService;
    private final ItemMenuService itemMenuService;
    private final EmployeeService employeeService;
    private final SystemConfigurationService systemConfigurationService;
    private final CategoryService categoryService;
    private final com.aatechsolutions.elgransazon.domain.repository.OrderRepository orderRepository;
    private final PromotionService promotionService;
    private final WebSocketNotificationService wsNotificationService;
    private final BusinessHoursService businessHoursService;
    private final TicketPdfService ticketPdfService;
    private final TicketEscPosService ticketEscPosService;
    private final com.aatechsolutions.elgransazon.domain.repository.ComplementRepository complementRepository;
    private final com.aatechsolutions.elgransazon.domain.repository.ItemMenuComplementRepository itemMenuComplementRepository;
    private final com.aatechsolutions.elgransazon.domain.repository.ItemMenuComboItemRepository itemMenuComboItemRepository;
    private final ObjectMapper objectMapper;
    private final ReservationService reservationService;
    private final DateTimeService dateTimeService;

    /**
     * Constructor with dependency injection
     * Injects admin, waiter, chef, barista, delivery and cashier order services
     * MANAGER uses the same service as ADMIN
     */
    public OrderController(
            @Qualifier("adminOrderService") OrderService adminOrderService,
            @Qualifier("waiterOrderService") OrderService waiterOrderService,
            @Qualifier("chefOrderService") OrderService chefOrderService,
            @Qualifier("baristaOrderService") OrderService baristaOrderService,
            @Qualifier("deliveryOrderService") OrderService deliveryOrderService,
            @Qualifier("cashierOrderService") OrderService cashierOrderService,
            RestaurantTableService restaurantTableService,
            ItemMenuService itemMenuService,
            EmployeeService employeeService,
            SystemConfigurationService systemConfigurationService,
            CategoryService categoryService,
            com.aatechsolutions.elgransazon.domain.repository.OrderRepository orderRepository,
            PromotionService promotionService,
            WebSocketNotificationService wsNotificationService,
            BusinessHoursService businessHoursService,
            TicketPdfService ticketPdfService,
            TicketEscPosService ticketEscPosService,
            com.aatechsolutions.elgransazon.domain.repository.ComplementRepository complementRepository,
            com.aatechsolutions.elgransazon.domain.repository.ItemMenuComplementRepository itemMenuComplementRepository,
            com.aatechsolutions.elgransazon.domain.repository.ItemMenuComboItemRepository itemMenuComboItemRepository,
            ObjectMapper objectMapper,
            ReservationService reservationService,
            DateTimeService dateTimeService) {
        
        this.chefOrderService = chefOrderService; // Store direct reference
        this.orderServices = Map.of(
            "admin", adminOrderService,
            "manager", adminOrderService,  // MANAGER uses admin service
            "waiter", waiterOrderService,
            "chef", chefOrderService,
            "barista", baristaOrderService,
            "delivery", deliveryOrderService,
            "cashier", cashierOrderService
        );
        this.restaurantTableService = restaurantTableService;
        this.itemMenuService = itemMenuService;
        this.employeeService = employeeService;
        this.systemConfigurationService = systemConfigurationService;
        this.categoryService = categoryService;
        this.orderRepository = orderRepository;
        this.promotionService = promotionService;
        this.wsNotificationService = wsNotificationService;
        this.businessHoursService = businessHoursService;
        this.ticketPdfService = ticketPdfService;
        this.ticketEscPosService = ticketEscPosService;
        this.complementRepository = complementRepository;
        this.itemMenuComplementRepository = itemMenuComplementRepository;
        this.itemMenuComboItemRepository = itemMenuComboItemRepository;
        this.objectMapper = objectMapper;
        this.reservationService = reservationService;
        this.dateTimeService = dateTimeService;
    }

    /**
     * Get the correct OrderService based on role
     */
    private OrderService getOrderService(String role) {
        OrderService service = orderServices.get(role.toLowerCase());
        if (service == null) {
            throw new IllegalArgumentException("Invalid role: " + role);
        }
        return service;
    }

    /**
     * Validate role path variable matches user's actual role
     * MANAGER can use admin routes
     */
    private void validateRole(String role, Authentication authentication) {
        String userRole = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.equals("ROLE_ADMIN") || auth.equals("ROLE_MANAGER") || 
                               auth.equals("ROLE_WAITER") || auth.equals("ROLE_CHEF") || 
                               auth.equals("ROLE_BARISTA") || auth.equals("ROLE_DELIVERY") || 
                               auth.equals("ROLE_CASHIER"))
                .map(auth -> auth.replace("ROLE_", "").toLowerCase())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("User has no valid role"));
        
        // MANAGER can access admin routes
        if (userRole.equals("manager") && role.equalsIgnoreCase("admin")) {
            return; // Allow MANAGER to use admin routes
        }
        
        if (!role.equalsIgnoreCase(userRole)) {
            throw new IllegalStateException("Access denied: Role mismatch");
        }
    }

    /**
     * Show list of all orders with filters
     */
    @GetMapping
    public String listOrders(
            @PathVariable String role,
            @RequestParam(required = false) Long tableId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) OrderType orderType,
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "1") int page,
            Authentication authentication,
            Model model) {
        
        log.debug("Displaying orders list with filters - role: {}, table: {}, status: {}, type: {}, date: {}", 
                  role, tableId, status, orderType, date);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);

        List<Order> orders;

        // Apply filters
        if (date != null && !date.isEmpty()) {
            LocalDateTime startDate = dateTimeService.startOfDayUtc(LocalDate.parse(date));
            LocalDateTime endDate = dateTimeService.endOfDayUtc(LocalDate.parse(date));
            orders = orderService.findByDateRange(startDate, endDate);
        } else {
            orders = orderService.findAll(); // Already filtered by role in service implementation
        }

        // Filter by table
        if (tableId != null) {
            orders = orders.stream()
                .filter(order -> order.getTable() != null && order.getTable().getId().equals(tableId))
                .collect(Collectors.toList());
        }

        // Filter by status
        if (status != null) {
            orders = orders.stream()
                .filter(order -> order.getStatus() == status)
                .collect(Collectors.toList());
        }

        // Filter by order type
        if (orderType != null) {
            orders = orders.stream()
                .filter(order -> order.getOrderType() == orderType)
                .collect(Collectors.toList());
        }

        // Sort by creation date (most recent first)
        orders = orders.stream()
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
            .collect(Collectors.toList());

        // ========== Calculate date range for statistics ==========
        // If date filter is applied, use that date; otherwise use today
        LocalDateTime statsStartDate;
        LocalDateTime statsEndDate;
        if (date != null && !date.isEmpty()) {
            statsStartDate = dateTimeService.startOfDayUtc(LocalDate.parse(date));
            statsEndDate = dateTimeService.endOfDayUtc(LocalDate.parse(date));
        } else {
            statsStartDate = dateTimeService.startOfDayUtc(dateTimeService.todayLocal());
            statsEndDate = dateTimeService.endOfDayUtc(dateTimeService.todayLocal());
        }

        // ========== Calculate statistics (dynamic based on date filter) ==========
        String currentUsername = authentication.getName();
        
        // paidCount: Count of PAID orders in the selected date range
        // - Admin: Global count (any employee)
        // - Waiter: Only orders they collected payment for (paidBy = currentUser)
        long paidCount;
        if ("admin".equals(role)) {
            paidCount = orderService.countPaidOrdersByDateRange(statsStartDate, statsEndDate);
        } else {
            // Waiter: only orders they collected payment for
            paidCount = orderService.countPaidOrdersByUsernameAndDateRange(currentUsername, statsStartDate, statsEndDate);
        }
        
        // Revenue for the selected date range (global)
        BigDecimal todayRevenue = orderService.getRevenueByDateRange(statsStartDate, statsEndDate);
        
        // Pending and In Preparation counts
        // - Admin: global counts within stats date range (by createdAt)
        // - Waiter: scoped to waiter's own orders (createdBy = waiter) within stats date range (by createdAt)
        // - Other roles: keep all-time global counts (operational awareness, unchanged behavior)
        long pendingCount;
        long inPreparationCount;
        if ("waiter".equals(role)) {
            final LocalDateTime sStart = statsStartDate;
            final LocalDateTime sEnd = statsEndDate;
            // orderService.findAll() for waiter is already filtered by createdBy=waiter
            List<Order> myOrdersInStatsRange = orderService.findAll().stream()
                .filter(o -> o.getCreatedAt() != null
                          && o.getCreatedAt().isAfter(sStart)
                          && o.getCreatedAt().isBefore(sEnd))
                .collect(Collectors.toList());
            pendingCount = myOrdersInStatsRange.stream()
                .filter(o -> o.getStatus() == OrderStatus.PENDING)
                .count();
            inPreparationCount = myOrdersInStatsRange.stream()
                .filter(o -> o.getStatus() == OrderStatus.IN_PREPARATION)
                .count();
        } else if ("admin".equals(role)) {
            pendingCount = orderService.countByStatusAndDateRange(OrderStatus.PENDING, statsStartDate, statsEndDate);
            inPreparationCount = orderService.countByStatusAndDateRange(OrderStatus.IN_PREPARATION, statsStartDate, statsEndDate);
        } else {
            pendingCount = orderService.countByStatus(OrderStatus.PENDING);
            inPreparationCount = orderService.countByStatus(OrderStatus.IN_PREPARATION);
        }
        
        // My Collected Revenue: Orders paid by current user in selected date range (regardless of who created them)
        // Used by Admin for "Ingresos Hoy Cobrados"
        BigDecimal myCollectedRevenue = orderService.getRevenueByUsernameAndDateRange(currentUsername, statsStartDate, statsEndDate);
        
        // My Own Revenue: Orders CREATED AND PAID by current user in selected date range
        // Used by Waiter for "Ingresos Propios Hoy" (orders I created AND I collected)
        BigDecimal myOwnRevenue = orderService.getRevenueCreatedAndPaidBySameUserAndDateRange(currentUsername, statsStartDate, statsEndDate);
        
        // Others Collected Revenue: Orders CREATED by current user but PAID by someone else in selected date range
        // Used by Waiter for "Ingresos Globales Hoy" (income from orders they created but others collected)
        BigDecimal othersCollectedRevenue = orderService.getRevenueCreatedByUserPaidByOthersAndDateRange(currentUsername, statsStartDate, statsEndDate);

        // Get filter data
        List<RestaurantTable> tables = restaurantTableService.findAllOrderByTableNumber();
        OrderStatus[] statuses = OrderStatus.values();
        OrderType[] orderTypes = OrderType.values();

        // Server-side pagination
        int pageSize = 15;
        int totalElements = orders.size();
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        if (totalPages == 0) totalPages = 1;
        page = Math.max(1, Math.min(page, totalPages));
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalElements);
        List<Order> pagedOrders = totalElements > 0 ? orders.subList(startIndex, endIndex) : orders;

        model.addAttribute("orders", pagedOrders);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalElements", totalElements);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("tables", tables);
        model.addAttribute("statuses", statuses);
        model.addAttribute("orderTypes", orderTypes);
        model.addAttribute("paidCount", paidCount);
        model.addAttribute("todayRevenue", todayRevenue);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("inPreparationCount", inPreparationCount);
        model.addAttribute("myCollectedRevenue", myCollectedRevenue);
        model.addAttribute("myOwnRevenue", myOwnRevenue);
        model.addAttribute("othersCollectedRevenue", othersCollectedRevenue);
        model.addAttribute("selectedTableId", tableId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedOrderType", orderType);
        model.addAttribute("selectedDate", date);
        model.addAttribute("currentRole", role);
        
        // Check if restaurant is currently open
        boolean isRestaurantOpen = businessHoursService.isOpenNow();
        model.addAttribute("isRestaurantOpen", isRestaurantOpen);
        log.debug("Restaurant is currently: {}", isRestaurantOpen ? "open" : "closed");

        return role + "/orders/list";
    }

    /**
     * Show table selection view for DINE_IN orders
     */
    @GetMapping("/select-table")
    public String selectTable(@PathVariable String role, Authentication authentication, Model model) {
        log.debug("Displaying table selection for new order - role: {}", role);

        // Validate role
        validateRole(role, authentication);

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
        long reservedNowCount = 0;
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
                } else if (isReservedNow) {
                    reservedNowCount++;
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
        model.addAttribute("reservedNowCount", reservedNowCount);
        model.addAttribute("outOfServiceCount", outOfServiceCount);
        model.addAttribute("totalCount", allTables.size());
        model.addAttribute("currentRole", role);

        return role + "/orders/order-table-selection";
    }

    /**
     * Get active reservation for a table (AJAX)
     * Returns the first PENDING reservation that is due now or has passed
     * Used to confirm if the customer at the table has a reservation
     */
    @GetMapping("/table/{tableId}/active-reservation")
    @ResponseBody
    public Map<String, Object> getActiveReservationForTable(
            @PathVariable String role,
            @PathVariable Long tableId,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            validateRole(role, authentication);
            
            // Get the first active pending reservation for this table
            Optional<Reservation> activeReservation = reservationService.findFirstActivePendingReservationForTable(tableId);
            
            if (activeReservation.isPresent()) {
                Reservation reservation = activeReservation.get();
                response.put("success", true);
                response.put("hasActiveReservation", true);
                response.put("reservationId", reservation.getId());
                response.put("customerName", reservation.getCustomerName());
                response.put("customerPhone", reservation.getCustomerPhone());
                response.put("partySize", reservation.getNumberOfGuests());
                response.put("reservationTime", reservation.getReservationTime().toString());
                response.put("reservationDate", reservation.getReservationDate().toString());
                response.put("notes", reservation.getSpecialRequests());
            } else {
                response.put("success", true);
                response.put("hasActiveReservation", false);
            }
        } catch (Exception e) {
            log.error("Error getting active reservation for table: {}", tableId, e);
            response.put("success", false);
            response.put("message", "Error al obtener información de reservación");
        }
        
        return response;
    }

    /**
     * Cancel a reservation (AJAX) - used when confirming the table user is not the reserved customer
     */
    @PostMapping("/reservation/{reservationId}/cancel")
    @ResponseBody
    public Map<String, Object> cancelReservationFromOrder(
            @PathVariable String role,
            @PathVariable Long reservationId,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            validateRole(role, authentication);
            String username = authentication.getName();
            
            reservationService.cancel(reservationId, username);
            
            response.put("success", true);
            response.put("message", "Reservación cancelada exitosamente");
        } catch (Exception e) {
            log.error("Error cancelling reservation: {}", reservationId, e);
            response.put("success", false);
            response.put("message", "Error al cancelar la reservación: " + e.getMessage());
        }
        
        return response;
    }

    /**
     * Show customer information form before order menu
     */
    @GetMapping("/customer-info")
    public String customerInfoForm(
            @PathVariable String role,
            @RequestParam(required = false) Long tableId,
            @RequestParam(required = false) Long reservationId,
            @RequestParam String orderType,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.debug("Displaying customer info form - role: {}, OrderType: {}, TableId: {}, ReservationId: {}", role, orderType, tableId, reservationId);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);

        // Validate orderType
        OrderType type;
        try {
            type = OrderType.valueOf(orderType);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tipo de pedido inválido");
            return "redirect:/" + role + "/orders/select-table";
        }

        // If DINE_IN, validate table
        RestaurantTable selectedTable = null;
        Reservation linkedReservation = null;
        if (type == OrderType.DINE_IN) {
            if (tableId == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Debe seleccionar una mesa para pedidos en restaurante");
                return "redirect:/" + role + "/orders/select-table";
            }
            
            selectedTable = restaurantTableService.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada"));
            
            // Validate table availability
            if (!orderService.isTableAvailableForOrder(tableId)) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "La mesa #" + selectedTable.getTableNumber() + " no está disponible");
                return "redirect:/" + role + "/orders/select-table";
            }
            
            // If there's a linked reservation, get its details
            if (reservationId != null) {
                try {
                    linkedReservation = reservationService.findByIdOrThrow(reservationId);
                } catch (Exception e) {
                    log.warn("Reservation not found: {}", reservationId);
                }
            }
        }

        model.addAttribute("orderType", type);
        model.addAttribute("selectedTable", selectedTable);
        model.addAttribute("linkedReservation", linkedReservation);
        model.addAttribute("currentRole", role);
        // Expose system configuration so DELIVERY block can prefill deliveryCost
        model.addAttribute("config", systemConfigurationService.getConfiguration());

        return role + "/orders/order-customer-info";
    }

    /**
     * Show menu items selection with cart
     */
    @GetMapping("/menu")
    public String menuSelection(
            @PathVariable String role,
            @RequestParam String orderType,
            @RequestParam(required = false) Long tableId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String customerPhone,
            @RequestParam(required = false) String deliveryAddress,
            @RequestParam(required = false) String deliveryReferences,
            @RequestParam(required = false) java.math.BigDecimal deliveryCost,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.debug("Displaying menu selection - role: {}, OrderType: {}, TableId: {}", role, orderType, tableId);

        // Validate role
        validateRole(role, authentication);

        // Get logged-in employee
        String username = authentication.getName();
        Employee employee = employeeService.findByUsername(username)
            .orElseThrow(() -> new IllegalStateException("Empleado no encontrado para usuario: " + username));

        // Validate orderType
        OrderType type;
        try {
            type = OrderType.valueOf(orderType);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tipo de pedido inválido");
            return "redirect:/" + role + "/orders/select-table";
        }

        // Get table info if DINE_IN
        RestaurantTable selectedTable = null;
        if (type == OrderType.DINE_IN && tableId != null) {
            selectedTable = restaurantTableService.findById(tableId)
                .orElse(null);
        }

        // Update availability for all items based on current stock
        itemMenuService.updateAllItemsAvailability();
        
        // Get available menu items grouped by category
        List<ItemMenu> availableItems = itemMenuService.findAvailableItems();
        
        // Group items by category for easier display
        Map<Long, List<ItemMenu>> itemsByCategory = availableItems.stream()
            .collect(Collectors.groupingBy(item -> item.getCategory().getIdCategory()));
        
        // Get all active categories - ONLY those with active items
        List<Category> categories = categoryService.getAllActiveCategories().stream()
            .filter(category -> itemsByCategory.containsKey(category.getIdCategory()))
            .collect(Collectors.toList());

        // Get system configuration
        SystemConfiguration config = systemConfigurationService.getConfiguration();
        
        // Get enabled payment methods based on order type
        // For DELIVERY orders, use deliveryPaymentMethods; for others use regular paymentMethods
        Map<PaymentMethodType, Boolean> paymentMethodsMap = type == OrderType.DELIVERY 
                ? config.getDeliveryPaymentMethods() 
                : config.getPaymentMethods();
        
        List<PaymentMethodType> enabledPaymentMethods = paymentMethodsMap.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(PaymentMethodType::name))
                .collect(Collectors.toList());
        
        // Validate at least one payment method is enabled
        if (enabledPaymentMethods.isEmpty()) {
            String orderTypeText = type == OrderType.DELIVERY ? "entregas a domicilio" : "el restaurante";
            log.warn("No payment methods enabled for {} in system configuration", orderTypeText);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "No hay métodos de pago habilitados para " + orderTypeText + ". Por favor contacte al administrador.");
            return "redirect:/" + role + "/orders";
        }

        model.addAttribute("orderType", type);
        model.addAttribute("selectedTable", selectedTable);
        model.addAttribute("customerName", customerName);
        model.addAttribute("customerPhone", customerPhone);
        model.addAttribute("deliveryAddress", deliveryAddress);
        model.addAttribute("deliveryReferences", deliveryReferences);
        // For DELIVERY orders, prefill from form value or config default; otherwise 0
        java.math.BigDecimal effectiveDeliveryCost;
        if (type == OrderType.DELIVERY) {
            effectiveDeliveryCost = (deliveryCost != null) ? deliveryCost : config.getDefaultDeliveryCost();
        } else {
            effectiveDeliveryCost = java.math.BigDecimal.ZERO;
        }
        model.addAttribute("deliveryCost", effectiveDeliveryCost);
        model.addAttribute("categories", categories);
        model.addAttribute("itemsByCategory", itemsByCategory);
        model.addAttribute("allItems", availableItems);
        model.addAttribute("employee", employee);
        model.addAttribute("currentRole", role);
        model.addAttribute("config", config);
        model.addAttribute("enabledPaymentMethods", enabledPaymentMethods);
        
        // Add active promotions for items
        List<Promotion> activePromotions = promotionService.findActivePromotions();
        model.addAttribute("activePromotions", activePromotions);

        return role + "/orders/order-menu";
    }

    /**
     * Show menu to add items to existing order
     * GET /{role}/orders/{orderId}/add-items
     */
    @GetMapping("/{orderId}/add-items")
    public String showMenuToAddItems(
            @PathVariable String role,
            @PathVariable Long orderId,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.debug("Displaying menu to add items to existing order - role: {}, orderId: {}", role, orderId);

        // Validate role
        validateRole(role, authentication);

        // Get logged-in employee
        String username = authentication.getName();
        Employee employee = employeeService.findByUsername(username)
            .orElseThrow(() -> new IllegalStateException("Empleado no encontrado para usuario: " + username));

        // Get the order
        OrderService orderService = getOrderService(role);
        Order order = orderService.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

        // Validate order can accept new items using canAcceptNewItems() method
        // This checks: DINE_IN until PAID, TAKEOUT until READY, DELIVERY until READY (not ON_THE_WAY)
        if (!order.canAcceptNewItems()) {
            String errorMsg = "No se pueden agregar items a este pedido";
            if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.CANCELLED) {
                errorMsg = "No se pueden agregar items a un pedido " + order.getStatus().getDisplayName();
            } else if (order.getOrderType() == OrderType.DELIVERY && order.getStatus() == OrderStatus.ON_THE_WAY) {
                errorMsg = "No se pueden agregar items a un pedido de DELIVERY que está EN CAMINO";
            } else if (order.getOrderType() == OrderType.DELIVERY && order.getStatus() == OrderStatus.DELIVERED) {
                errorMsg = "No se pueden agregar items a un pedido de DELIVERY que ya fue ENTREGADO";
            }
            redirectAttributes.addFlashAttribute("errorMessage", errorMsg);
            return "redirect:/" + role + "/orders";
        }

        // Update availability for all items based on current stock
        itemMenuService.updateAllItemsAvailability();
        
        // Get available menu items grouped by category
        List<ItemMenu> availableItems = itemMenuService.findAvailableItems();
        
        // Group items by category for easier display
        Map<Long, List<ItemMenu>> itemsByCategory = availableItems.stream()
            .collect(Collectors.groupingBy(item -> item.getCategory().getIdCategory()));
        
        // Get all active categories - ONLY those with active items
        List<Category> categories = categoryService.getAllActiveCategories().stream()
            .filter(category -> itemsByCategory.containsKey(category.getIdCategory()))
            .collect(Collectors.toList());

        // Get system configuration
        SystemConfiguration config = systemConfigurationService.getConfiguration();
        
        // Get enabled payment methods based on order type
        // For DELIVERY orders, use deliveryPaymentMethods; for others use regular paymentMethods
        Map<PaymentMethodType, Boolean> paymentMethodsMap = order.getOrderType() == OrderType.DELIVERY 
                ? config.getDeliveryPaymentMethods() 
                : config.getPaymentMethods();
        
        List<PaymentMethodType> enabledPaymentMethods = paymentMethodsMap.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(PaymentMethodType::name))
                .collect(Collectors.toList());

        // Set model attributes - similar to new order but with existing order context
        model.addAttribute("orderType", order.getOrderType());
        model.addAttribute("selectedTable", order.getTable());
        model.addAttribute("customerName", order.getCustomerName());
        model.addAttribute("customerPhone", order.getCustomerPhone());
        model.addAttribute("deliveryAddress", order.getDeliveryAddress());
        model.addAttribute("deliveryReferences", order.getDeliveryReferences());
        model.addAttribute("categories", categories);
        model.addAttribute("itemsByCategory", itemsByCategory);
        model.addAttribute("allItems", availableItems);
        model.addAttribute("employee", employee);
        model.addAttribute("currentRole", role);
        model.addAttribute("config", config);
        model.addAttribute("enabledPaymentMethods", enabledPaymentMethods);
        
        // Add active promotions for items
        List<Promotion> activePromotions = promotionService.findActivePromotions();
        model.addAttribute("activePromotions", activePromotions);
        
        // IMPORTANT: Add existing order ID and number so the template knows it's "add mode"
        model.addAttribute("existingOrderId", order.getIdOrder());
        model.addAttribute("existingOrderNumber", order.getOrderNumber());

        return role + "/orders/order-menu";
    }

    /**
     * Add items to existing order (POST handler)
     * POST /{role}/orders/{orderId}/add-items
     */
    @PostMapping("/{orderId}/add-items")
    public String addItemsToOrder(
            @PathVariable String role,
            @PathVariable Long orderId,
            @RequestParam(value = "itemIds", required = false) List<Long> itemIds,
            @RequestParam(value = "quantities", required = false) List<Integer> quantities,
            @RequestParam(value = "comments", required = false) List<String> comments,
            @RequestParam(value = "promotionPrices", required = false) List<String> promotionPrices,
            @RequestParam(value = "promotionIds", required = false) List<String> promotionIds,
            @RequestParam(value = "complements", required = false) List<String> complementsJson,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        String username = authentication.getName();
        log.info("Adding items to existing order ID: {} by user: {} (role: {})", orderId, username, role);

        // Validate role
        validateRole(role, authentication);
        
        // Validate restaurant is open
        if (!businessHoursService.isOpenNow()) {
            log.warn("Attempt to add items to order outside business hours by user: {}", username);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "No se pueden agregar items al pedido. El restaurante no se encuentra en horario laborable en este momento.");
            return "redirect:/" + role + "/orders";
        }
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);
        
        log.info("Item IDs: {}", itemIds);
        log.info("Quantities: {}", quantities);
        log.info("Complements JSON list size: {}", complementsJson != null ? complementsJson.size() : 0);

        try {
            // Build order details from form data (includes complement validation)
            List<OrderDetail> newOrderDetails = buildOrderDetails(itemIds, quantities, comments, promotionPrices, promotionIds, complementsJson);

            if (newOrderDetails.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Debe agregar al menos un item al pedido");
                return "redirect:/" + role + "/orders/" + orderId + "/add-items";
            }

            // Validate dine-in-only items: only allowed for DINE_IN orders
            Order existingOrder = orderService.findByIdOrThrow(orderId);
            if (existingOrder.getOrderType() != OrderType.DINE_IN) {
                for (OrderDetail detail : newOrderDetails) {
                    if (Boolean.TRUE.equals(detail.getItemMenu().getDineInOnly())) {
                        throw new IllegalArgumentException("El item '" + detail.getItemMenu().getName() + "' solo está disponible para consumo en el establecimiento.");
                    }
                }
            }
            
            // Validate all items belong to active categories
            for (OrderDetail detail : newOrderDetails) {
                ItemMenu item = detail.getItemMenu();
                if (item.getCategory() != null && !Boolean.TRUE.equals(item.getCategory().getActive())) {
                    throw new IllegalArgumentException("El item '" + item.getName() + "' pertenece a la categoría '" + 
                        item.getCategory().getName() + "' y está desactivada por el momento.");
                }
            }

            log.info("Built {} new order details", newOrderDetails.size());

            // Use the service method which handles all validations and stock deduction (including complements)
            Order updated = orderService.addItemsToExistingOrder(orderId, newOrderDetails, username);

            log.info("Items added successfully to order: {}", updated.getOrderNumber());
            
            redirectAttributes.addFlashAttribute("successMessage",
                    "Se agregaron " + newOrderDetails.size() + " items al pedido " + updated.getOrderNumber());
            
            return "redirect:/" + role + "/orders/view/" + orderId;

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Validation error adding items to order: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/" + role + "/orders/" + orderId + "/add-items";

        } catch (Exception e) {
            log.error("Error adding items to order", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al agregar items al pedido: " + e.getMessage());
            return "redirect:/" + role + "/orders/" + orderId + "/add-items";
        }
    }

    /**
     * Add items to an existing order (AJAX version)
     */
    @PostMapping(value = "/{orderId}/add-items-async", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addItemsToOrderAsync(
            @PathVariable String role,
            @PathVariable Long orderId,
            @RequestBody Map<String, Object> requestData,
            Authentication authentication) {

        String username = authentication.getName();
        log.info("Adding items to order {} ASYNC by user: {} (role: {})", orderId, username, role);
        
        Map<String, Object> response = new HashMap<>();

        try {
            validateRole(role, authentication);
            OrderService orderService = getOrderService(role);

            // Parse items from JSON body
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) requestData.get("items");
            List<OrderDetail> newOrderDetails = buildOrderDetailsFromJson(items);

            if (newOrderDetails.isEmpty()) {
                throw new IllegalArgumentException("Debe agregar al menos un item al pedido");
            }

            // Validate dine-in-only items: only allowed for DINE_IN orders
            Order existingOrderForValidation = orderService.findByIdOrThrow(orderId);
            if (existingOrderForValidation.getOrderType() != OrderType.DINE_IN) {
                for (OrderDetail detail : newOrderDetails) {
                    if (Boolean.TRUE.equals(detail.getItemMenu().getDineInOnly())) {
                        throw new IllegalArgumentException("El item '" + detail.getItemMenu().getName() + "' solo está disponible para consumo en el establecimiento.");
                    }
                }
            }
            
            // Validate all items belong to active categories
            for (OrderDetail detail : newOrderDetails) {
                ItemMenu item = detail.getItemMenu();
                if (item.getCategory() != null && !Boolean.TRUE.equals(item.getCategory().getActive())) {
                    throw new IllegalArgumentException("El item '" + item.getName() + "' pertenece a la categoría '" + 
                        item.getCategory().getName() + "' y está desactivada por el momento.");
                }
            }

            // Use the service method which handles all validations and stock deduction (including complements)
            Order updated = orderService.addItemsToExistingOrder(orderId, newOrderDetails, username);

            response.put("success", true);
            response.put("message", "Se agregaron " + newOrderDetails.size() + " items al pedido " + updated.getOrderNumber());
            response.put("redirectUrl", "/" + role + "/orders/view/" + orderId);
            
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Validation error adding items to order async: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
             if (e.getMessage() != null && (e.getMessage().contains("Stock insuficiente") || e.getMessage().contains("No tenemos suficiente stock"))) {
                 response.put("errorType", "STOCK_ERROR");
            }
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Error adding items to order async", e);
            response.put("success", false);
            response.put("message", "Error al agregar items al pedido: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Show form to create a new order
     */
    @GetMapping("/new")
    public String newOrderForm(
            @PathVariable String role,
            @RequestParam(required = false) Long tableId,
            @RequestParam(required = false) String orderType,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.debug("Displaying new order form - role: {}", role);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);

        // Get logged-in employee
        String username = authentication.getName();
        Employee employee = employeeService.findByUsername(username)
            .orElseThrow(() -> new IllegalStateException("Empleado no encontrado para usuario: " + username));

        Order order = new Order();
        order.setEmployee(employee);
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedBy(username);

        // Set table if provided
        if (tableId != null) {
            RestaurantTable table = restaurantTableService.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada"));
            
            // Validate table availability
            if (!orderService.isTableAvailableForOrder(tableId)) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "La mesa #" + table.getTableNumber() + " no está disponible o ya tiene un pedido activo");
                return "redirect:/" + role + "/orders";
            }
            
            order.setTable(table);
            order.setOrderType(OrderType.DINE_IN);
        }

        // Set order type if provided
        if (orderType != null) {
            try {
                order.setOrderType(OrderType.valueOf(orderType));
            } catch (IllegalArgumentException e) {
                order.setOrderType(OrderType.DINE_IN);
            }
        } else if (order.getOrderType() == null) {
            order.setOrderType(OrderType.DINE_IN);
        }

        // Get system configuration for tax rate and payment methods
        SystemConfiguration config = systemConfigurationService.getConfiguration();
        
        // Get AVAILABLE tables that are not blocked by reservations
        List<RestaurantTable> availableTables = restaurantTableService.findAvailableTables().stream()
            .filter(t -> !restaurantTableService.isTableBlockedByReservation(t.getId()))
            .collect(Collectors.toCollection(ArrayList::new));
        
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

        // Get enabled payment methods based on current order type
        // For DELIVERY orders, use deliveryPaymentMethods; for others use regular paymentMethods
        Map<PaymentMethodType, Boolean> paymentMethodsMap = (order.getOrderType() == OrderType.DELIVERY) 
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

        model.addAttribute("order", order);
        model.addAttribute("employee", employee);
        model.addAttribute("orderDetails", new ArrayList<>()); // Empty list for new orders
        model.addAttribute("availableTables", availableTables);
        model.addAttribute("availableItems", availableItemsDTO);
        model.addAttribute("orderTypes", OrderType.values());
        model.addAttribute("paymentMethods", enabledPaymentMethods);
        model.addAttribute("regularPaymentMethods", regularPaymentMethodsDTO);
        model.addAttribute("deliveryPaymentMethods", deliveryPaymentMethodsDTO);
        model.addAttribute("taxRate", config.getTaxRate());
        model.addAttribute("formAction", "/" + role + "/orders");
        model.addAttribute("currentRole", role);

        return role + "/orders/form";
    }

    /**
     * Create a new order (AJAX version)
     */
    @PostMapping(value = "/create-async", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createOrderAsync(
            @PathVariable String role,
            @RequestBody Map<String, Object> requestData,
            Authentication authentication) {

        String username = authentication.getName();
        log.info("Creating new order ASYNC (JSON) by user: {} (role: {})", username, role);
        
        Map<String, Object> response = new HashMap<>();

        // Prevent duplicate submissions: if this user already has an order in progress, reject
        String submissionKey = username + "_" + role;
        if (!activeOrderSubmissions.add(submissionKey)) {
            log.warn("Duplicate order submission blocked for user: {} (role: {})", username, role);
            response.put("success", false);
            response.put("message", "Ya hay un pedido en proceso. Por favor espere.");
            return ResponseEntity.status(429).body(response);
        }

        try {
            validateRole(role, authentication);
            OrderService orderService = getOrderService(role);

            // Validate restaurant is open
            if (!businessHoursService.isOpenNow()) {
                throw new IllegalStateException("No se puede crear el pedido. El restaurante no se encuentra en horario laborable en este momento.");
            }

            // Parse order fields from JSON
            String orderTypeStr = (String) requestData.get("orderType");
            String paymentMethodStr = (String) requestData.get("paymentMethod");
            Long employeeId = requestData.get("employeeId") != null ? Long.valueOf(requestData.get("employeeId").toString()) : null;
            Long tableId = requestData.get("tableId") != null ? Long.valueOf(requestData.get("tableId").toString()) : null;
            String customerName = (String) requestData.get("customerName");
            String customerPhone = (String) requestData.get("customerPhone");
            String deliveryAddress = (String) requestData.get("deliveryAddress");
            String deliveryReferences = (String) requestData.get("deliveryReferences");
            Object deliveryCostRaw = requestData.get("deliveryCost");

            OrderType orderType = orderTypeStr != null ? OrderType.valueOf(orderTypeStr) : OrderType.DINE_IN;
            PaymentMethodType paymentMethod = paymentMethodStr != null ? PaymentMethodType.valueOf(paymentMethodStr) : PaymentMethodType.CASH;

            // Validate payment method based on order type
            SystemConfiguration config = systemConfigurationService.getConfiguration();
            if (!config.isPaymentMethodEnabledForOrderType(paymentMethod, orderType)) {
                String context = orderType == OrderType.DELIVERY ? " para entregas a domicilio" : "";
                throw new IllegalArgumentException("El método de pago seleccionado (" + paymentMethod.getDisplayName() + ") no está habilitado" + context);
            }

            // Build Order entity
            Order order = new Order();
            order.setOrderType(orderType);
            order.setPaymentMethod(paymentMethod);
            order.setCustomerName(customerName);
            order.setCustomerPhone(customerPhone);
            order.setDeliveryAddress(deliveryAddress);
            order.setDeliveryReferences(deliveryReferences);
            // Delivery cost: only meaningful for DELIVERY (service layer also enforces this)
            if (orderType == OrderType.DELIVERY) {
                java.math.BigDecimal dc;
                if (deliveryCostRaw == null || deliveryCostRaw.toString().isBlank()) {
                    dc = config.getDefaultDeliveryCost();
                } else {
                    try {
                        dc = new java.math.BigDecimal(deliveryCostRaw.toString());
                    } catch (NumberFormatException ex) {
                        throw new IllegalArgumentException("Costo de envío inválido");
                    }
                }
                if (dc.compareTo(java.math.BigDecimal.ZERO) < 0
                        || dc.compareTo(new java.math.BigDecimal("999999.99")) > 0) {
                    throw new IllegalArgumentException("El costo de envío debe estar entre 0 y 999,999.99");
                }
                order.setDeliveryCost(dc);
            } else {
                order.setDeliveryCost(java.math.BigDecimal.ZERO);
            }
            order.setCreatedBy(username);
            order.setStatus(OrderStatus.PENDING);

            if (employeeId != null) {
                Employee employee = employeeService.findById(employeeId)
                    .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado con ID: " + employeeId));
                order.setEmployee(employee);
            }

            if (tableId != null) {
                RestaurantTable table = restaurantTableService.findById(tableId)
                    .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada con ID: " + tableId));
                order.setTable(table);
            }

            // Parse items from JSON body
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) requestData.get("items");
            List<OrderDetail> orderDetails = buildOrderDetailsFromJson(items);

            if (orderDetails.isEmpty()) {
                throw new IllegalArgumentException("Debe agregar al menos un item al pedido");
            }
            
            // Validate dine-in-only items are only in DINE_IN orders
            if (order.getOrderType() != OrderType.DINE_IN) {
                for (OrderDetail detail : orderDetails) {
                    if (Boolean.TRUE.equals(detail.getItemMenu().getDineInOnly())) {
                        throw new IllegalArgumentException("El item '" + detail.getItemMenu().getName() + "' solo está disponible para consumo en el establecimiento.");
                    }
                }
            }
            
            // Validate all items belong to active categories
            for (OrderDetail detail : orderDetails) {
                ItemMenu item = detail.getItemMenu();
                if (item.getCategory() != null && !Boolean.TRUE.equals(item.getCategory().getActive())) {
                    throw new IllegalArgumentException("El item '" + item.getName() + "' pertenece a la categoría '" + 
                        item.getCategory().getName() + "' y está desactivada por el momento.");
                }
            }
            
            // Validate promotions are still active
            Map<String, Object> promotionValidation = validatePromotions(items);
            if (!(Boolean) promotionValidation.get("allValid")) {
                response.put("success", false);
                response.put("errorType", "PROMOTION_EXPIRED");
                response.put("expiredPromotions", promotionValidation.get("expiredPromotions"));
                response.put("message", "Algunas promociones ya no están disponibles");
                return ResponseEntity.ok(response);
            }

            Order createdOrder = orderService.create(order, orderDetails);

            response.put("success", true);
            response.put("message", "Pedido creado exitosamente No. " + createdOrder.getOrderNumber());
            response.put("redirectUrl", "/" + role + "/orders");
            
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Validation error creating order async: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            if (e.getMessage() != null && (e.getMessage().contains("Stock insuficiente") || e.getMessage().contains("No tenemos suficiente stock"))) {
                 response.put("errorType", "STOCK_ERROR");
            }
            return ResponseEntity.badRequest().body(response);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Optimistic locking failure creating order async: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "El stock de los ingredientes ha cambiado mientras realizaba el pedido. Por favor intente nuevamente.");
            response.put("errorType", "CONCURRENCY_ERROR");
            return ResponseEntity.status(409).body(response);
        } catch (Exception e) {
            log.error("Error creating order async", e);
            response.put("success", false);
            response.put("message", "Error al crear el pedido: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        } finally {
            // Always release the submission lock
            activeOrderSubmissions.remove(submissionKey);
        }
    }

    /**
     * Create a new order
     */
    @PostMapping
    public String createOrder(
            @PathVariable String role,
            @ModelAttribute("order") Order order,  // Sin @Valid - los campos se llenan programáticamente
            BindingResult bindingResult,
            @RequestParam(value = "employeeId", required = true) Long employeeId,
            @RequestParam(value = "tableId", required = false) Long tableId,
            @RequestParam(value = "itemIds", required = false) List<Long> itemIds,
            @RequestParam(value = "quantities", required = false) List<Integer> quantities,
            @RequestParam(value = "comments", required = false) List<String> comments,
            @RequestParam(value = "promotionPrices", required = false) List<String> promotionPrices,
            @RequestParam(value = "promotionIds", required = false) List<String> promotionIds,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        String username = authentication.getName();
        log.info("Creating new order by user: {} (role: {})", username, role);

        // Validate role
        validateRole(role, authentication);
        
        // Validate restaurant is open
        if (!businessHoursService.isOpenNow()) {
            log.warn("Attempt to create order outside business hours by user: {}", username);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "No se puede crear el pedido. El restaurante no se encuentra en horario laborable en este momento.");
            return "redirect:/" + role + "/orders";
        }
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);
        log.info("Employee ID (param): {}", employeeId);
        log.info("Order Type: {}", order.getOrderType());
        log.info("Table ID (param): {}", tableId);
        log.info("Customer Name: {}", order.getCustomerName());
        log.info("Customer Phone: {}", order.getCustomerPhone());
        log.info("Delivery Address: {}", order.getDeliveryAddress());
        log.info("Payment Method: {}", order.getPaymentMethod());
        log.info("Item IDs: {}", itemIds);
        log.info("Quantities: {}", quantities);
        log.info("Comments: {}", comments);

        // No validamos bindingResult porque Order se completa programáticamente

        try {
            // Validate payment method is enabled based on order type (DELIVERY uses different payment methods)
            SystemConfiguration config = systemConfigurationService.getConfiguration();
            if (!config.isPaymentMethodEnabledForOrderType(order.getPaymentMethod(), order.getOrderType())) {
                String context = order.getOrderType() == OrderType.DELIVERY ? " para entregas a domicilio" : "";
                log.warn("Payment method not enabled for order type {}: {}", order.getOrderType(), order.getPaymentMethod());
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "El método de pago seleccionado (" + order.getPaymentMethod().getDisplayName() + ") no está habilitado" + context);
                return "redirect:/" + role + "/orders/menu?orderType=" + order.getOrderType().name() +
                    (tableId != null ? "&tableId=" + tableId : "") +
                    (order.getCustomerName() != null ? "&customerName=" + order.getCustomerName() : "") +
                    (order.getCustomerPhone() != null ? "&customerPhone=" + order.getCustomerPhone() : "");
            }
            
            // Set employee
            Employee employee = employeeService.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado con ID: " + employeeId));
            order.setEmployee(employee);
            
            // Set table if provided
            if (tableId != null) {
                RestaurantTable table = restaurantTableService.findById(tableId)
                    .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada con ID: " + tableId));
                order.setTable(table);
            }
            
            // Build order details from form data
            List<OrderDetail> orderDetails = buildOrderDetails(itemIds, quantities, comments, promotionPrices, promotionIds, null);

            if (orderDetails.isEmpty()) {
                model.addAttribute("errorMessage", "Debe agregar al menos un item al pedido");
                loadFormData(model, order, username, role);
                return role + "/orders/form";
            }
            
            // Validate dine-in-only items are only in DINE_IN orders
            if (order.getOrderType() != OrderType.DINE_IN) {
                for (OrderDetail detail : orderDetails) {
                    if (Boolean.TRUE.equals(detail.getItemMenu().getDineInOnly())) {
                        redirectAttributes.addFlashAttribute("errorMessage", 
                            "El item '" + detail.getItemMenu().getName() + "' solo está disponible para consumo en el establecimiento.");
                        return "redirect:/" + role + "/orders";
                    }
                }
            }
            
            // Validate all items belong to active categories
            for (OrderDetail detail : orderDetails) {
                ItemMenu item = detail.getItemMenu();
                if (item.getCategory() != null && !Boolean.TRUE.equals(item.getCategory().getActive())) {
                    redirectAttributes.addFlashAttribute("errorMessage", 
                        "El item '" + item.getName() + "' pertenece a la categoría '" + 
                        item.getCategory().getName() + "' y está desactivada por el momento.");
                    return "redirect:/" + role + "/orders";
                }
            }

            // Set audit fields
            order.setCreatedBy(username);

            // Create order
            Order created = orderService.create(order, orderDetails);

            log.info("Order created successfully: {}", created.getOrderNumber());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Pedido " + created.getOrderNumber() + " creado exitosamente");
            return "redirect:/" + role + "/orders";

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Validation error creating order: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            loadFormData(model, order, username, role);
            return role + "/orders/form";

        } catch (Exception e) {
            log.error("Error creating order", e);
            model.addAttribute("errorMessage", "Error al crear el pedido: " + e.getMessage());
            loadFormData(model, order, username, role);
            return role + "/orders/form";
        }
    }

    /**
     * Show form to edit an existing order (only PENDING orders)
     */
    @GetMapping("/edit/{id}")
    public String editOrderForm(
            @PathVariable String role,
            @PathVariable Long id,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.debug("Displaying edit form for order ID: {} (role: {})", id, role);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);

        return orderService.findByIdWithDetails(id)
                .map(order -> {
                    // Cannot edit PAID or CANCELLED orders
                    if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.CANCELLED) {
                        redirectAttributes.addFlashAttribute("errorMessage", 
                            "No se pueden editar pedidos PAGADOS o CANCELADOS");
                        return "redirect:/" + role + "/orders";
                    }

                    SystemConfiguration config = systemConfigurationService.getConfiguration();
                    
                    // Get AVAILABLE tables that are not blocked by reservations
                    List<RestaurantTable> availableTables = restaurantTableService.findAvailableTables().stream()
                        .filter(t -> !restaurantTableService.isTableBlockedByReservation(t.getId()))
                        .collect(Collectors.toCollection(ArrayList::new));
                    
                    // If order has a table assigned, ensure it's in the list
                    if (order.getTable() != null) {
                        boolean tableInList = availableTables.stream()
                            .anyMatch(t -> t.getId().equals(order.getTable().getId()));
                        if (!tableInList) {
                            availableTables.add(order.getTable());
                        }
                    }
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
                    
                    Map<PaymentMethodType, Boolean> paymentMethods = order.getOrderType() == OrderType.DELIVERY 
                        ? config.getDeliveryPaymentMethods() 
                        : config.getPaymentMethods();
                    List<PaymentMethodType> enabledPaymentMethods = paymentMethods.entrySet().stream()
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

                    // Convert order details to simple DTOs to avoid circular reference issues
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
                    model.addAttribute("employee", order.getEmployee()); // Add employee to model
                    model.addAttribute("orderDetails", orderDetailsDTO);
                    model.addAttribute("availableTables", availableTables);
                    model.addAttribute("availableItems", availableItemsDTO);
                    model.addAttribute("orderTypes", OrderType.values());
                    model.addAttribute("paymentMethods", enabledPaymentMethods);
                    model.addAttribute("regularPaymentMethods", regularPaymentMethodsDTO);
                    model.addAttribute("deliveryPaymentMethods", deliveryPaymentMethodsDTO);
                    model.addAttribute("taxRate", config.getTaxRate());
                    model.addAttribute("defaultDeliveryCost", config.getDefaultDeliveryCost());
                    model.addAttribute("formAction", "/" + role + "/orders/" + id);
                    model.addAttribute("currentRole", role);
                    
                    // Determine if order type can be changed based on current status
                    boolean canChangeOrderType = canChangeOrderType(order);
                    model.addAttribute("canChangeOrderType", canChangeOrderType);
                    
                    return role + "/orders/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Pedido no encontrado");
                    return "redirect:/" + role + "/orders";
                });
    }

    /**
     * Update an existing order (only basic info: customer, order type, payment method)
     * Does NOT modify order items or stock - those are managed separately via add-items/delete-item
     */
    @PostMapping("/{id}")
    public String updateOrder(
            @PathVariable String role,
            @PathVariable Long id,
            @ModelAttribute("order") Order order,  // Removed @Valid to handle custom validation
            BindingResult bindingResult,
            @RequestParam(value = "employeeId", required = false) Long employeeId,
            @RequestParam(value = "tableId", required = false) Long tableId,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        String username = authentication.getName();
        log.info("Updating order INFO (no items) with ID: {} by user: {} (role: {})", id, username, role);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);

        try {
            // Get existing order to validate order type change
            Order existingOrder = orderService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));
            
            // Validate order type change restrictions ONLY if the order type is actually changing
            if (order.getOrderType() != existingOrder.getOrderType()) {
                if (!canChangeOrderType(existingOrder)) {
                    String statusMessage = getOrderTypeChangeRestrictionMessage(existingOrder);
                    redirectAttributes.addFlashAttribute("errorMessage", statusMessage);
                    return "redirect:/" + role + "/orders/edit/" + id;
                }
                // If changing away from DINE_IN, validate no dine-in-only items exist
                if (order.getOrderType() != OrderType.DINE_IN && existingOrder.getOrderDetails() != null) {
                    for (OrderDetail detail : existingOrder.getOrderDetails()) {
                        if (Boolean.TRUE.equals(detail.getItemMenu().getDineInOnly())) {
                            redirectAttributes.addFlashAttribute("errorMessage",
                                "No se puede cambiar el tipo de pedido porque contiene el item '" + detail.getItemMenu().getName() + "' que solo está disponible para consumo en el establecimiento.");
                            return "redirect:/" + role + "/orders/edit/" + id;
                        }
                    }
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
                    return "redirect:/" + role + "/orders/edit/" + id;
                }
            }
            
            // Verify payment method is enabled in configuration if it's being set
            if (order.getPaymentMethod() != null) {
                SystemConfigurationService configService = systemConfigurationService; // Accessed via field
                SystemConfiguration config = configService.getConfiguration();
                // Validate based on order type - DELIVERY orders use deliveryPaymentMethods
                if (!config.isPaymentMethodEnabledForOrderType(order.getPaymentMethod(), order.getOrderType())) {
                    // Use redirect to preserve the order data on reload
                    String context = order.getOrderType() == OrderType.DELIVERY ? " para entregas a domicilio" : "";
                    redirectAttributes.addFlashAttribute("errorMessage", 
                        "El método de pago seleccionado (" + order.getPaymentMethod().getDisplayName() + ") está deshabilitado" + context);
                    return "redirect:/" + role + "/orders/edit/" + id;
                }
            }

            // Set table from form data
            if (tableId != null) {
                RestaurantTable table = restaurantTableService.findById(tableId)
                    .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada"));
                order.setTable(table);
                log.info("Table #{} will be assigned to order", table.getTableNumber());
            } else {
                order.setTable(null);
                log.info("No table will be assigned to order");
            }

            // Set audit fields
            order.setUpdatedBy(username);

            // Update ONLY basic order info (no items, no stock manipulation)
            // Items are managed separately via add-items and delete-item endpoints
            Order updated = orderService.updateOrderInfo(id, order);

            log.info("Order info updated successfully: {}", updated.getOrderNumber());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Pedido " + updated.getOrderNumber() + " actualizado exitosamente");
            return "redirect:/" + role + "/orders";

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Validation error updating order: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            order.setIdOrder(id);
            loadFormData(model, order, username, role);
            loadEditOrderData(model, id, orderService);
            model.addAttribute("formAction", "/" + role + "/orders/" + id);
            return role + "/orders/form";

        } catch (Exception e) {
            log.error("Error updating order", e);
            String constraintMsg = GlobalExceptionHandler.extractConstraintMessages(e);
            String errorMsg = constraintMsg != null ? constraintMsg : "Error al actualizar el pedido: " + e.getMessage();
            model.addAttribute("errorMessage", errorMsg);
            order.setIdOrder(id);
            loadFormData(model, order, username, role);
            loadEditOrderData(model, id, orderService);
            model.addAttribute("formAction", "/" + role + "/orders/" + id);
            return role + "/orders/form";
        }
    }

    /**
     * View order details
     */
    @GetMapping("/view/{id}")
    public String viewOrder(
            @PathVariable String role,
            @PathVariable Long id,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        log.debug("Viewing order ID: {} (role: {})", id, role);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);

        return orderService.findById(id)
                .map(order -> {
                    model.addAttribute("order", order);
                    model.addAttribute("orderDetails", order.getOrderDetails());
                    model.addAttribute("currentRole", role);
                    return role + "/orders/view";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Pedido no encontrado");
                    return "redirect:/" + role + "/orders";
                });
    }

    /**
     * Get maximum available quantity for a menu item based on ingredient stock (AJAX)
     */
    @GetMapping("/menu-items/{itemId}/max-quantity")
    @ResponseBody
    public Map<String, Object> getMaxQuantity(
            @PathVariable String role,
            @PathVariable Long itemId,
            Authentication authentication) {
        
        log.debug("Getting max available quantity for menu item {} (role: {})", itemId, role);
        validateRole(role, authentication);
        
        Map<String, Object> response = new HashMap<>();
        try {
            int maxQuantity = itemMenuService.getMaxAvailableQuantity(itemId);
            response.put("maxQuantity", maxQuantity);
            response.put("success", true);
        } catch (Exception e) {
            log.error("Error getting max quantity for item {}", itemId, e);
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("maxQuantity", 0);
        }
        
        return response;
    }

    /**
     * Cancel an order (AJAX)
     */
    @PostMapping("/{id}/cancel")
    @ResponseBody
    public Map<String, Object> cancelOrder(
            @PathVariable String role,
            @PathVariable Long id,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Cancelling order ID: {} by user: {} (role: {})", id, username, role);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);

        Map<String, Object> response = new HashMap<>();
        
        try {
            Order cancelled = orderService.cancel(id, username);
            response.put("success", true);
            response.put("message", "Pedido " + cancelled.getOrderNumber() + " cancelado exitosamente");
            response.put("order", buildOrderDTO(cancelled));
            
            // Analyze items to determine stock return information
            String stockInfo = analyzeStockReturn(cancelled);
            if (stockInfo != null && !stockInfo.isEmpty()) {
                response.put("stockInfo", stockInfo);
            }
            
            // Send WebSocket notification about order cancellation
            wsNotificationService.notifyOrderCancelled(cancelled);
            
        } catch (IllegalStateException e) {
            log.warn("Error cancelling order: {}", e.getMessage());
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
     * Change order status (AJAX)
     */
    @PostMapping("/{id}/change-status")
    @ResponseBody
    public Map<String, Object> changeStatus(
            @PathVariable String role,
            @PathVariable Long id,
            @RequestParam String newStatus,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Changing order status. ID: {}, New Status: {}, User: {} (role: {})", id, newStatus, username, role);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);

        Map<String, Object> response = new HashMap<>();
        
        try {
            OrderStatus status = OrderStatus.valueOf(newStatus);
            
            // Get current employee
            Employee currentEmployee = employeeService.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Empleado no encontrado"));
            
            // Get the order to check current state
            Order order = orderService.findByIdOrThrow(id);

            // SECURITY: Disallow marking an order as PAID via the generic change-status endpoint.
            // Cobrar (pasar a PAID) DEBE hacerse a través de los controladores de pago dedicados
            // (WaiterPaymentController, CashierPaymentController, PaymentController, DeliveryController.processPayment),
            // los cuales aplican las validaciones específicas por rol: método de pago habilitado,
            // restricciones por rol (waiter no efectivo, etc.), propina, descuento de orden, y
            // generación de autofactura. Permitir PAID aquí saltaba esas validaciones.
            if (status == OrderStatus.PAID) {
                response.put("success", false);
                response.put("message", "Para cobrar una orden debes usar el formulario de pago. No se puede marcar como PAGADO desde aquí.");
                return response;
            }
            
            // Set preparedBy BEFORE changing status (when someone accepts the order)
            // For CHEF role: use preparedBy
            // For BARISTA role: use preparedByBarista
            if (status == OrderStatus.IN_PREPARATION && order.getStatus() == OrderStatus.PENDING) {
                if ("chef".equalsIgnoreCase(role) && order.getPreparedBy() == null) {
                    // Chef accepting the order
                    order.setPreparedBy(currentEmployee);
                    orderRepository.save(order);
                    log.info("Setting preparedBy (Chef) to: {} for order {}", username, id);
                } else if ("barista".equalsIgnoreCase(role) && order.getPreparedByBarista() == null) {
                    // Barista accepting the order
                    order.setPreparedByBarista(currentEmployee);
                    orderRepository.save(order);
                    log.info("Setting preparedByBarista to: {} for order {}", username, id);
                }
            }
            
            // Now change the status
            Order updated = orderService.changeStatus(id, status, username);
            
            response.put("success", true);
            response.put("message", "Estado del pedido cambiado a " + status.getDisplayName());
            response.put("order", buildOrderDTO(updated));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid status: {}", newStatus);
            response.put("success", false);
            response.put("message", "Estado inválido: " + newStatus);
        } catch (IllegalStateException e) {
            log.warn("Error changing order status: {}", e.getMessage());
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
     * Add new items to an existing order (AJAX)
     * Only available for DINE_IN orders (customers at table can order more)
     */
    @PostMapping("/{id}/add-items-ajax")
    @ResponseBody
    public Map<String, Object> addItemsToOrderAjax(
            @PathVariable String role,
            @PathVariable Long id,
            @RequestBody List<AddItemRequest> items,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Adding {} items to order ID: {} by user: {} (role: {})", 
                 items.size(), id, username, role);

        // Validate role
        validateRole(role, authentication);
        
        // Validate restaurant is open
        if (!businessHoursService.isOpenNow()) {
            log.warn("Attempt to add items to order outside business hours by user: {}", username);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "No se pueden agregar items al pedido. El restaurante no se encuentra en horario laborable en este momento.");
            return errorResponse;
        }
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);

        Map<String, Object> response = new HashMap<>();
        
        try {
            // Get the existing order
            Order order = orderService.findByIdOrThrow(id);

            // Validate that order can accept new items
            if (!order.canAcceptNewItems()) {
                response.put("success", false);
                String errorMsg = "No se pueden agregar items a este pedido.";
                if (order.getOrderType() == OrderType.DELIVERY) {
                    if (order.getStatus() == OrderStatus.ON_THE_WAY) {
                        errorMsg = "No se pueden agregar items a un pedido de DELIVERY que está EN CAMINO.";
                    } else if (order.getStatus() == OrderStatus.DELIVERED) {
                        errorMsg = "No se pueden agregar items a un pedido de DELIVERY que ya fue ENTREGADO.";
                    }
                } else if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.CANCELLED) {
                    errorMsg = "No se pueden agregar items a un pedido " + order.getStatus().getDisplayName() + ".";
                }
                response.put("message", errorMsg);
                return response;
            }

            // Build new order details
            List<OrderDetail> newItems = new ArrayList<>();
            for (AddItemRequest itemRequest : items) {
                ItemMenu item = itemMenuService.findById(itemRequest.getIdItemMenu())
                    .orElseThrow(() -> new IllegalArgumentException("Item no encontrado: " + itemRequest.getIdItemMenu()));

                // Validate dine-in-only items: only allowed for DINE_IN orders
                if (Boolean.TRUE.equals(item.getDineInOnly()) && order.getOrderType() != OrderType.DINE_IN) {
                    response.put("success", false);
                    response.put("message", "El item '" + item.getName() + "' solo está disponible para consumo en el establecimiento  aquí.");
                    return response;
                }

                OrderDetail detail = OrderDetail.builder()
                    .itemMenu(item)
                    .itemName(item.getName())
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(item.getPrice())
                    .comments(itemRequest.getComments())
                    .build();

                detail.calculateSubtotal();
                newItems.add(detail);
            }

            // Add items to order
            Order updated = orderService.addItemsToExistingOrder(id, newItems, username);

            response.put("success", true);
            response.put("message", String.format("Se agregaron %d items al pedido. " +
                "Los nuevos items aparecerán en cocina como PENDIENTES.", newItems.size()));
            response.put("order", buildOrderDTO(updated));
            response.put("newItemsCount", newItems.size());
            response.put("newTotal", updated.getFormattedTotal());
        } catch (IllegalStateException e) {
            log.warn("Error adding items to order: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("Error adding items to order", e);
            response.put("success", false);
            response.put("message", "Error al agregar items: " + e.getMessage());
        }

        return response;
    }

    /**
     * Change status of specific items in an order (AJAX)
     * Used by chef to update individual item statuses
     */
    @PostMapping("/{id}/change-items-status")
    @ResponseBody
    public Map<String, Object> changeItemsStatus(
            @PathVariable String role,
            @PathVariable Long id,
            @RequestParam List<Long> itemDetailIds,
            @RequestParam String newStatus,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Changing status of {} items in order {} to {} by user: {} (role: {})", 
                 itemDetailIds.size(), id, newStatus, username, role);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);

        Map<String, Object> response = new HashMap<>();
        
        try {
            OrderStatus status;
            try {
                status = OrderStatus.valueOf(newStatus);
            } catch (IllegalArgumentException ex) {
                log.warn("Invalid status enum: {}", newStatus);
                response.put("success", false);
                response.put("message", "Estado inválido: " + newStatus);
                return response;
            }
            
            Order updated = orderService.changeItemsStatus(id, itemDetailIds, status, username);

            response.put("success", true);
            response.put("message", String.format("Se cambió el estado de %d items a %s", 
                itemDetailIds.size(), status.getDisplayName()));
            response.put("order", buildOrderDTO(updated));
            response.put("orderStatus", updated.getStatus().name());
        } catch (IllegalArgumentException e) {
            log.warn("Error changing items status (IllegalArgument): {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("Error changing items status: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("Error changing items status", e);
            response.put("success", false);
            response.put("message", "Error al cambiar el estado de los items: " + e.getMessage());
        }

        return response;
    }

    /**
     * Accept TO_ACCEPT items in a customer-created order (AJAX).
     * Only ADMIN/MANAGER allowed via this controller; CASHIER uses CashierController endpoint.
     * If itemDetailIds is null/empty, all TO_ACCEPT items are accepted.
     */
    @PostMapping("/{id}/accept-items")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER')")
    @ResponseBody
    public Map<String, Object> acceptItems(
            @PathVariable String role,
            @PathVariable Long id,
            @RequestParam(value = "itemDetailIds", required = false) List<Long> itemDetailIds,
            Authentication authentication) {

        String username = authentication.getName();
        log.info("Accepting TO_ACCEPT items {} in order {} by user: {} (role: {})",
                 itemDetailIds, id, username, role);

        validateRole(role, authentication);
        OrderService orderService = getOrderService(role);

        Map<String, Object> response = new HashMap<>();
        try {
            Order updated = orderService.acceptOrderItems(id, itemDetailIds, username);
            response.put("success", true);
            response.put("message", "Items aceptados correctamente");
            response.put("order", buildOrderDTO(updated));
            response.put("orderStatus", updated.getStatus().name());
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Error accepting items in order {}: {}", id, e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("Error accepting items in order {}", id, e);
            response.put("success", false);
            response.put("message", "Error al aceptar los items: " + e.getMessage());
        }
        return response;
    }

    /**
     * Change ALL chef items in order to next status (AJAX)
     * This is a convenience endpoint for chef to avoid touching screen many times
     * Only available for CHEF role
     */
    @PostMapping("/{id}/change-all-chef-items")
    @ResponseBody
    public Map<String, Object> changeAllChefItems(
            @PathVariable String role,
            @PathVariable Long id,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Changing ALL chef items in order {} by user: {} (role: {})", id, username, role);

        // Validate role - ONLY chef can use this
        validateRole(role, authentication);
        
        if (!"chef".equalsIgnoreCase(role)) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Esta operación solo está disponible para el rol de chef");
            return errorResponse;
        }

        Map<String, Object> response = new HashMap<>();
        
        try {
            // Cast to ChefOrderServiceImpl to access the new method
            ChefOrderServiceImpl chefService = (ChefOrderServiceImpl) chefOrderService;
            Order updated = chefService.changeAllChefItemsToNextStatus(id, username);

            response.put("success", true);
            response.put("message", "Todos los items del chef han sido actualizados");
            response.put("order", buildOrderDTO(updated));
            response.put("orderStatus", updated.getStatus().name());
        } catch (IllegalStateException e) {
            log.warn("Error changing all chef items: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("Error changing all chef items", e);
            response.put("success", false);
            response.put("message", "Error al cambiar el estado de los items: " + e.getMessage());
        }

        return response;
    }

    /**
     * Delete a specific item from an order (AJAX)
     */
    @DeleteMapping("/{orderId}/items/{itemId}")
    @ResponseBody
    public Map<String, Object> deleteOrderItem(
            @PathVariable String role,
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Deleting item {} from order {} by user: {} (role: {})", itemId, orderId, username, role);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);

        Map<String, Object> response = new HashMap<>();
        
        try {
            OrderDetail deletedItem = orderService.deleteOrderItem(orderId, itemId, username);
            
            // Get updated order
            Order order = orderService.findByIdOrThrow(orderId);
            
            // Send WebSocket notification so chef/barista can remove the item from their view
            wsNotificationService.notifyItemDeleted(order, deletedItem);
            log.info("🔔 WebSocket sent: Item {} deleted from order {} by {} (role: {})", 
                deletedItem.getItemMenu().getName(), order.getOrderNumber(), username, role);
            
            // Analyze stock return for this specific item
            String stockInfo = analyzeItemStockReturn(deletedItem);
            
            response.put("success", true);
            response.put("message", String.format("Item '%s' eliminado del pedido exitosamente", 
                    deletedItem.getItemMenu().getName()));
            response.put("stockInfo", stockInfo);
            response.put("orderTotal", order.getTotal());
            response.put("orderStatus", order.getStatus().name());
            response.put("orderStatusLabel", order.getStatus().getDisplayName());
            response.put("remainingItems", order.getOrderDetails().stream().filter(d -> !d.isComboChild()).count());
            
        } catch (IllegalArgumentException e) {
            log.warn("Item not found: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("Cannot delete item: {}", e.getMessage());
            
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
     * Delete a specific complement from an order item (AJAX)
     */
    @DeleteMapping("/{orderId}/items/{itemId}/complements/{complementId}")
    @ResponseBody
    public Map<String, Object> deleteOrderItemComplement(
            @PathVariable String role,
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @PathVariable Long complementId,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Deleting complement {} from item {} of order {} by user: {} (role: {})", 
                complementId, itemId, orderId, username, role);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);

        Map<String, Object> response = new HashMap<>();
        
        try {
            OrderDetailComplement deletedComplement = orderService.deleteOrderItemComplement(orderId, itemId, complementId, username);
            
            // Get updated order
            Order order = orderService.findByIdOrThrow(orderId);
            
            // Analyze stock return for this specific complement
            String stockInfo = analyzeComplementStockReturn(deletedComplement, order, itemId);
            
            response.put("success", true);
            response.put("message", String.format("Complemento '%s' eliminado del pedido exitosamente", 
                    deletedComplement.getComplement().getName()));
            response.put("stockInfo", stockInfo);
            response.put("orderTotal", order.getTotal());
            response.put("orderSubtotal", order.getSubtotal());
            response.put("complementName", deletedComplement.getComplement().getName());
            
        } catch (IllegalArgumentException e) {
            log.warn("Complement not found: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("Cannot delete complement: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("Error deleting complement from order", e);
            response.put("success", false);
            response.put("message", "Error al eliminar el complemento: " + e.getMessage());
        }

        return response;
    }

    /**
     * Analyze stock return info for a deleted complement
     */
    private String analyzeComplementStockReturn(OrderDetailComplement deletedComplement, Order order, Long itemId) {
        // Find the item detail to check its status
        OrderDetail itemDetail = order.getOrderDetails().stream()
                .filter(d -> d.getIdOrderDetail().equals(itemId))
                .findFirst()
                .orElse(null);
        
        if (itemDetail == null) {
            return "Stock info no disponible (item no encontrado)";
        }
        
        OrderStatus itemStatus = itemDetail.getItemStatus();
        boolean requiresPrep = Boolean.TRUE.equals(itemDetail.getItemMenu().getRequiresPreparation());
        boolean requiresBarista = Boolean.TRUE.equals(itemDetail.getItemMenu().getRequiresBaristaPreparation());
        
        if (itemStatus == OrderStatus.PENDING) {
            return "✅ Stock del complemento devuelto automáticamente (item pendiente)";
        } else if (itemStatus == OrderStatus.READY && !requiresPrep && !requiresBarista) {
            return "✅ Stock del complemento devuelto automáticamente (item no requiere preparación)";
        } else if (itemStatus == OrderStatus.IN_PREPARATION) {
            return "⚠️ Stock del complemento debe ser devuelto manualmente (item en preparación)";
        } else if (itemStatus == OrderStatus.READY && (requiresPrep || requiresBarista)) {
            return "⚠️ Stock del complemento debe ser devuelto manualmente (item ya preparado)";
        }
        
        return "Stock procesado";
    }

    /**
     * Get valid next statuses for an order (AJAX)
     */
    @GetMapping("/{id}/valid-statuses")
    @ResponseBody
    public Map<String, Object> getValidStatuses(
            @PathVariable String role,
            @PathVariable Long id,
            Authentication authentication) {
        
        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            Order order = orderService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));
            
            List<OrderStatus> validStatuses = new ArrayList<>();
            
            // Different rules based on role
            if ("chef".equalsIgnoreCase(role)) {
                // Chef can only change: PENDING -> IN_PREPARATION, IN_PREPARATION -> READY
                if (order.getStatus() == OrderStatus.PENDING) {
                    validStatuses.add(OrderStatus.IN_PREPARATION);
                } else if (order.getStatus() == OrderStatus.IN_PREPARATION) {
                    validStatuses.add(OrderStatus.READY);
                }
            } else if ("waiter".equalsIgnoreCase(role)) {
                // Waiter can only mark as DELIVERED if status is READY.
                // PAID is intentionally NOT offered here: cobrar debe pasar SIEMPRE por
                // WaiterPaymentController para aplicar todas las validaciones (método de pago, etc.).
                if (order.getStatus() == OrderStatus.READY) {
                    validStatuses.add(OrderStatus.DELIVERED);
                }
            } else if ("admin".equalsIgnoreCase(role) || "manager".equalsIgnoreCase(role)) {
                // Admin/Manager can mark Ready -> Delivered. PAID se excluye del select:
                // el cobro DEBE hacerse a través de PaymentController (form de pago).
                if (order.getStatus() == OrderStatus.READY) {
                    validStatuses.add(OrderStatus.DELIVERED);
                }
            } else {
                // Other roles (e.g. Cashier, Delivery) — use default transitions, but
                // remove PAID: el cobro debe pasar por su respectivo PaymentController.
                OrderStatus[] allValidStatuses = OrderStatus.getValidNextStatuses(
                    order.getStatus(), 
                    order.getOrderType()
                );
                for (OrderStatus s : allValidStatuses) {
                    if (s != OrderStatus.PAID) {
                        validStatuses.add(s);
                    }
                }
            }
            
            List<Map<String, String>> statusList = new ArrayList<>();
            for (OrderStatus status : validStatuses) {
                Map<String, String> statusMap = new HashMap<>();
                statusMap.put("value", status.name());
                statusMap.put("label", status.getDisplayName());
                statusList.add(statusMap);
            }
            
            response.put("success", true);
            response.put("currentStatus", order.getStatus().name());
            response.put("currentStatusLabel", order.getStatus().getDisplayName());
            response.put("orderType", order.getOrderType().name());
            response.put("validStatuses", statusList);
            response.put("canBeCancelled", order.getStatus().canBeCancelled());
        } catch (Exception e) {
            log.error("Error getting valid statuses", e);
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        
        return response;
    }

    // ========== AJAX ENDPOINTS ==========

    /**
     * Validate stock for order items (AJAX)
     */
    @PostMapping("/validate-stock")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validateStock(
            @PathVariable String role,
            @RequestBody List<OrderDetailRequest> items,
            Authentication authentication) {
        
        log.debug("Validating stock for {} items (role: {})", items.size(), role);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);

        try {
            List<OrderDetail> orderDetails = items.stream()
                .map(req -> {
                    ItemMenu item = itemMenuService.findById(req.getItemId())
                        .orElseThrow(() -> new IllegalArgumentException("Item no encontrado"));
                    
                    OrderDetail detail = new OrderDetail();
                    detail.setItemMenu(item);
                    detail.setQuantity(req.getQuantity());
                    return detail;
                })
                .collect(Collectors.toList());

            Map<Long, String> stockErrors = orderService.validateStock(orderDetails);

            Map<String, Object> response = new HashMap<>();
            response.put("valid", stockErrors.isEmpty());
            response.put("errors", stockErrors);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error validating stock", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("valid", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Calculate order total (AJAX)
     * NOTE: ItemMenu.price already includes IVA. We calculate subtotal (without IVA)
     * and taxAmount backwards from the total for display purposes only.
     */
    @PostMapping("/calculate-total")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> calculateTotal(
            @RequestBody List<OrderDetailRequest> items) {
        
        log.debug("Calculating total for {} items", items.size());

        try {
            // Prices already include IVA, so this sum IS the total
            BigDecimal total = BigDecimal.ZERO;

            for (OrderDetailRequest req : items) {
                ItemMenu item = itemMenuService.findById(req.getItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Item no encontrado"));
                
                BigDecimal itemTotal = item.getPrice()
                    .multiply(BigDecimal.valueOf(req.getQuantity()));
                total = total.add(itemTotal);
            }

            // Calculate subtotal (without IVA) and taxAmount for display
            SystemConfiguration config = systemConfigurationService.getConfiguration();
            BigDecimal taxRate = config.getTaxRate();
            BigDecimal subtotal;
            BigDecimal taxAmount;

            if (taxRate != null && taxRate.compareTo(BigDecimal.ZERO) > 0) {
                // subtotal = total / (1 + taxRate/100)
                BigDecimal taxMultiplier = BigDecimal.ONE.add(
                    taxRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                );
                subtotal = total.divide(taxMultiplier, 2, RoundingMode.HALF_UP);
                taxAmount = total.subtract(subtotal);
            } else {
                subtotal = total;
                taxAmount = BigDecimal.ZERO;
            }

            Map<String, Object> response = new HashMap<>();
            response.put("subtotal", subtotal);
            response.put("taxRate", taxRate);
            response.put("taxAmount", taxAmount);
            response.put("total", total);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error calculating total", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Get available items with stock info (AJAX)
     */
    @GetMapping("/available-items")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getAvailableItems() {
        log.debug("Getting available items");

        try {
            List<ItemMenu> items = itemMenuService.findAvailableItems();
            
            List<Map<String, Object>> response = items.stream()
                .map(item -> {
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put("id", item.getIdItemMenu());
                    itemMap.put("name", item.getName());
                    itemMap.put("price", item.getPrice());
                    itemMap.put("available", item.getAvailable());
                    itemMap.put("categoryName", item.getCategory().getName());
                    return itemMap;
                })
                .collect(Collectors.toList());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting available items", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // ========== HELPER METHODS ==========

    /**
     * Build order details from form data
     * @param complementsJson List of JSON strings containing complement data for each item
     *                        Format: [{"id": 1, "name": "Salsa BBQ", "quantity": 2, "extraPrice": 5.00, "maxQuantity": 3}, ...]
     */
    private List<OrderDetail> buildOrderDetails(
            List<Long> itemIds,
            List<Integer> quantities,
            List<String> comments,
            List<String> promotionPrices,
            List<String> promotionIds,
            List<String> complementsJson) {
        
        List<OrderDetail> orderDetails = new ArrayList<>();

        if (itemIds == null || itemIds.isEmpty()) {
            return orderDetails;
        }

        for (int i = 0; i < itemIds.size(); i++) {
            Long itemId = itemIds.get(i);
            Integer quantity = (quantities != null && i < quantities.size()) ? quantities.get(i) : 1;
            String comment = (comments != null && i < comments.size()) ? comments.get(i) : null;
            String promotionIdStr = (promotionIds != null && i < promotionIds.size()) ? promotionIds.get(i) : null;
            String complementJson = (complementsJson != null && i < complementsJson.size()) ? complementsJson.get(i) : null;

            if (itemId == null || quantity == null || quantity <= 0) {
                continue;
            }

            ItemMenu item = itemMenuService.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item no encontrado: " + itemId));

            // Validate item is active
            if (!Boolean.TRUE.equals(item.getActive())) {
                throw new IllegalStateException("El item '" + item.getName() + "' está desactivado y no puede ser seleccionado.");
            }
            
            // NOTE: Item availability schedule validation moved to OrderServiceImpl.validateItemsAvailability()
            // This ensures stock validation happens FIRST (material constraint priority)

            // ========== COMBO EXPANSION ==========
            // If item is a combo, expand into parent + child OrderDetails
            if (Boolean.TRUE.equals(item.getIsCombo())) {
                String comboGroupId = "combo_" + System.currentTimeMillis() + "_" + itemId;
                
                // Get combo child items
                List<ItemMenuComboItem> comboItems = itemMenuComboItemRepository
                    .findByComboMenuIdItemMenuOrderByDisplayOrderAsc(itemId);
                
                if (comboItems.isEmpty()) {
                    throw new IllegalArgumentException("El combo '" + item.getName() + "' no tiene items hijos configurados.");
                }
                
                // 1. Create PARENT OrderDetail (combo item itself - holds the price)
                OrderDetail comboParent = OrderDetail.builder()
                    .itemMenu(item)
                    .itemName(item.getName())
                    .quantity(quantity)
                    .unitPrice(item.getPrice())
                    .comments(comment)
                    .itemStatus(OrderStatus.READY) // Combo parent is always READY (no preparation needed)
                    .comboGroupId(comboGroupId)
                    .build();
                comboParent.calculateSubtotal();
                
                // Apply promotion to parent if provided
                if (promotionIdStr != null && !promotionIdStr.trim().isEmpty()) {
                    try {
                        Long promotionId = Long.parseLong(promotionIdStr);
                        Promotion promotion = promotionService.findById(promotionId).orElse(null);
                        if (promotion != null && promotion.isValidNow()) {
                            boolean appliesToItem = promotion.getItems().stream()
                                .anyMatch(pi -> pi.getIdItemMenu().equals(itemId));
                            if (appliesToItem) {
                                BigDecimal discountedTotal = promotion.calculateDiscountedPrice(item.getPrice(), quantity);
                                BigDecimal discountedPerUnit = discountedTotal.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
                                comboParent.setSubtotal(discountedTotal.setScale(2, RoundingMode.HALF_UP));
                                comboParent.setAppliedPromotionId(promotionId);
                                comboParent.setPromotionAppliedPrice(discountedPerUnit);
                                log.info("Applied promotion '{}' to combo '{}'", promotion.getName(), item.getName());
                            }
                        }
                    } catch (NumberFormatException e) {
                        log.warn("Invalid promotion ID for combo: {}", promotionIdStr);
                    }
                }
                
                orderDetails.add(comboParent);
                
                // 2. Create CHILD OrderDetails for each combo child item
                // Parse per-child complements from the JSON if provided
                List<Map<String, Object>> childComplementsFromFrontend = null;
                if (complementJson != null && !complementJson.isEmpty() && !complementJson.equals("[]")) {
                    try {
                        String decodedJson = java.net.URLDecoder.decode(complementJson, java.nio.charset.StandardCharsets.UTF_8);
                        childComplementsFromFrontend = objectMapper.readValue(
                            decodedJson, new TypeReference<List<Map<String, Object>>>() {});
                    } catch (Exception e) {
                        log.warn("Error parsing combo child complements: {}", e.getMessage());
                    }
                }
                
                for (ItemMenuComboItem comboChild : comboItems) {
                    ItemMenu childItem = comboChild.getChildMenu();
                    int childQty = comboChild.getQuantity() * quantity; // multiply by combo quantity
                    
                    // Validate child item is active
                    if (!Boolean.TRUE.equals(childItem.getActive())) {
                        throw new IllegalStateException("El item '" + childItem.getName() + "' del combo '" + item.getName() + "' está desactivado y no puede ser seleccionado.");
                    }
                    
                    // NOTE: Child item availability schedule validation moved to OrderServiceImpl.validateItemsAvailability()
                    // This ensures stock validation happens FIRST for all items including combo children
                    
                    boolean childRequiresChef = Boolean.TRUE.equals(childItem.getRequiresPreparation());
                    boolean childRequiresBarista = Boolean.TRUE.equals(childItem.getRequiresBaristaPreparation());
                    
                    OrderDetail childDetail = OrderDetail.builder()
                        .itemMenu(childItem)
                        .itemName(childItem.getName())
                        .quantity(childQty)
                        .unitPrice(BigDecimal.ZERO) // Children have $0 price (combo price is on parent)
                        .comments(comment)
                        .itemStatus((childRequiresChef || childRequiresBarista) ? OrderStatus.PENDING : OrderStatus.READY)
                        .comboGroupId(comboGroupId)
                        .build();
                    childDetail.calculateSubtotal();
                    
                    // Locate this child's complements payload (may be null/empty if user skipped sauce selection)
                    List<Map<String, Object>> childComps = null;
                    if (childComplementsFromFrontend != null) {
                        for (Map<String, Object> compEntry : childComplementsFromFrontend) {
                            Long targetChildId = compEntry.get("childItemId") != null ?
                                Long.valueOf(compEntry.get("childItemId").toString()) : null;
                            if (targetChildId != null && targetChildId.equals(childItem.getIdItemMenu())) {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> tmp = (List<Map<String, Object>>) compEntry.get("complements");
                                childComps = tmp;
                                break;
                            }
                        }
                    }
                    
                    // BACKEND VALIDATION: Always enforce minSauces for the child item, even if no complements were sent
                    Integer childMinSauces = childItem.getMinSauces();
                    if (childMinSauces != null && childMinSauces > 0) {
                        int selectedSaucesForChild = 0;
                        if (childComps != null) {
                            for (Map<String, Object> compData : childComps) {
                                if (compData.get("id") == null) continue;
                                Long cId = ((Number) compData.get("id")).longValue();
                                Complement tempComp = complementRepository.findById(cId).orElse(null);
                                if (tempComp != null && Boolean.TRUE.equals(tempComp.getIsSauce())) {
                                    selectedSaucesForChild++;
                                }
                            }
                        }
                        if (selectedSaucesForChild < childMinSauces) {
                            throw new IllegalArgumentException(
                                "El item '" + childItem.getName() + "' del combo '" + item.getName() +
                                "' requiere al menos " + childMinSauces +
                                " salsa(s) o especialidad(es), pero se seleccionaron " + selectedSaucesForChild + ".");
                        }
                    }
                    
                    // Process per-child complements if provided (also re-validates min/max + per-complement bounds)
                    if (childComps != null && !childComps.isEmpty()) {
                        processComplementsForDetail(childDetail, childItem, childComps, childQty);
                    }
                    
                    orderDetails.add(childDetail);
                    log.info("Combo '{}' - added child '{}' x{} (status: {})", 
                        item.getName(), childItem.getName(), childQty, childDetail.getItemStatus());
                }
                
                log.info("Expanded combo '{}' into {} child items + 1 parent (groupId: {})", 
                    item.getName(), comboItems.size(), comboGroupId);
                continue; // Skip the regular OrderDetail creation below
            }
            // ========== END COMBO EXPANSION ==========

            OrderDetail.OrderDetailBuilder detailBuilder = OrderDetail.builder()
                .itemMenu(item)
                .itemName(item.getName())
                .quantity(quantity)
                .unitPrice(item.getPrice())
                .comments(comment);
            
            // Set item status based on whether it requires ANY preparation (chef or barista)
            // ONLY items requiring NO preparation at all go directly to READY
            // Items requiring barista preparation MUST start as PENDING
            boolean requiresChefPreparation = Boolean.TRUE.equals(item.getRequiresPreparation());
            boolean requiresBaristaPreparation = Boolean.TRUE.equals(item.getRequiresBaristaPreparation());
            
            if (requiresChefPreparation || requiresBaristaPreparation) {
                // Item requires preparation by chef OR barista - starts PENDING
                detailBuilder.itemStatus(OrderStatus.PENDING);
            } else {
                // Item requires NO preparation - goes directly to READY
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
                            // SECURITY: Recalculate price in backend (don't trust frontend)
                            BigDecimal calculatedDiscountedTotal = promotion.calculateDiscountedPrice(
                                item.getPrice(), 
                                quantity
                            );
                            
                            // IMPORTANT FIX: For BUY_X_PAY_Y promotions, we must set the subtotal
                            // directly from the calculated total to avoid precision errors.
                            // The promotionAppliedPrice is stored with 2 decimals for display purposes only.
                            BigDecimal calculatedPricePerUnit = calculatedDiscountedTotal
                                .divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
                            
                            // Store the CORRECT subtotal directly in the builder to avoid precision errors
                            // This applies to ALL promotion types (BUY_X_PAY_Y, PERCENTAGE_DISCOUNT, FIXED_AMOUNT_DISCOUNT)
                            detailBuilder.subtotal(calculatedDiscountedTotal.setScale(2, RoundingMode.HALF_UP));
                            
                            log.info("BACKEND VALIDATION - Item: {}, Qty: {}, Promotion: {}, " +
                                    "Original Price/Unit: ${}, Calculated Price/Unit: ${}, " +
                                    "Calculated Total: ${}", 
                                    item.getName(), quantity, promotion.getName(),
                                    item.getPrice(), calculatedPricePerUnit, calculatedDiscountedTotal);
                            
                            // Set the VALIDATED promotion data
                            detailBuilder.appliedPromotionId(promotionId);
                            detailBuilder.promotionAppliedPrice(calculatedPricePerUnit);
                            
                            // Validate minimum quantity for BUY_X_PAY_Y promotions
                            if (promotion.getPromotionType() == PromotionType.BUY_X_PAY_Y) {
                                if (quantity < promotion.getBuyQuantity()) {
                                    log.warn("Quantity {} is less than required {} for promotion {}. Applying no promotion.",
                                            quantity, promotion.getBuyQuantity(), promotion.getName());
                                    // Don't apply promotion if minimum quantity not met
                                    detailBuilder.appliedPromotionId(null);
                                    detailBuilder.promotionAppliedPrice(null);
                                }
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
            detail.calculateSubtotal();
            
            // Process complements for this item
            if (complementJson != null && !complementJson.isEmpty() && !complementJson.equals("[]")) {
                try {
                    // URL-decode the JSON (it was encoded to prevent Spring from splitting on commas)
                    String decodedJson = java.net.URLDecoder.decode(complementJson, java.nio.charset.StandardCharsets.UTF_8);
                    log.info("Decoded complements JSON for item {}: '{}' (length: {})", 
                        itemId, decodedJson, decodedJson.length());
                    
                    List<Map<String, Object>> selectedComplements = objectMapper.readValue(
                        decodedJson, 
                        new TypeReference<List<Map<String, Object>>>() {}
                    );
                    
                    // Normalize combo-format complements into flat format
                    // Combo format: [{"childItemId":3,"complements":[{"id":1,"quantity":1}]}]
                    // Regular format: [{"id":1,"quantity":1}]
                    // If the item was changed from combo to non-combo, the cart may still
                    // send combo-format data. Flatten it so it can be processed normally.
                    if (!selectedComplements.isEmpty() && selectedComplements.get(0).containsKey("childItemId")) {
                        List<Map<String, Object>> flatComplements = new ArrayList<>();
                        for (Map<String, Object> comboEntry : selectedComplements) {
                            Object compsObj = comboEntry.get("complements");
                            if (compsObj instanceof List<?> compsList) {
                                for (Object compObj : compsList) {
                                    if (compObj instanceof Map<?, ?> compMap) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> typedMap = (Map<String, Object>) compMap;
                                        flatComplements.add(typedMap);
                                    }
                                }
                            }
                        }
                        selectedComplements = flatComplements;
                        log.info("Normalized combo-format complements to {} flat entries for non-combo item {}", 
                            flatComplements.size(), item.getName());
                    }
                    
                    BigDecimal complementsTotal = BigDecimal.ZERO;
                    List<OrderDetailComplement> orderDetailComplements = new ArrayList<>();
                    
                    // BACKEND VALIDATION: Count selected sauces and validate against min/maxSauces
                    Integer maxSauces = item.getMaxSauces();
                    Integer minSauces = item.getMinSauces();
                    int selectedSaucesCount = 0;
                    
                    // First pass: count sauces to validate limit
                    for (Map<String, Object> compData : selectedComplements) {
                        if (compData.get("id") == null) continue; // skip malformed entries
                        Long complementId = ((Number) compData.get("id")).longValue();
                        
                        // Also check in DB to be sure
                        Complement tempComplement = complementRepository.findById(complementId).orElse(null);
                        if (tempComplement != null && Boolean.TRUE.equals(tempComplement.getIsSauce())) {
                            selectedSaucesCount++;
                        }
                    }
                    
                    // Validate sauces count against maxSauces limit
                    if (maxSauces != null && maxSauces > 0 && selectedSaucesCount > maxSauces) {
                        throw new IllegalArgumentException(
                            "El item '" + item.getName() + "' permite máximo " + maxSauces + 
                            " salsa(s), pero se seleccionaron " + selectedSaucesCount + ".");
                    }
                    
                    // Validate sauces count against minSauces limit
                    if (minSauces != null && minSauces > 0 && selectedSaucesCount < minSauces) {
                        throw new IllegalArgumentException(
                            "El item '" + item.getName() + "' requiere al menos " + minSauces + 
                            " salsa(s) o especialidad(es), pero se seleccionaron " + selectedSaucesCount + ".");
                    }
                    
                    log.info("Item '{}' - minSauces: {}, maxSauces: {}, selectedSauces: {}", 
                        item.getName(), minSauces, maxSauces, selectedSaucesCount);
                    
                    for (Map<String, Object> compData : selectedComplements) {
                        if (compData.get("id") == null) continue; // skip malformed entries
                        Long complementId = ((Number) compData.get("id")).longValue();
                        Integer compQuantity = ((Number) compData.get("quantity")).intValue();
                        
                        // Fetch complement from database
                        Complement complement = complementRepository.findById(complementId)
                            .orElseThrow(() -> new IllegalArgumentException(
                                "Complemento no encontrado: " + complementId));
                        
                        // Validate complement is active and available
                        if (!Boolean.TRUE.equals(complement.getActive())) {
                            throw new IllegalStateException(
                                "El complemento '" + complement.getName() + "' está desactivado.");
                        }
                        if (!Boolean.TRUE.equals(complement.getAvailable())) {
                            throw new IllegalStateException(
                                "El complemento '" + complement.getName() + "' no está disponible (sin stock).");
                        }
                        
                        // CRITICAL VALIDATION: Check maxQuantity from ItemMenuComplement
                        ItemMenuComplement itemMenuComplement = itemMenuComplementRepository
                            .findByItemMenuIdItemMenuAndComplementIdComplement(itemId, complementId)
                            .orElseThrow(() -> new IllegalArgumentException(
                                "El complemento '" + complement.getName() + "' no está asociado al item '" + item.getName() + "'."));
                        
                        // Validate complement association is active
                        if (!Boolean.TRUE.equals(itemMenuComplement.getActive())) {
                            throw new IllegalStateException(
                                "El complemento '" + complement.getName() + "' no está activo para el item '" + item.getName() + "'.");
                        }
                        
                        // BACKEND VALIDATION: Quantity must be >= 1 and <= maxQuantity
                        if (compQuantity < 1) {
                            throw new IllegalArgumentException(
                                "La cantidad del complemento '" + complement.getName() + "' debe ser al menos 1.");
                        }
                        if (compQuantity > itemMenuComplement.getMaxQuantity()) {
                            throw new IllegalArgumentException(
                                "La cantidad del complemento '" + complement.getName() + "' (" + compQuantity + 
                                ") excede el máximo permitido (" + itemMenuComplement.getMaxQuantity() + 
                                ") para el item '" + item.getName() + "'.");
                        }
                        
                        // Compute effective quantity: sauces are per-serving (×item quantity),
                        // non-sauces are absolute. We persist the EFFECTIVE quantity so the stored
                        // value is the real total of complement portions consumed by the customer.
                        // This decouples historical orders from later toggles of Complement.isSauce.
                        int effectiveCompQty = Boolean.TRUE.equals(complement.getIsSauce())
                            ? compQuantity * quantity
                            : compQuantity;
                        if (!complement.hasEnoughStock(effectiveCompQty)) {
                            throw new IllegalStateException(
                                "Stock insuficiente para el complemento '" + complement.getName() + 
                                "'. Se requieren " + effectiveCompQty + " porciones.");
                        }
                        
                        // Create OrderDetailComplement with effective quantity (subtotal computed from it)
                        OrderDetailComplement odc = OrderDetailComplement.builder()
                            .orderDetail(detail)
                            .complement(complement)
                            .complementName(complement.getName())
                            .quantity(effectiveCompQty)
                            .unitPrice(complement.getExtraPrice())
                            .stockDeducted(false)
                            .build();
                        odc.calculateSubtotal();
                        
                        orderDetailComplements.add(odc);
                        
                        // Subtotal is already the real total — no further multiplication needed
                        complementsTotal = complementsTotal.add(odc.getSubtotal());
                        
                        log.debug("Added complement '{}' x{} (effective: {}) to item '{}' - unit price: {}, subtotal: {}",
                            complement.getName(), compQuantity, effectiveCompQty, item.getName(),
                            odc.getUnitPrice(), odc.getSubtotal());
                    }
                    
                    // Set complements on the detail
                    // NOTE: Item subtotal does NOT include complements - they are tracked separately
                    // The order total calculation uses getTotalWithComplements() to include them
                    detail.setSelectedComplements(orderDetailComplements);
                    
                    if (complementsTotal.compareTo(BigDecimal.ZERO) > 0) {
                        log.info("Item '{}' subtotal: {}, complements total: {} (stored separately)", 
                            item.getName(), detail.getSubtotal(), complementsTotal);
                    }
                    
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                    log.error("Error parsing complements JSON for item {}: {}", itemId, e.getMessage());
                    throw new IllegalArgumentException("Error procesando complementos: " + e.getMessage());
                }
            }
            
            orderDetails.add(detail);
        }

        return orderDetails;
    }

    /**
     * Process complements for an OrderDetail (reusable for both regular and combo child items)
     */
    private void processComplementsForDetail(OrderDetail detail, ItemMenu item, 
            List<Map<String, Object>> selectedComplements, int itemQuantity) {
        
        Long itemId = item.getIdItemMenu();
        List<OrderDetailComplement> orderDetailComplements = new ArrayList<>();
        
        // Count sauces for validation
        Integer maxSauces = item.getMaxSauces();
        Integer minSauces = item.getMinSauces();
        int selectedSaucesCount = 0;
        
        for (Map<String, Object> compData : selectedComplements) {
            if (compData.get("id") == null) continue;
            Long complementId = ((Number) compData.get("id")).longValue();
            Complement tempComplement = complementRepository.findById(complementId).orElse(null);
            if (tempComplement != null && Boolean.TRUE.equals(tempComplement.getIsSauce())) {
                selectedSaucesCount++;
            }
        }
        
        if (maxSauces != null && maxSauces > 0 && selectedSaucesCount > maxSauces) {
            throw new IllegalArgumentException(
                "El item '" + item.getName() + "' permite máximo " + maxSauces + 
                " salsa(s), pero se seleccionaron " + selectedSaucesCount + ".");
        }
        if (minSauces != null && minSauces > 0 && selectedSaucesCount < minSauces) {
            throw new IllegalArgumentException(
                "El item '" + item.getName() + "' requiere al menos " + minSauces + 
                " salsa(s) o especialidad(es), pero se seleccionaron " + selectedSaucesCount + ".");
        }
        
        for (Map<String, Object> compData : selectedComplements) {
            if (compData.get("id") == null) continue;
            Long complementId = ((Number) compData.get("id")).longValue();
            Integer compQuantity = ((Number) compData.get("quantity")).intValue();
            
            Complement complement = complementRepository.findById(complementId)
                .orElseThrow(() -> new IllegalArgumentException("Complemento no encontrado: " + complementId));
            
            if (!Boolean.TRUE.equals(complement.getActive())) {
                throw new IllegalStateException("El complemento '" + complement.getName() + "' está desactivado.");
            }
            if (!Boolean.TRUE.equals(complement.getAvailable())) {
                throw new IllegalStateException("El complemento '" + complement.getName() + "' no está disponible (sin stock).");
            }
            
            // CRITICAL VALIDATION: Check complement is associated to the item
            ItemMenuComplement itemMenuComplement = itemMenuComplementRepository
                .findByItemMenuIdItemMenuAndComplementIdComplement(itemId, complementId)
                .orElseThrow(() -> new IllegalArgumentException(
                    "El complemento '" + complement.getName() + "' no está asociado al item '" + item.getName() + "'."));
            
            // Validate complement association is active
            if (!Boolean.TRUE.equals(itemMenuComplement.getActive())) {
                throw new IllegalStateException(
                    "El complemento '" + complement.getName() + "' no está activo para el item '" + item.getName() + "'.");
            }
            
            // BACKEND VALIDATION: Quantity must be >= 1 and <= maxQuantity
            if (compQuantity < 1) {
                throw new IllegalArgumentException(
                    "La cantidad del complemento '" + complement.getName() + "' debe ser al menos 1.");
            }
            if (compQuantity > itemMenuComplement.getMaxQuantity()) {
                throw new IllegalArgumentException(
                    "La cantidad del complemento '" + complement.getName() + "' (" + compQuantity + 
                    ") excede el máximo permitido (" + itemMenuComplement.getMaxQuantity() + 
                    ") para el item '" + item.getName() + "'.");
            }
            
            // Compute effective quantity: sauces are per-serving (×item quantity),
            // non-sauces are absolute. We persist the EFFECTIVE quantity so storage already
            // represents the real total of complement portions. This keeps stock, totals,
            // tickets and reports independent of any later toggle of Complement.isSauce.
            int effectiveCompQty = Boolean.TRUE.equals(complement.getIsSauce())
                ? compQuantity * itemQuantity
                : compQuantity;
            if (!complement.hasEnoughStock(effectiveCompQty)) {
                throw new IllegalStateException("Stock insuficiente para el complemento '" + complement.getName() + "'.");
            }
            
            OrderDetailComplement odc = OrderDetailComplement.builder()
                .orderDetail(detail)
                .complement(complement)
                .complementName(complement.getName())
                .quantity(effectiveCompQty)
                .unitPrice(complement.getExtraPrice())
                .stockDeducted(false)
                .build();
            odc.calculateSubtotal();
            orderDetailComplements.add(odc);
        }
        
        detail.setSelectedComplements(orderDetailComplements);
    }

    /**
     * Build order details from JSON items array (used by JSON endpoints).
     * Each item in the list has: itemId, quantity, comments, promotionPrice, promotionId, complements[{id, quantity}]
     */
    private List<OrderDetail> buildOrderDetailsFromJson(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }

        // Convert JSON item maps to parallel lists and delegate to buildOrderDetails
        List<Long> itemIds = new ArrayList<>();
        List<Integer> quantities = new ArrayList<>();
        List<String> comments = new ArrayList<>();
        List<String> promotionPrices = new ArrayList<>();
        List<String> promotionIds = new ArrayList<>();
        List<String> complementsJson = new ArrayList<>();

        for (Map<String, Object> itemData : items) {
            itemIds.add(Long.valueOf(itemData.get("itemId").toString()));
            quantities.add(Integer.valueOf(itemData.get("quantity").toString()));
            comments.add(itemData.get("comments") != null ? itemData.get("comments").toString() : "");

            Object promoPrice = itemData.get("promotionPrice");
            promotionPrices.add(promoPrice != null ? promoPrice.toString() : "");

            Object promoId = itemData.get("promotionId");
            promotionIds.add(promoId != null ? promoId.toString() : "");

            // Complements come as a nested JSON array [{id, quantity}] - serialize back to JSON string
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> complements = (List<Map<String, Object>>) itemData.get("complements");
            if (complements != null && !complements.isEmpty()) {
                try {
                    // Encode to match what buildOrderDetails expects (URL-encoded JSON)
                    String jsonStr = objectMapper.writeValueAsString(complements);
                    complementsJson.add(java.net.URLEncoder.encode(jsonStr, java.nio.charset.StandardCharsets.UTF_8));
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                    log.error("Error serializing complements for item {}: {}", itemData.get("itemId"), e.getMessage());
                    complementsJson.add("[]");
                }
            } else {
                complementsJson.add("[]");
            }
        }

        return buildOrderDetails(itemIds, quantities, comments, promotionPrices, promotionIds, complementsJson);
    }

    /**
     * Load form data for rendering
     */
    private void loadFormData(Model model, Order order, String username, String role) {
        Employee employee = employeeService.findByUsername(username)
            .orElseThrow(() -> new IllegalStateException("Empleado no encontrado"));

        SystemConfiguration config = systemConfigurationService.getConfiguration();
        
        // Get AVAILABLE tables that are not blocked by reservations
        List<RestaurantTable> availableTables = restaurantTableService.findAvailableTables().stream()
            .filter(t -> !restaurantTableService.isTableBlockedByReservation(t.getId()))
            .collect(Collectors.toCollection(ArrayList::new));
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
        
        Map<PaymentMethodType, Boolean> paymentMethodsMap = (order.getOrderType() == OrderType.DELIVERY) 
            ? config.getDeliveryPaymentMethods() 
            : config.getPaymentMethods();
        List<PaymentMethodType> enabledPaymentMethods = paymentMethodsMap.entrySet().stream()
            .filter(Map.Entry::getValue)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        // Build DTOs for both payment method sets (needed by JS for dynamic order type switching)
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

        model.addAttribute("employee", employee);
        model.addAttribute("availableTables", availableTables);
        model.addAttribute("availableItems", availableItemsDTO);
        model.addAttribute("orderTypes", OrderType.values());
        model.addAttribute("paymentMethods", enabledPaymentMethods);
        model.addAttribute("regularPaymentMethods", regularPaymentMethodsDTO);
        model.addAttribute("deliveryPaymentMethods", deliveryPaymentMethodsDTO);
        model.addAttribute("taxRate", config.getTaxRate());
        model.addAttribute("currentRole", role);
    }

    /**
     * Load edit-specific data (order details, canChangeOrderType, current table) when
     * re-rendering the edit form after a validation error.
     */
    @SuppressWarnings("unchecked")
    private void loadEditOrderData(Model model, Long orderId, OrderService orderService) {
        orderService.findByIdWithDetails(orderId).ifPresent(existingOrder -> {
            // Build orderDetailsDTO so the items section renders
            List<Map<String, Object>> orderDetailsDTO = existingOrder.getOrderDetails().stream()
                .map(detail -> {
                    Map<String, Object> dto = new HashMap<>();
                    dto.put("quantity", detail.getQuantity());
                    dto.put("comments", detail.getComments() != null ? detail.getComments() : "");
                    Map<String, Object> itemMenuDTO = new HashMap<>();
                    itemMenuDTO.put("idItemMenu", detail.getItemMenu().getIdItemMenu());
                    itemMenuDTO.put("name", detail.getItemMenu().getName());
                    itemMenuDTO.put("price", detail.getItemMenu().getPrice());
                    dto.put("itemMenu", itemMenuDTO);
                    return dto;
                })
                .collect(Collectors.toList());
            model.addAttribute("orderDetails", orderDetailsDTO);
            model.addAttribute("canChangeOrderType", canChangeOrderType(existingOrder));

            // Ensure the order's current table is in the available tables list
            if (existingOrder.getTable() != null) {
                List<RestaurantTable> availableTables = (List<RestaurantTable>) model.getAttribute("availableTables");
                if (availableTables != null && availableTables.stream()
                        .noneMatch(t -> t.getId().equals(existingOrder.getTable().getId()))) {
                    availableTables.add(existingOrder.getTable());
                }
            }
        });
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
        
        // Item-level status information
        dto.put("pendingItemsCount", order.getPendingItemsCount());
        dto.put("newItemsCount", order.getNewItemsCount());
        dto.put("hasPendingItems", order.hasPendingItems());
        dto.put("hasNewItems", order.hasNewItems());
        dto.put("canAcceptNewItems", order.canAcceptNewItems());
        
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
        
        // Include order details with item status
        if (order.getOrderDetails() != null && !order.getOrderDetails().isEmpty()) {
            List<Map<String, Object>> items = order.getOrderDetails().stream()
                .map(this::buildOrderDetailDTO)
                .collect(Collectors.toList());
            dto.put("items", items);
        }
        
        return dto;
    }

    /**
     * Build a DTO for an order detail (item)
     */
    private Map<String, Object> buildOrderDetailDTO(OrderDetail detail) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", detail.getIdOrderDetail());
        dto.put("itemName", detail.getItemMenu().getName());
        dto.put("quantity", detail.getQuantity());
        dto.put("unitPrice", detail.getUnitPrice());
        dto.put("subtotal", detail.getSubtotal());
        dto.put("comments", detail.getComments());
        dto.put("itemStatus", detail.getItemStatus().name());
        dto.put("itemStatusLabel", detail.getItemStatus().getDisplayName());
        dto.put("isNew", detail.isNew());
        dto.put("isPending", detail.isPending());
        dto.put("isInPreparation", detail.isInPreparation());
        dto.put("isReady", detail.isReady());
        dto.put("isDelivered", detail.isDelivered());
        
        if (detail.getPreparedBy() != null) {
            dto.put("preparedBy", detail.getPreparedBy());
        }
        
        return dto;
    }

    /**
     * Get available menu items (AJAX endpoint for add items modal)
     * Returns all available items with their details
     */
    @GetMapping("/menu-items/available")
    @ResponseBody
    public Map<String, Object> getAvailableMenuItems(@PathVariable String role, Authentication authentication) {
        log.info("Fetching available menu items for role: {}", role);
        
        // Validate role
        validateRole(role, authentication);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Get all available menu items
            List<ItemMenu> availableItems = itemMenuService.findAll().stream()
                .filter(ItemMenu::getAvailable)
                .collect(Collectors.toList());
            
            // Build DTOs
            List<Map<String, Object>> itemDTOs = availableItems.stream()
                .map(item -> {
                    Map<String, Object> dto = new HashMap<>();
                    dto.put("idItemMenu", item.getIdItemMenu());
                    dto.put("name", item.getName());
                    dto.put("description", item.getDescription());
                    dto.put("price", item.getPrice());
                    dto.put("available", item.getAvailable());
                    dto.put("category", item.getCategory() != null ? item.getCategory().getName() : null);
                    dto.put("categoryId", item.getCategory() != null ? item.getCategory().getIdCategory() : null);
                    return dto;
                })
                .collect(Collectors.toList());
            
            response.put("success", true);
            response.put("data", itemDTOs);
            response.put("count", itemDTOs.size());
        } catch (Exception e) {
            log.error("Error fetching available menu items", e);
            response.put("success", false);
            response.put("message", "Error al obtener los items del menú: " + e.getMessage());
        }
        
        return response;
    }

    /**
     * DTO for adding items to existing order (AJAX)
     */
    public static class AddItemRequest {
        private Long idItemMenu;
        private Integer quantity;
        private String comments;

        public Long getIdItemMenu() {
            return idItemMenu;
        }

        public void setIdItemMenu(Long idItemMenu) {
            this.idItemMenu = idItemMenu;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public String getComments() {
            return comments;
        }

        public void setComments(String comments) {
            this.comments = comments;
        }
    }

    /**
     * DTO for order detail request (AJAX)
     */
    public static class OrderDetailRequest {
        private Long itemId;
        private Integer quantity;

        public Long getItemId() {
            return itemId;
        }

        public void setItemId(Long itemId) {
            this.itemId = itemId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
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
            // Skip combo child items - they are part of a combo and should not be
            // counted individually. The combo parent represents the combo as a whole.
            if (detail.isComboChild()) {
                continue;
            }
            
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
     * Download PDF ticket for paid order
     * GET /{role}/orders/{orderId}/download-ticket
     */
    @GetMapping("/{orderId}/download-ticket")
    public ResponseEntity<byte[]> downloadTicket(
            @PathVariable String role,
            @PathVariable Long orderId,
            Authentication authentication) {
        
        log.info("User {} downloading ticket for order {}", authentication.getName(), orderId);
        
        validateRole(role, authentication);
        
        try {
            // Find the order using the appropriate service
            OrderService orderService = getOrderService(role);
            Order order = orderService.findByIdWithDetails(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada"));
            
            // Generate PDF ticket
            byte[] pdfBytes = ticketPdfService.generateTicket(order);
            
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "ticket_" + order.getOrderNumber() + ".pdf");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
                    
        } catch (IllegalArgumentException e) {
            log.warn("Order not found: {}", orderId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error generating ticket for order {}", orderId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Download ESC/POS raw ticket for paid order (for thermal printers via QZ Tray)
     * GET /{role}/orders/{orderId}/download-ticket-raw
     */
    @GetMapping("/{orderId}/download-ticket-raw")
    public ResponseEntity<byte[]> downloadTicketRaw(
            @PathVariable String role,
            @PathVariable Long orderId,
            Authentication authentication) {

        log.info("User {} downloading ESC/POS ticket for order {}", authentication.getName(), orderId);

        validateRole(role, authentication);

        try {
            OrderService orderService = getOrderService(role);
            Order order = orderService.findByIdWithDetails(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada"));

            byte[] escposBytes = ticketEscPosService.generateTicket(order);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "ticket_" + order.getOrderNumber() + ".bin");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(escposBytes);

        } catch (IllegalArgumentException e) {
            log.warn("Order not found: {}", orderId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error generating ESC/POS ticket for order {}", orderId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Validate if promotions sent from frontend are still active
     * Returns a map with validation results and list of expired promotions
     */
    private Map<String, Object> validatePromotions(List<Map<String, Object>> items) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> expiredPromotions = new ArrayList<>();
        boolean allValid = true;
        
        for (Map<String, Object> itemData : items) {
            Object promoIdObj = itemData.get("promotionId");
            if (promoIdObj == null || promoIdObj.toString().trim().isEmpty()) {
                continue; // No promotion applied to this item
            }
            
            try {
                Long promotionId = Long.valueOf(promoIdObj.toString());
                Long itemId = Long.valueOf(itemData.get("itemId").toString());
                Integer quantity = Integer.valueOf(itemData.get("quantity").toString());
                
                ItemMenu item = itemMenuService.findById(itemId).orElse(null);
                if (item == null) {
                    continue;
                }
                
                Promotion promotion = promotionService.findById(promotionId).orElse(null);
                
                // Check if promotion is no longer valid (deleted, expired, or inactive)
                if (promotion == null || !promotion.isValidNow()) {
                    allValid = false;
                    Map<String, Object> expiredInfo = new HashMap<>();
                    expiredInfo.put("itemId", itemId);
                    expiredInfo.put("itemName", item.getName());
                    expiredInfo.put("normalPrice", item.getPrice());
                    expiredInfo.put("quantity", quantity);
                    expiredInfo.put("subtotal", item.getPrice().multiply(BigDecimal.valueOf(quantity)));
                    
                    if (promotion != null) {
                        expiredInfo.put("promotionName", promotion.getName());
                        expiredInfo.put("reason", "expirada");
                    } else {
                        expiredInfo.put("promotionName", "Promoción");
                        expiredInfo.put("reason", "eliminada");
                    }
                    
                    expiredPromotions.add(expiredInfo);
                    continue; // Skip item association check since promotion is already invalid
                }
                
                // Check if item was removed from promotion (promotion.items no longer contains item)
                boolean itemStillInPromotion = promotion.getItems() != null && 
                    promotion.getItems().stream()
                        .anyMatch(promoItem -> promoItem.getIdItemMenu().equals(itemId));
                
                if (!itemStillInPromotion) {
                    allValid = false;
                    Map<String, Object> expiredInfo = new HashMap<>();
                    expiredInfo.put("itemId", itemId);
                    expiredInfo.put("itemName", item.getName());
                    expiredInfo.put("normalPrice", item.getPrice());
                    expiredInfo.put("quantity", quantity);
                    expiredInfo.put("subtotal", item.getPrice().multiply(BigDecimal.valueOf(quantity)));
                    expiredInfo.put("promotionName", promotion.getName());
                    expiredInfo.put("reason", "ya no aplica para este producto");
                    
                    expiredPromotions.add(expiredInfo);
                }
                
            } catch (NumberFormatException e) {
                log.warn("Invalid promotion or item ID format", e);
            }
        }
        
        result.put("allValid", allValid);
        result.put("expiredPromotions", expiredPromotions);
        return result;
    }
    
    /**
     * Get order statistics - REST endpoint for real-time updates
     * Note: This endpoint always returns TODAY's statistics for real-time polling
     */
    @GetMapping("/stats")
    @ResponseBody
    public Map<String, Object> getOrderStats(@PathVariable String role, Authentication authentication) {
        validateRole(role, authentication);
        OrderService orderService = getOrderService(role);
        String currentUsername = authentication.getName();
        
        // For real-time stats, always use today
        LocalDateTime statsStartDate = dateTimeService.startOfDayUtc(dateTimeService.todayLocal());
        LocalDateTime statsEndDate = dateTimeService.endOfDayUtc(dateTimeService.todayLocal());
        
        Map<String, Object> stats = new HashMap<>();
        
        // paidCount: PAID orders today
        // - Admin: Global count (any employee)
        // - Waiter: Only orders they collected payment for
        if ("admin".equals(role)) {
            stats.put("paidCount", orderService.countPaidOrdersByDateRange(statsStartDate, statsEndDate));
        } else {
            stats.put("paidCount", orderService.countPaidOrdersByUsernameAndDateRange(currentUsername, statsStartDate, statsEndDate));
        }
        
        stats.put("todayRevenue", orderService.getRevenueByDateRange(statsStartDate, statsEndDate));
        stats.put("pendingCount", orderService.countByStatus(OrderStatus.PENDING));
        stats.put("inPreparationCount", orderService.countByStatus(OrderStatus.IN_PREPARATION));
        
        // My Collected Revenue: Orders paid by current user today (regardless of who created them)
        BigDecimal myCollectedRevenue = orderService.getRevenueByUsernameAndDateRange(currentUsername, statsStartDate, statsEndDate);
        stats.put("myCollectedRevenue", myCollectedRevenue);
        
        // My Own Revenue: Orders CREATED AND PAID by current user today
        BigDecimal myOwnRevenue = orderService.getRevenueCreatedAndPaidBySameUserAndDateRange(currentUsername, statsStartDate, statsEndDate);
        stats.put("myOwnRevenue", myOwnRevenue);
        
        // Others Collected Revenue: Orders CREATED by current user but PAID by someone else today
        BigDecimal othersCollectedRevenue = orderService.getRevenueCreatedByUserPaidByOthersAndDateRange(currentUsername, statsStartDate, statsEndDate);
        stats.put("othersCollectedRevenue", othersCollectedRevenue);
        
        return stats;
    }
}




