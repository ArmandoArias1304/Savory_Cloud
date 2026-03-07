package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.OrderService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for Admin Kitchen Management
 * Allows administrators to monitor ALL kitchen orders and chef activities
 */
@Controller
@RequestMapping("/admin/kitchen")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER')")
public class AdminKitchenController {

    @Qualifier("adminOrderService")
    private final OrderService adminOrderService;
    private final EmployeeService employeeService;
    private final SystemConfigurationService configurationService;

    /**
     * Main kitchen view - shows all active orders (PENDING + IN_PREPARATION)
     * Admin can see ALL orders from ALL chefs
     */
    @GetMapping
    public String kitchenDashboard(Model model) {
        log.info("Admin accessing kitchen dashboard");
        
        // Get system configuration
        SystemConfiguration config = configurationService.getConfiguration();
        
        // Get ALL active orders (no chef filtering)
        List<Order> allActiveOrders = adminOrderService.findAll().stream()
            .filter(order -> 
                order.getStatus() == OrderStatus.PENDING || 
                order.getStatus() == OrderStatus.IN_PREPARATION
            )
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
            .toList();
        
        // Separate by status
        List<Order> pendingOrders = allActiveOrders.stream()
            .filter(o -> o.getStatus() == OrderStatus.PENDING)
            .toList();
        
        List<Order> inPreparationOrders = allActiveOrders.stream()
            .filter(o -> o.getStatus() == OrderStatus.IN_PREPARATION)
            .toList();
        
        // Count active chefs (chefs with orders in preparation)
        long activeChefsCount = allActiveOrders.stream()
            .filter(o -> o.getPreparedBy() != null)
            .map(o -> o.getPreparedBy().getIdEmpleado())
            .distinct()
            .count();
        
        // Count active baristas (baristas with orders in preparation)
        long activeBaristasCount = allActiveOrders.stream()
            .filter(o -> o.getPreparedByBarista() != null)
            .map(o -> o.getPreparedByBarista().getIdEmpleado())
            .distinct()
            .count();
        
        model.addAttribute("config", config);
        model.addAttribute("allOrders", allActiveOrders);
        model.addAttribute("pendingOrders", pendingOrders);
        model.addAttribute("inPreparationOrders", inPreparationOrders);
        model.addAttribute("pendingCount", pendingOrders.size());
        model.addAttribute("inPreparationCount", inPreparationOrders.size());
        model.addAttribute("activeChefsCount", activeChefsCount);
        model.addAttribute("activeBaristasCount", activeBaristasCount);
        
        log.info("Kitchen dashboard: {} pending, {} in preparation, {} active chefs, {} active baristas",
                 pendingOrders.size(), inPreparationOrders.size(), activeChefsCount, activeBaristasCount);
        
        return "admin/kitchen/index";
    }

    /**
     * All orders view - comprehensive history with filters
     * Shows orders in all states with advanced filtering
     */
    @GetMapping("/all-orders")
    public String allOrders(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long chefId,
            @RequestParam(required = false) Long baristaId,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        
        log.info("Admin accessing all orders view - filters: startDate={}, endDate={}, status={}, chefId={}, baristaId={}, page={}",
                 startDate, endDate, status, chefId, baristaId, page);
        
        // Get system configuration
        SystemConfiguration config = configurationService.getConfiguration();
        
        // Date range filtering (only if dates are provided)
        LocalDateTime dateTimeStart = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime dateTimeEnd = endDate != null ? endDate.atTime(LocalTime.MAX) : null;
        
        // Get all orders, filter by date only if dates are provided
        List<Order> filteredOrders = adminOrderService.findAll().stream()
            .filter(order -> {
                LocalDateTime orderDate = order.getCreatedAt();
                if (orderDate == null) return false;
                // Only apply date filters if dates are provided
                if (dateTimeStart != null && orderDate.isBefore(dateTimeStart)) return false;
                if (dateTimeEnd != null && orderDate.isAfter(dateTimeEnd)) return false;
                return true;
            })
            .filter(order -> status == null || order.getStatus() == status)
            .filter(order -> chefId == null || 
                           (order.getPreparedBy() != null && 
                            order.getPreparedBy().getIdEmpleado().equals(chefId)))
            .filter(order -> baristaId == null || 
                           (order.getPreparedByBarista() != null && 
                            order.getPreparedByBarista().getIdEmpleado().equals(baristaId)))
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
            .toList();
        
        // Pagination
        int pageSize = 15;
        int totalOrders = filteredOrders.size();
        int totalPages = (int) Math.ceil((double) totalOrders / pageSize);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        
        int startIndex = currentPage * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalOrders);
        
        List<Order> paginatedOrders = filteredOrders.subList(startIndex, endIndex);
        
        // Get all chefs for filter dropdown
        List<Employee> allChefs = employeeService.findAll().stream()
            .filter(emp -> emp.hasRole("ROLE_CHEF"))
            .sorted((e1, e2) -> {
                String name1 = (e1.getNombre() != null ? e1.getNombre() : "") + " " + 
                              (e1.getApellido() != null ? e1.getApellido() : "");
                String name2 = (e2.getNombre() != null ? e2.getNombre() : "") + " " + 
                              (e2.getApellido() != null ? e2.getApellido() : "");
                return name1.compareTo(name2);
            })
            .toList();
        
        // Get all baristas for filter dropdown
        List<Employee> allBaristas = employeeService.findAll().stream()
            .filter(emp -> emp.hasRole("ROLE_BARISTA"))
            .sorted((e1, e2) -> {
                String name1 = (e1.getNombre() != null ? e1.getNombre() : "") + " " + 
                              (e1.getApellido() != null ? e1.getApellido() : "");
                String name2 = (e2.getNombre() != null ? e2.getNombre() : "") + " " + 
                              (e2.getApellido() != null ? e2.getApellido() : "");
                return name1.compareTo(name2);
            })
            .toList();
        
        // Statistics by status
        Map<OrderStatus, Long> statusCounts = filteredOrders.stream()
            .collect(Collectors.groupingBy(Order::getStatus, Collectors.counting()));
        
        model.addAttribute("config", config);
        model.addAttribute("orders", paginatedOrders);
        model.addAttribute("allChefs", allChefs);
        model.addAttribute("allBaristas", allBaristas);
        model.addAttribute("statusCounts", statusCounts);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedChefId", chefId);
        model.addAttribute("selectedBaristaId", baristaId);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("orderStatuses", OrderStatus.values());
        
        return "admin/kitchen/all-orders";
    }
}
