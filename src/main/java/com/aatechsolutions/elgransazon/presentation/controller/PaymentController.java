package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.*;
import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import com.aatechsolutions.elgransazon.presentation.dto.SplitAccountDTO;
import com.aatechsolutions.elgransazon.presentation.dto.SplitItemDTO;
import com.aatechsolutions.elgransazon.util.OrderDiscountSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for Payment processing
 * Handles payment of delivered orders
 * Accessible by ADMIN, MANAGER, and WAITER roles
 */
@Controller
@RequestMapping("/admin/payments")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_WAITER')")
@Slf4j
public class PaymentController {

    private final OrderService orderService;
    private final SystemConfigurationService systemConfigurationService;
    private final OrderRepository orderRepository;
    private final EmployeeService employeeService;
    private final FacturamaService facturamaService;
    private final SplitPaymentService splitPaymentService;
    private final ObjectMapper objectMapper;

    // Constructor manual para inyectar adminOrderService específicamente
    public PaymentController(
            @Qualifier("adminOrderService") OrderService orderService,
            SystemConfigurationService systemConfigurationService,
            OrderRepository orderRepository,
            EmployeeService employeeService,
            FacturamaService facturamaService,
            SplitPaymentService splitPaymentService,
            ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.systemConfigurationService = systemConfigurationService;
        this.orderRepository = orderRepository;
        this.employeeService = employeeService;
        this.facturamaService = facturamaService;
        this.splitPaymentService = splitPaymentService;
        this.objectMapper = objectMapper;
    }

    /**
     * Show payment form for an order
     * Only DELIVERED orders can be paid
     */
    @GetMapping("/form/{orderId}")
    public String showPaymentForm(
            @PathVariable Long orderId,
            @RequestParam(value = "split", required = false) String split,
            Model model,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {
        
        log.debug("Displaying payment form for order ID: {}", orderId);
        
        // Check if user is a waiter
        boolean isWaiter = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_WAITER"));

        return orderService.findByIdWithDetails(orderId)
                .map(order -> {
                    // A departing-guest collection may charge the ENTREGADO items of an
                    // open order (some items still PENDING/READY). Full-order payment
                    // requires the whole order ENTREGADO.
                    boolean departureEligible = order.canCollectDeparture();
                    if (order.getStatus() != OrderStatus.DELIVERED && !departureEligible) {
                        redirectAttributes.addFlashAttribute("errorMessage", 
                            "Solo se pueden pagar órdenes con estado ENTREGADO o cobrar por persona los ítems ya entregados. Estado actual: " + order.getStatus().getDisplayName());
                        return "redirect:/admin/orders";
                    }
                    if (order.getStatus() == OrderStatus.DELIVERED && !order.isReadyToCharge()) {
                        redirectAttributes.addFlashAttribute("errorMessage",
                            "Solo se cobran los ítems con estado ENTREGADO. Aún hay ítems sin entregar en este pedido.");
                        return "redirect:/admin/orders";
                    }

                    // Get system configuration
                    SystemConfiguration config = systemConfigurationService.getConfiguration();

                    // If waiter collection is disabled, waiters cannot collect payments
                    if (isWaiter && !Boolean.TRUE.equals(config.getWaiterDeliveryCanCollect())) {
                        redirectAttributes.addFlashAttribute("errorMessage", 
                            "El cobro por meseros está deshabilitado. Por favor, dirija al cliente a caja.");
                        return "redirect:/admin/orders";
                    }

                    // Get enabled payment methods based on order type
                    // For DELIVERY orders, use delivery payment methods
                    // For other orders (DINE_IN, TAKEOUT), use restaurant payment methods
                    Map<PaymentMethodType, Boolean> paymentMethodsMap = order.getOrderType() == OrderType.DELIVERY 
                        ? config.getDeliveryPaymentMethods() 
                        : config.getPaymentMethods();
                    List<PaymentMethodType> enabledPaymentMethods = paymentMethodsMap.entrySet().stream()
                        .filter(Map.Entry::getValue)
                        .map(Map.Entry::getKey)
                        .filter(method -> {
                            // Filter specifically for waiters - they cannot see CASH or TRANSFER options
                            if (isWaiter) {
                                return method != PaymentMethodType.CASH && method != PaymentMethodType.TRANSFER;
                            }
                            return true;
                        })
                        .collect(Collectors.toList());

                    // Check if there are enabled payment methods available for this user
                    if (enabledPaymentMethods.isEmpty()) {
                        redirectAttributes.addFlashAttribute("errorMessage", 
                             isWaiter ? "No tiene permisos para procesar los métodos de pago habilitados." : "No hay métodos de pago habilitados en la configuración del sistema");
                        return "redirect:/admin/orders";
                    }

                    // Create list of enabled payment method names for Thymeleaf validation
                    List<String> enabledPaymentMethodNames = enabledPaymentMethods.stream()
                        .map(PaymentMethodType::name)
                        .collect(Collectors.toList());

                    // Split editor semantics: "departure" (cobro de la persona que
                    // se va) while items are still pending — only ENTREGADO items
                    // offered and the order stays open. A fully delivered order can
                    // always be settled per person (assign whole items); when it
                    // already has partial charges the editor auto-opens in
                    // "Por persona" over the remaining units (regular payment is
                    // no longer possible on such an order).
                    boolean departureHint = "items".equalsIgnoreCase(split);
                    boolean departure = order.canCollectDeparture()
                            && (departureHint || order.hasUndeliveredItems());
                    model.addAttribute("splitDeparture", departure);
                    // Mirrors the zeroCashTips flag passed to SplitPaymentService
                    // (admin/manager always drop cash tips) so the split cards hide
                    // the tip picker for a cash-paying account.
                    model.addAttribute("splitZeroCashTips", true);
                    // Parts-equal was removed (SAT): a product cannot be divided,
                    // so the split editor is per-person (whole items) only.
                    model.addAttribute("splitAllowEqual", false);
                    model.addAttribute("splitAutoEnable",
                            order.hasPartialCollections());
                    model.addAttribute("nextPersonNumber", order.nextPersonNumber());
                    model.addAttribute("order", order);
                    model.addAttribute("enabledPaymentMethods", enabledPaymentMethods);
                    model.addAttribute("enabledPaymentMethodNames", enabledPaymentMethodNames);
                    model.addAttribute("currentRole", isWaiter ? "waiter" : "admin");
                    
                    return "admin/payments/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Orden no encontrada");
                    return "redirect:/admin/orders";
                });
    }

    /**
     * Process payment for an order
     */
    @PostMapping("/process/{orderId}")
    public String processPayment(
            @PathVariable Long orderId,
            @RequestParam PaymentMethodType paymentMethod,
            @RequestParam(required = false, defaultValue = "0") BigDecimal tip,
            @RequestParam(required = false, defaultValue = "0") BigDecimal orderDiscount,
            @RequestParam(required = false) String discountType,
            @RequestParam(required = false, defaultValue = "0") BigDecimal orderDiscountPercent,
            @RequestParam(required = false) String splitEnabled,
            @RequestParam(required = false) String splitMode,
            @RequestParam(required = false) String splitAccounts,
            @RequestParam(required = false) String splitDeparture,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes,
            jakarta.servlet.http.HttpSession session) {
        
        String username = authentication.getName();
        log.info("Processing payment for order ID: {} by user: {}", orderId, username);
        log.info("Payment method: {}, Tip: {}, Order discount: {}", paymentMethod, tip, orderDiscount);

        // Check if user is a waiter
        boolean isWaiter = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_WAITER"));

        // Waiters never capture a discount, but a locked (percentage) order must
        // still be settled with its fixed percentage — handled by the resolver.
        boolean allowDiscount = !isWaiter;

        try {
            // Find the order
            Order order = orderService.findByIdWithDetails(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada"));

            // Departure = the order still has items pending: charge only the
            // ENTREGADO items of the person(s) leaving and keep the order open.
            // A fully delivered order opened via the $ button — even one with
            // prior partial charges — settles through the full split flow over
            // the remaining units.
            boolean departureEligible = order.canCollectDeparture()
                    && ("true".equalsIgnoreCase(splitDeparture) || order.hasUndeliveredItems());
            if (order.getStatus() != OrderStatus.DELIVERED && !departureEligible) {
                throw new IllegalStateException("Solo se pueden pagar órdenes con estado ENTREGADO o cobrar por persona los ítems ya entregados. Estado actual: " + order.getStatus().getDisplayName());
            }
            if (order.getStatus() == OrderStatus.DELIVERED && !order.isReadyToCharge()) {
                throw new IllegalStateException("Solo se cobran los ítems con estado ENTREGADO. Aún hay ítems sin entregar en este pedido.");
            }

            // Get system configuration to validate payment method
            SystemConfiguration config = systemConfigurationService.getConfiguration();

            // If waiter collection is disabled, waiters cannot collect payments
            if (isWaiter && !Boolean.TRUE.equals(config.getWaiterDeliveryCanCollect())) {
                throw new IllegalStateException("El cobro por meseros está deshabilitado. Por favor, dirija al cliente a caja.");
            }

            // ========== DEPARTING-GUEST / SPLIT BILL FLOW ==========
            if ("true".equalsIgnoreCase(splitEnabled)) {
                if (departureEligible) {
                    // Departing-guest flow: percentage only, and the captured percentage
                    // is locked on the order from this very first collection.
                    applyOrderDiscount(order, username, discountType, orderDiscount,
                            orderDiscountPercent, allowDiscount, true);
                    orderRepository.save(order);
                    return processDepartingGuestPayment(order, isWaiter, username, splitAccounts,
                            config, redirectAttributes);
                }
                // Full-order split requires every item already delivered.
                if (!order.isReadyToCharge()) {
                    throw new IllegalStateException("Solo se cobran los ítems con estado ENTREGADO. Aún hay ítems sin entregar en este pedido.");
                }
                // A split bill only accepts a percentage discount, and the captured
                // percentage is locked so every later collection reuses it exactly.
                applyOrderDiscount(order, username, discountType, orderDiscount,
                        orderDiscountPercent, allowDiscount, true);
                orderRepository.save(order);
                return processSplitPayment(order, isWaiter, username, splitMode, splitAccounts,
                        config, redirectAttributes);
            }

            // Regular (non-split) payment charges the whole ENTREGADO order; never
            // re-charge units already collected from departing guests.
            if (!order.isReadyToCharge()) {
                throw new IllegalStateException("Solo se cobran los ítems con estado ENTREGADO. Aún hay ítems sin entregar en este pedido.");
            }
            if (order.hasPartialCollections()) {
                // The remaining bill can still be charged globally: one account
                // takes every unit still owed and the order closes as PAID.
                // Per-person splitting stays available via "Dividir cuenta".
                return processGlobalRemainingPayment(order, isWaiter, username, paymentMethod, tip,
                        discountType, orderDiscount, orderDiscountPercent, config, redirectAttributes);
            }

            // Validate payment method based on order type
            boolean isPaymentMethodEnabled = order.getOrderType() == OrderType.DELIVERY 
                ? config.isDeliveryPaymentMethodEnabled(paymentMethod)
                : config.isPaymentMethodEnabled(paymentMethod);
            
            if (!isPaymentMethodEnabled) {
                // Stay on the same page showing the error message
                log.warn("Payment method {} is disabled for order type {}", paymentMethod.getDisplayName(), order.getOrderType());
                
                // Get enabled payment methods based on order type
                Map<PaymentMethodType, Boolean> paymentMethodsMap = order.getOrderType() == OrderType.DELIVERY 
                    ? config.getDeliveryPaymentMethods() 
                    : config.getPaymentMethods();
                List<PaymentMethodType> enabledPaymentMethods = paymentMethodsMap.entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .filter(method -> {
                        // Filter specifically for waiters - they cannot see CASH or TRANSFER options
                        if (isWaiter) {
                            return method != PaymentMethodType.CASH && method != PaymentMethodType.TRANSFER;
                        }
                        return true;
                    })
                    .collect(Collectors.toList());
                
                // Convert to strings for Thymeleaf
                List<String> enabledPaymentMethodNames = enabledPaymentMethods.stream()
                        .map(PaymentMethodType::name)
                        .collect(Collectors.toList());
                
                String orderTypeLabel = order.getOrderType() == OrderType.DELIVERY ? "entregas a domicilio" : "el restaurante";
                
                model.addAttribute("order", order);
                model.addAttribute("enabledPaymentMethods", enabledPaymentMethods);
                model.addAttribute("enabledPaymentMethodNames", enabledPaymentMethodNames);
                model.addAttribute("splitZeroCashTips", true);
                model.addAttribute("errorMessage", 
                    "El método de pago '" + paymentMethod.getDisplayName() + 
                    "' está deshabilitado para " + orderTypeLabel + ". Por favor seleccione otro método de pago.");
                
                return "admin/payments/form";
            }

            // Check restriction for waiters
            if (isWaiter && (paymentMethod == PaymentMethodType.CASH || paymentMethod == PaymentMethodType.TRANSFER)) {
                throw new IllegalStateException("Los meseros no pueden procesar pagos en " + paymentMethod.getDisplayName());
            }

            // Validate tip
            if (tip == null) {
                tip = BigDecimal.ZERO;
            }
            if (tip.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("La propina no puede ser negativa");
            }
            if (tip.compareTo(new BigDecimal("999999.99")) > 0) {
                throw new IllegalArgumentException("La propina no puede ser mayor a $999,999.99");
            }
            if (tip.scale() > 2) {
                throw new IllegalArgumentException("La propina solo permite hasta 2 decimales");
            }

            // Cash payments: the tip is given directly to the waiter in cash,
            // so no tip can be registered in the system for CASH payments.
            if (paymentMethod == PaymentMethodType.CASH) {
                tip = BigDecimal.ZERO;
            }

            // Validate + resolve the order discount (monto fijo o porcentaje) and
            // recompute totals BEFORE setting tip/payment metadata.
            applyOrderDiscount(order, username, discountType, orderDiscount,
                    orderDiscountPercent, allowDiscount, false);

            // Get current employee who is collecting the payment
            Employee currentEmployee = employeeService.findByUsername(username)
                    .orElseThrow(() -> new IllegalStateException("Empleado no encontrado"));

            // Set tip, payment method, and paidBy
            order.setTip(tip);
            order.setPaymentMethod(paymentMethod);
            order.setPaidBy(currentEmployee);
            order.setUpdatedBy(username);
            order.setUpdatedAt(java.time.LocalDateTime.now()); // Explicitly set updatedAt
            
            // Save order first with tip, payment method, and paidBy
            orderRepository.save(order);
            log.info("Order {} updated with tip: {}, payment method: {}, and paid by: {}", 
                     order.getOrderNumber(), tip, paymentMethod, currentEmployee.getFullName());

            // Change status to PAID
            // NOTE: The OrderService.changeStatus() method will automatically free the table
            // when status changes to PAID, so we don't need to do it manually here
            orderService.changeStatus(orderId, OrderStatus.PAID, username);

            log.info("Payment processed successfully for order: {}", order.getOrderNumber());
            
            // Reload order to get updated values
            order = orderService.findByIdWithDetails(orderId).orElse(order);
            final var paidOrder = order;

            // Generate autofactura key (if Facturama billing is configured for this company)
            try {
                facturamaService.getConfigForCurrentCompany()
                    .filter(com.aatechsolutions.elgransazon.domain.entity.FacturamaConfig::isReady)
                    .ifPresent(fc -> {
                        String autofacturaKey = java.util.UUID.randomUUID().toString();
                        jakarta.servlet.http.HttpServletRequest req =
                                ((org.springframework.web.context.request.ServletRequestAttributes)
                                org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes())
                                .getRequest();
                        String baseUrl = req.getScheme() + "://" + req.getServerName()
                                + (req.getServerPort() == 80 || req.getServerPort() == 443 ? "" : ":" + req.getServerPort());
                        String selfInvoiceUrl = baseUrl + "/autofactura/" + autofacturaKey;
                        paidOrder.setAutofacturaKey(autofacturaKey);
                        paidOrder.setSelfInvoiceUrl(selfInvoiceUrl);
                        orderRepository.save(paidOrder);
                        log.info("Autofactura key generated for order: {}", paidOrder.getOrderNumber());
                    });
            } catch (Exception ex) {
                log.warn("Autofactura key generation failed (non-blocking): {}", ex.getMessage());
            }

            redirectAttributes.addFlashAttribute("successMessage",
                    "Pago procesado exitosamente para el pedido " + order.getOrderNumber() + 
                    ". Total pagado: " + order.getFormattedTotalWithTip());
            redirectAttributes.addFlashAttribute("printTicketOrderId", orderId);
            
            return "redirect:/admin/orders";

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Validation error processing payment: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/payments/form/" + orderId;

        } catch (Exception e) {
            log.error("Error processing payment for order ID: " + orderId, e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al procesar el pago: " + e.getMessage());
            return "redirect:/admin/payments/form/" + orderId;
        }
    }

    /**
     * Validates (monto fijo $ / porcentaje %) and applies the order discount,
     * then recomputes the order totals. Locked orders keep their fixed
     * percentage; split bills and departing-guest collections only accept a
     * percentage and lock it from the very first collection, and so does any
     * charge on an order that already has partial collections.
     */
    private void applyOrderDiscount(Order order, String username, String discountType,
                                    BigDecimal orderDiscount, BigDecimal orderDiscountPercent,
                                    boolean allowDiscount, boolean percentageOnlyFlow) {
        OrderDiscountSupport.Resolved resolved = OrderDiscountSupport.resolve(
                order, discountType, orderDiscount, orderDiscountPercent, allowDiscount, percentageOnlyFlow);
        order.setOrderDiscountPercent(resolved.isPercentMode() ? resolved.getPercent() : null);
        if (resolved.isLock()) {
            order.setOrderDiscountLocked(true);
        }
        order.setOrderDiscount(resolved.getAmount());
        order.recalculateAmounts();
        order.setUpdatedBy(username);
        order.setUpdatedAt(java.time.LocalDateTime.now());
    }

    /**
     * Global settlement of the remaining bill after departing-guest charges:
     * one single account takes every unit still owed (the "charge the whole
     * table" flow) and the order closes as PAID. Per-person splitting stays
     * available by enabling "Dividir cuenta" in the form.
     */
    private String processGlobalRemainingPayment(Order order, boolean isWaiter, String username,
                                                 PaymentMethodType paymentMethod, BigDecimal tip,
                                                 String discountType, BigDecimal orderDiscount,
                                                 BigDecimal orderDiscountPercent,
                                                 SystemConfiguration config,
                                                 RedirectAttributes redirectAttributes) {
        // Settlement after partial charges: the discount applies only to what is
        // still owed (never retroactively to already-collected units).
        applyOrderDiscount(order, username, discountType, orderDiscount,
                orderDiscountPercent, !isWaiter, false);
        orderRepository.save(order);

        List<SplitItemDTO> items = new ArrayList<>();
        for (OrderDetail d : order.getOrderDetails()) {
            if (d.isComboChild() || d.getItemStatus() == OrderStatus.CANCELLED) {
                continue;
            }
            BigDecimal remaining = d.getRemainingQuantity();
            if (remaining != null && remaining.compareTo(BigDecimal.ZERO) > 0) {
                SplitItemDTO item = new SplitItemDTO();
                item.setOrderDetailId(d.getIdOrderDetail());
                item.setQuantity(remaining);
                items.add(item);
            }
        }
        if (items.isEmpty()) {
            throw new IllegalStateException("No queda saldo pendiente por cobrar en este pedido");
        }
        SplitAccountDTO account = new SplitAccountDTO();
        account.setIndex(1);
        account.setPersonLabel("Cuenta");
        account.setPaymentMethod(paymentMethod.name());
        account.setTip(tip != null ? tip : BigDecimal.ZERO);
        account.setItems(items);
        try {
            String json = objectMapper.writeValueAsString(java.util.List.of(account));
            return processSplitPayment(order, isWaiter, username, "ITEMS", json,
                    config, redirectAttributes);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("No se pudo preparar el cobro global del pedido", e);
        }
    }

    /**
     * Split-bill flow: divide the DELIVERED order into N per-person accounts,
     * each with its own method/tip/items, pay them all and mark the order PAID.
     * The order discount was already validated and applied by the caller.
     */
    private String processSplitPayment(Order order, boolean isWaiter, String username,
                                       String splitMode, String splitAccounts,
                                       SystemConfiguration config, RedirectAttributes redirectAttributes) {
        // Parse the per-account plan
        List<SplitAccountDTO> accounts;
        try {
            accounts = objectMapper.readValue(splitAccounts, new TypeReference<List<SplitAccountDTO>>() {});
        } catch (Exception e) {
            log.warn("Invalid splitAccounts JSON: {}", e.getMessage());
            throw new IllegalArgumentException("Los datos de la división de cuenta son inválidos");
        }
        if (!"ITEMS".equalsIgnoreCase(splitMode)) {
            throw new IllegalArgumentException(
                    "El modo 'Partes iguales' ya no está disponible: un producto no puede dividirse. "
                            + "Use la división por persona (asignar ítems completos) o realice un solo pago.");
        }
        SplitMode mode = SplitMode.ITEMS;

        // Validate per-account payment methods (role rules + enabled config)
        for (SplitAccountDTO acc : accounts) {
            if (acc.getPaymentMethod() == null || acc.getPaymentMethod().isBlank()) {
                throw new IllegalArgumentException(acc.getDisplayLabel() + ": debe seleccionar un método de pago");
            }
            PaymentMethodType method;
            try {
                method = PaymentMethodType.valueOf(acc.getPaymentMethod());
            } catch (Exception e) {
                throw new IllegalArgumentException(acc.getDisplayLabel() + ": método de pago no válido");
            }
            boolean enabled = order.getOrderType() == OrderType.DELIVERY
                    ? config.isDeliveryPaymentMethodEnabled(method)
                    : config.isPaymentMethodEnabled(method);
            if (!enabled) {
                throw new IllegalArgumentException("El método de pago '" + method.getDisplayName()
                        + "' de " + acc.getDisplayLabel() + " está deshabilitado. Por favor seleccione otro método de pago.");
            }
            if (isWaiter && (method == PaymentMethodType.CASH || method == PaymentMethodType.TRANSFER)) {
                throw new IllegalStateException("Los meseros no pueden procesar pagos en " + method.getDisplayName());
            }
        }

        // Get current employee who is collecting the payments
        Employee currentEmployee = employeeService.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Empleado no encontrado"));

        // The order discount was already validated and applied by the caller.
        order.setUpdatedBy(username);
        order.setUpdatedAt(java.time.LocalDateTime.now());
        orderRepository.save(order);

        // Build request base URL for autofactura self-invoice links
        jakarta.servlet.http.HttpServletRequest req =
                ((org.springframework.web.context.request.ServletRequestAttributes)
                org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes())
                .getRequest();
        String baseUrl = req.getScheme() + "://" + req.getServerName()
                + (req.getServerPort() == 80 || req.getServerPort() == 443 ? "" : ":" + req.getServerPort());

        // Create the per-person payments (each with its own folio, method, tip, autofactura key)
        List<Payment> payments = splitPaymentService.createSplitPayments(
                order, mode, accounts, currentEmployee, username, true, baseUrl);

        // Order-level metadata for backward compatibility: method of the first account,
        // tip = sum of ALL account tips of this order (previous departing-guest/split
        // charges included), collector.
        BigDecimal totalTips = order.getPayments().stream()
                .map(p -> p.getTip() != null ? p.getTip() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setPaymentMethod(payments.get(0).getPaymentMethod());
        order.setTip(totalTips);
        order.setPaidBy(currentEmployee);
        order.setUpdatedBy(username);
        order.setUpdatedAt(java.time.LocalDateTime.now());
        orderRepository.save(order);

        // Change status to PAID: frees the table and updates stats exactly once
        orderService.changeStatus(order.getIdOrder(), OrderStatus.PAID, username);

        log.info("Split payment processed for order {}: {} accounts, collected by {}",
                order.getOrderNumber(), payments.size(), username);

        redirectAttributes.addFlashAttribute("successMessage",
                "Pago procesado exitosamente para el pedido " + order.getOrderNumber()
                        + ". Se cobr" + (payments.size() == 1 ? "ó " + payments.size() + " cuenta" : "aron " + payments.size() + " cuentas") + ".");
        redirectAttributes.addFlashAttribute("printTicketOrderId", order.getIdOrder());
        redirectAttributes.addFlashAttribute("printTicketOrderIds",
                payments.stream().map(Payment::getIdPayment).toList());

        return "redirect:/admin/orders";
    }

    /**
     * Departing-guest collection (admin/manager/waiter on admin screens): charge
     * ONLY the already ENTREGADO items of the person(s) leaving while the order
     * stays open — pending items are charged when the rest of the party settles.
     */
    private String processDepartingGuestPayment(Order order, boolean isWaiter, String username,
                                                String splitAccounts,
                                                SystemConfiguration config,
                                                RedirectAttributes redirectAttributes) {
        List<SplitAccountDTO> accounts;
        try {
            accounts = objectMapper.readValue(splitAccounts, new TypeReference<List<SplitAccountDTO>>() {});
        } catch (Exception e) {
            log.warn("Invalid splitAccounts JSON: {}", e.getMessage());
            throw new IllegalArgumentException("Los datos de la persona que se va son inválidos");
        }

        // Validate per-account payment methods (role rules + enabled config)
        for (SplitAccountDTO acc : accounts) {
            if (acc.getPaymentMethod() == null || acc.getPaymentMethod().isBlank()) {
                throw new IllegalArgumentException(acc.getDisplayLabel() + ": debe seleccionar un método de pago");
            }
            PaymentMethodType method;
            try {
                method = PaymentMethodType.valueOf(acc.getPaymentMethod());
            } catch (Exception e) {
                throw new IllegalArgumentException(acc.getDisplayLabel() + ": método de pago no válido");
            }
            // Departing-guest collections are dine-in/takeout (never delivery)
            if (!config.isPaymentMethodEnabled(method)) {
                throw new IllegalArgumentException("El método de pago '" + method.getDisplayName()
                        + "' de " + acc.getDisplayLabel() + " está deshabilitado. Por favor seleccione otro método de pago.");
            }
            if (isWaiter && (method == PaymentMethodType.CASH || method == PaymentMethodType.TRANSFER)) {
                throw new IllegalStateException("Los meseros no pueden procesar pagos en " + method.getDisplayName());
            }
        }

        // Get current employee who is collecting the payments
        Employee currentEmployee = employeeService.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Empleado no encontrado"));

        jakarta.servlet.http.HttpServletRequest req =
                ((org.springframework.web.context.request.ServletRequestAttributes)
                org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes())
                .getRequest();
        String baseUrl = req.getScheme() + "://" + req.getServerName()
                + (req.getServerPort() == 80 || req.getServerPort() == 443 ? "" : ":" + req.getServerPort());

        List<Payment> payments = splitPaymentService.collectDepartingGuests(
                order, accounts, currentEmployee, username, true, baseUrl);

        log.info("Departing-guest collection processed for order {}: {} cuenta(s), by {}",
                order.getOrderNumber(), payments.size(), username);

        // If nothing remains owed and nothing is still pending, the collection
        // settled the whole order: close it (frees the table / updates stats).
        boolean liquidated = !order.hasUndeliveredItems() && !order.hasChargeableDeliveredItems();
        String message;
        if (liquidated) {
            BigDecimal allTips = order.getPayments().stream()
                    .map(p -> p.getTip() != null ? p.getTip() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            order.setPaymentMethod(payments.get(0).getPaymentMethod());
            order.setTip(allTips);
            order.setPaidBy(currentEmployee);
            order.setUpdatedBy(username);
            order.setUpdatedAt(java.time.LocalDateTime.now());
            orderRepository.save(order);
            orderService.changeStatus(order.getIdOrder(), OrderStatus.PAID, username);
            message = "El pedido " + order.getOrderNumber()
                    + " quedó liquidado: la mesa fue liberada.";
        } else {
            // Accumulate the tip on the order as guests pay: the order stays
            // open, but the aggregate must reflect every departing-guest charge
            // so the final settlement (and tip views/reports) keep them all.
            BigDecimal allTips = order.getPayments().stream()
                    .map(p -> p.getTip() != null ? p.getTip() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            order.setTip(allTips);
            order.setUpdatedBy(username);
            order.setUpdatedAt(java.time.LocalDateTime.now());
            orderRepository.save(order);
            message = "Cobro procesado a la persona que se va del pedido " + order.getOrderNumber()
                    + ". El pedido continúa abierto con el resto de la cuenta.";
        }
        redirectAttributes.addFlashAttribute("successMessage", message);
        redirectAttributes.addFlashAttribute("printTicketOrderId", order.getIdOrder());
        redirectAttributes.addFlashAttribute("printTicketOrderIds",
                payments.stream().map(Payment::getIdPayment).toList());

        return "redirect:/admin/orders";
    }

    /**
     * Download PDF ticket from session
     */
    @GetMapping("/download-ticket")
    public org.springframework.http.ResponseEntity<byte[]> downloadTicket(
            jakarta.servlet.http.HttpSession session) {
        
        byte[] pdfBytes = (byte[]) session.getAttribute("ticketPdf");
        String filename = (String) session.getAttribute("ticketFilename");
        
        if (pdfBytes == null || filename == null) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        
        // Clear session attributes
        session.removeAttribute("ticketPdf");
        session.removeAttribute("ticketFilename");
        
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
        
        return new org.springframework.http.ResponseEntity<>(pdfBytes, headers, org.springframework.http.HttpStatus.OK);
    }
}
