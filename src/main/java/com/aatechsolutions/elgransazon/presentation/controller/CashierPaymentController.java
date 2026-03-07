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
 * Controller for Cashier Payment processing
 * Handles payment of delivered orders by cashier
 */
@Controller
@RequestMapping("/cashier/payments")
@PreAuthorize("hasRole('ROLE_CASHIER')")
@Slf4j
public class CashierPaymentController {

    private final CashierOrderServiceImpl cashierOrderService;
    private final SystemConfigurationService systemConfigurationService;
    private final OrderRepository orderRepository;
    private final EmployeeService employeeService;
    private final TicketPdfService ticketPdfService;

    public CashierPaymentController(
            @Qualifier("cashierOrderService") CashierOrderServiceImpl cashierOrderService,
            SystemConfigurationService systemConfigurationService,
            OrderRepository orderRepository,
            EmployeeService employeeService,
            TicketPdfService ticketPdfService) {
        this.cashierOrderService = cashierOrderService;
        this.systemConfigurationService = systemConfigurationService;
        this.orderRepository = orderRepository;
        this.employeeService = employeeService;
        this.ticketPdfService = ticketPdfService;
    }

    /**
     * Show payment form for an order
     * Only DELIVERED orders can be paid
     */
    @GetMapping("/form/{orderId}")
    public String showPaymentForm(
            @PathVariable Long orderId,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        String username = authentication.getName();
        log.debug("Cashier {} displaying payment form for order ID: {}", username, orderId);

        return cashierOrderService.findByIdWithDetails(orderId)
                .map(order -> {
                    // Validate that order is in DELIVERED status
                    if (order.getStatus() != OrderStatus.DELIVERED) {
                        redirectAttributes.addFlashAttribute("errorMessage", 
                            "Solo se pueden pagar órdenes con estado ENTREGADO. Estado actual: " + order.getStatus().getDisplayName());
                        return "redirect:/cashier/orders";
                    }

                    // Get system configuration
                    SystemConfiguration config = systemConfigurationService.getConfiguration();
                    
                    // Get enabled payment methods based on order type
                    Map<PaymentMethodType, Boolean> paymentMethods = order.getOrderType() == OrderType.DELIVERY 
                        ? config.getDeliveryPaymentMethods() 
                        : config.getPaymentMethods();
                    List<PaymentMethodType> enabledPaymentMethods = paymentMethods.entrySet().stream()
                        .filter(Map.Entry::getValue)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());

                    // Check if there are enabled payment methods
                    if (enabledPaymentMethods.isEmpty()) {
                        redirectAttributes.addFlashAttribute("errorMessage", 
                            "No hay métodos de pago habilitados en la configuración del sistema");
                        return "redirect:/cashier/orders";
                    }

                    // Create list of enabled payment method names for Thymeleaf validation
                    List<String> enabledPaymentMethodNames = enabledPaymentMethods.stream()
                        .map(PaymentMethodType::name)
                        .collect(Collectors.toList());

                    model.addAttribute("order", order);
                    model.addAttribute("enabledPaymentMethods", enabledPaymentMethods);
                    model.addAttribute("enabledPaymentMethodNames", enabledPaymentMethodNames);
                    model.addAttribute("currentPaymentMethod", order.getPaymentMethod() != null ? order.getPaymentMethod().name() : null);
                    model.addAttribute("currentRole", "cashier");
                    
                    return "cashier/payments/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Orden no encontrada");
                    return "redirect:/cashier/orders";
                });
    }

    /**
     * Process payment for an order
     * Cashier can collect ANY payment method (including CASH)
     */
    @PostMapping("/process/{orderId}")
    public String processPayment(
            @PathVariable Long orderId,
            @RequestParam PaymentMethodType paymentMethod,
            @RequestParam(required = false, defaultValue = "0") BigDecimal tip,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes,
            jakarta.servlet.http.HttpSession session) {
        
        String username = authentication.getName();
        log.info("Cashier {} processing payment for order ID: {}", username, orderId);
        log.info("Payment method: {}, Tip: {}", paymentMethod, tip);

        try {
            // Find the order
            Order order = cashierOrderService.findByIdWithDetails(orderId)
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
                    .collect(Collectors.toList());
                
                // Convert to strings for Thymeleaf
                List<String> enabledPaymentMethodNames = enabledPaymentMethods.stream()
                        .map(PaymentMethodType::name)
                        .collect(Collectors.toList());
                
                String orderTypeLabel = order.getOrderType() == OrderType.DELIVERY ? "entregas a domicilio" : "el restaurante";
                
                model.addAttribute("order", order);
                model.addAttribute("enabledPaymentMethods", enabledPaymentMethods);
                model.addAttribute("enabledPaymentMethodNames", enabledPaymentMethodNames);
                model.addAttribute("currentPaymentMethod", order.getPaymentMethod() != null ? order.getPaymentMethod().name() : null);
                model.addAttribute("currentRole", "cashier");
                model.addAttribute("errorMessage", 
                    "El método de pago '" + paymentMethod.getDisplayName() + 
                    "' está deshabilitado para " + orderTypeLabel + ". Por favor seleccione otro método de pago.");
                
                return "cashier/payments/form";
            }

            // Validate tip is not negative
            if (tip == null) {
                tip = BigDecimal.ZERO;
            }
            if (tip.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("La propina no puede ser negativa");
            }

            // Get current cashier employee
            Employee cashier = employeeService.findByUsername(username)
                    .orElseThrow(() -> new IllegalStateException("Cajero no encontrado"));

            // Set tip, payment method, and paidBy
            order.setTip(tip);
            order.setPaymentMethod(paymentMethod);
            order.setPaidBy(cashier);
            order.setUpdatedBy(username);
            order.setUpdatedAt(java.time.LocalDateTime.now());
            
            // Save order first with tip, payment method, and paidBy
            orderRepository.save(order);
            log.info("Order {} updated with tip: {}, payment method: {}, and paid by: {}", 
                     order.getOrderNumber(), tip, paymentMethod, cashier.getFullName());

            // Change status to PAID
            // This will automatically free the table if applicable
            cashierOrderService.changeStatus(orderId, OrderStatus.PAID, username);

            log.info("Payment processed successfully by cashier {} for order: {}", username, order.getOrderNumber());
            
            // Reload order to get updated values
            order = cashierOrderService.findByIdWithDetails(orderId).orElse(order);
            
            // Generate PDF ticket
            try {
                byte[] pdfBytes = ticketPdfService.generateTicket(order);
                session.setAttribute("ticketPdf", pdfBytes);
                session.setAttribute("ticketFilename", "ticket_" + order.getOrderNumber() + ".pdf");
                log.info("PDF ticket generated and stored in session");
            } catch (Exception e) {
                log.error("Error generating PDF ticket: {}", e.getMessage(), e);
                // Continue even if PDF generation fails
            }
            
            redirectAttributes.addFlashAttribute("successMessage",
                    "Pago procesado exitosamente para el pedido " + order.getOrderNumber() + 
                    ". Total pagado: " + order.getFormattedTotalWithTip());
            redirectAttributes.addFlashAttribute("downloadTicket", true);
            
            return "redirect:/cashier/orders";

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Validation error processing payment: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/cashier/payments/form/" + orderId;

        } catch (Exception e) {
            log.error("Error processing payment for order ID: " + orderId, e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al procesar el pago: " + e.getMessage());
            return "redirect:/cashier/payments/form/" + orderId;
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
