package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.OrderService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller for Delivery role views
 * Handles delivery-related pages for managing DELIVERY orders
 */
@Controller
@RequestMapping("/delivery")
@RequiredArgsConstructor
@Slf4j
public class DeliveryController {

    @Qualifier("deliveryOrderService")
    private final OrderService deliveryOrderService;
    private final EmployeeService employeeService;
    private final SystemConfigurationService configurationService;

    /**
     * Display delivery dashboard
     * 
     * @param authentication Spring Security authentication object
     * @param model Spring MVC model
     * @return delivery dashboard view
     */
    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        String username = authentication.getName();
        log.info("Delivery {} accessed dashboard", username);
        
        // Get system configuration
        SystemConfiguration config = configurationService.getConfiguration();
        
        model.addAttribute("config", config);
        model.addAttribute("username", username);
        model.addAttribute("role", "Delivery");
        
        return "delivery/dashboard";
    }

    /**
     * Display pending deliveries (READY orders available to accept)
     * Shows all READY DELIVERY orders that are available for any delivery person to accept
     * 
     * Also shows ON_THE_WAY and DELIVERED orders that the current delivery person has accepted
     * 
     * @param authentication Spring Security authentication object
     * @param model Spring MVC model
     * @return pending deliveries view
     */
    @GetMapping("/orders/pending")
    public String pendingDeliveries(Authentication authentication, Model model) {
        String username = authentication.getName();
        log.info("Delivery {} viewing pending deliveries", username);
        
        Employee currentEmployee = employeeService.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Empleado no encontrado"));
        
        // Get READY orders (available to all delivery persons)
        List<Order> readyOrders = deliveryOrderService.findByStatus(OrderStatus.READY);
        
        // Get ON_THE_WAY orders accepted by current delivery person
        List<Order> onTheWayOrders = deliveryOrderService.findByStatus(OrderStatus.ON_THE_WAY).stream()
                .filter(order -> order.getDeliveredBy() != null && 
                                order.getDeliveredBy().getIdEmpleado().equals(currentEmployee.getIdEmpleado()))
                .collect(Collectors.toList());
        
        // Get DELIVERED orders accepted by current delivery person (pending payment)
        List<Order> deliveredOrders = deliveryOrderService.findByStatus(OrderStatus.DELIVERED).stream()
                .filter(order -> order.getDeliveredBy() != null && 
                                order.getDeliveredBy().getIdEmpleado().equals(currentEmployee.getIdEmpleado()))
                .collect(Collectors.toList());
        
        // Combine all lists
        readyOrders.addAll(onTheWayOrders);
        readyOrders.addAll(deliveredOrders);
        
        // Get system configuration for delivery payment methods
        SystemConfiguration config = configurationService.getConfiguration();
        
        model.addAttribute("orders", readyOrders);
        model.addAttribute("currentEmployee", currentEmployee);
        model.addAttribute("config", config);
        
        return "delivery/orders/pending";
    }

    /**
     * Display completed deliveries
     * Shows PAID and CANCELLED orders delivered by the current delivery person
     * 
     * @param authentication Spring Security authentication object
     * @param model Spring MVC model
     * @return completed deliveries view
     */
    @GetMapping("/orders/completed")
    public String completedDeliveries(Authentication authentication, Model model) {
        String username = authentication.getName();
        log.info("Delivery {} viewing completed deliveries", username);
        
        Employee currentEmployee = employeeService.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Empleado no encontrado"));
        
        // Get PAID orders delivered by current delivery person
        List<Order> paidOrders = deliveryOrderService.findByStatus(OrderStatus.PAID).stream()
                .filter(order -> order.getDeliveredBy() != null && 
                                order.getDeliveredBy().getIdEmpleado().equals(currentEmployee.getIdEmpleado()))
                .collect(Collectors.toList());
        
        // Get CANCELLED orders that were assigned to current delivery person
        List<Order> cancelledOrders = deliveryOrderService.findByStatus(OrderStatus.CANCELLED).stream()
                .filter(order -> order.getDeliveredBy() != null && 
                                order.getDeliveredBy().getIdEmpleado().equals(currentEmployee.getIdEmpleado()))
                .collect(Collectors.toList());
        
        // Combine and sort by updated date (most recent first)
        List<Order> completedOrders = new java.util.ArrayList<>();
        completedOrders.addAll(paidOrders);
        completedOrders.addAll(cancelledOrders);
        completedOrders.sort((o1, o2) -> o2.getUpdatedAt().compareTo(o1.getUpdatedAt()));
        
        log.info("Found {} completed deliveries (PAID: {}, CANCELLED: {}) for delivery person {}", 
            completedOrders.size(), paidOrders.size(), cancelledOrders.size(), username);
        
        model.addAttribute("orders", completedOrders);
        model.addAttribute("currentEmployee", currentEmployee);
        model.addAttribute("username", username);
        model.addAttribute("role", "Delivery");
        
        return "delivery/orders/completed";
    }

    /**
     * Show payment form for a DELIVERED order
     * Shows payment methods enabled for delivery
     */
    @GetMapping("/payments/form/{orderId}")
    public String showPaymentForm(
            @PathVariable Long orderId,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        String username = authentication.getName();
        log.info("Delivery {} accessing payment form for order {}", username, orderId);
        
        Employee currentEmployee = employeeService.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Empleado no encontrado"));
        
        return deliveryOrderService.findByIdWithDetails(orderId)
                .map(order -> {
                    // Validate that order is in DELIVERED status
                    if (order.getStatus() != OrderStatus.DELIVERED) {
                        redirectAttributes.addFlashAttribute("errorMessage", "Solo se pueden cobrar pedidos entregados");
                        return "redirect:/delivery/orders/pending";
                    }
                    
                    // Validate that current delivery person delivered this order
                    if (order.getDeliveredBy() == null || 
                        !order.getDeliveredBy().getIdEmpleado().equals(currentEmployee.getIdEmpleado())) {
                        redirectAttributes.addFlashAttribute("errorMessage", "Solo puedes cobrar pedidos que tú entregaste");
                        return "redirect:/delivery/orders/pending";
                    }
                    
                    // Get system configuration
                    SystemConfiguration config = configurationService.getConfiguration();
                    
                    // Get enabled delivery payment methods for the view
                    List<PaymentMethodType> enabledDeliveryPaymentMethods = java.util.Arrays.stream(PaymentMethodType.values())
                            .filter(config::isDeliveryPaymentMethodEnabled)
                            .collect(Collectors.toList());
                    
                    // Convert to strings for Thymeleaf (to avoid static class access issues)
                    List<String> enabledPaymentMethodNames = enabledDeliveryPaymentMethods.stream()
                            .map(PaymentMethodType::name)
                            .collect(Collectors.toList());
                    
                    model.addAttribute("order", order);
                    model.addAttribute("config", config);
                    model.addAttribute("currentEmployee", currentEmployee);
                    model.addAttribute("enabledPaymentMethods", enabledDeliveryPaymentMethods);
                    model.addAttribute("enabledPaymentMethodNames", enabledPaymentMethodNames);
                    
                    return "delivery/payments/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Orden no encontrada");
                    return "redirect:/delivery/orders/pending";
                });
    }

    /**
     * Process payment for a DELIVERED order
     * Validates that the payment method is enabled for delivery
     */
    @PostMapping("/payments/process/{orderId}")
    public String processPayment(
            @PathVariable Long orderId,
            @RequestParam(required = false, defaultValue = "0") BigDecimal tip,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        String username = authentication.getName();
        log.info("Delivery {} processing payment for order {}", username, orderId);
        
        Employee currentEmployee = employeeService.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Empleado no encontrado"));
        
        try {
            Order order = deliveryOrderService.findByIdWithDetails(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada"));
            
            // Validate that order is in DELIVERED status
            if (order.getStatus() != OrderStatus.DELIVERED) {
                throw new IllegalStateException("Solo se pueden cobrar pedidos entregados");
            }
            
            // Validate that current delivery person delivered this order
            if (order.getDeliveredBy() == null || 
                !order.getDeliveredBy().getIdEmpleado().equals(currentEmployee.getIdEmpleado())) {
                throw new IllegalStateException("Solo puedes cobrar pedidos que tú entregaste");
            }

            // Get system configuration to validate payment method is enabled FOR DELIVERY
            SystemConfiguration config = configurationService.getConfiguration();
            PaymentMethodType paymentMethod = order.getPaymentMethod();
            if (!config.isDeliveryPaymentMethodEnabled(paymentMethod)) {
                // Stay on the same page showing the error message
                log.warn("Payment method {} is disabled for delivery", paymentMethod.getDisplayName());
                
                // Get enabled delivery payment methods for the view
                List<PaymentMethodType> enabledDeliveryPaymentMethods = java.util.Arrays.stream(PaymentMethodType.values())
                        .filter(config::isDeliveryPaymentMethodEnabled)
                        .collect(Collectors.toList());
                
                // Convert to strings for Thymeleaf
                List<String> enabledPaymentMethodNames = enabledDeliveryPaymentMethods.stream()
                        .map(PaymentMethodType::name)
                        .collect(Collectors.toList());
                
                model.addAttribute("order", order);
                model.addAttribute("config", config);
                model.addAttribute("currentEmployee", currentEmployee);
                model.addAttribute("enabledPaymentMethods", enabledDeliveryPaymentMethods);
                model.addAttribute("enabledPaymentMethodNames", enabledPaymentMethodNames);
                model.addAttribute("errorMessage", 
                    "El método de pago '" + paymentMethod.getDisplayName() + 
                    "' está deshabilitado para entregas a domicilio. Por favor contacte al administrador.");
                
                return "delivery/payments/form";
            }
            
            // Validate tip
            if (tip == null) {
                tip = BigDecimal.ZERO;
            }
            if (tip.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("La propina no puede ser negativa");
            }
            
            // Set tip and paidBy before changing status to PAID
            order.setTip(tip);
            order.setPaidBy(currentEmployee); // Set who collected the payment
            order.setUpdatedBy(username);
            order.setUpdatedAt(java.time.LocalDateTime.now());
            
            // Change status to PAID
            deliveryOrderService.changeStatus(orderId, OrderStatus.PAID, username);
            
            log.info("Payment processed successfully for order {} by delivery {}", order.getOrderNumber(), username);
            redirectAttributes.addFlashAttribute("successMessage", 
                "Pago cobrado exitosamente. Pedido #" + order.getOrderNumber() + " completado.");
            
            return "redirect:/delivery/orders/pending";
            
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Error processing payment: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/delivery/orders/pending";
        } catch (Exception e) {
            log.error("Unexpected error processing payment", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al procesar el pago");
            return "redirect:/delivery/orders/pending";
        }
    }

    /**
     * Display user tips summary
     *
     * @param authentication Spring Security authentication object
     * @param model Spring MVC model
     * @param redirectAttributes Redirect attributes for error messages
     * @return tips view
     */
    @GetMapping("/tip/view")
    public String viewTips(Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        String username = authentication.getName();
        log.info("Delivery {} accessed tips view", username);
        
        try {
            Employee employee = employeeService.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
            
            // Get all DELIVERY orders delivered by this employee with PAID status
            List<Order> allPaidOrders = deliveryOrderService.findAll().stream()
                    .filter(order -> order.getDeliveredBy() != null && 
                                    order.getDeliveredBy().getIdEmpleado().equals(employee.getIdEmpleado()) &&
                                    order.getStatus() == OrderStatus.PAID)
                    .collect(Collectors.toList());
            
            // Calculate total tips (all time)
            BigDecimal totalTips = allPaidOrders.stream()
                    .map(order -> order.getTip() != null ? order.getTip() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Get today's paid orders
            java.time.LocalDateTime startOfDay = java.time.LocalDate.now().atStartOfDay();
            java.time.LocalDateTime endOfDay = java.time.LocalDate.now().atTime(java.time.LocalTime.MAX);
            
            List<Order> todaysPaidOrders = allPaidOrders.stream()
                    .filter(order -> {
                        java.time.LocalDateTime createdAt = order.getCreatedAt();
                        return createdAt != null && 
                               !createdAt.isBefore(startOfDay) && 
                               !createdAt.isAfter(endOfDay);
                    })
                    .collect(Collectors.toList());
            
            // Calculate today's tips
            BigDecimal todayTips = todaysPaidOrders.stream()
                    .map(order -> order.getTip() != null ? order.getTip() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Additional statistics
            int totalOrders = allPaidOrders.size();
            int todayOrders = todaysPaidOrders.size();
            
            // Average tip per order
            BigDecimal averageTip = totalOrders > 0 
                    ? totalTips.divide(BigDecimal.valueOf(totalOrders), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            
            BigDecimal todayAverageTip = todayOrders > 0
                    ? todayTips.divide(BigDecimal.valueOf(todayOrders), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            
            model.addAttribute("employee", employee);
            model.addAttribute("totalTips", totalTips);
            model.addAttribute("todayTips", todayTips);
            model.addAttribute("totalOrders", totalOrders);
            model.addAttribute("todayOrders", todayOrders);
            model.addAttribute("averageTip", averageTip);
            model.addAttribute("todayAverageTip", todayAverageTip);
            
            return "delivery/tip/view";
            
        } catch (Exception e) {
            log.error("Error loading tips for delivery {}: {}", username, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cargar las propinas");
            return "redirect:/delivery/dashboard";
        }
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
        log.info("Delivery {} viewing profile", username);
        
        try {
            Employee employee = employeeService.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
            
            model.addAttribute("employee", employee);
            return "delivery/profile/view";
            
        } catch (Exception e) {
            log.error("Error loading profile for delivery {}: {}", username, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cargar el perfil");
            return "redirect:/delivery/dashboard";
        }
    }

    /**
     * Display delivery reports with charts
     *
     * @param authentication Spring Security authentication object
     * @param model Spring MVC model
     * @param redirectAttributes Redirect attributes for error messages
     * @return reports view
     */
    @GetMapping("/reports/view")
    public String viewReports(Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        String username = authentication.getName();
        log.info("Delivery {} viewing reports", username);
        
        try {
            Employee employee = employeeService.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
            
            // Get all DELIVERY orders delivered by this employee
            List<Order> allOrders = deliveryOrderService.findAll().stream()
                    .filter(order -> order.getDeliveredBy() != null && 
                                    order.getDeliveredBy().getIdEmpleado().equals(employee.getIdEmpleado()))
                    .collect(Collectors.toList());
            
            // Filter paid orders
            List<Order> paidOrders = allOrders.stream()
                    .filter(order -> order.getStatus() == OrderStatus.PAID)
                    .collect(Collectors.toList());
            
            // Get today's date
            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.LocalDateTime startOfDay = today.atStartOfDay();
            java.time.LocalDateTime endOfDay = today.atTime(java.time.LocalTime.MAX);
            
            // Today's orders
            List<Order> todaysOrders = allOrders.stream()
                    .filter(order -> {
                        java.time.LocalDateTime createdAt = order.getCreatedAt();
                        return createdAt != null && 
                               !createdAt.isBefore(startOfDay) && 
                               !createdAt.isAfter(endOfDay);
                    })
                    .collect(Collectors.toList());
            
            // Calculate statistics
            // Total orders by status
            long totalPending = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.PENDING).count();
            long totalInPreparation = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.IN_PREPARATION).count();
            long totalReady = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.READY).count();
            long totalDelivered = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count();
            long totalPaid = paidOrders.size();
            long totalCancelled = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();
            
            // Today's orders by status
            long todayPending = todaysOrders.stream().filter(o -> o.getStatus() == OrderStatus.PENDING).count();
            long todayInPreparation = todaysOrders.stream().filter(o -> o.getStatus() == OrderStatus.IN_PREPARATION).count();
            long todayReady = todaysOrders.stream().filter(o -> o.getStatus() == OrderStatus.READY).count();
            long todayDelivered = todaysOrders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count();
            long todayPaid = todaysOrders.stream().filter(o -> o.getStatus() == OrderStatus.PAID).count();
            long todayCancelled = todaysOrders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();
            
            // Calculate revenue and tips
            BigDecimal totalRevenue = paidOrders.stream()
                    .map(order -> order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal totalTips = paidOrders.stream()
                    .map(order -> order.getTip() != null ? order.getTip() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal todayRevenue = todaysOrders.stream()
                    .filter(o -> o.getStatus() == OrderStatus.PAID)
                    .map(order -> order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal todayTips = todaysOrders.stream()
                    .filter(o -> o.getStatus() == OrderStatus.PAID)
                    .map(order -> order.getTip() != null ? order.getTip() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Last 7 days statistics
            java.util.Map<String, Long> last7DaysOrders = new java.util.HashMap<>();
            java.util.Map<String, BigDecimal> last7DaysRevenue = new java.util.HashMap<>();
            java.util.Map<String, BigDecimal> last7DaysTips = new java.util.HashMap<>();
            
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM");
            
            for (int i = 6; i >= 0; i--) {
                java.time.LocalDate date = today.minusDays(i);
                java.time.LocalDateTime dayStart = date.atStartOfDay();
                java.time.LocalDateTime dayEnd = date.atTime(java.time.LocalTime.MAX);
                
                String dateKey = date.format(formatter);
                
                List<Order> dayOrders = allOrders.stream()
                        .filter(order -> {
                            java.time.LocalDateTime createdAt = order.getCreatedAt();
                            return createdAt != null && 
                                   !createdAt.isBefore(dayStart) && 
                                   !createdAt.isAfter(dayEnd);
                        })
                        .collect(Collectors.toList());
                
                long dayOrderCount = dayOrders.size();
                
                BigDecimal dayRevenue = dayOrders.stream()
                        .filter(o -> o.getStatus() == OrderStatus.PAID)
                        .map(order -> order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                BigDecimal dayTips = dayOrders.stream()
                        .filter(o -> o.getStatus() == OrderStatus.PAID)
                        .map(order -> order.getTip() != null ? order.getTip() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                last7DaysOrders.put(dateKey, dayOrderCount);
                last7DaysRevenue.put(dateKey, dayRevenue);
                last7DaysTips.put(dateKey, dayTips);
            }
            
            // Average per order
            BigDecimal averageRevenue = paidOrders.size() > 0 
                    ? totalRevenue.divide(BigDecimal.valueOf(paidOrders.size()), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            
            BigDecimal averageTip = paidOrders.size() > 0
                    ? totalTips.divide(BigDecimal.valueOf(paidOrders.size()), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            
            // Add to model
            model.addAttribute("employee", employee);
            
            // Totals
            model.addAttribute("totalOrders", allOrders.size());
            model.addAttribute("totalPending", totalPending);
            model.addAttribute("totalInPreparation", totalInPreparation);
            model.addAttribute("totalReady", totalReady);
            model.addAttribute("totalDelivered", totalDelivered);
            model.addAttribute("totalPaid", totalPaid);
            model.addAttribute("totalCancelled", totalCancelled);
            model.addAttribute("totalRevenue", totalRevenue);
            model.addAttribute("totalTips", totalTips);
            
            // Today
            model.addAttribute("todayOrders", todaysOrders.size());
            model.addAttribute("todayPending", todayPending);
            model.addAttribute("todayInPreparation", todayInPreparation);
            model.addAttribute("todayReady", todayReady);
            model.addAttribute("todayDelivered", todayDelivered);
            model.addAttribute("todayPaid", todayPaid);
            model.addAttribute("todayCancelled", todayCancelled);
            model.addAttribute("todayRevenue", todayRevenue);
            model.addAttribute("todayTips", todayTips);
            
            // Averages
            model.addAttribute("averageRevenue", averageRevenue);
            model.addAttribute("averageTip", averageTip);
            
            // Last 7 days (as lists for JavaScript)
            model.addAttribute("last7DaysLabels", new java.util.ArrayList<>(last7DaysOrders.keySet()));
            model.addAttribute("last7DaysOrdersData", new java.util.ArrayList<>(last7DaysOrders.values()));
            model.addAttribute("last7DaysRevenueData", new java.util.ArrayList<>(last7DaysRevenue.values()));
            model.addAttribute("last7DaysTipsData", new java.util.ArrayList<>(last7DaysTips.values()));
            
            return "delivery/reports/view";
            
        } catch (Exception e) {
            log.error("Error loading reports for delivery {}: {}", username, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cargar los reportes");
            return "redirect:/delivery/dashboard";
        }
    }
}
