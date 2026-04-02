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
    private final TicketPdfService ticketPdfService;

    public WaiterPaymentController(
            @Qualifier("waiterOrderService") WaiterOrderServiceImpl waiterOrderService,
            SystemConfigurationService systemConfigurationService,
            OrderRepository orderRepository,
            EmployeeService employeeService,
            TicketPdfService ticketPdfService) {
        this.waiterOrderService = waiterOrderService;
        this.systemConfigurationService = systemConfigurationService;
        this.orderRepository = orderRepository;
        this.employeeService = employeeService;
        this.ticketPdfService = ticketPdfService;
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
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        String username = authentication.getName();
        log.debug("Waiter {} displaying payment form for order ID: {}", username, orderId);

        return waiterOrderService.findByIdWithDetails(orderId)
                .map(order -> {
                    // Validate that order is in DELIVERED status
                    if (order.getStatus() != OrderStatus.DELIVERED) {
                        redirectAttributes.addFlashAttribute("errorMessage", 
                            "Solo se pueden pagar órdenes con estado ENTREGADO. Estado actual: " + order.getStatus().getDisplayName());
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

            // Find the order
            Order order = waiterOrderService.findByIdWithDetails(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada"));

            // Validate that order is NOT DELIVERY - waiters cannot collect delivery payments
            if (order.getOrderType() == OrderType.DELIVERY) {
                throw new IllegalStateException("Los meseros no pueden cobrar pedidos de entrega a domicilio. Por favor, dirija al repartidor o cajero.");
            }

            // Validate that order is in DELIVERED status
            if (order.getStatus() != OrderStatus.DELIVERED) {
                throw new IllegalStateException("Solo se pueden pagar órdenes con estado ENTREGADO. Estado actual: " + order.getStatus().getDisplayName());
            }

            // Get system configuration to validate payment method
            SystemConfiguration config = systemConfigurationService.getConfiguration();
            if (!config.isPaymentMethodEnabled(paymentMethod)) {
                throw new IllegalStateException("El método de pago seleccionado no está habilitado: " + paymentMethod.getDisplayName());
            }

            // Validate tip is not negative
            if (tip == null) {
                tip = BigDecimal.ZERO;
            }
            if (tip.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("La propina no puede ser negativa");
            }

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
