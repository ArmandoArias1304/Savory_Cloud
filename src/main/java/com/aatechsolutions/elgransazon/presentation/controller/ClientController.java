package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.*;
import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.ComplementRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuComboItemRepository;
import com.aatechsolutions.elgransazon.domain.repository.ItemMenuComplementRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import com.aatechsolutions.elgransazon.presentation.dto.ChangePasswordDTO;
import com.aatechsolutions.elgransazon.presentation.dto.UpdateProfileDTO;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Controller for Customer views
 * Handles menu display and order management for customers
 */
@Controller
@RequestMapping("/client")
@PreAuthorize("hasRole('ROLE_CLIENT')")
@Slf4j
public class ClientController {

    private final OrderService orderService;
    private final ItemMenuService itemMenuService;
    private final CategoryService categoryService;
    
    // Guard to prevent duplicate order submissions per customer (concurrent rapid clicks)
    private final Set<String> activeOrderSubmissions = ConcurrentHashMap.newKeySet();
    private final SystemConfigurationService systemConfigurationService;
    private final CustomerService customerService;
    private final PromotionService promotionService;
    private final ReviewService reviewService;
    private final PasswordEncoder passwordEncoder;
    private final TicketPdfService ticketPdfService;
    private final BusinessHoursService businessHoursService;
    private final CustomerAddressService customerAddressService;
    private final ComplementRepository complementRepository;
    private final ItemMenuComplementRepository itemMenuComplementRepository;
    private final ItemMenuComboItemRepository itemMenuComboItemRepository;
    private final DateTimeService dateTimeService;

    public ClientController(
            @Qualifier("customerOrderService") OrderService orderService,
            ItemMenuService itemMenuService,
            CategoryService categoryService,
            SystemConfigurationService systemConfigurationService,
            CustomerService customerService,
            PromotionService promotionService,
            ReviewService reviewService,
            PasswordEncoder passwordEncoder,
            TicketPdfService ticketPdfService,
            BusinessHoursService businessHoursService,
            CustomerAddressService customerAddressService,
            ComplementRepository complementRepository,
            ItemMenuComplementRepository itemMenuComplementRepository,
            ItemMenuComboItemRepository itemMenuComboItemRepository,
            DateTimeService dateTimeService) {
        this.orderService = orderService;
        this.itemMenuService = itemMenuService;
        this.categoryService = categoryService;
        this.systemConfigurationService = systemConfigurationService;
        this.customerService = customerService;
        this.promotionService = promotionService;
        this.reviewService = reviewService;
        this.passwordEncoder = passwordEncoder;
        this.ticketPdfService = ticketPdfService;
        this.businessHoursService = businessHoursService;
        this.customerAddressService = customerAddressService;
        this.complementRepository = complementRepository;
        this.itemMenuComplementRepository = itemMenuComplementRepository;
        this.itemMenuComboItemRepository = itemMenuComboItemRepository;
        this.dateTimeService = dateTimeService;
    }

    /**
     * Helper method to get the authenticated customer from the current company context.
     * MULTI-TENANT: Uses CompanyContext to search customer in the correct company.
     * @param authentication The security authentication object
     * @return The Customer entity
     * @throws IllegalStateException if customer not found or no company context
     */
    private Customer getAuthenticatedCustomer(Authentication authentication) {
        Company currentCompany = CompanyContext.getCurrentCompany();
        if (currentCompany == null) {
            throw new IllegalStateException("No company context available");
        }
        return customerService.findByUsernameOrEmailAndCompany(authentication.getName(), currentCompany)
                .orElseThrow(() -> new IllegalStateException("Cliente no encontrado"));
    }

    /**
     * Show customer dashboard (landing page after login)
     */
    @GetMapping("/dashboard")
    public String showDashboard(Authentication authentication, Model model) {
        log.debug("Customer {} accessing dashboard", authentication.getName());
        
        try {
            // Get customer info
            Customer customer = getAuthenticatedCustomer(authentication);
            
            // Get customer statistics
            List<Order> allOrders = orderService.findAll();
            long totalOrders = allOrders.size();
            long activeOrders = allOrders.stream()
                    .filter(o -> o.getStatus() != OrderStatus.CANCELLED && 
                               o.getStatus() != OrderStatus.PAID)
                    .count();
            
            model.addAttribute("customer", customer);
            model.addAttribute("totalOrders", totalOrders);
            model.addAttribute("activeOrders", activeOrders);
            
            // Check if restaurant is currently open
            boolean isRestaurantOpen = businessHoursService.isOpenNow();
            model.addAttribute("isRestaurantOpen", isRestaurantOpen);
            log.debug("Restaurant is currently: {}", isRestaurantOpen ? "open" : "closed");
            
            return "client/dashboard";
            
        } catch (Exception e) {
            log.error("Error loading dashboard for customer", e);
            model.addAttribute("errorMessage", "Error al cargar el dashboard: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Show menu to customer in VIEW-ONLY mode (when restaurant is closed)
     */
    @GetMapping("/view")
    public String showMenuViewOnly(Authentication authentication, Model model) {
        log.debug("Customer {} accessing menu in view-only mode", authentication.getName());
        
        try {
            // Update item availability
            itemMenuService.updateAllItemsAvailability();
            
            // Get available items
            List<ItemMenu> availableItems = itemMenuService.findAvailableItems();
            
            // Group items by category
            Map<Long, List<ItemMenu>> itemsByCategory = availableItems.stream()
                    .collect(Collectors.groupingBy(item -> item.getCategory().getIdCategory()));
            
            // Get active categories - ONLY those with active items
            List<Category> categories = categoryService.getAllActiveCategories().stream()
                    .filter(category -> itemsByCategory.containsKey(category.getIdCategory()))
                    .collect(Collectors.toList());
            
            // Get system configuration
            SystemConfiguration config = systemConfigurationService.getConfiguration();
            
            // Get customer info
            Customer customer = getAuthenticatedCustomer(authentication);
            
            model.addAttribute("config", config);
            model.addAttribute("categories", categories);
            model.addAttribute("itemsByCategory", itemsByCategory);
            model.addAttribute("currentRole", "client");
            model.addAttribute("customer", customer);
            
            return "client/view";
            
        } catch (Exception e) {
            log.error("Error showing view-only menu", e);
            model.addAttribute("errorMessage", "Error al cargar el menú: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Show menu to customer (landing page after login)
     */
    @GetMapping("/menu")
    public String showMenu(Authentication authentication, Model model) {
        log.debug("Customer {} accessing menu", authentication.getName());
        
        try {
            // Update item availability
            itemMenuService.updateAllItemsAvailability();
            
            // Get available items
            List<ItemMenu> availableItems = itemMenuService.findAvailableItems();
            
            // Group items by category
            Map<Long, List<ItemMenu>> itemsByCategory = availableItems.stream()
                    .collect(Collectors.groupingBy(item -> item.getCategory().getIdCategory()));
            
            // Get active categories - ONLY those with active items
            List<Category> categories = categoryService.getAllActiveCategories().stream()
                    .filter(category -> itemsByCategory.containsKey(category.getIdCategory()))
                    .collect(Collectors.toList());
            
            // Get system configuration
            SystemConfiguration config = systemConfigurationService.getConfiguration();
            
            // Get customer info
            Customer customer = getAuthenticatedCustomer(authentication);
            
            // Get enabled payment methods - for client we need both restaurant and delivery
            // Default is TAKEOUT (uses restaurant methods), DELIVERY uses delivery methods
            // We'll pass both sets to allow JavaScript to switch based on selected order type
            Map<PaymentMethodType, Boolean> restaurantPayments = config.getPaymentMethods();
            Map<PaymentMethodType, Boolean> deliveryPayments = config.getDeliveryPaymentMethods();
            
            // Default to restaurant methods (TAKEOUT is the default)
            List<PaymentMethodType> enabledPaymentMethods = restaurantPayments.entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            
            // Get delivery enabled methods for when user switches to DELIVERY
            List<PaymentMethodType> deliveryPaymentMethods = deliveryPayments.entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            
            // Get customer addresses for delivery selection
            var customerAddresses = customerAddressService.getAddressesByCustomerId(customer.getIdCustomer());
            var defaultAddress = customerAddresses.stream()
                    .filter(CustomerAddress::getIsDefault)
                    .findFirst()
                    .orElse(null);
            
            // Default delivery cost (used by JS to add to cart total when orderType==DELIVERY)
            BigDecimal defaultDeliveryCost = config.getDefaultDeliveryCost() != null
                    ? config.getDefaultDeliveryCost()
                    : BigDecimal.ZERO;

            model.addAttribute("config", config);
            model.addAttribute("categories", categories);
            model.addAttribute("itemsByCategory", itemsByCategory);
            model.addAttribute("currentRole", "client");
            model.addAttribute("customer", customer);
            model.addAttribute("orderTypes", Arrays.asList(OrderType.TAKEOUT, OrderType.DELIVERY));
            model.addAttribute("orderType", OrderType.DELIVERY); // Default order type
            model.addAttribute("enabledPaymentMethods", enabledPaymentMethods);
            model.addAttribute("deliveryPaymentMethods", deliveryPaymentMethods);
            model.addAttribute("customerAddresses", customerAddresses);
            model.addAttribute("defaultAddress", defaultAddress);
            model.addAttribute("hasAddresses", !customerAddresses.isEmpty());
            model.addAttribute("defaultDeliveryCost", defaultDeliveryCost);
            
            return "client/menu";
            
        } catch (Exception e) {
            log.error("Error loading menu for customer", e);
            model.addAttribute("errorMessage", "Error al cargar el menú: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Show order history for customer
     */
    @GetMapping("/orders")
    public String showOrderHistory(Authentication authentication, @RequestParam(defaultValue = "1") int page, Model model) {
        log.debug("Customer {} accessing order history", authentication.getName());
        
        try {
            // Get customer orders
            List<Order> orders = orderService.findAll();
            
            // Sort by created date descending
            orders.sort((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()));
            
            // Calculate statistics
            long totalOrders = orders.size();
            long activeOrders = orders.stream()
                    .filter(o -> o.getStatus() != OrderStatus.CANCELLED && 
                               o.getStatus() != OrderStatus.PAID)
                    .count();
            long completedOrders = orders.stream()
                    .filter(o -> o.getStatus() == OrderStatus.PAID)
                    .count();
            
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
            model.addAttribute("totalOrders", totalOrders);
            model.addAttribute("activeOrders", activeOrders);
            model.addAttribute("completedOrders", completedOrders);
            model.addAttribute("orderStatuses", OrderStatus.values());
            
            return "client/orders";
            
        } catch (Exception e) {
            log.error("Error loading order history for customer", e);
            model.addAttribute("errorMessage", "Error al cargar el historial: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Show order details
     */
    @GetMapping("/orders/{id}")
    public String showOrderDetail(@PathVariable Long id, Authentication authentication, Model model,
                                   RedirectAttributes redirectAttributes) {
        log.debug("Customer {} accessing order detail: {}", authentication.getName(), id);
        
        try {
            Order order = orderService.findByIdWithDetails(id)
                    .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));
            
            model.addAttribute("order", order);
            model.addAttribute("orderDetails", order.getOrderDetails());
            
            return "client/order-detail";
            
        } catch (Exception e) {
            log.error("Error loading order detail", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
            return "redirect:/client/orders";
        }
    }

    /**
     * Create new order (AJAX endpoint)
     */
    @PostMapping("/orders/create")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestBody Map<String, Object> orderData,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Customer {} creating new order", username);
        
        // Use username + companyId as key to avoid cross-company collisions in multi-tenant env.
        // Two customers from different companies can share the same email — using only username
        // would incorrectly block one of them when both submit simultaneously.
        Long companyId = CompanyContext.getCurrentCompanyId();
        String submissionKey = username + "_" + companyId;
        
        // Prevent duplicate submissions: if this customer already has an order in progress, reject
        if (!activeOrderSubmissions.add(submissionKey)) {
            log.warn("Duplicate order submission blocked for customer: {} (company: {})", username, companyId);
            return ResponseEntity.status(429).body(Map.of(
                "success", false,
                "message", "Ya hay un pedido en proceso. Por favor espere."
            ));
        }
        
        try {
            // Validate restaurant is open
            if (!businessHoursService.isOpenNow()) {
                log.warn("Attempt to create order outside business hours by customer: {}", authentication.getName());
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "No se puede crear el pedido. El restaurante no se encuentra en horario laborable en este momento."
                ));
            }
            
            // Get customer
            Customer customer = getAuthenticatedCustomer(authentication);
            
            // Parse order data
            OrderType orderType = OrderType.valueOf((String) orderData.get("orderType"));
            PaymentMethodType paymentMethod = PaymentMethodType.valueOf((String) orderData.get("paymentMethod"));
            
            // For DELIVERY orders, get selected address
            String deliveryAddress = null;
            String deliveryReferences = null;
            Double deliveryLatitude = null;
            Double deliveryLongitude = null;
            
            if (orderType == OrderType.DELIVERY) {
                // Get address from frontend selection (new map-based system)
                String addressFromFrontend = (String) orderData.get("deliveryAddress");
                Object addressIdObj = orderData.get("deliveryAddressId");
                
                if (addressIdObj != null && !addressIdObj.toString().isEmpty()) {
                    // Verify the address belongs to the customer
                    Long addressId = Long.valueOf(addressIdObj.toString());
                    var selectedAddress = customerAddressService.getAddressById(addressId, customer.getIdCustomer());
                    
                    if (selectedAddress.isPresent()) {
                        CustomerAddress addr = selectedAddress.get();
                        deliveryAddress = addr.getDisplayAddress();
                        deliveryLatitude = addr.getLatitude();
                        deliveryLongitude = addr.getLongitude();
                        // Get reference from saved address if not provided from frontend
                        String frontendReferences = (String) orderData.get("deliveryReferences");
                        if (frontendReferences == null || frontendReferences.trim().isEmpty()) {
                            deliveryReferences = addr.getReference();
                        } else {
                            deliveryReferences = frontendReferences;
                        }
                    } else if (addressFromFrontend != null && !addressFromFrontend.trim().isEmpty()) {
                        deliveryAddress = addressFromFrontend;
                        deliveryReferences = (String) orderData.get("deliveryReferences");
                    }
                } else if (addressFromFrontend != null && !addressFromFrontend.trim().isEmpty()) {
                    deliveryAddress = addressFromFrontend;
                    deliveryReferences = (String) orderData.get("deliveryReferences");
                }
                
                // Validate delivery address is provided
                if (deliveryAddress == null || deliveryAddress.trim().isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Para pedidos a domicilio debes seleccionar una dirección guardada"
                    ));
                }

                // Validate delivery range (only when admin has configured restaurant coords + max distance).
                SystemConfiguration deliveryConfig = systemConfigurationService.getConfiguration();
                if (deliveryConfig != null && deliveryConfig.hasDeliveryRangeRestriction()
                        && !deliveryConfig.isWithinDeliveryRange(deliveryLatitude, deliveryLongitude)) {
                    log.warn("Customer {} blocked from creating DELIVERY order — address out of range (lat={}, lng={})",
                            customer.getUsername(), deliveryLatitude, deliveryLongitude);
                    return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "El servicio de entrega a domicilio no está disponible para su ubicación"
                    ));
                }
            }
            
            BigDecimal taxRate = new BigDecimal(systemConfigurationService.getConfiguration().getTaxRate().toString());
            // Customers cannot override the delivery cost — always use the configured default.
            BigDecimal effectiveDeliveryCost = (orderType == OrderType.DELIVERY)
                    ? (systemConfigurationService.getConfiguration().getDefaultDeliveryCost() != null
                        ? systemConfigurationService.getConfiguration().getDefaultDeliveryCost()
                        : BigDecimal.ZERO)
                    : BigDecimal.ZERO;
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) orderData.get("items");
            
            // Create order
            Order order = Order.builder()
                    .orderType(orderType)
                    .paymentMethod(paymentMethod)
                    .deliveryAddress(deliveryAddress)
                    .deliveryReferences(deliveryReferences)
                    .deliveryLatitude(deliveryLatitude)
                    .deliveryLongitude(deliveryLongitude)
                    .taxRate(taxRate)
                    .deliveryCost(effectiveDeliveryCost)
                    .status(OrderStatus.PENDING)
                    .customer(customer)
                    .customerName(customer.getFullName())
                    .customerPhone(customer.getPhone())
                    .createdBy(authentication.getName())
                    .build();
            
            // Create order details
            List<OrderDetail> orderDetails = new ArrayList<>();
            for (Map<String, Object> itemData : items) {
                Long itemId = Long.valueOf(itemData.get("itemId").toString());
                Integer quantity = Integer.valueOf(itemData.get("quantity").toString());
                String comments = (String) itemData.get("comments");
                
                ItemMenu itemMenu = itemMenuService.findById(itemId)
                        .orElseThrow(() -> new IllegalArgumentException("Item no encontrado: " + itemId));
                
                // Validate item is active
                if (!Boolean.TRUE.equals(itemMenu.getActive())) {
                    throw new IllegalStateException("El item '" + itemMenu.getName() + "' está desactivado y no puede ser seleccionado.");
                }
                
                // Validate item belongs to an active category
                if (itemMenu.getCategory() != null && !Boolean.TRUE.equals(itemMenu.getCategory().getActive())) {
                    throw new IllegalStateException("El item '" + itemMenu.getName() + "' pertenece a la categoría '" + 
                        itemMenu.getCategory().getName() + "' y está desactivada por el momento.");
                }
                
                // Check stock availability BEFORE schedule (available=false means out of stock)
                if (!Boolean.TRUE.equals(itemMenu.getAvailable())) {
                    log.warn("Stock check failed for item '{}' in client order", itemMenu.getName());
                    return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "¡Lo sentimos! No tenemos suficiente stock de los siguientes items: " + itemMenu.getName() + ". ¡Te invitamos a seguir descubriendo las deliciosas opciones de nuestro menú!",
                        "errorType", "STOCK_ERROR"
                    ));
                }
                
                // Validate item availability schedule (day and time)
                if (!itemMenuService.isItemAvailableNow(itemId)) {
                    java.time.DayOfWeek javaDow = dateTimeService.todayLocal().getDayOfWeek();
                    DayOfWeek todayDay = DayOfWeek.valueOf(javaDow.name());
                    String todayName = todayDay.getDisplayName();
                    
                    String scheduleMsg = "";
                    if (itemMenu.getAvailabilityDays() != null) {
                        var todayAvail = itemMenu.getAvailabilityDays().stream()
                            .filter(a -> a.getDayOfWeek() == todayDay)
                            .findFirst()
                            .orElse(null);
                        
                        if (todayAvail != null && todayAvail.getStartTime() != null && todayAvail.getEndTime() != null) {
                            scheduleMsg = "Hoy (" + todayName + ") disponible de " + todayAvail.getStartTime() + " a " + todayAvail.getEndTime() + ".";
                        } else if (todayAvail != null) {
                            scheduleMsg = "Hoy (" + todayName + ") está disponible pero fuera de horario.";
                        } else {
                            scheduleMsg = "No disponible hoy (" + todayName + "). Disponible: " + itemMenu.getAvailabilityDescription();
                        }
                    }
                    
                    throw new IllegalStateException("El item '" + itemMenu.getName() + "' no está disponible en este momento. " + scheduleMsg);
                }
                
                // ========== COMBO EXPANSION ==========
                if (Boolean.TRUE.equals(itemMenu.getIsCombo())) {
                    List<OrderDetail> comboDetails = expandComboItem(itemMenu, quantity, comments, itemData);
                    orderDetails.addAll(comboDetails);
                    continue; // Skip regular order detail creation
                }
                // ========== END COMBO EXPANSION ==========
                
                // IMPORTANT: ALWAYS recalculate prices in backend (don't trust frontend values)
                // Get promotion ID from frontend
                Long promotionId = null;
                Object promotionIdObj = itemData.get("promotionId");
                if (promotionIdObj != null && !promotionIdObj.toString().isEmpty()) {
                    try {
                        promotionId = Long.valueOf(promotionIdObj.toString());
                        log.debug("Promotion ID from frontend: {} for item {}", promotionId, itemId);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid promotion ID format: {}", promotionIdObj);
                    }
                }
                
                // Recalculate promotion price in backend (NEVER trust frontend values)
                BigDecimal unitPrice = itemMenu.getPrice();
                BigDecimal promotionAppliedPrice = null;
                BigDecimal calculatedSubtotal = null;
                
                if (promotionId != null) {
                    // Fetch promotion from database
                    Promotion promotion = promotionService.findById(promotionId)
                            .orElse(null);
                    
                    if (promotion != null && promotion.isValidNow()) {
                        // Recalculate based on promotion type
                        switch (promotion.getPromotionType()) {
                            case FIXED_AMOUNT_DISCOUNT:
                                // Fixed discount with minQuantityForFixedDiscount consideration
                                // Use calculateDiscountedPrice which properly handles minQuantity
                                BigDecimal fixedDiscountTotal = promotion.calculateDiscountedPrice(unitPrice, quantity);
                                promotionAppliedPrice = fixedDiscountTotal
                                    .divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
                                calculatedSubtotal = fixedDiscountTotal.setScale(2, RoundingMode.HALF_UP);
                                log.debug("FIXED_AMOUNT_DISCOUNT: original={}, discount={}, minQty={}, qty={}, total={}", 
                                    unitPrice, promotion.getDiscountAmount(), 
                                    promotion.getMinQuantityForFixedDiscount(), quantity, fixedDiscountTotal);
                                break;
                                
                            case PERCENTAGE_DISCOUNT:
                                // Percentage discount: newPrice = originalPrice * (1 - discount%)
                                promotionAppliedPrice = promotion.calculatePercentageDiscount(unitPrice);
                                log.debug("PERCENTAGE_DISCOUNT: original={}, percentage={}, final={}", 
                                    unitPrice, promotion.getDiscountAmount(), promotionAppliedPrice);
                                break;
                                
                            case BUY_X_PAY_Y:
                                // BUY_X_PAY_Y: only applies if quantity >= buyQuantity
                                if (quantity >= promotion.getBuyQuantity()) {
                                    // Calculate total discounted price using promotion method
                                    BigDecimal calculatedDiscountedTotal = promotion.calculateDiscountedPrice(unitPrice, quantity);
                                    
                                    // IMPORTANT FIX: For BUY_X_PAY_Y promotions, set the subtotal
                                    // directly to avoid precision errors from rounding intermediate values.
                                    // The promotionAppliedPrice is for display only (with 2 decimals).
                                    promotionAppliedPrice = calculatedDiscountedTotal
                                        .divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
                                    calculatedSubtotal = calculatedDiscountedTotal.setScale(2, RoundingMode.HALF_UP);
                                    
                                    log.debug("BUY_X_PAY_Y: original={}, buy={}, pay={}, pricePerUnit={}, subtotal={}", 
                                        unitPrice, promotion.getBuyQuantity(), promotion.getPayQuantity(), 
                                        promotionAppliedPrice, calculatedSubtotal);
                                } else {
                                    log.debug("BUY_X_PAY_Y not applied: quantity {} < buyQuantity {}", 
                                        quantity, promotion.getBuyQuantity());
                                }
                                break;
                        }
                    } else {
                        log.warn("Promotion {} is not active or doesn't exist, ignoring", promotionId);
                    }
                }
                
                // Build the order detail with recalculated values
                OrderDetail.OrderDetailBuilder detailBuilder = OrderDetail.builder()
                        .itemMenu(itemMenu)
                        .itemName(itemMenu.getName())
                        .quantity(quantity)
                        .unitPrice(unitPrice)
                        .promotionAppliedPrice(promotionAppliedPrice)
                        .appliedPromotionId(promotionId)
                        .comments(comments);
                        // NOTE: itemStatus lo asigna autoritativamente OrderServiceImpl.createInternal
                        // según los flags de preparación del ItemMenu (chef/barista/parrillero).
                        // .itemStatus(OrderStatus.PENDING);
                
                // For BUY_X_PAY_Y, set the pre-calculated subtotal directly
                if (calculatedSubtotal != null) {
                    detailBuilder.subtotal(calculatedSubtotal);
                }
                
                OrderDetail detail = detailBuilder.build();
                
                // Calculate subtotal (this will use promotionAppliedPrice if present)
                // Only if we didn't already set it for BUY_X_PAY_Y
                if (calculatedSubtotal == null) {
                    detail.calculateSubtotal();
                }
                
                // ========== PROCESS COMPLEMENTS ==========
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> itemComplements = (List<Map<String, Object>>) itemData.get("complements");
                
                if (itemComplements != null && !itemComplements.isEmpty()) {
                    List<OrderDetailComplement> orderDetailComplements = new ArrayList<>();

                    // BACKEND VALIDATION: Count selected sauces and validate against min/maxSauces
                    Integer maxSauces = itemMenu.getMaxSauces();
                    Integer minSauces = itemMenu.getMinSauces();
                    int selectedSaucesCount = 0;
                    for (Map<String, Object> compData : itemComplements) {
                        if (compData.get("id") == null) continue;
                        Long cId = Long.valueOf(compData.get("id").toString());
                        Complement tempComplement = complementRepository.findById(cId).orElse(null);
                        if (tempComplement != null && Boolean.TRUE.equals(tempComplement.getIsSauce())) {
                            selectedSaucesCount++;
                        }
                    }
                    if (maxSauces != null && maxSauces > 0 && selectedSaucesCount > maxSauces) {
                        throw new IllegalArgumentException(
                            "El item '" + itemMenu.getName() + "' permite máximo " + maxSauces +
                            " salsa(s) o especialidad(es), pero se seleccionaron " + selectedSaucesCount + ".");
                    }
                    if (minSauces != null && minSauces > 0 && selectedSaucesCount < minSauces) {
                        throw new IllegalArgumentException(
                            "El item '" + itemMenu.getName() + "' requiere al menos " + minSauces +
                            " salsa(s) o especialidad(es), pero se seleccionaron " + selectedSaucesCount + ".");
                    }
                    log.info("Item '{}' - minSauces: {}, maxSauces: {}, selectedSauces: {}",
                        itemMenu.getName(), minSauces, maxSauces, selectedSaucesCount);

                    for (Map<String, Object> compData : itemComplements) {
                        Long complementId = Long.valueOf(compData.get("id").toString());
                        Integer compQuantity = Integer.valueOf(compData.get("quantity").toString());
                        
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
                                "El complemento '" + complement.getName() + "' no está asociado al item '" + itemMenu.getName() + "'."));
                        
                        // Validate complement association is active
                        if (!Boolean.TRUE.equals(itemMenuComplement.getActive())) {
                            throw new IllegalStateException(
                                "El complemento '" + complement.getName() + "' no está activo para el item '" + itemMenu.getName() + "'.");
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
                                ") para el item '" + itemMenu.getName() + "'.");
                        }
                        
                        // Compute effective qty (sauce → per-serving × item qty; non-sauce → as-is)
                        // and persist that effective value so stored quantity is the real total.
                        int effectiveCompQty = Boolean.TRUE.equals(complement.getIsSauce())
                            ? compQuantity * quantity
                            : compQuantity;
                        if (!complement.hasEnoughStock(effectiveCompQty)) {
                            throw new IllegalStateException(
                                "Stock insuficiente para el complemento '" + complement.getName() + "'");
                        }
                        
                        // Create OrderDetailComplement with effective qty
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
                        
                        log.debug("Added complement '{}' x{} (effective: {}) to item '{}' - unit price: {}, subtotal: {}",
                            complement.getName(), compQuantity, effectiveCompQty, itemMenu.getName(),
                            odc.getUnitPrice(), odc.getSubtotal());
                    }
                    
                    // Set complements on the detail
                    detail.setSelectedComplements(orderDetailComplements);
                    log.info("Item '{}' has {} complements attached", itemMenu.getName(), orderDetailComplements.size());
                }
                
                orderDetails.add(detail);
            }
            
            // Validate dine-in-only items: only allowed for DINE_IN orders
            if (orderType != OrderType.DINE_IN) {
                for (OrderDetail detail : orderDetails) {
                    if (Boolean.TRUE.equals(detail.getItemMenu().getDineInOnly())) {
                        return ResponseEntity.badRequest().body(Map.of(
                            "success", false,
                            "message", "El item '" + detail.getItemMenu().getName() + "' solo está disponible para consumo en el establecimiento."
                        ));
                    }
                }
            }
            
            // Validate stock
            Map<Long, String> stockErrors = orderService.validateStock(orderDetails);
            if (!stockErrors.isEmpty()) {
                // Build error message with item names
                String itemNames = stockErrors.values().stream()
                    .collect(Collectors.joining(", "));
                
                log.warn("Stock validation failed for client order: {}", itemNames);
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "¡Lo sentimos! No tenemos suficiente stock de los siguientes items: " + itemNames + ". ¡Te invitamos a seguir descubriendo las deliciosas opciones de nuestro menú!",
                    "errorType", "STOCK_ERROR"
                ));
            }
            
            // Validate promotions are still active
            Map<String, Object> promotionValidation = validatePromotions(items);
            if (!(Boolean) promotionValidation.get("allValid")) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("errorType", "PROMOTION_EXPIRED");
                response.put("expiredPromotions", promotionValidation.get("expiredPromotions"));
                response.put("message", "Algunas promociones ya no están disponibles");
                return ResponseEntity.ok(response);
            }
            
            // Create order
            Order createdOrder = orderService.create(order, orderDetails);
            
            log.info("Order created successfully: {}", createdOrder.getOrderNumber());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Pedido creado exitosamente",
                "orderNumber", createdOrder.getOrderNumber(),
                "orderId", createdOrder.getIdOrder()
            ));
            
        } catch (IllegalStateException | IllegalArgumentException e) {
            log.warn("Order validation failed: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            if (e.getMessage() != null && (e.getMessage().contains("Stock insuficiente") || e.getMessage().contains("No tenemos suficiente stock"))) {
                errorResponse.put("errorType", "STOCK_ERROR");
            }
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            log.error("Unexpected error creating order", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error al crear el pedido: " + e.getMessage()
            ));
        } finally {
            // Always release the submission lock
            activeOrderSubmissions.remove(submissionKey);
        }
    }

    /**
     * Show menu to add items to existing order
     * GET /client/orders/{orderId}/add-items
     */
    @GetMapping("/orders/{orderId}/add-items")
    public String showMenuToAddItems(
            @PathVariable Long orderId,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.debug("Customer {} adding items to order {}", authentication.getName(), orderId);

        try {
            // Get customer
            Customer customer = getAuthenticatedCustomer(authentication);

            // Get the order
            Order order = orderService.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

            // Validate order belongs to customer
            if (!order.getCustomer().getIdCustomer().equals(customer.getIdCustomer())) {
                redirectAttributes.addFlashAttribute("errorMessage", "No tienes permiso para modificar este pedido");
                return "redirect:/client/orders";
            }

            // Validate order can accept new items (customer-side rules)
            if (!order.canCustomerAcceptNewItems()) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    String.format("No se pueden agregar items a este pedido. Tipo: %s, Estado: %s",
                        order.getOrderType().getDisplayName(),
                        order.getStatus().getDisplayName()));
                return "redirect:/client/orders";
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
                    .collect(Collectors.toList());

            // Set model attributes - similar to new order but with existing order context
            model.addAttribute("orderType", order.getOrderType());
            model.addAttribute("paymentMethod", order.getPaymentMethod());
            model.addAttribute("customerName", order.getCustomerName());
            model.addAttribute("customerPhone", order.getCustomerPhone());
            model.addAttribute("deliveryAddress", order.getDeliveryAddress());
            model.addAttribute("deliveryReferences", order.getDeliveryReferences());
            model.addAttribute("categories", categories);
            model.addAttribute("itemsByCategory", itemsByCategory);
            model.addAttribute("allItems", availableItems);
            model.addAttribute("customer", customer);
            model.addAttribute("currentRole", "client");
            model.addAttribute("config", config);
            model.addAttribute("enabledPaymentMethods", enabledPaymentMethods);
            
            // Add active promotions for items
            List<Promotion> activePromotions = promotionService.findActivePromotions();
            model.addAttribute("activePromotions", activePromotions);
            
            // IMPORTANT: Add existing order ID and number so the template knows it's "add mode"
            model.addAttribute("existingOrderId", order.getIdOrder());
            model.addAttribute("existingOrderNumber", order.getOrderNumber());

            return "client/add-items-menu";
            
        } catch (Exception e) {
            log.error("Error showing add items menu", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
            return "redirect:/client/orders";
        }
    }

    /**
     * Add items to existing order (AJAX endpoint)
     * POST /client/orders/{orderId}/add-items
     */
    @PostMapping("/orders/{orderId}/add-items")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addItemsToOrder(
            @PathVariable Long orderId,
            @RequestBody Map<String, Object> requestData,
            Authentication authentication) {
        
        log.info("Customer {} adding items to order {}", authentication.getName(), orderId);

        try {
            // Validate restaurant is open
            if (!businessHoursService.isOpenNow()) {
                log.warn("Attempt to add items to order outside business hours by customer: {}", authentication.getName());
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "No se pueden agregar items al pedido. El restaurante no se encuentra en horario laborable en este momento."
                ));
            }
            // Get customer
            Customer customer = getAuthenticatedCustomer(authentication);

            // Get the order
            Order order = orderService.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

            // Validate order belongs to customer
            if (!order.getCustomer().getIdCustomer().equals(customer.getIdCustomer())) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "No tienes permiso para modificar este pedido"
                ));
            }

            // Validate order can accept new items (customer-side rules)
            if (!order.canCustomerAcceptNewItems()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", String.format("No se pueden agregar items a este pedido. Tipo: %s, Estado: %s",
                        order.getOrderType().getDisplayName(),
                        order.getStatus().getDisplayName())
                ));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) requestData.get("items");

            // Create new order details
            List<OrderDetail> newItems = new ArrayList<>();
            for (Map<String, Object> itemData : items) {
                Long itemId = Long.valueOf(itemData.get("itemId").toString());
                Integer quantity = Integer.valueOf(itemData.get("quantity").toString());
                String comments = (String) itemData.get("comments");

                ItemMenu itemMenu = itemMenuService.findById(itemId)
                        .orElseThrow(() -> new IllegalArgumentException("Item no encontrado: " + itemId));

                // Validate item is active
                if (!Boolean.TRUE.equals(itemMenu.getActive())) {
                    throw new IllegalStateException("El item '" + itemMenu.getName() + "' está desactivado y no puede ser seleccionado.");
                }
                
                // Validate item belongs to an active category
                if (itemMenu.getCategory() != null && !Boolean.TRUE.equals(itemMenu.getCategory().getActive())) {
                    throw new IllegalStateException("El item '" + itemMenu.getName() + "' pertenece a la categoría '" + 
                        itemMenu.getCategory().getName() + "' y está desactivada por el momento.");
                }
                
                // Check stock availability BEFORE schedule (available=false means out of stock)
                if (!Boolean.TRUE.equals(itemMenu.getAvailable())) {
                    log.warn("Stock check failed for item '{}' in client add-items", itemMenu.getName());
                    return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "¡Lo sentimos! No tenemos suficiente stock de los siguientes items: " + itemMenu.getName() + ". ¡Te invitamos a seguir descubriendo las deliciosas opciones de nuestro menú!",
                        "errorType", "STOCK_ERROR"
                    ));
                }
                
                // Validate item availability schedule (day and time)
                if (!itemMenuService.isItemAvailableNow(itemId)) {
                    java.time.DayOfWeek javaDow = dateTimeService.todayLocal().getDayOfWeek();
                    DayOfWeek todayDay = DayOfWeek.valueOf(javaDow.name());
                    String todayName = todayDay.getDisplayName();
                    
                    String scheduleMsg = "";
                    if (itemMenu.getAvailabilityDays() != null) {
                        var todayAvail = itemMenu.getAvailabilityDays().stream()
                            .filter(a -> a.getDayOfWeek() == todayDay)
                            .findFirst()
                            .orElse(null);
                        
                        if (todayAvail != null && todayAvail.getStartTime() != null && todayAvail.getEndTime() != null) {
                            scheduleMsg = "Hoy (" + todayName + ") disponible de " + todayAvail.getStartTime() + " a " + todayAvail.getEndTime() + ".";
                        } else if (todayAvail != null) {
                            scheduleMsg = "Hoy (" + todayName + ") está disponible pero fuera de horario.";
                        } else {
                            scheduleMsg = "No disponible hoy (" + todayName + "). Disponible: " + itemMenu.getAvailabilityDescription();
                        }
                    }
                    
                    throw new IllegalStateException("El item '" + itemMenu.getName() + "' no está disponible en este momento. " + scheduleMsg);
                }
                
                // ========== COMBO EXPANSION ==========
                if (Boolean.TRUE.equals(itemMenu.getIsCombo())) {
                    List<OrderDetail> comboDetails = expandComboItem(itemMenu, quantity, comments, itemData);
                    newItems.addAll(comboDetails);
                    continue; // Skip regular order detail creation
                }
                // ========== END COMBO EXPANSION ==========

                // IMPORTANT: ALWAYS recalculate prices in backend (don't trust frontend values)
                // Get promotion ID from frontend
                Long promotionId = null;
                Object promotionIdObj = itemData.get("promotionId");
                if (promotionIdObj != null && !promotionIdObj.toString().isEmpty()) {
                    try {
                        promotionId = Long.valueOf(promotionIdObj.toString());
                        log.debug("Promotion ID from frontend: {} for item {}", promotionId, itemId);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid promotion ID format: {}", promotionIdObj);
                    }
                }
                
                // Recalculate promotion price in backend (NEVER trust frontend values)
                BigDecimal unitPrice = itemMenu.getPrice();
                BigDecimal promotionAppliedPrice = null;
                BigDecimal calculatedSubtotal = null;
                
                if (promotionId != null) {
                    // Fetch promotion from database
                    Promotion promotion = promotionService.findById(promotionId)
                            .orElse(null);
                    
                    if (promotion != null && promotion.isValidNow()) {
                        // Recalculate based on promotion type
                        switch (promotion.getPromotionType()) {
                            case FIXED_AMOUNT_DISCOUNT:
                                // Fixed discount with minQuantityForFixedDiscount consideration
                                // Use calculateDiscountedPrice which properly handles minQuantity
                                BigDecimal fixedDiscountTotal = promotion.calculateDiscountedPrice(unitPrice, quantity);
                                promotionAppliedPrice = fixedDiscountTotal
                                    .divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
                                calculatedSubtotal = fixedDiscountTotal.setScale(2, RoundingMode.HALF_UP);
                                log.debug("FIXED_AMOUNT_DISCOUNT: original={}, discount={}, minQty={}, qty={}, total={}", 
                                    unitPrice, promotion.getDiscountAmount(), 
                                    promotion.getMinQuantityForFixedDiscount(), quantity, fixedDiscountTotal);
                                break;
                                
                            case PERCENTAGE_DISCOUNT:
                                promotionAppliedPrice = promotion.calculatePercentageDiscount(unitPrice);
                                log.debug("PERCENTAGE_DISCOUNT: original={}, percentage={}, final={}", 
                                    unitPrice, promotion.getDiscountAmount(), promotionAppliedPrice);
                                break;
                                
                            case BUY_X_PAY_Y:
                                // BUY_X_PAY_Y: only applies if quantity >= buyQuantity
                                if (quantity >= promotion.getBuyQuantity()) {
                                    // Calculate total discounted price using promotion method
                                    BigDecimal calculatedDiscountedTotal = promotion.calculateDiscountedPrice(unitPrice, quantity);
                                    
                                    // IMPORTANT FIX: For BUY_X_PAY_Y promotions, set the subtotal
                                    // directly to avoid precision errors from rounding intermediate values.
                                    // The promotionAppliedPrice is for display only (with 2 decimals).
                                    promotionAppliedPrice = calculatedDiscountedTotal
                                        .divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
                                    calculatedSubtotal = calculatedDiscountedTotal.setScale(2, RoundingMode.HALF_UP);
                                    
                                    log.debug("BUY_X_PAY_Y: original={}, buy={}, pay={}, pricePerUnit={}, subtotal={}", 
                                        unitPrice, promotion.getBuyQuantity(), promotion.getPayQuantity(), 
                                        promotionAppliedPrice, calculatedSubtotal);
                                } else {
                                    log.debug("BUY_X_PAY_Y not applied: quantity {} < buyQuantity {}", 
                                        quantity, promotion.getBuyQuantity());
                                }
                                break;
                        }
                    } else {
                        log.warn("Promotion {} is not active or doesn't exist, ignoring", promotionId);
                    }
                }

                // Build the order detail with recalculated values
                OrderDetail.OrderDetailBuilder detailBuilder = OrderDetail.builder()
                        .itemMenu(itemMenu)
                        .itemName(itemMenu.getName())
                        .quantity(quantity)
                        .unitPrice(unitPrice)
                        .promotionAppliedPrice(promotionAppliedPrice)
                        .appliedPromotionId(promotionId)
                        .comments(comments);
                        // NOTE: itemStatus lo asigna autoritativamente OrderServiceImpl.addItemsToExistingOrder
                        // según los flags de preparación del ItemMenu (chef/barista/parrillero).
                        // .itemStatus(OrderStatus.PENDING);
                
                // For BUY_X_PAY_Y, set the pre-calculated subtotal directly
                if (calculatedSubtotal != null) {
                    detailBuilder.subtotal(calculatedSubtotal);
                }
                
                OrderDetail detail = detailBuilder.build();
                
                // Calculate subtotal (this will use promotionAppliedPrice if present)
                // Only if we didn't already set it for BUY_X_PAY_Y
                if (calculatedSubtotal == null) {
                    detail.calculateSubtotal();
                }
                
                // ========== PROCESS COMPLEMENTS ==========
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> itemComplements = (List<Map<String, Object>>) itemData.get("complements");
                
                if (itemComplements != null && !itemComplements.isEmpty()) {
                    List<OrderDetailComplement> orderDetailComplements = new ArrayList<>();

                    // BACKEND VALIDATION: Count selected sauces and validate against min/maxSauces
                    Integer maxSauces = itemMenu.getMaxSauces();
                    Integer minSauces = itemMenu.getMinSauces();
                    int selectedSaucesCount = 0;
                    for (Map<String, Object> compData : itemComplements) {
                        if (compData.get("id") == null) continue;
                        Long cId = Long.valueOf(compData.get("id").toString());
                        Complement tempComplement = complementRepository.findById(cId).orElse(null);
                        if (tempComplement != null && Boolean.TRUE.equals(tempComplement.getIsSauce())) {
                            selectedSaucesCount++;
                        }
                    }
                    if (maxSauces != null && maxSauces > 0 && selectedSaucesCount > maxSauces) {
                        throw new IllegalArgumentException(
                            "El item '" + itemMenu.getName() + "' permite máximo " + maxSauces +
                            " salsa(s) o especialidad(es), pero se seleccionaron " + selectedSaucesCount + ".");
                    }
                    if (minSauces != null && minSauces > 0 && selectedSaucesCount < minSauces) {
                        throw new IllegalArgumentException(
                            "El item '" + itemMenu.getName() + "' requiere al menos " + minSauces +
                            " salsa(s) o especialidad(es), pero se seleccionaron " + selectedSaucesCount + ".");
                    }
                    log.info("Item '{}' - minSauces: {}, maxSauces: {}, selectedSauces: {}",
                        itemMenu.getName(), minSauces, maxSauces, selectedSaucesCount);

                    for (Map<String, Object> compData : itemComplements) {
                        Long complementId = Long.valueOf(compData.get("id").toString());
                        Integer compQuantity = Integer.valueOf(compData.get("quantity").toString());
                        
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
                                "El complemento '" + complement.getName() + "' no está asociado al item '" + itemMenu.getName() + "'."));
                        
                        // Validate complement association is active
                        if (!Boolean.TRUE.equals(itemMenuComplement.getActive())) {
                            throw new IllegalStateException(
                                "El complemento '" + complement.getName() + "' no está activo para el item '" + itemMenu.getName() + "'.");
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
                                ") para el item '" + itemMenu.getName() + "'.");
                        }
                        
                        // Compute effective qty (sauce → per-serving × item qty; non-sauce → as-is)
                        // and persist that effective value so stored quantity is the real total.
                        int effectiveCompQty = Boolean.TRUE.equals(complement.getIsSauce())
                            ? compQuantity * quantity
                            : compQuantity;
                        if (!complement.hasEnoughStock(effectiveCompQty)) {
                            throw new IllegalStateException(
                                "Stock insuficiente para el complemento '" + complement.getName() + "'");
                        }
                        
                        // Create OrderDetailComplement with effective qty
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
                        
                        log.debug("Added complement '{}' x{} (effective: {}) to item '{}' - unit price: {}, subtotal: {}",
                            complement.getName(), compQuantity, effectiveCompQty, itemMenu.getName(),
                            odc.getUnitPrice(), odc.getSubtotal());
                    }
                    
                    // Set complements on the detail
                    detail.setSelectedComplements(orderDetailComplements);
                    log.info("Item '{}' has {} complements attached", itemMenu.getName(), orderDetailComplements.size());
                }
                
                newItems.add(detail);
            }

            // Validate dine-in-only items: only allowed for DINE_IN orders
            if (order.getOrderType() != OrderType.DINE_IN) {
                for (OrderDetail detail : newItems) {
                    if (Boolean.TRUE.equals(detail.getItemMenu().getDineInOnly())) {
                        return ResponseEntity.badRequest().body(Map.of(
                            "success", false,
                            "message", "El item '" + detail.getItemMenu().getName() + "' solo está disponible para consumo en el establecimiento."
                        ));
                    }
                }
            }

            // Add items to order
            Order updatedOrder = orderService.addItemsToExistingOrder(orderId, newItems, authentication.getName());

            log.info("Items added successfully to order: {}", updatedOrder.getOrderNumber());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Items agregados exitosamente al pedido",
                "orderNumber", updatedOrder.getOrderNumber(),
                "orderId", updatedOrder.getIdOrder()
            ));

        } catch (IllegalStateException | IllegalArgumentException e) {
            log.warn("Add items validation failed: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            if (e.getMessage() != null && (e.getMessage().contains("Stock insuficiente") || e.getMessage().contains("No tenemos suficiente stock"))) {
                errorResponse.put("errorType", "STOCK_ERROR");
            }
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            log.error("Unexpected error adding items to order", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error al agregar items: " + e.getMessage()
            ));
        }
    }

    /**
     * Cancel order (AJAX endpoint)
     * POST /client/orders/{orderId}/cancel
     */
    @PostMapping("/orders/{orderId}/cancel")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cancelOrder(
            @PathVariable Long orderId,
            Authentication authentication) {
        
        log.info("Customer {} cancelling order {}", authentication.getName(), orderId);

        try {
            // Get customer
            Customer customer = getAuthenticatedCustomer(authentication);

            // Get the order
            Order order = orderService.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

            // Validate order belongs to customer
            if (!order.getCustomer().getIdCustomer().equals(customer.getIdCustomer())) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "No tienes permiso para cancelar este pedido"
                ));
            }

            // Cancel the order - CustomerOrderServiceImpl.cancel() handles all validation:
            // - Order not in final states (CANCELLED, PAID, DELIVERED, ON_THE_WAY)
            // - Items with preparation (Chef/Barista) must be PENDING
            // - Items without preparation must be READY
            Order cancelledOrder = orderService.cancel(orderId, authentication.getName());

            log.info("Order {} cancelled successfully by customer {}", cancelledOrder.getOrderNumber(), authentication.getName());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Pedido " + cancelledOrder.getOrderNumber() + " cancelado exitosamente.",
                "orderNumber", cancelledOrder.getOrderNumber()
            ));

        } catch (IllegalStateException | IllegalArgumentException e) {
            // Business-rule violation (e.g. items still in preparation, order already finalized).
            // Log as WARN — this is a normal validation outcome, not an unexpected error.
            log.warn("Cannot cancel order {}: {}", orderId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error cancelling order", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Delete item from order (AJAX endpoint)
     * POST /client/orders/{orderId}/items/{itemId}/delete
     */
    @PostMapping("/orders/{orderId}/items/{itemId}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteOrderItem(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            Authentication authentication) {
        
        log.info("Customer {} deleting item {} from order {}", authentication.getName(), itemId, orderId);

        try {
            // Get customer
            Customer customer = getAuthenticatedCustomer(authentication);

            // Get the order to validate ownership
            Order order = orderService.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

            // Validate order belongs to customer
            if (!order.getCustomer().getIdCustomer().equals(customer.getIdCustomer())) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "No tienes permiso para modificar este pedido"
                ));
            }

            // Try to delete the item
            OrderDetail deletedItem = orderService.deleteOrderItem(orderId, itemId, authentication.getName());

            log.info("Item '{}' deleted from order {} by customer {}", 
                    deletedItem.getItemMenu().getName(), order.getOrderNumber(), authentication.getName());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Item '" + deletedItem.getItemMenu().getName() + "' eliminado exitosamente.",
                "itemName", deletedItem.getItemMenu().getName()
            ));

        } catch (IllegalStateException e) {
            // Check if it's the last item - should cancel order instead
            if ("LAST_ITEM_CANCEL_ORDER".equals(e.getMessage())) {
                log.info("Last item deletion requested - cancelling order {} instead", orderId);
                
                try {
                    Order cancelledOrder = orderService.cancel(orderId, authentication.getName());
                    
                    return ResponseEntity.ok(Map.of(
                        "success", true,
                        "isLastItem", true,
                        "orderCancelled", true,
                        "message", "Era el último item del pedido. El pedido " + cancelledOrder.getOrderNumber() + " ha sido cancelado.",
                        "orderNumber", cancelledOrder.getOrderNumber()
                    ));
                } catch (Exception cancelError) {
                    log.error("Error cancelling order for last item deletion", cancelError);
                    return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "isLastItem", true,
                        "message", "No se puede eliminar el item ni cancelar el pedido: " + cancelError.getMessage()
                    ));
                }
            }
            
            // Business-rule violation (e.g. item already in preparation/ready and customer can't delete).
            // Log as WARN — this is a normal validation outcome, not an unexpected error.
            log.warn("Cannot delete item {} from order {}: {}", itemId, orderId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error deleting item from order", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error al eliminar el item: " + e.getMessage()
            ));
        }
    }

    /**
     * Get active promotions (AJAX endpoint)
     */
    @GetMapping("/promotions/active")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getActivePromotions() {
        log.debug("Fetching active promotions for customer");
        
        try {
            List<Promotion> promotions = promotionService.findActivePromotions();
            
            List<Map<String, Object>> promotionData = promotions.stream()
                    .map(this::convertPromotionToMap)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(promotionData);
            
        } catch (Exception e) {
            log.error("Error fetching active promotions", e);
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Get maximum available quantity for a menu item based on ingredient stock (AJAX)
     */
    @GetMapping("/menu-items/{id}/max-quantity")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMaxQuantity(@PathVariable Long id) {
        log.debug("Getting max available quantity for menu item {}", id);
        
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            int maxQuantity = itemMenuService.getMaxAvailableQuantity(id);
            response.put("maxQuantity", maxQuantity);
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting max quantity for item {}", id, e);
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("maxQuantity", 0);
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Update customer profile (WITHOUT password) - Using DTO
     */
    @PostMapping("/profile/update")
    public String updateProfile(
            @Valid @ModelAttribute UpdateProfileDTO profileDTO,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.info("Customer {} updating profile", authentication.getName());
        
        try {
            Customer existing = getAuthenticatedCustomer(authentication);
            
            // Validate form errors
            if (bindingResult.hasErrors()) {
                log.warn("Validation errors found during profile update");
                model.addAttribute("customer", existing);
                model.addAttribute("profileDTO", profileDTO);
                model.addAttribute("passwordDTO", new ChangePasswordDTO());
                return "client/profile";
            }
            
            // Validate unique constraints (except for current customer)
            if (!existing.getUsername().equalsIgnoreCase(profileDTO.getUsername()) && 
                customerService.existsByUsernameAndCompany(profileDTO.getUsername(), CompanyContext.getCurrentCompany())) {
                bindingResult.rejectValue("username", "error.customer", "El nombre de usuario ya está en uso");
                model.addAttribute("customer", existing);
                model.addAttribute("profileDTO", profileDTO);
                model.addAttribute("passwordDTO", new ChangePasswordDTO());
                return "client/profile";
            }
            
            if (!existing.getPhone().equals(profileDTO.getPhone()) && 
                customerService.existsByPhoneAndCompany(profileDTO.getPhone(), CompanyContext.getCurrentCompany())) {
                bindingResult.rejectValue("phone", "error.customer", "El teléfono ya está registrado");
                model.addAttribute("customer", existing);
                model.addAttribute("profileDTO", profileDTO);
                model.addAttribute("passwordDTO", new ChangePasswordDTO());
                return "client/profile";
            }
            
            // Update only allowed fields from DTO
            existing.setFullName(profileDTO.getFullName());
            existing.setUsername(profileDTO.getUsername());
            existing.setPhone(profileDTO.getPhone());
            
            // MULTI-TENANT: Use update with company parameter
            Company currentCompany = CompanyContext.requireCurrentCompany();
            customerService.update(existing.getIdCustomer(), existing, currentCompany);
            
            log.info("Customer profile updated successfully: {}", existing.getUsername());
            redirectAttributes.addFlashAttribute("successMessage", "Perfil actualizado exitosamente");
            return "redirect:/client/profile";
            
        } catch (Exception e) {
            log.error("Error updating customer profile", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al actualizar perfil: " + e.getMessage());
            return "redirect:/client/profile";
        }
    }

    /**
     * Change customer password (SEPARATE endpoint) - Using DTO
     */
    @PostMapping("/profile/change-password")
    public String changePassword(
            @Valid @ModelAttribute ChangePasswordDTO passwordDTO,
            BindingResult bindingResult,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        log.info("Customer {} changing password", authentication.getName());
        
        try {
            // Validate form errors
            if (bindingResult.hasErrors()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Por favor corrige los errores en el formulario");
                return "redirect:/client/profile";
            }
            
            // Validate passwords match
            if (!passwordDTO.passwordsMatch()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Las contraseñas no coinciden");
                return "redirect:/client/profile";
            }
            
            Customer existing = getAuthenticatedCustomer(authentication);
            
            // Encode the new password before updating
            String encodedPassword = passwordEncoder.encode(passwordDTO.getNewPassword());
            existing.setPassword(encodedPassword);
            
            // MULTI-TENANT: Use update with company parameter
            Company currentCompany = CompanyContext.requireCurrentCompany();
            customerService.update(existing.getIdCustomer(), existing, currentCompany);
            
            log.info("Customer password changed successfully: {}", existing.getUsername());
            redirectAttributes.addFlashAttribute("successMessage", "Contraseña actualizada exitosamente");
            return "redirect:/client/profile";
            
        } catch (Exception e) {
            log.error("Error changing customer password", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cambiar contraseña: " + e.getMessage());
            return "redirect:/client/profile";
        }
    }

    /**
     * Show customer profile
     */
    @GetMapping("/profile")
    public String showProfile(Authentication authentication, Model model) {
        log.debug("Customer {} accessing profile", authentication.getName());
        
        try {
            Customer customer = getAuthenticatedCustomer(authentication);
            
            // Create DTOs for form binding
            UpdateProfileDTO profileDTO = new UpdateProfileDTO();
            profileDTO.setFullName(customer.getFullName());
            profileDTO.setUsername(customer.getUsername());
            profileDTO.setPhone(customer.getPhone());
            
            // Get customer addresses for the map
            var addresses = customerAddressService.getAddressesByCustomerId(customer.getIdCustomer());
            
            model.addAttribute("customer", customer); // For display (email, etc.)
            model.addAttribute("profileDTO", profileDTO); // For profile form binding
            model.addAttribute("passwordDTO", new ChangePasswordDTO()); // For password form binding
            model.addAttribute("addresses", addresses); // For addresses section
            return "client/profile";
            
        } catch (Exception e) {
            log.error("Error loading customer profile", e);
            model.addAttribute("errorMessage", "Error al cargar perfil: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Show customer review form
     */
    @GetMapping("/review")
    public String showReviewForm(Authentication authentication, Model model) {
        log.debug("Customer {} accessing review form", authentication.getName());
        
        try {
            Customer customer = getAuthenticatedCustomer(authentication);
            
            // Check if customer has at least one PAID order
            List<Order> customerOrders = orderService.findAll();
            boolean hasPaidOrder = customerOrders.stream()
                    .anyMatch(order -> order.getStatus() == OrderStatus.PAID);
            
            if (!hasPaidOrder) {
                model.addAttribute("noPurchase", true);
                return "client/review";
            }
            
            // Check if customer already has a review
            Optional<Review> existingReview = reviewService.getReviewByCustomer(customer);
            
            model.addAttribute("customer", customer);
            model.addAttribute("existingReview", existingReview.orElse(null));
            model.addAttribute("hasReview", existingReview.isPresent());
            model.addAttribute("noPurchase", false);
            
            return "client/review";
            
        } catch (Exception e) {
            log.error("Error loading review form", e);
            model.addAttribute("errorMessage", "Error al cargar formulario: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Submit or update customer review
     */
    @PostMapping("/review")
    public String submitReview(
            @RequestParam Integer rating,
            @RequestParam String comment,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        log.info("Customer {} submitting review", authentication.getName());
        
        try {
            Customer customer = getAuthenticatedCustomer(authentication);
            
            // Verify customer has at least one PAID order
            List<Order> customerOrders = orderService.findAll();
            boolean hasPaidOrder = customerOrders.stream()
                    .anyMatch(order -> order.getStatus() == OrderStatus.PAID);
            
            if (!hasPaidOrder) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                        "Debes realizar al menos una compra antes de dejar una reseña");
                return "redirect:/client/review";
            }
            
            // Validate input
            if (rating == null || rating < 1 || rating > 5) {
                redirectAttributes.addFlashAttribute("errorMessage", "La calificación debe estar entre 1 y 5 estrellas");
                return "redirect:/client/review";
            }
            
            if (comment == null || comment.trim().length() < 10) {
                redirectAttributes.addFlashAttribute("errorMessage", "El comentario debe tener al menos 10 caracteres");
                return "redirect:/client/review";
            }
            
            if (comment.trim().length() > 500) {
                redirectAttributes.addFlashAttribute("errorMessage", "El comentario no puede exceder 500 caracteres");
                return "redirect:/client/review";
            }
            
            // Create or update review
            reviewService.createOrUpdateReview(customer, rating, comment.trim());
            
            redirectAttributes.addFlashAttribute("successMessage", 
                    "Su reseña se ha enviado correctamente. ¡Gracias!");
            
            return "redirect:/client/review";
            
        } catch (Exception e) {
            log.error("Error submitting review", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al enviar reseña: " + e.getMessage());
            return "redirect:/client/review";
        }
    }

    /**
     * Download PDF ticket for paid order
     * GET /client/orders/{orderId}/download-ticket
     */
    @GetMapping("/orders/{orderId}/download-ticket")
    public ResponseEntity<byte[]> downloadTicket(
            @PathVariable Long orderId,
            Authentication authentication) {
        
        log.info("Customer {} downloading ticket for order {}", authentication.getName(), orderId);
        
        try {
            // Find the order
            Order order = orderService.findByIdWithDetails(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada"));
            
            // Validate that order belongs to the customer
            Customer customer = getAuthenticatedCustomer(authentication);
            
            if (!order.getCustomer().getIdCustomer().equals(customer.getIdCustomer())) {
                log.warn("Customer {} attempted to download ticket for order {} that doesn't belong to them", 
                         authentication.getName(), orderId);
                return ResponseEntity.status(403).build();
            }
            
            // Validate that order is PAID
            if (order.getStatus() != OrderStatus.PAID) {
                log.warn("Attempted to download ticket for unpaid order: {}", order.getOrderNumber());
                return ResponseEntity.badRequest().build();
            }
            
            // Generate PDF ticket
            byte[] pdfBytes = ticketPdfService.generateTicket(order);
            
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "ticket_" + order.getOrderNumber() + ".pdf");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            
            return new ResponseEntity<>(pdfBytes, headers, org.springframework.http.HttpStatus.OK);
            
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Error downloading ticket: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error generating ticket PDF for order ID: " + orderId, e);
            return ResponseEntity.status(500).build();
        }
    }

    // ========== Helper Methods ==========

    private Map<String, Object> convertPromotionToMap(Promotion promotion) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", promotion.getIdPromotion());
        map.put("name", promotion.getName());
        map.put("description", promotion.getDescription());
        map.put("type", promotion.getPromotionType().name());
        map.put("promotionType", promotion.getPromotionType().name()); // Added for JavaScript compatibility
        map.put("imageUrl", promotion.getImageUrl());
        
        // Add type-specific fields and display label
        if (promotion.getPromotionType() == PromotionType.BUY_X_PAY_Y) {
            map.put("buyQuantity", promotion.getBuyQuantity());
            map.put("payQuantity", promotion.getPayQuantity());
            map.put("displayLabel", promotion.getBuyQuantity() + "x" + promotion.getPayQuantity());
        } else if (promotion.getPromotionType() == PromotionType.PERCENTAGE_DISCOUNT) {
            map.put("discountPercentage", promotion.getDiscountPercentage());
            map.put("displayLabel", promotion.getDiscountPercentage().setScale(2) + "% OFF");
        } else if (promotion.getPromotionType() == PromotionType.FIXED_AMOUNT_DISCOUNT) {
            map.put("discountAmount", promotion.getDiscountAmount());
            map.put("minQuantityForFixedDiscount", promotion.getMinQuantityForFixedDiscount());
            // Build display label considering minQuantityForFixedDiscount
            Integer minQty = promotion.getMinQuantityForFixedDiscount();
            if (minQty != null && minQty > 1) {
                map.put("displayLabel", "$" + promotion.getDiscountAmount() + " OFF (c/" + minQty + " items)");
            } else {
                map.put("displayLabel", "$" + promotion.getDiscountAmount() + " OFF");
            }
        }
        
        // Add item IDs (filtered by current company and distinct)
        Company currentCompany = CompanyContext.requireCurrentCompany();
        List<Long> itemIds = promotion.getItems().stream()
                .filter(item -> item.getCompany() != null && currentCompany.getIdCompany().equals(item.getCompany().getIdCompany()))
                .map(ItemMenu::getIdItemMenu)
                .distinct()
                .collect(Collectors.toList());
        map.put("itemIds", itemIds);
        
        return map;
    }

    // ========== ADDRESS MANAGEMENT ENDPOINTS ==========

    /**
     * Get all addresses for the current customer (AJAX)
     */
    @GetMapping("/addresses")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getAddresses(Authentication authentication) {
        try {
            Customer customer = getAuthenticatedCustomer(authentication);
            
            var addresses = customerAddressService.getAddressesByCustomerId(customer.getIdCustomer());
            
            List<Map<String, Object>> addressList = addresses.stream()
                    .map(addr -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", addr.getIdAddress());
                        map.put("label", addr.getLabel());
                        map.put("address", addr.getAddress());
                        map.put("reference", addr.getReference());
                        map.put("latitude", addr.getLatitude());
                        map.put("longitude", addr.getLongitude());
                        map.put("isDefault", addr.getIsDefault());
                        map.put("displayAddress", addr.getDisplayAddress());
                        return map;
                    })
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(addressList);
        } catch (Exception e) {
            log.error("Error fetching addresses", e);
            return ResponseEntity.badRequest().body(List.of());
        }
    }

    /**
     * Create a new address (AJAX)
     */
    @PostMapping("/addresses")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createAddress(
            @RequestBody Map<String, Object> addressData,
            Authentication authentication) {
        try {
            Customer customer = getAuthenticatedCustomer(authentication);
            
            String label = (String) addressData.get("label");
            String address = (String) addressData.get("address");
            String reference = (String) addressData.get("reference");
            Double latitude = ((Number) addressData.get("latitude")).doubleValue();
            Double longitude = ((Number) addressData.get("longitude")).doubleValue();
            boolean setAsDefault = Boolean.TRUE.equals(addressData.get("setAsDefault"));

            // Block saving an address that is outside the configured delivery range.
            SystemConfiguration cfg = systemConfigurationService.getConfiguration();
            if (cfg != null && cfg.hasDeliveryRangeRestriction() && !cfg.isWithinDeliveryRange(latitude, longitude)) {
                log.warn("Customer {} blocked from creating address — out of delivery range (lat={}, lng={})",
                        customer.getUsername(), latitude, longitude);
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "El servicio de entrega a domicilio no está disponible para su ubicación"
                ));
            }

            var newAddress = customerAddressService.createAddress(
                    customer.getIdCustomer(), label, address, reference, 
                    latitude, longitude, setAsDefault);
            
            log.info("Address created for customer {}: {}", customer.getUsername(), label);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Dirección guardada exitosamente",
                    "addressId", newAddress.getIdAddress()
            ));
        } catch (Exception e) {
            log.error("Error creating address", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error al guardar dirección: " + e.getMessage()
            ));
        }
    }

    /**
     * Update an existing address (AJAX)
     */
    @PutMapping("/addresses/{addressId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateAddress(
            @PathVariable Long addressId,
            @RequestBody Map<String, Object> addressData,
            Authentication authentication) {
        try {
            Customer customer = getAuthenticatedCustomer(authentication);
            
            String label = (String) addressData.get("label");
            String address = (String) addressData.get("address");
            String reference = (String) addressData.get("reference");
            Double latitude = ((Number) addressData.get("latitude")).doubleValue();
            Double longitude = ((Number) addressData.get("longitude")).doubleValue();
            boolean setAsDefault = Boolean.TRUE.equals(addressData.get("setAsDefault"));

            // Block updating an address that is outside the configured delivery range.
            SystemConfiguration cfg = systemConfigurationService.getConfiguration();
            if (cfg != null && cfg.hasDeliveryRangeRestriction() && !cfg.isWithinDeliveryRange(latitude, longitude)) {
                log.warn("Customer {} blocked from updating address {} — out of delivery range (lat={}, lng={})",
                        customer.getUsername(), addressId, latitude, longitude);
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "El servicio de entrega a domicilio no está disponible para su ubicación"
                ));
            }

            customerAddressService.updateAddress(
                    addressId, customer.getIdCustomer(), label, address, 
                    reference, latitude, longitude, setAsDefault);
            
            log.info("Address {} updated for customer {}", addressId, customer.getUsername());
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Dirección actualizada exitosamente"
            ));
        } catch (Exception e) {
            log.error("Error updating address", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error al actualizar dirección: " + e.getMessage()
            ));
        }
    }

    /**
     * Delete an address (AJAX)
     */
    @DeleteMapping("/addresses/{addressId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteAddress(
            @PathVariable Long addressId,
            Authentication authentication) {
        try {
            Customer customer = getAuthenticatedCustomer(authentication);
            
            boolean deleted = customerAddressService.deleteAddress(addressId, customer.getIdCustomer());
            
            if (deleted) {
                log.info("Address {} deleted for customer {}", addressId, customer.getUsername());
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Dirección eliminada exitosamente"
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "No se encontró la dirección"
                ));
            }
        } catch (Exception e) {
            log.error("Error deleting address", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error al eliminar dirección: " + e.getMessage()
            ));
        }
    }

    /**
     * Set an address as default (AJAX)
     */
    @PostMapping("/addresses/{addressId}/set-default")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setDefaultAddress(
            @PathVariable Long addressId,
            Authentication authentication) {
        try {
            Customer customer = getAuthenticatedCustomer(authentication);
            
            customerAddressService.setAsDefault(addressId, customer.getIdCustomer());
            
            log.info("Address {} set as default for customer {}", addressId, customer.getUsername());
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Dirección establecida como predeterminada"
            ));
        } catch (Exception e) {
            log.error("Error setting default address", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error: " + e.getMessage()
            ));
        }
    }

    /**
     * Delete a specific complement from an order item (AJAX)
     */
    @DeleteMapping("/orders/{orderId}/items/{itemId}/complements/{complementId}")
    @ResponseBody
    public Map<String, Object> deleteOrderItemComplement(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @PathVariable Long complementId,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Client {} deleting complement {} from item {} of order {}", 
                username, complementId, itemId, orderId);

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
            log.error("Complement not found: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
        } catch (IllegalStateException e) {
            log.error("Cannot delete complement: {}", e.getMessage());
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
        boolean requiresParrillero = Boolean.TRUE.equals(itemDetail.getItemMenu().getRequiresParrilleroPreparation());
        
        if (itemStatus == OrderStatus.PENDING) {
            return "âœ… Stock del complemento devuelto automáticamente (item pendiente)";
        } else if (!requiresPrep && !requiresBarista && !requiresParrillero && itemStatus == OrderStatus.READY) {
            return "âœ… Stock del complemento devuelto automáticamente (item listo sin preparación)";
        } else {
            return "âš ï¸ El stock del complemento NO fue devuelto (el item ya está en preparación o listo)";
        }
    }

    /**
     * Expand a combo item into parent + child OrderDetails.
     * Returns the list of OrderDetails (parent + children) for this combo.
     */
    @SuppressWarnings("unchecked")
    private List<OrderDetail> expandComboItem(ItemMenu comboItem, int quantity, String comments,
            Map<String, Object> itemData) {
        
        List<OrderDetail> result = new ArrayList<>();
        Long itemId = comboItem.getIdItemMenu();
        String comboGroupId = "combo_" + System.currentTimeMillis() + "_" + itemId;
        
        // Get combo children
        List<ItemMenuComboItem> comboChildren = itemMenuComboItemRepository
                .findByComboMenuIdItemMenuOrderByDisplayOrderAsc(itemId);
        
        if (comboChildren.isEmpty()) {
            throw new IllegalArgumentException("El combo '" + comboItem.getName() + "' no tiene items configurados.");
        }
        
        // 1. Create PARENT OrderDetail (holds the combo price)
        // Handle promotion for parent
        Long promotionId = null;
        Object promotionIdObj = itemData.get("promotionId");
        if (promotionIdObj != null && !promotionIdObj.toString().isEmpty()) {
            try {
                promotionId = Long.valueOf(promotionIdObj.toString());
            } catch (NumberFormatException e) {
                log.warn("Invalid promotion ID for combo: {}", promotionIdObj);
            }
        }
        
        BigDecimal unitPrice = comboItem.getPrice();
        BigDecimal promotionAppliedPrice = null;
        BigDecimal calculatedSubtotal = null;
        
        if (promotionId != null) {
            Promotion promotion = promotionService.findById(promotionId).orElse(null);
            if (promotion != null && promotion.isValidNow()) {
                boolean appliesToItem = promotion.getItems().stream()
                        .anyMatch(pi -> pi.getIdItemMenu().equals(itemId));
                if (appliesToItem) {
                    BigDecimal discountedTotal = promotion.calculateDiscountedPrice(unitPrice, quantity);
                    promotionAppliedPrice = discountedTotal.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
                    calculatedSubtotal = discountedTotal.setScale(2, RoundingMode.HALF_UP);
                    log.info("Applied promotion '{}' to combo '{}'", promotion.getName(), comboItem.getName());
                }
            }
        }
        
        OrderDetail comboParent = OrderDetail.builder()
                .itemMenu(comboItem)
                .itemName(comboItem.getName())
                .quantity(quantity)
                .unitPrice(unitPrice)
                .promotionAppliedPrice(promotionAppliedPrice)
                .appliedPromotionId(promotionId)
                .comments(comments)
                // NOTE: itemStatus del combo parent lo asigna OrderServiceImpl (createInternal
                // detecta isComboParent y fuerza READY; addItems lo deja READY por no requerir prep).
                // .itemStatus(OrderStatus.READY)
                .comboGroupId(comboGroupId)
                .build();
        
        if (calculatedSubtotal != null) {
            comboParent.setSubtotal(calculatedSubtotal);
        } else {
            comboParent.calculateSubtotal();
        }
        result.add(comboParent);
        
        // Parse per-child complements from frontend
        List<Map<String, Object>> perChildComplements = null;
        List<Map<String, Object>> rawComplements = (List<Map<String, Object>>) itemData.get("complements");
        if (rawComplements != null && !rawComplements.isEmpty()) {
            // Check if this is per-child format (has childItemId key)
            if (rawComplements.get(0).containsKey("childItemId")) {
                perChildComplements = rawComplements;
            }
        }
        
        // 2. Create CHILD OrderDetails
        for (ItemMenuComboItem comboChild : comboChildren) {
            ItemMenu childItem = comboChild.getChildMenu();
            int childQty = comboChild.getQuantity() * quantity;
            
            // NOTE: itemStatus del combo child lo asigna autoritativamente OrderServiceImpl
            // (createInternal / addItemsToExistingOrder) según los flags chef/barista/parrillero del ItemMenu.
            // El cálculo previo aquí no incluía parrillero y dejaba items solo-parrillero en READY.
            OrderDetail childDetail = OrderDetail.builder()
                    .itemMenu(childItem)
                    .itemName(childItem.getName())
                    .quantity(childQty)
                    .unitPrice(BigDecimal.ZERO)
                    .comments(comments)
                    .comboGroupId(comboGroupId)
                    .build();
            childDetail.calculateSubtotal();
            
            // Locate this child's complements payload (may be null/empty if user skipped sauce selection)
            List<Map<String, Object>> childCompsForMinCheck = null;
            if (perChildComplements != null) {
                for (Map<String, Object> compEntry : perChildComplements) {
                    Long targetChildId = compEntry.get("childItemId") != null ?
                            Long.valueOf(compEntry.get("childItemId").toString()) : null;
                    if (targetChildId != null && targetChildId.equals(childItem.getIdItemMenu())) {
                        childCompsForMinCheck = (List<Map<String, Object>>) compEntry.get("complements");
                        break;
                    }
                }
            }
            
            // BACKEND VALIDATION: Always enforce minSauces for the child item, even if no complements were sent
            Integer childMinSaucesCheck = childItem.getMinSauces();
            if (childMinSaucesCheck != null && childMinSaucesCheck > 0) {
                int selectedSaucesForChild = 0;
                if (childCompsForMinCheck != null) {
                    for (Map<String, Object> compData : childCompsForMinCheck) {
                        if (compData.get("id") == null) continue;
                        Long cId = Long.valueOf(compData.get("id").toString());
                        Complement tempComp = complementRepository.findById(cId).orElse(null);
                        if (tempComp != null && Boolean.TRUE.equals(tempComp.getIsSauce())) {
                            selectedSaucesForChild++;
                        }
                    }
                }
                if (selectedSaucesForChild < childMinSaucesCheck) {
                    throw new IllegalArgumentException(
                        "El item '" + childItem.getName() + "' del combo '" + comboItem.getName() +
                        "' requiere al menos " + childMinSaucesCheck +
                        " salsa(s) o especialidad(es), pero se seleccionaron " + selectedSaucesForChild + ".");
                }
            }
            
            // Process per-child complements with full validation (matching OrderController logic)
            if (perChildComplements != null) {
                for (Map<String, Object> compEntry : perChildComplements) {
                    Long targetChildId = compEntry.get("childItemId") != null ?
                            Long.valueOf(compEntry.get("childItemId").toString()) : null;
                    if (targetChildId != null && targetChildId.equals(childItem.getIdItemMenu())) {
                        List<Map<String, Object>> childComps = (List<Map<String, Object>>) compEntry.get("complements");
                        if (childComps != null && !childComps.isEmpty()) {
                            // Validate maxSauces count
                            Integer maxSauces = childItem.getMaxSauces();
                            Integer minSauces = childItem.getMinSauces();
                            int selectedSaucesCount = 0;
                            for (Map<String, Object> compData : childComps) {
                                if (compData.get("id") == null) continue;
                                Long cId = Long.valueOf(compData.get("id").toString());
                                Complement tempComp = complementRepository.findById(cId).orElse(null);
                                if (tempComp != null && Boolean.TRUE.equals(tempComp.getIsSauce())) {
                                    selectedSaucesCount++;
                                }
                            }
                            if (maxSauces != null && maxSauces > 0 && selectedSaucesCount > maxSauces) {
                                throw new IllegalArgumentException(
                                    "El item '" + childItem.getName() + "' permite máximo " + maxSauces +
                                    " salsa(s), pero se seleccionaron " + selectedSaucesCount + ".");
                            }
                            if (minSauces != null && minSauces > 0 && selectedSaucesCount < minSauces) {
                                throw new IllegalArgumentException(
                                    "El item '" + childItem.getName() + "' requiere al menos " + minSauces +
                                    " salsa(s) o especialidad(es), pero se seleccionaron " + selectedSaucesCount + ".");
                            }
                            
                            List<OrderDetailComplement> orderDetailComplements = new ArrayList<>();
                            for (Map<String, Object> compData : childComps) {
                                if (compData.get("id") == null) continue;
                                Long complementId = Long.valueOf(compData.get("id").toString());
                                Integer compQuantity = compData.get("quantity") != null ?
                                        Integer.valueOf(compData.get("quantity").toString()) : 1;
                                
                                Complement complement = complementRepository.findById(complementId)
                                        .orElseThrow(() -> new IllegalArgumentException(
                                            "Complemento no encontrado: " + complementId));
                                
                                if (!Boolean.TRUE.equals(complement.getActive())) {
                                    throw new IllegalStateException(
                                        "El complemento '" + complement.getName() + "' está desactivado.");
                                }
                                if (!Boolean.TRUE.equals(complement.getAvailable())) {
                                    throw new IllegalStateException(
                                        "El complemento '" + complement.getName() + "' no está disponible (sin stock).");
                                }
                                
                                // Validate complement is associated to the child item
                                ItemMenuComplement itemMenuComplement = itemMenuComplementRepository
                                    .findByItemMenuIdItemMenuAndComplementIdComplement(
                                        childItem.getIdItemMenu(), complementId)
                                    .orElseThrow(() -> new IllegalArgumentException(
                                        "El complemento '" + complement.getName() + "' no está asociado al item '" + childItem.getName() + "'."));
                                
                                if (!Boolean.TRUE.equals(itemMenuComplement.getActive())) {
                                    throw new IllegalStateException(
                                        "El complemento '" + complement.getName() + "' no está activo para el item '" + childItem.getName() + "'.");
                                }
                                
                                // Validate quantity bounds
                                if (compQuantity < 1) {
                                    throw new IllegalArgumentException(
                                        "La cantidad del complemento '" + complement.getName() + "' debe ser al menos 1.");
                                }
                                if (compQuantity > itemMenuComplement.getMaxQuantity()) {
                                    throw new IllegalArgumentException(
                                        "La cantidad del complemento '" + complement.getName() + "' (" + compQuantity +
                                        ") excede el máximo permitido (" + itemMenuComplement.getMaxQuantity() +
                                        ") para el item '" + childItem.getName() + "'.");
                                }
                                
                                // Compute effective qty (sauce → per-serving × child qty; non-sauce → as-is)
                                // and persist that effective value as the real total.
                                int effectiveCompQty = Boolean.TRUE.equals(complement.getIsSauce())
                                    ? compQuantity * childQty
                                    : compQuantity;
                                if (!complement.hasEnoughStock(effectiveCompQty)) {
                                    throw new IllegalStateException(
                                        "Stock insuficiente para el complemento '" + complement.getName() + "'.");
                                }
                                
                                OrderDetailComplement odc = OrderDetailComplement.builder()
                                        .orderDetail(childDetail)
                                        .complement(complement)
                                        .complementName(complement.getName())
                                        .quantity(effectiveCompQty)
                                        .unitPrice(complement.getExtraPrice())
                                        .stockDeducted(false)
                                        .build();
                                odc.calculateSubtotal();
                                orderDetailComplements.add(odc);
                            }
                            if (!orderDetailComplements.isEmpty()) {
                                childDetail.setSelectedComplements(orderDetailComplements);
                            }
                        }
                    }
                }
            }
            
            result.add(childDetail);
            log.info("Combo '{}' - child '{}' x{} (status: {})", comboItem.getName(),
                    childItem.getName(), childQty, childDetail.getItemStatus());
        }
        
        log.info("Expanded combo '{}' into {} child items + 1 parent (groupId: {})",
                comboItem.getName(), comboChildren.size(), comboGroupId);
        return result;
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
}
