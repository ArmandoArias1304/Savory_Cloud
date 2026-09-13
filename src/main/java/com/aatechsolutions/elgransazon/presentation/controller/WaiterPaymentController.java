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
 * Controller for Waiter Payment processing
 * Handles payment of delivered orders by waiter (non-cash only)
 */
@Controller
@RequestMapping("/waiter/payments")
@PreAuthorize("hasRole('ROLE_WAITER')")
@Slf4j
public class WaiterPaymentController {

    private final WaiterOrderServiceImpl waiterOrderService;
    private final SystemConfigurationService systemConfigurationService;
    private final OrderRepository orderRepository;
    private final EmployeeService employeeService;
    private final FacturamaService facturamaService;
    private final SplitPaymentService splitPaymentService;
    private final ObjectMapper objectMapper;

    public WaiterPaymentController(
            @Qualifier("waiterOrderService") WaiterOrderServiceImpl waiterOrderService,
            SystemConfigurationService systemConfigurationService,
            OrderRepository orderRepository,
            EmployeeService employeeService,
            FacturamaService facturamaService,
            SplitPaymentService splitPaymentService,
            ObjectMapper objectMapper) {
        this.waiterOrderService = waiterOrderService;
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
     * Waiters can only collect CREDIT_CARD and DEBIT_CARD payments
     * Waiters CANNOT collect DELIVERY orders - those go to delivery person or cashier
     */
    @GetMapping("/form/{orderId}")
    public String showPaymentForm(
            @PathVariable Long orderId,
            @RequestParam(value = "split", required = false) String split,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        String username = authentication.getName();
        log.debug("Waiter {} displaying payment form for order ID: {}", username, orderId);

        return waiterOrderService.findByIdWithDetails(orderId)
                .map(order -> {
                    // A departing-guest collection may charge the ENTREGADO items of an
                    // open order (some items still PENDING/READY). Full-order payment
                    // requires the whole order ENTREGADO.
                    boolean departureEligible = order.canCollectDeparture();
                    if (order.getStatus() != OrderStatus.DELIVERED && !departureEligible) {
                        redirectAttributes.addFlashAttribute("errorMessage", 
                            "Solo se pueden pagar órdenes con estado ENTREGADO o cobrar por persona los ítems ya entregados. Estado actual: " + order.getStatus().getDisplayName());
                        return "redirect:/waiter/orders";
                    }

                    // Validate that order is NOT DELIVERY - waiters cannot collect delivery payments
                    if (order.getOrderType() == OrderType.DELIVERY) {
                        redirectAttributes.addFlashAttribute("errorMessage", 
                            "Los meseros no pueden cobrar pedidos de entrega a domicilio. Por favor, dirija al repartidor o cajero.");
                        return "redirect:/waiter/orders";
                    }

                    // Validate that payment method is allowed for waiters (only CREDIT_CARD and DEBIT_CARD)
                    // Waiters cannot collect CASH or TRANSFER payments
                    if (order.getPaymentMethod() == PaymentMethodType.CASH || 
                        order.getPaymentMethod() == PaymentMethodType.TRANSFER) {
                        redirectAttributes.addFlashAttribute("errorMessage", 
                            "Los meseros solo pueden cobrar pagos con tarjeta de crédito o débito. Por favor, dirija al cliente a caja.");
                        return "redirect:/waiter/orders";
                    }

                    // Get system configuration
                    SystemConfiguration config = systemConfigurationService.getConfiguration();

                    // Validate that waiter collection is enabled in system configuration
                    if (!Boolean.TRUE.equals(config.getWaiterDeliveryCanCollect())) {
                        redirectAttributes.addFlashAttribute("errorMessage", 
                            "El cobro por meseros está deshabilitado. Por favor, dirija al cliente a caja.");
                        return "redirect:/waiter/orders";
                    }

                    // Get enabled payment methods (only CREDIT_CARD and DEBIT_CARD for waiters)
                    Map<PaymentMethodType, Boolean> paymentMethods = config.getPaymentMethods();
                    List<PaymentMethodType> enabledPaymentMethods = paymentMethods.entrySet().stream()
                        .filter(Map.Entry::getValue)
                        .map(Map.Entry::getKey)
                        .filter(method -> method == PaymentMethodType.CREDIT_CARD || 
                                          method == PaymentMethodType.DEBIT_CARD) // Only cards
                        .collect(Collectors.toList());

                    // Check if there are enabled card payment methods
                    if (enabledPaymentMethods.isEmpty()) {
                        redirectAttributes.addFlashAttribute("errorMessage", 
                            "No hay métodos de pago con tarjeta habilitados en la configuración del sistema. Por favor, dirija al cliente a caja.");
                        return "redirect:/waiter/orders";
                    }

                    // Split editor semantics: "departure" (cobro de la persona que
                    // se va). Triggered whenever the user pressed the 👥 button
                    // (split=items hint) on a collectable order — whether it still
                    // has pending items or is already fully ENTREGADO. Only
                    // ENTREGADO items are offered, units may be left unassigned,
                    // and the order stays open. A fully delivered order opened via
                    // the $ button (no hint) settles per person (whole items only).
                    boolean departureHint = "items".equalsIgnoreCase(split);
                    boolean departure = order.canCollectDeparture()
                            && (departureHint || order.hasUndeliveredItems());
                    model.addAttribute("splitDeparture", departure);
                    // Mirrors the zeroCashTips flag passed to SplitPaymentService
                    // (waiters keep cash tips) so the split cards render the tip
                    // picker exactly as the backend will store it.
                    model.addAttribute("splitZeroCashTips", false);
                    // Parts-equal was removed (SAT): a product cannot be divided,
                    // so the split editor is per-person (whole items) only.
                    model.addAttribute("splitAllowEqual", false);
                    model.addAttribute("splitAutoEnable",
                            order.hasPartialCollections());
                    model.addAttribute("nextPersonNumber", order.nextPersonNumber());
                    model.addAttribute("order", order);
                    model.addAttribute("enabledPaymentMethods", enabledPaymentMethods);
                    model.addAttribute("currentPaymentMethod", order.getPaymentMethod().name());
                    model.addAttribute("currentRole", "waiter");
                    
                    return "waiter/payments/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Orden no encontrada");
                    return "redirect:/waiter/orders";
                });
    }

    /**
     * Process payment for an order
     * Waiter can only collect CREDIT_CARD and DEBIT_CARD payments
     * Waiter CANNOT collect DELIVERY orders
     */
    @PostMapping("/process/{orderId}")
    public String processPayment(
            @PathVariable Long orderId,
            @RequestParam PaymentMethodType paymentMethod,
            @RequestParam(required = false, defaultValue = "0") BigDecimal tip,
            @RequestParam(required = false) String splitEnabled,
            @RequestParam(required = false) String splitMode,
            @RequestParam(required = false) String splitAccounts,
            @RequestParam(required = false) String splitDeparture,
            Authentication authentication,
            RedirectAttributes redirectAttributes,
            jakarta.servlet.http.HttpSession session) {
        
        String username = authentication.getName();
        log.info("Waiter {} processing payment for order ID: {}", username, orderId);
        log.info("Payment method: {}, Tip: {}", paymentMethod, tip);

        try {
            // Validate that payment method is allowed for waiters (only CREDIT_CARD and DEBIT_CARD)
            if (paymentMethod != PaymentMethodType.CREDIT_CARD && 
                paymentMethod != PaymentMethodType.DEBIT_CARD) {
                throw new IllegalStateException("Los meseros solo pueden cobrar pagos con tarjeta de crédito o débito. Por favor, dirija al cliente a caja.");
            }

            // Validate that waiter collection is enabled in system configuration
            if (!Boolean.TRUE.equals(systemConfigurationService.getConfiguration().getWaiterDeliveryCanCollect())) {
                throw new IllegalStateException("El cobro por meseros está deshabilitado. Por favor, dirija al cliente a caja.");
            }

            // Find the order
            Order order = waiterOrderService.findByIdWithDetails(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada"));

            // Validate that order is NOT DELIVERY - waiters cannot collect delivery payments
            if (order.getOrderType() == OrderType.DELIVERY) {
                throw new IllegalStateException("Los meseros no pueden cobrar pedidos de entrega a domicilio. Por favor, dirija al repartidor o cajero.");
            }

            // Departure = the user pressed the 👥 button (form submits
            // splitDeparture=true) OR the order still has items pending: charge
            // only the ENTREGADO items of the person(s) leaving and keep the order
            // open. A fully delivered order opened via the $ button — even one
            // with prior partial charges — settles through the full split flow
            // over the remaining units.
            boolean departureEligible = order.canCollectDeparture()
                    && ("true".equalsIgnoreCase(splitDeparture) || order.hasUndeliveredItems());
            if (order.getStatus() != OrderStatus.DELIVERED && !departureEligible) {
                throw new IllegalStateException("Solo se pueden pagar órdenes con estado ENTREGADO o cobrar por persona los ítems ya entregados. Estado actual: " + order.getStatus().getDisplayName());
            }

            // Get system configuration to validate payment method
            SystemConfiguration config = systemConfigurationService.getConfiguration();
            if (!config.isPaymentMethodEnabled(paymentMethod)) {
                throw new IllegalStateException("El método de pago seleccionado no está habilitado: " + paymentMethod.getDisplayName());
            }

            // ========== DEPARTING-GUEST / SPLIT BILL FLOW ==========
            if ("true".equalsIgnoreCase(splitEnabled)) {
                if (departureEligible) {
                    // Waiters never capture a discount; a locked order keeps its fixed %.
                    applyOrderDiscount(order, username, true);
                    orderRepository.save(order);
                    return processDepartingGuestPayment(order, username, splitAccounts, config, redirectAttributes);
                }
                // Full-order split requires every item already delivered.
                if (!order.isReadyToCharge()) {
                    throw new IllegalStateException("Solo se cobran los ítems con estado ENTREGADO. Aún hay ítems sin entregar en este pedido.");
                }
                // A split bill only accepts a percentage discount, and the captured
                // percentage is locked so every later collection reuses it exactly.
                applyOrderDiscount(order, username, true);
                orderRepository.save(order);
                return processSplitPayment(order, username, splitMode, splitAccounts, config, redirectAttributes);
            }

            // Regular (non-split) payment always charges the whole ENTREGADO order; never
            // re-charge units already collected from departing guests.
            if (!order.isReadyToCharge()) {
                throw new IllegalStateException("Solo se cobran los ítems con estado ENTREGADO. Aún hay ítems sin entregar en este pedido.");
            }
            if (order.hasPartialCollections()) {
                // The remaining bill can still be charged globally: one account
                // takes every unit still owed and the order closes as PAID.
                // Per-person splitting stays available via "Dividir cuenta".
                return processGlobalRemainingPayment(order, username, paymentMethod, tip,
                        config, redirectAttributes);
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

            // Waiters never capture a discount; a locked order keeps its fixed %.
            applyOrderDiscount(order, username, false);

            // Get current waiter employee
            Employee waiter = employeeService.findByUsername(username)
                    .orElseThrow(() -> new IllegalStateException("Mesero no encontrado"));

            // Set tip, payment method, and paidBy
            order.setTip(tip);
            order.setPaymentMethod(paymentMethod);
            order.setPaidBy(waiter);
            order.setUpdatedBy(username);
            order.setUpdatedAt(java.time.LocalDateTime.now());
            
            // Save order first with tip, payment method, and paidBy
            orderRepository.save(order);
            log.info("Order {} updated with tip: {}, payment method: {}, and paid by: {}", 
                     order.getOrderNumber(), tip, paymentMethod, waiter.getFullName());

            // Change status to PAID
            // This will automatically free the table if applicable
            waiterOrderService.changeStatus(orderId, OrderStatus.PAID, username);

            log.info("Payment processed successfully by waiter {} for order: {}", username, order.getOrderNumber());
            
            // Reload order to get updated values
            order = waiterOrderService.findByIdWithDetails(orderId).orElse(order);
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
            
            return "redirect:/waiter/orders";

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Validation error processing payment: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/waiter/payments/form/" + orderId;

        } catch (Exception e) {
            log.error("Error processing payment for order ID: " + orderId, e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al procesar el pago: " + e.getMessage());
            return "redirect:/waiter/payments/form/" + orderId;
        }
    }

    /**
     * Waiters never capture a discount (allowDiscount=false), but a locked
     * percentage order must keep applying its fixed % on every collection.
     */
    private void applyOrderDiscount(Order order, String username, boolean percentageOnlyFlow) {
        OrderDiscountSupport.Resolved resolved = OrderDiscountSupport.resolve(
                order, null, BigDecimal.ZERO, BigDecimal.ZERO, false, percentageOnlyFlow);
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
    private String processGlobalRemainingPayment(Order order, String username,
                                                 PaymentMethodType paymentMethod, BigDecimal tip,
                                                 SystemConfiguration config,
                                                 RedirectAttributes redirectAttributes) {
        applyOrderDiscount(order, username, false);
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
            return processSplitPayment(order, username, "ITEMS", json, config, redirectAttributes);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("No se pudo preparar el cobro global del pedido", e);
        }
    }

    /**
     * Split-bill flow for waiters: divide the DELIVERED order into N per-person
     * accounts. Waiters can only collect card payments (CREDIT_CARD / DEBIT_CARD)
     * and cannot apply order-level discounts.
     */
    private String processSplitPayment(Order order, String username,
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

        // Validate per-account payment methods (cards only for waiters)
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
            if (method != PaymentMethodType.CREDIT_CARD && method != PaymentMethodType.DEBIT_CARD) {
                throw new IllegalStateException("Los meseros solo pueden cobrar pagos con tarjeta de crédito o débito. Por favor, dirija al cliente a caja.");
            }
            if (!config.isPaymentMethodEnabled(method)) {
                throw new IllegalStateException("El método de pago seleccionado para " + acc.getDisplayLabel() + " no está habilitado: " + method.getDisplayName());
            }
        }

        // Get current waiter employee
        Employee waiter = employeeService.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Mesero no encontrado"));

        // Build request base URL for autofactura self-invoice links
        jakarta.servlet.http.HttpServletRequest req =
                ((org.springframework.web.context.request.ServletRequestAttributes)
                org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes())
                .getRequest();
        String baseUrl = req.getScheme() + "://" + req.getServerName()
                + (req.getServerPort() == 80 || req.getServerPort() == 443 ? "" : ":" + req.getServerPort());

        // Create the per-person payments (each with its own folio, method, tip, autofactura key)
        // zeroCashTips is irrelevant for waiters (cards only), but keep the flag semantics consistent.
        List<Payment> payments = splitPaymentService.createSplitPayments(
                order, mode, accounts, waiter, username, false, baseUrl);

        // Order-level metadata for backward compatibility: tip = sum of ALL
        // account tips of this order (earlier departing-guest/split charges
        // included), so tip views/reports keep every tip collected.
        BigDecimal totalTips = order.getPayments().stream()
                .map(p -> p.getTip() != null ? p.getTip() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setPaymentMethod(payments.get(0).getPaymentMethod());
        order.setTip(totalTips);
        order.setPaidBy(waiter);
        order.setUpdatedBy(username);
        order.setUpdatedAt(java.time.LocalDateTime.now());
        orderRepository.save(order);

        // Change status to PAID: frees the table and updates stats exactly once
        waiterOrderService.changeStatus(order.getIdOrder(), OrderStatus.PAID, username);

        log.info("Split payment processed for order {}: {} accounts, collected by {}",
                order.getOrderNumber(), payments.size(), username);

        redirectAttributes.addFlashAttribute("successMessage",
                "Pago procesado exitosamente para el pedido " + order.getOrderNumber()
                        + ". Se cobr" + (payments.size() == 1 ? "ó " + payments.size() + " cuenta" : "aron " + payments.size() + " cuentas") + ".");
        redirectAttributes.addFlashAttribute("printTicketOrderId", order.getIdOrder());
        redirectAttributes.addFlashAttribute("printTicketOrderIds",
                payments.stream().map(Payment::getIdPayment).toList());

        return "redirect:/waiter/orders";
    }

    /**
     * Departing-guest collection (waiters): charge ONLY the already ENTREGADO
     * items of the person(s) leaving while the order stays open — pending items
     * are charged when the rest of the party settles.
     */
    private String processDepartingGuestPayment(Order order, String username,
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

        // Waiters can only collect card payments (CREDIT_CARD / DEBIT_CARD)
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
            if (method != PaymentMethodType.CREDIT_CARD && method != PaymentMethodType.DEBIT_CARD) {
                throw new IllegalStateException("Los meseros solo pueden cobrar pagos con tarjeta de crédito o débito. Por favor, dirija al cliente a caja.");
            }
            if (!config.isPaymentMethodEnabled(method)) {
                throw new IllegalStateException("El método de pago seleccionado para " + acc.getDisplayLabel() + " no está habilitado: " + method.getDisplayName());
            }
        }

        Employee waiter = employeeService.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Mesero no encontrado"));

        jakarta.servlet.http.HttpServletRequest req =
                ((org.springframework.web.context.request.ServletRequestAttributes)
                org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes())
                .getRequest();
        String baseUrl = req.getScheme() + "://" + req.getServerName()
                + (req.getServerPort() == 80 || req.getServerPort() == 443 ? "" : ":" + req.getServerPort());

        List<Payment> payments = splitPaymentService.collectDepartingGuests(
                order, accounts, waiter, username, false, baseUrl);

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
            order.setPaidBy(waiter);
            order.setUpdatedBy(username);
            order.setUpdatedAt(java.time.LocalDateTime.now());
            orderRepository.save(order);
            waiterOrderService.changeStatus(order.getIdOrder(), OrderStatus.PAID, username);
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

        return "redirect:/waiter/orders";
    }

    /**
     * Download PDF ticket from session
     */
    @GetMapping("/download-ticket")
    public org.springframework.http.ResponseEntity<byte[]> downloadTicket(
            jakarta.servlet.http.HttpSession session) {
        
        byte[] pdfBytes = (byte[]) session.getAttribute("ticketPdf");
        String filename = (String) session.getAttribute("ticketFilename");
        
        if (pdfBytes == null) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        
        // Clear session attributes
        session.removeAttribute("ticketPdf");
        session.removeAttribute("ticketFilename");
        
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"" + (filename != null ? filename : "ticket.pdf") + "\"")
                .body(pdfBytes);
    }
}
