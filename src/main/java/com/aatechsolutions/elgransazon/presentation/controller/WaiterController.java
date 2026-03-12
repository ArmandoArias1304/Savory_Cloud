package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.BusinessHoursService;
import com.aatechsolutions.elgransazon.application.service.CategoryService;
import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.ItemMenuService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.domain.entity.Category;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for Waiter role views
 * Handles all waiter-related pages and operations
 */
@Controller
@RequestMapping("/waiter")
@RequiredArgsConstructor
@Slf4j
public class WaiterController {

    private final EmployeeService employeeService;
    private final OrderRepository orderRepository;
    private final ItemMenuService itemMenuService;
    private final CategoryService categoryService;
    private final SystemConfigurationService configurationService;
    private final BusinessHoursService businessHoursService;
    private final DateTimeService dateTimeService;

    /**
     * Display waiter dashboard
     *
     * @param authentication Spring Security authentication object
     * @param model Spring MVC model
     * @return waiter dashboard view
     */
    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        String username = authentication.getName();
        log.info("Waiter {} accessed dashboard", username);
        
        // Get system configuration
        SystemConfiguration config = configurationService.getConfiguration();
        
        // Check if restaurant is currently open
        boolean isRestaurantOpen = businessHoursService.isOpenNow();
        
        model.addAttribute("config", config);
        model.addAttribute("username", username);
        model.addAttribute("role", "Mesero");
        model.addAttribute("isRestaurantOpen", isRestaurantOpen);
        log.debug("Restaurant is currently: {}", isRestaurantOpen ? "open" : "closed");
        
        return "waiter/dashboard";
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
        log.info("User {} accessed profile", username);
        
        try {
            Employee employee = employeeService.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
            
            model.addAttribute("employee", employee);
            return "waiter/profile/view";
            
        } catch (Exception e) {
            log.error("Error loading profile for user {}: {}", username, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cargar el perfil");
            return "redirect:/waiter/dashboard";
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
        log.info("User {} accessed tips view", username);
        
        try {
            Employee employee = employeeService.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
            
            // Get all PAID orders for this employee
            List<Order> allPaidOrders = orderRepository.findByEmployeeId(employee.getIdEmpleado())
                    .stream()
                    .filter(order -> order.getStatus() == OrderStatus.PAID)
                    .toList();
            
            // Calculate total tips (all time)
            BigDecimal totalTips = allPaidOrders.stream()
                    .map(order -> order.getTip() != null ? order.getTip() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Get today's paid orders
            LocalDateTime startOfDay = dateTimeService.startOfDayUtc(dateTimeService.todayLocal());
            LocalDateTime endOfDay = dateTimeService.endOfDayUtc(dateTimeService.todayLocal());
            
            List<Order> todaysPaidOrders = allPaidOrders.stream()
                    .filter(order -> {
                        LocalDateTime createdAt = order.getCreatedAt();
                        return createdAt != null && 
                               !createdAt.isBefore(startOfDay) && 
                               !createdAt.isAfter(endOfDay);
                    })
                    .toList();
            
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
            
            return "waiter/tip/view";
            
        } catch (Exception e) {
            log.error("Error loading tips for user {}: {}", username, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cargar las propinas");
            return "redirect:/waiter/dashboard";
        }
    }

    /**
     * Display user reports with charts
     *
     * @param authentication Spring Security authentication object
     * @param model Spring MVC model
     * @param redirectAttributes Redirect attributes for error messages
     * @return reports view
     */
    @GetMapping("/reports/view")
    public String viewReports(Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        String username = authentication.getName();
        log.info("User {} accessed reports view", username);
        
        try {
            Employee employee = employeeService.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
            
            // Get all orders for this employee
            List<Order> allOrders = orderRepository.findByEmployeeId(employee.getIdEmpleado());
            
            // Filter paid orders
            List<Order> paidOrders = allOrders.stream()
                    .filter(order -> order.getStatus() == OrderStatus.PAID)
                    .toList();
            
            // Get today's date
            LocalDate today = dateTimeService.todayLocal();
            LocalDateTime startOfDay = dateTimeService.startOfDayUtc(today);
            LocalDateTime endOfDay = dateTimeService.endOfDayUtc(today);
            
            // Today's orders
            List<Order> todaysOrders = allOrders.stream()
                    .filter(order -> {
                        LocalDateTime createdAt = order.getCreatedAt();
                        return createdAt != null && 
                               !createdAt.isBefore(startOfDay) && 
                               !createdAt.isAfter(endOfDay);
                    })
                    .toList();
            
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
            Map<String, Long> last7DaysOrders = new HashMap<>();
            Map<String, BigDecimal> last7DaysRevenue = new HashMap<>();
            Map<String, BigDecimal> last7DaysTips = new HashMap<>();
            
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM");
            
            for (int i = 6; i >= 0; i--) {
                LocalDate date = today.minusDays(i);
                LocalDateTime dayStart = dateTimeService.startOfDayUtc(date);
                LocalDateTime dayEnd = dateTimeService.endOfDayUtc(date);
                
                String dateKey = date.format(formatter);
                
                List<Order> dayOrders = allOrders.stream()
                        .filter(order -> {
                            LocalDateTime createdAt = order.getCreatedAt();
                            return createdAt != null && 
                                   !createdAt.isBefore(dayStart) && 
                                   !createdAt.isAfter(dayEnd);
                        })
                        .toList();
                
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
            
            // Last 7 days (as JSON strings for JavaScript)
            model.addAttribute("last7DaysLabels", last7DaysOrders.keySet().stream().collect(Collectors.toList()));
            model.addAttribute("last7DaysOrdersData", last7DaysOrders.values().stream().collect(Collectors.toList()));
            model.addAttribute("last7DaysRevenueData", last7DaysRevenue.values().stream().collect(Collectors.toList()));
            model.addAttribute("last7DaysTipsData", last7DaysTips.values().stream().collect(Collectors.toList()));
            
            return "waiter/reports/view";
            
        } catch (Exception e) {
            log.error("Error loading reports for user {}: {}", username, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cargar los reportes");
            return "redirect:/waiter/dashboard";
        }
    }

    /**
     * Display menu items (visual only)
     *
     * @param model Spring MVC model
     * @param redirectAttributes Redirect attributes for error messages
     * @return menu view
     */
    @GetMapping("/menu/view")
    public String viewMenu(Model model, RedirectAttributes redirectAttributes) {
        log.info("Accessed visual menu view");
        
        try {
            // Get system configuration
            SystemConfiguration config = configurationService.getConfiguration();
            
            // Get all available menu items
            List<ItemMenu> availableItems = itemMenuService.findAvailableItems();
            
            // Group items by category
            Map<Long, List<ItemMenu>> itemsByCategory = availableItems.stream()
                    .collect(Collectors.groupingBy(item -> item.getCategory().getIdCategory()));
            
            // Get all active categories - ONLY those with active items
            List<Category> categories = categoryService.getAllActiveCategories().stream()
                    .filter(category -> itemsByCategory.containsKey(category.getIdCategory()))
                    .collect(Collectors.toList());
            
            model.addAttribute("config", config);
            model.addAttribute("categories", categories);
            model.addAttribute("itemsByCategory", itemsByCategory);
            
            return "waiter/menu/view";
            
        } catch (Exception e) {
            log.error("Error loading menu: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cargar el menú");
            return "redirect:/waiter/dashboard";
        }
    }

    /**
     * Display waiters ranking by sales
     *
     * @param model Spring MVC model
     * @param redirectAttributes Redirect attributes for error messages
     * @return ranking view
     */
    @GetMapping("/ranking/view")
    public String viewRanking(Model model, RedirectAttributes redirectAttributes) {
        log.info("Accessed waiters ranking view");
        
        try {
            // Get system configuration
            SystemConfiguration config = configurationService.getConfiguration();
            
            // Get today's date range
            LocalDate today = dateTimeService.todayLocal();
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime endOfDay = today.atTime(LocalTime.MAX);
            
            // Get all employees with WAITER role
            List<Employee> allWaiters = employeeService.findAll().stream()
                    .filter(emp -> emp.getRoles().stream()
                            .anyMatch(role -> role.getNombreRol().equals("ROLE_WAITER")))
                    .toList();
            
            // Calculate sales for each waiter (TODAY ONLY)
            List<Map<String, Object>> waiterSales = allWaiters.stream()
                    .map(waiter -> {
                        // Get all PAID orders for this waiter TODAY
                        List<Order> todayPaidOrders = orderRepository.findByEmployeeId(waiter.getIdEmpleado())
                                .stream()
                                .filter(order -> {
                                    LocalDateTime createdAt = order.getCreatedAt();
                                    return order.getStatus() == OrderStatus.PAID &&
                                           createdAt != null && 
                                           !createdAt.isBefore(startOfDay) && 
                                           !createdAt.isAfter(endOfDay);
                                })
                                .toList();
                        
                        // Calculate total sales TODAY
                        BigDecimal totalSales = todayPaidOrders.stream()
                                .map(order -> order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        
                        // Calculate total orders TODAY
                        int totalOrders = todayPaidOrders.size();
                        
                        // Get initials
                        String firstName = waiter.getNombre() != null ? waiter.getNombre() : "";
                        String lastName = waiter.getApellido() != null ? waiter.getApellido() : "";
                        String initials = "";
                        if (!firstName.isEmpty()) {
                            initials += firstName.charAt(0);
                        }
                        if (!lastName.isEmpty()) {
                            initials += lastName.charAt(0);
                        }
                        initials = initials.toUpperCase();
                        
                        Map<String, Object> waiterData = new HashMap<>();
                        waiterData.put("employee", waiter);
                        waiterData.put("totalSales", totalSales);
                        waiterData.put("totalOrders", totalOrders);
                        waiterData.put("initials", initials);
                        
                        return waiterData;
                    })
                    .filter(waiterData -> {
                        // Only include waiters with sales TODAY
                        BigDecimal sales = (BigDecimal) waiterData.get("totalSales");
                        return sales.compareTo(BigDecimal.ZERO) > 0;
                    })
                    .sorted((w1, w2) -> {
                        BigDecimal sales1 = (BigDecimal) w1.get("totalSales");
                        BigDecimal sales2 = (BigDecimal) w2.get("totalSales");
                        return sales2.compareTo(sales1); // Descending order
                    })
                    .limit(5) // Top 5 waiters
                    .toList();
            
            model.addAttribute("config", config);
            model.addAttribute("waiterRanking", waiterSales);
            model.addAttribute("rankingDate", today);
            
            return "waiter/ranking/view";
            
        } catch (Exception e) {
            log.error("Error loading ranking: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cargar el ranking");
            return "redirect:/waiter/dashboard";
        }
    }
}
