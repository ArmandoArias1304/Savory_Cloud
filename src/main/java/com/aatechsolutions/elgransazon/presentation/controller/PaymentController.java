package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.*;
import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
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

    // Constructor manual para inyectar adminOrderService específicamente
    public PaymentController(
            @Qualifier("adminOrderService") OrderService orderService,
            SystemConfigurationService systemConfigurationService,
            OrderRepository orderRepository,
            EmployeeService employeeService,
            FacturamaService facturamaService) {
        this.orderService = orderService;
        this.systemConfigurationService = systemConfigurationService;
        this.orderRepository = orderRepository;
        this.employeeService = employeeService;
        this.facturamaService = facturamaService;
    }

    /**
     * Show payment form for an order
     * Only DELIVERED orders can be paid
     */
    @GetMapping("/form/{orderId}")
    public String showPaymentForm(
            @PathVariable Long orderId,
            Model model,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {
        
        log.debug("Displaying payment form for order ID: {}", orderId);
        
        // Check if user is a waiter
        boolean isWaiter = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_WAITER"));

        return orderService.findByIdWithDetails(orderId)
                .map(order -> {
                    // Validate that order is in DELIVERED status
                    if (order.getStatus() != OrderStatus.DELIVERED) {
                        redirectAttributes.addFlashAttribute("errorMessage", 
                            "Solo se pueden pagar órdenes con estado ENTREGADO. Estado actual: " + order.getStatus().getDisplayName());
                        return "redirect:/admin/orders";
                    }

                    // Get system configuration
                    SystemConfiguration config = systemConfigurationService.getConfiguration();
                    
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

                    model.addAttribute("order", order);
                    model.addAttribute("enabledPaymentMethods", enabledPaymentMethods);
                    model.addAttribute("enabledPaymentMethodNames", enabledPaymentMethodNames);
                    
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

        // Waiters cannot apply order-level discount; force to zero regardless of submitted value.
        if (isWaiter) {
            orderDiscount = BigDecimal.ZERO;
        }

        try {
            // Find the order
            Order order = orderService.findByIdWithDetails(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada"));

            // Validate that order is in DELIVERED status
            if (order.getStatus() != OrderStatus.DELIVERED) {
                throw new IllegalStateException("Solo se pueden pagar órdenes con estado ENTREGADO. Estado actual: " + order.getStatus().getDisplayName());
            }

            // Get system configuration to validate payment method
            SystemConfiguration config = systemConfigurationService.getConfiguration();
            
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

            // Validate order discount (descuento sobre el total de la orden, incluye IVA).
            if (orderDiscount == null) {
                orderDiscount = BigDecimal.ZERO;
            }
            if (orderDiscount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("El descuento sobre el total no puede ser negativo");
            }
            if (orderDiscount.compareTo(new BigDecimal("999999.99")) > 0) {
                throw new IllegalArgumentException("El descuento sobre el total no puede ser mayor a $999,999.99");
            }
            if (orderDiscount.scale() > 2) {
                throw new IllegalArgumentException("El descuento sobre el total solo permite hasta 2 decimales");
            }
            // Cap to current order total (gross, includes envío). Cannot exceed what client owes.
            BigDecimal currentTotal = order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO;
            if (orderDiscount.compareTo(currentTotal) > 0) {
                throw new IllegalArgumentException(
                        "El descuento (" + String.format("$%.2f", orderDiscount)
                                + ") no puede ser mayor al total de la orden ("
                                + String.format("$%.2f", currentTotal) + ")");
            }

            // Get current employee who is collecting the payment
            Employee currentEmployee = employeeService.findByUsername(username)
                    .orElseThrow(() -> new IllegalStateException("Empleado no encontrado"));

            // Apply order discount and recompute totals BEFORE setting tip/payment metadata.
            order.setOrderDiscount(orderDiscount);
            order.recalculateAmounts();

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
