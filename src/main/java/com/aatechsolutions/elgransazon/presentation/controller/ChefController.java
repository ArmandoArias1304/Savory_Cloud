package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.CategoryService;
import com.aatechsolutions.elgransazon.application.service.DateTimeService;
import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.ItemMenuService;
import com.aatechsolutions.elgransazon.application.service.OrderService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.domain.entity.Category;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for Chef and Barista role views
 * Handles chef-related pages for managing kitchen orders and barista beverage preparation
 * CHEF and BARISTA roles share the same interface with role-specific filtering
 */
@Controller
@RequestMapping("/chef")
@RequiredArgsConstructor
@Slf4j
public class ChefController {

    @Qualifier("chefOrderService")
    private final OrderService chefOrderService;
    @Qualifier("baristaOrderService")
    private final OrderService baristaOrderService;
    private final EmployeeService employeeService;
    private final OrderRepository orderRepository;
    private final ItemMenuService itemMenuService;
    private final CategoryService categoryService;
    private final SystemConfigurationService configurationService;
    private final DateTimeService dateTimeService;

    /**
     * Detect if current user is a Barista
     */
    private boolean isBarista(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_BARISTA"));
    }

    /**
     * Get appropriate order service based on user role
     */
    private OrderService getOrderService(Authentication authentication) {
        return isBarista(authentication) ? baristaOrderService : chefOrderService;
    }

    /**
     * Get role display name based on user role
     */
    private String getRoleDisplayName(Authentication authentication) {
        return isBarista(authentication) ? "Barista" : "Chef";
    }

    /**
     * Display chef/barista dashboard
     * 
     * @param authentication Spring Security authentication object
     * @param model Spring MVC model
     * @return chef dashboard view (shared with barista)
     */
    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        String username = authentication.getName();
        String roleDisplay = getRoleDisplayName(authentication);
        log.info("{} {} accessed dashboard", roleDisplay, username);
        
        // Get system configuration
        SystemConfiguration config = configurationService.getConfiguration();
        
        model.addAttribute("config", config);
        model.addAttribute("username", username);
        model.addAttribute("role", roleDisplay);
        model.addAttribute("isBarista", isBarista(authentication));
        
        return "chef/dashboard";
    }

    /**
     * Display working orders (PENDING, IN_PREPARATION only)
     * Shows orders that chef/barista is currently working on
     * 
     * IMPORTANT FILTERING LOGIC:
     * - PENDING orders WITHOUT preparedBy/preparedByBarista (never accepted): Shown to ALL chefs/baristas
     * - PENDING orders WITH preparedBy/preparedByBarista (previously accepted): Shown ONLY to the chef/barista who accepted it originally
     * - IN_PREPARATION orders: Only shown to the chef/barista who accepted them
     * 
     * This prevents order "stealing" when new items are added to previously accepted orders
     * 
     * @param authentication Spring Security authentication object
     * @param model Spring MVC model
     * @return pending orders view
     */
    @GetMapping("/orders/pending")
    public String pendingOrders(Authentication authentication, Model model) {
        String username = authentication.getName();
        String roleDisplay = getRoleDisplayName(authentication);
        boolean isBaristaRole = isBarista(authentication);
        OrderService orderService = getOrderService(authentication);
        
        log.info("{} {} viewing working orders", roleDisplay, username);
        
        // Obtener órdenes en trabajo con filtrado inteligente
        List<Order> workingOrders = orderService.findAll().stream()
            .filter(order -> {
                // For Barista: check preparedByBarista, For Chef: check preparedBy
                Employee preparer = isBaristaRole ? order.getPreparedByBarista() : order.getPreparedBy();
                
                // CASO 1: Orden PENDING que NUNCA fue aceptada (preparer = null)
                // Estas órdenes son visibles para TODOS los chefs/baristas (disponibles para aceptar)
                if (order.getStatus() == OrderStatus.PENDING && preparer == null) {
                    log.debug("Order {} is PENDING and available for all {}", order.getOrderNumber(), roleDisplay);
                    return true;
                }
                
                // CASO 2: Orden PENDING que YA fue aceptada antes (preparer != null)
                // Esta orden SOLO es visible para el chef/barista que la aceptó originalmente
                // Esto ocurre cuando se agregan nuevos items a una orden que ya fue entregada
                if (order.getStatus() == OrderStatus.PENDING && preparer != null) {
                    boolean belongsToThisUser = preparer.getUsername().equalsIgnoreCase(username);
                    if (belongsToThisUser) {
                        log.debug("Order {} returned to PENDING but belongs to {} {}", 
                            order.getOrderNumber(), roleDisplay, username);
                    }
                    return belongsToThisUser;
                }
                
                // CASO 3: Orden IN_PREPARATION
                // Solo visible para el chef/barista que la aceptó
                if (order.getStatus() == OrderStatus.IN_PREPARATION) {
                    boolean belongsToThisUser = preparer != null && 
                           preparer.getUsername().equalsIgnoreCase(username);
                    return belongsToThisUser;
                }
                
                return false;
            })
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt())) // Más reciente primero
            .toList();
        
        log.info("{} {} has {} working orders ({} pending, {} in preparation)", 
                 roleDisplay, username, workingOrders.size(),
                 workingOrders.stream().filter(o -> o.getStatus() == OrderStatus.PENDING).count(),
                 workingOrders.stream().filter(o -> o.getStatus() == OrderStatus.IN_PREPARATION).count());
        
        // Sort order details by status for each order
        workingOrders.forEach(this::sortOrderDetailsByStatus);
        
        // Contar por estados
        long pendingCount = workingOrders.stream()
            .filter(o -> o.getStatus() == OrderStatus.PENDING)
            .count();
        long inPreparationCount = workingOrders.stream()
            .filter(o -> o.getStatus() == OrderStatus.IN_PREPARATION)
            .count();
        
        model.addAttribute("orders", workingOrders);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("inPreparationCount", inPreparationCount);
        model.addAttribute("username", username);
        model.addAttribute("role", roleDisplay);
        model.addAttribute("isBarista", isBaristaRole);
        model.addAttribute("currentChef", username);
        
        return "chef/orders/pending";
    }

    /**
     * Display completed orders history
     * Shows orders prepared by the current chef/barista that are no longer PENDING or IN_PREPARATION
     * (READY, DELIVERED, PAID, CANCELLED, etc.)
     * 
     * @param authentication Spring Security authentication object
     * @param model Spring MVC model
     * @return my orders view (history)
     */
    @GetMapping("/orders/my-orders")
    public String myOrders(
            @RequestParam(defaultValue = "1") int page,
            Authentication authentication, Model model) {
        String username = authentication.getName();
        String roleDisplay = getRoleDisplayName(authentication);
        boolean isBaristaRole = isBarista(authentication);
        // Usamos repository directamente porque el servicio filtra solo órdenes activas
        
        log.info("{} {} viewing completed orders history", roleDisplay, username);
        
        // MULTI-TENANT: Get current company to filter orders
        Company company = CompanyContext.requireCurrentCompany();
        
        // Obtener historial: Órdenes asignadas a este usuario que ya no tienen items pendientes
        // MULTI-TENANT: Filter by company
        List<Order> sourceOrders = orderRepository.findByCompany(company);
        
        List<Order> completedOrders = sourceOrders.stream()
            .filter(order -> {
                // 1. Verificar si el usuario es el preparador asignado
                Employee preparer = isBaristaRole ? order.getPreparedByBarista() : order.getPreparedBy();
                if (preparer == null || !preparer.getUsername().equalsIgnoreCase(username)) {
                    return false;
                }

                // BUG FIX: Si la orden está CANCELADA, mostrarla siempre en el historial
                // Esto permite ver órdenes que el chef/barista comenzó a preparar pero luego fueron canceladas
                if (order.getStatus() == OrderStatus.CANCELLED) {
                    return true;
                }

                // 2. Verificar si el usuario ya terminó su parte
                // (No debe tener items relevantes en estado PENDING o IN_PREPARATION)
                boolean hasPendingWork = order.getOrderDetails().stream().anyMatch(detail -> {
                    ItemMenu item = detail.getItemMenu();
                    boolean isRelevantItem = isBaristaRole 
                        ? Boolean.TRUE.equals(item.getRequiresBaristaPreparation())
                        : Boolean.TRUE.equals(item.getRequiresPreparation());
                    
                    if (!isRelevantItem) return false;

                    return detail.getItemStatus() == OrderStatus.PENDING || 
                           detail.getItemStatus() == OrderStatus.IN_PREPARATION;
                });

                // Si NO tiene trabajo pendiente, va al historial
                return !hasPendingWork;
            })
            .sorted((o1, o2) -> {
                LocalDateTime d1 = o1.getUpdatedAt() != null ? o1.getUpdatedAt() : o1.getCreatedAt();
                LocalDateTime d2 = o2.getUpdatedAt() != null ? o2.getUpdatedAt() : o2.getCreatedAt();
                return d2.compareTo(d1);
            })
            .toList();
        
        log.info("Found {} completed orders prepared by {} {}", completedOrders.size(), roleDisplay, username);
        
        // Sort order details by status for each order
        completedOrders.forEach(this::sortOrderDetailsByStatus);
        
        // Contar por estados
        long readyCount = completedOrders.stream()
            .filter(o -> o.getStatus() == OrderStatus.READY)
            .count();
        long deliveredCount = completedOrders.stream()
            .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
            .count();
        long paidCount = completedOrders.stream()
            .filter(o -> o.getStatus() == OrderStatus.PAID)
            .count();
        
        // Server-side pagination
        int pageSize = 15;
        int totalElements = completedOrders.size();
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        if (totalPages == 0) totalPages = 1;
        page = Math.max(1, Math.min(page, totalPages));
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalElements);
        List<Order> pagedOrders = totalElements > 0 ? completedOrders.subList(startIndex, endIndex) : completedOrders;
        
        model.addAttribute("orders", pagedOrders);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalElements", totalElements);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("readyCount", readyCount);
        model.addAttribute("deliveredCount", deliveredCount);
        model.addAttribute("paidCount", paidCount);
        model.addAttribute("username", username);
        model.addAttribute("role", roleDisplay);
        model.addAttribute("isBarista", isBaristaRole);
        
        return "chef/orders/my-orders";
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
        String roleDisplay = getRoleDisplayName(authentication);
        log.info("{} {} accessed profile", roleDisplay, username);
        
        try {
            Employee employee = employeeService.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
            
            model.addAttribute("employee", employee);
            model.addAttribute("isBarista", isBarista(authentication));
            return "chef/profile/view";
            
        } catch (Exception e) {
            log.error("Error loading profile for {} {}: {}", roleDisplay, username, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cargar el perfil");
            return "redirect:/chef/dashboard";
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
        String roleDisplay = getRoleDisplayName(authentication);
        boolean isBaristaRole = isBarista(authentication);
        
        log.info("{} {} accessed reports view", roleDisplay, username);
        
        try {
            Employee employee = employeeService.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
            
            // MULTI-TENANT: Filter orders by company
            Company company = CompanyContext.requireCurrentCompany();
            
            // Get all orders prepared by this chef/barista
            List<Order> allOrders = orderRepository.findByCompany(company).stream()
                    .filter(order -> {
                        Employee preparer = isBaristaRole ? order.getPreparedByBarista() : order.getPreparedBy();
                        return preparer != null && preparer.getUsername().equalsIgnoreCase(username);
                    })
                    .toList();
            
            // Get today's date
            LocalDate today = dateTimeService.todayLocal();
            LocalDateTime startOfDay = dateTimeService.startOfDayUtc(today);
            LocalDateTime endOfDay = dateTimeService.endOfDayUtc(today);
            
            // Today's orders prepared by this chef/barista
            List<Order> todaysOrders = allOrders.stream()
                    .filter(order -> {
                        LocalDateTime updatedAt = order.getUpdatedAt();
                        return updatedAt != null && 
                               !updatedAt.isBefore(startOfDay) && 
                               !updatedAt.isAfter(endOfDay);
                    })
                    .toList();
            
            // Count by status (All time)
            long totalPending = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.PENDING).count();
            long totalInPreparation = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.IN_PREPARATION).count();
            long totalReady = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.READY).count();
            long totalDelivered = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count();
            long totalPaid = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.PAID).count();
            long totalCancelled = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();
            
            // Count by status (Today)
            long todayPending = todaysOrders.stream().filter(o -> o.getStatus() == OrderStatus.PENDING).count();
            long todayInPreparation = todaysOrders.stream().filter(o -> o.getStatus() == OrderStatus.IN_PREPARATION).count();
            long todayReady = todaysOrders.stream().filter(o -> o.getStatus() == OrderStatus.READY).count();
            long todayDelivered = todaysOrders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count();
            long todayPaid = todaysOrders.stream().filter(o -> o.getStatus() == OrderStatus.PAID).count();
            long todayCancelled = todaysOrders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();
            
            // Last 7 days data
            List<String> last7DaysLabels = new ArrayList<>();
            List<Long> last7DaysOrdersData = new ArrayList<>();
            
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM");
            
            for (int i = 6; i >= 0; i--) {
                LocalDate date = today.minusDays(i);
                LocalDateTime dayStart = dateTimeService.startOfDayUtc(date);
                LocalDateTime dayEnd = dateTimeService.endOfDayUtc(date);
                
                long dayOrders = allOrders.stream()
                        .filter(order -> {
                            LocalDateTime updatedAt = order.getUpdatedAt();
                            return updatedAt != null && 
                                   !updatedAt.isBefore(dayStart) && 
                                   !updatedAt.isAfter(dayEnd);
                        })
                        .count();
                
                last7DaysLabels.add(date.format(formatter));
                last7DaysOrdersData.add(dayOrders);
            }
            
            model.addAttribute("employee", employee);
            model.addAttribute("totalOrders", allOrders.size());
            model.addAttribute("todayOrders", todaysOrders.size());
            model.addAttribute("isBarista", isBaristaRole);
            
            // All time counts
            model.addAttribute("totalPending", totalPending);
            model.addAttribute("totalInPreparation", totalInPreparation);
            model.addAttribute("totalReady", totalReady);
            model.addAttribute("totalDelivered", totalDelivered);
            model.addAttribute("totalPaid", totalPaid);
            model.addAttribute("totalCancelled", totalCancelled);
            
            // Today counts
            model.addAttribute("todayPending", todayPending);
            model.addAttribute("todayInPreparation", todayInPreparation);
            model.addAttribute("todayReady", todayReady);
            model.addAttribute("todayDelivered", todayDelivered);
            model.addAttribute("todayPaid", todayPaid);
            model.addAttribute("todayCancelled", todayCancelled);
            
            // Last 7 days
            model.addAttribute("last7DaysLabels", last7DaysLabels);
            model.addAttribute("last7DaysOrdersData", last7DaysOrdersData);
            
            return "chef/reports/view";
            
        } catch (Exception e) {
            log.error("Error loading reports for {} {}: {}", roleDisplay, username, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cargar los reportes");
            return "redirect:/chef/dashboard";
        }
    }

    /**
     * Display menu items (visual only)
     *
     * @param authentication Spring Security authentication object
     * @param model Spring MVC model
     * @param redirectAttributes Redirect attributes for error messages
     * @return menu view
     */
    @GetMapping("/menu/view")
    public String viewMenu(Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        String roleDisplay = getRoleDisplayName(authentication);
        log.info("{} accessed visual menu view", roleDisplay);
        
        try {
            // Get system configuration
            SystemConfiguration config = configurationService.getConfiguration();
            
            // Get all available items
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
            model.addAttribute("isBarista", isBarista(authentication));
            
            return "chef/menu/view";
            
        } catch (Exception e) {
            log.error("Error loading menu: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cargar el menú");
            return "redirect:/chef/dashboard";
        }
    }

    /**
     * Display chefs/baristas ranking by prepared orders
     *
     * @param authentication Spring Security authentication object
     * @param model Spring MVC model
     * @param redirectAttributes Redirect attributes for error messages
     * @return ranking view
     */
    @GetMapping("/ranking/view")
    public String viewRanking(Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        String roleDisplay = getRoleDisplayName(authentication);
        boolean isBaristaRole = isBarista(authentication);
        
        log.info("{} accessed ranking view", roleDisplay);
        
        try {
            // Get system configuration
            SystemConfiguration config = configurationService.getConfiguration();
            
            LocalDate today = dateTimeService.todayLocal();
            LocalDateTime startOfDay = dateTimeService.startOfDayUtc(today);
            LocalDateTime endOfDay = dateTimeService.endOfDayUtc(today);
            
            // Get all chefs or baristas based on role
            String targetRole = isBaristaRole ? "ROLE_BARISTA" : "ROLE_CHEF";
            List<Employee> employees = employeeService.findAll().stream()
                    .filter(emp -> emp.hasRole(targetRole))
                    .toList();
            
            // MULTI-TENANT: Filter orders by company
            Company company = CompanyContext.requireCurrentCompany();
            List<Order> companyOrders = orderRepository.findByCompany(company);
            
            // Count today's PAID orders prepared by each employee
            List<Map<String, Object>> employeeRanking = employees.stream()
                    .map(emp -> {
                        // Count PAID orders prepared by this employee today
                        long ordersCount = companyOrders.stream()
                                .filter(order -> order.getStatus() == OrderStatus.PAID)
                                .filter(order -> {
                                    Employee preparer = isBaristaRole ? order.getPreparedByBarista() : order.getPreparedBy();
                                    return preparer != null && preparer.getIdEmpleado().equals(emp.getIdEmpleado());
                                })
                                .filter(order -> {
                                    LocalDateTime updatedAt = order.getUpdatedAt();
                                    return updatedAt != null && 
                                           !updatedAt.isBefore(startOfDay) && 
                                           !updatedAt.isAfter(endOfDay);
                                })
                                .count();
                        
                        // Get initials
                        String firstName = emp.getNombre() != null ? emp.getNombre() : "";
                        String lastName = emp.getApellido() != null ? emp.getApellido() : "";
                        String initials = "";
                        if (!firstName.isEmpty()) {
                            initials += firstName.charAt(0);
                        }
                        if (!lastName.isEmpty()) {
                            initials += lastName.charAt(0);
                        }
                        initials = initials.toUpperCase();
                        
                        Map<String, Object> empData = new HashMap<>();
                        empData.put("employee", emp);
                        empData.put("orderCount", ordersCount);
                        empData.put("initials", initials);
                        
                        return empData;
                    })
                    .filter(empData -> {
                        // Only include employees with orders TODAY
                        Long count = (Long) empData.get("orderCount");
                        return count > 0;
                    })
                    .sorted((e1, e2) -> {
                        Long count1 = (Long) e1.get("orderCount");
                        Long count2 = (Long) e2.get("orderCount");
                        return count2.compareTo(count1); // Descending order
                    })
                    .limit(5) // Top 5
                    .toList();
            
            model.addAttribute("config", config);
            model.addAttribute("waiterRanking", employeeRanking); // Using same attribute name for template compatibility
            model.addAttribute("rankingDate", today);
            model.addAttribute("isBarista", isBaristaRole);
            
            return "chef/ranking/view";
            
        } catch (Exception e) {
            log.error("Error loading ranking: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cargar el ranking");
            return "redirect:/chef/dashboard";
        }
    }

    /**
     * Sort order details by item status priority
     * Priority order: NEW items first, then PENDING, IN_PREPARATION, READY, DELIVERED
     * 
     * @param order The order whose details need to be sorted
     */
    private void sortOrderDetailsByStatus(Order order) {
        if (order.getOrderDetails() != null && !order.getOrderDetails().isEmpty()) {
            order.getOrderDetails().sort((d1, d2) -> {
                // Priority 1: NEW items first (isNewItem = true)
                int newItemCompare = Boolean.compare(
                    d2.getIsNewItem() != null && d2.getIsNewItem(),
                    d1.getIsNewItem() != null && d1.getIsNewItem()
                );
                if (newItemCompare != 0) return newItemCompare;
                
                // Priority 2: By item status
                int priority1 = getItemStatusPriority(d1.getItemStatus());
                int priority2 = getItemStatusPriority(d2.getItemStatus());
                return Integer.compare(priority1, priority2);
            });
        }
    }

    /**
     * Get priority for item status sorting
     * Lower number = higher priority (appears first)
     */
    private int getItemStatusPriority(OrderStatus status) {
        if (status == null) return 0;
        
        switch (status) {
            case PENDING: return 1;
            case IN_PREPARATION: return 2;
            case READY: return 3;
            case DELIVERED: return 4;
            case PAID: return 5;
            case CANCELLED: return 6;
            default: return 99;
        }
    }
}
