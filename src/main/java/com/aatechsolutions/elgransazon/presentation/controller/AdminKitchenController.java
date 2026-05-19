package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.DateTimeService;
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
    private final DateTimeService dateTimeService;

    /**
     * Main kitchen view - shows all active orders (PENDING + IN_PREPARATION)
     * Admin can see ALL orders from ALL chefs
     */
    @GetMapping
    public String kitchenDashboard(
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        log.info("Admin accessing kitchen dashboard, filter={}, page={}", filter, page);
        
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

        // Count active parrilleros (parrilleros with orders in preparation)
        long activeParrillerosCount = allActiveOrders.stream()
            .filter(o -> o.getPreparedByParrillero() != null)
            .map(o -> o.getPreparedByParrillero().getIdEmpleado())
            .distinct()
            .count();
        
        // Apply filter for pagination
        List<Order> displayOrders = switch (filter) {
            case "pending" -> pendingOrders;
            case "in-preparation" -> inPreparationOrders;
            default -> allActiveOrders;
        };
        
        // Pagination
        int pageSize = 15;
        int totalOrders = displayOrders.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalOrders / pageSize));
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int startIndex = currentPage * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalOrders);
        List<Order> paginatedOrders = displayOrders.subList(startIndex, endIndex);
        
        model.addAttribute("config", config);
        model.addAttribute("allOrders", paginatedOrders);
        model.addAttribute("filter", filter);
        model.addAttribute("pendingOrders", pendingOrders);
        model.addAttribute("inPreparationOrders", inPreparationOrders);
        model.addAttribute("pendingCount", pendingOrders.size());
        model.addAttribute("inPreparationCount", inPreparationOrders.size());
        model.addAttribute("activeChefsCount", activeChefsCount);
        model.addAttribute("activeBaristasCount", activeBaristasCount);
        model.addAttribute("activeParrillerosCount", activeParrillerosCount);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("pageSize", pageSize);
        
        log.info("Kitchen dashboard: {} pending, {} in preparation, {} active chefs, {} active baristas, page {}/{}",
                 pendingOrders.size(), inPreparationOrders.size(), activeChefsCount, activeBaristasCount, activeParrillerosCount, currentPage + 1, totalPages);
        
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
            @RequestParam(required = false) Long parrilleroId,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        
        log.info("Admin accessing all orders view - filters: startDate={}, endDate={}, status={}, chefId={}, baristaId={}, parrilleroId={}, page={}",
                 startDate, endDate, status, chefId, baristaId, parrilleroId, page);
        
        // Get system configuration
        SystemConfiguration config = configurationService.getConfiguration();
        
        // Date range filtering (only if dates are provided)
        LocalDateTime dateTimeStart = startDate != null ? dateTimeService.startOfDayUtc(startDate) : null;
        LocalDateTime dateTimeEnd = endDate != null ? dateTimeService.endOfDayUtc(endDate) : null;
        
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
            .filter(order -> parrilleroId == null || 
                           (order.getPreparedByParrillero() != null && 
                            order.getPreparedByParrillero().getIdEmpleado().equals(parrilleroId)))
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

        // Get all parrilleros for filter dropdown
        List<Employee> allParrilleros = employeeService.findAll().stream()
            .filter(emp -> emp.hasRole("ROLE_PARRILLERO"))
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
        model.addAttribute("allParrilleros", allParrilleros);
        model.addAttribute("statusCounts", statusCounts);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedChefId", chefId);
        model.addAttribute("selectedBaristaId", baristaId);
        model.addAttribute("selectedParrilleroId", parrilleroId);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("orderStatuses", OrderStatus.values());
        
        return "admin/kitchen/all-orders";
    }
}
