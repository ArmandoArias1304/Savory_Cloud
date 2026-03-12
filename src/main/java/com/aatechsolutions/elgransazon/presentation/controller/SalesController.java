package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.OrderService;
import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.domain.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for Sales (Ventas) reports
 * Displays all PAID orders with filters and statistics
 * Accessible by ADMIN, MANAGER, and WAITER roles
 */
@Controller
@RequestMapping("/admin/sales")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_WAITER')")
@Slf4j
public class SalesController {

    private final OrderService orderService;
    private final EmployeeService employeeService;
    private final DateTimeService dateTimeService;

    // Constructor manual para inyectar adminOrderService específicamente
    public SalesController(
            @Qualifier("adminOrderService") OrderService orderService,
            EmployeeService employeeService,
            DateTimeService dateTimeService) {
        this.orderService = orderService;
        this.employeeService = employeeService;
        this.dateTimeService = dateTimeService;
    }

    /**
     * Show sales report with filters
     */
    @GetMapping
    public String listSales(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) PaymentMethodType paymentMethod,
            @RequestParam(defaultValue = "1") int page,
            Model model) {
        
        log.debug("Displaying sales report with filters - startDate: {}, endDate: {}, employee: {}, paymentMethod: {}", 
                  startDate, endDate, employeeId, paymentMethod);

        // Get all PAID orders
        List<Order> paidOrders = orderService.findByStatus(OrderStatus.PAID);

        // Apply date filter
        if (startDate != null && !startDate.isEmpty()) {
            paidOrders = filterByDateRange(paidOrders, startDate, endDate);
        }

        // Filter by employee (who collected payment)
        if (employeeId != null) {
            paidOrders = paidOrders.stream()
                .filter(order -> order.getPaidBy() != null && 
                               order.getPaidBy().getIdEmpleado().equals(employeeId))
                .collect(Collectors.toList());
        }

        // Filter by payment method
        if (paymentMethod != null) {
            paidOrders = paidOrders.stream()
                .filter(order -> order.getPaymentMethod() == paymentMethod)
                .collect(Collectors.toList());
        }

        // Sort by payment date (most recent first)
        paidOrders = paidOrders.stream()
            .sorted((o1, o2) -> {
                LocalDateTime date1 = o1.getUpdatedAt() != null ? o1.getUpdatedAt() : o1.getCreatedAt();
                LocalDateTime date2 = o2.getUpdatedAt() != null ? o2.getUpdatedAt() : o2.getCreatedAt();
                return date2.compareTo(date1);
            })
            .collect(Collectors.toList());

        // Calculate statistics
        BigDecimal totalSales = calculateTotalSales(paidOrders); // Total con propina
        BigDecimal totalSalesWithoutTip = calculateTotalSalesWithoutTip(paidOrders); // Total sin propina
        BigDecimal totalTips = calculateTotalTips(paidOrders); // Total de propinas
        long totalCount = paidOrders.size();

        // Get all employees for filter - Only employees that handle payments
        List<Employee> employees = employeeService.findAllEnabled().stream()
                .filter(e -> e.hasRole(Role.ADMIN) || 
                           e.hasRole(Role.WAITER) || 
                           e.hasRole(Role.CASHIER) || 
                           e.hasRole(Role.DELIVERY))
                .collect(Collectors.toList());

        // Get all payment methods
        PaymentMethodType[] paymentMethods = PaymentMethodType.values();

        // Server-side pagination
        int pageSize = 15;
        int totalElements = paidOrders.size();
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        if (totalPages == 0) totalPages = 1;
        page = Math.max(1, Math.min(page, totalPages));
        int startIdx = (page - 1) * pageSize;
        int endIdx = Math.min(startIdx + pageSize, totalElements);
        List<Order> pagedSales = totalElements > 0 ? paidOrders.subList(startIdx, endIdx) : paidOrders;

        model.addAttribute("sales", pagedSales);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalElements", totalElements);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("employees", employees);
        model.addAttribute("paymentMethods", paymentMethods);
        model.addAttribute("totalSales", totalSales);
        model.addAttribute("totalSalesWithoutTip", totalSalesWithoutTip);
        model.addAttribute("totalTips", totalTips);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("selectedEmployeeId", employeeId);
        model.addAttribute("selectedPaymentMethod", paymentMethod);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        return "admin/sales/list";
    }

    /**
     * Filter orders by date range
     */
    private List<Order> filterByDateRange(List<Order> orders, String startDate, String endDate) {
        LocalDateTime startDateTime = null;
        LocalDateTime endDateTime = null;

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            java.time.ZoneId companyZone = dateTimeService.getCompanyZone();
            java.time.ZoneId utcZone = java.time.ZoneId.of("UTC");

            if (startDate != null && !startDate.isEmpty()) {
                // Convertir inicio de día en TZ de empresa → UTC para comparar con valores almacenados
                startDateTime = LocalDate.parse(startDate, formatter)
                    .atStartOfDay(companyZone)
                    .withZoneSameInstant(utcZone)
                    .toLocalDateTime();
            }

            if (endDate != null && !endDate.isEmpty()) {
                // Convertir fin de día en TZ de empresa → UTC
                endDateTime = LocalDate.parse(endDate, formatter)
                    .atTime(23, 59, 59)
                    .atZone(companyZone)
                    .withZoneSameInstant(utcZone)
                    .toLocalDateTime();
            } else if (startDate != null && !startDate.isEmpty()) {
                // Si solo hay fecha de inicio, usar la misma como fin del día
                endDateTime = LocalDate.parse(startDate, formatter)
                    .atTime(23, 59, 59)
                    .atZone(companyZone)
                    .withZoneSameInstant(utcZone)
                    .toLocalDateTime();
            }
        } catch (Exception e) {
            log.error("Error parsing date range: {} - {}", startDate, endDate, e);
            return orders;
        }

        if (startDateTime == null && endDateTime == null) {
            return orders;
        }

        final LocalDateTime finalStartDateTime = startDateTime;
        final LocalDateTime finalEndDateTime = endDateTime;

        return orders.stream()
            .filter(order -> {
                LocalDateTime orderDate = order.getUpdatedAt() != null ? 
                    order.getUpdatedAt() : order.getCreatedAt();
                
                if (orderDate == null) return false;
                
                boolean afterStart = finalStartDateTime == null || !orderDate.isBefore(finalStartDateTime);
                boolean beforeEnd = finalEndDateTime == null || !orderDate.isAfter(finalEndDateTime);
                
                return afterStart && beforeEnd;
            })
            .collect(Collectors.toList());
    }

    /**
     * Calculate total sales amount (with tip)
     */
    private BigDecimal calculateTotalSales(List<Order> orders) {
        return orders.stream()
            .map(order -> order.getTotalWithTip() != null ? 
                         order.getTotalWithTip() : order.getTotal())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate total sales amount (without tip)
     */
    private BigDecimal calculateTotalSalesWithoutTip(List<Order> orders) {
        return orders.stream()
            .map(Order::getTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate total tips amount
     */
    private BigDecimal calculateTotalTips(List<Order> orders) {
        return orders.stream()
            .map(order -> order.getTip() != null ? order.getTip() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
