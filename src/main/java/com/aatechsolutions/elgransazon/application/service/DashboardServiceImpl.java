package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.*;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import com.aatechsolutions.elgransazon.presentation.dto.DashboardStatsDTO;
import com.aatechsolutions.elgransazon.presentation.dto.DashboardStatsDTO.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of Dashboard service
 * Provides aggregated statistics for the admin dashboard
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final OrderRepository orderRepository;
    private final EmployeeService employeeService;
    private final IngredientRepository ingredientRepository;
    private final RestaurantTableRepository tableRepository;
    private final ReservationRepository reservationRepository;

    @Override
    public DashboardStatsDTO getDashboardStats() {
        log.debug("Calculating dashboard statistics");
        
        // MULTI-TENANT: Require company context
        Company company = CompanyContext.requireCurrentCompany();

        // Get today's date range
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        
        // Get yesterday's date range
        LocalDateTime yesterdayStart = LocalDate.now().minusDays(1).atStartOfDay();
        LocalDateTime yesterdayEnd = LocalDate.now().minusDays(1).atTime(LocalTime.MAX);

        // MULTI-TENANT: Get orders filtered by company
        List<Order> todayOrders = orderRepository.findByDateRangeAndCompany(company, todayStart, todayEnd);
        List<Order> yesterdayOrders = orderRepository.findByDateRangeAndCompany(company, yesterdayStart, yesterdayEnd);

        // Calculate sales statistics
        BigDecimal todaySales = calculateSales(todayOrders);
        BigDecimal yesterdaySales = calculateSales(yesterdayOrders);
        Double salesChangePercentage = calculatePercentageChange(todaySales, yesterdaySales);

        // Calculate orders statistics (only PAID orders)
        Long todayOrdersCount = todayOrders.stream()
            .filter(order -> order.getStatus() == OrderStatus.PAID)
            .count();
        Long yesterdayOrdersCount = yesterdayOrders.stream()
            .filter(order -> order.getStatus() == OrderStatus.PAID)
            .count();
        Double ordersChangePercentage = calculatePercentageChange(
            BigDecimal.valueOf(todayOrdersCount), 
            BigDecimal.valueOf(yesterdayOrdersCount)
        );

        // Calculate customers statistics
        Long todayCustomers = countUniqueCustomers(todayOrders);
        Long yesterdayCustomers = countUniqueCustomers(yesterdayOrders);
        Double customersChangePercentage = calculatePercentageChange(
            BigDecimal.valueOf(todayCustomers), 
            BigDecimal.valueOf(yesterdayCustomers)
        );

        // Calculate projected revenue
        BigDecimal totalHistoricalRevenue = calculateTotalHistoricalRevenue();

        // Get popular items
        List<PopularItemDTO> popularItems = getPopularItems(todayOrders);

        // Get active employees (excluding PROGRAMMER) - filtered by company
        Long totalEmployees = employeeService.countAllByCompany(company);
        Long activeEmployees = employeeService.countEnabledByCompany(company);
        Double capacityPercentage = totalEmployees > 0 
            ? (activeEmployees.doubleValue() / totalEmployees.doubleValue()) * 100 
            : 0.0;
        
        // MULTI-TENANT: Get employee initials filtered by company
        List<Employee> employeesForInitials = company != null 
            ? employeeService.findAllByCompany(company) 
            : employeeService.findAll();
        List<String> employeeInitials = employeesForInitials
            .stream()
            .filter(emp -> emp.getEnabled() && !emp.hasRole(Role.PROGRAMMER))
            .limit(4)
            .map(emp -> {
                String firstName = emp.getNombre() != null && !emp.getNombre().isEmpty() 
                    ? emp.getNombre().substring(0, 1).toUpperCase() 
                    : "";
                String lastName = emp.getApellido() != null && !emp.getApellido().isEmpty() 
                    ? emp.getApellido().substring(0, 1).toUpperCase() 
                    : "";
                return firstName + lastName;
            })
            .collect(Collectors.toList());

        // Get inventory alerts (out of stock, low stock)
        List<InventoryAlertDTO> inventoryAlerts = getInventoryAlerts();

        // Get hourly sales for today
        List<HourlySalesDTO> hourlySales = getHourlySales(todayOrders);

        // Get table status
        TableStatusDTO tableStatus = getTableStatus();

        // Get pending orders summary
        PendingOrdersDTO pendingOrders = getPendingOrders();

        // Get today's reservations
        List<ReservationDTO> todayReservations = getTodayReservations();

        return DashboardStatsDTO.builder()
            .todaySales(todaySales)
            .salesChangePercentage(Math.abs(salesChangePercentage))
            .salesIncreased(salesChangePercentage >= 0)
            .todayOrders(todayOrdersCount)
            .ordersChangePercentage(Math.abs(ordersChangePercentage))
            .ordersIncreased(ordersChangePercentage >= 0)
            .todayCustomers(todayCustomers)
            .customersChangePercentage(Math.abs(customersChangePercentage))
            .customersIncreased(customersChangePercentage >= 0)
            .totalHistoricalRevenue(totalHistoricalRevenue)
            .popularItems(popularItems)
            .activeEmployees(activeEmployees.intValue())
            .totalEmployees(totalEmployees.intValue())
            .capacityPercentage(capacityPercentage)
            .employeeInitials(employeeInitials)
            .inventoryAlerts(inventoryAlerts)
            .hourlySales(hourlySales)
            .tableStatus(tableStatus)
            .pendingOrders(pendingOrders)
            .todayReservations(todayReservations)
            .build();
    }

    /**
     * Calculate total sales from orders (only PAID orders)
     * Only includes subtotal + tax, excludes tips
     */
    private BigDecimal calculateSales(List<Order> orders) {
        return orders.stream()
            .filter(order -> order.getStatus() == OrderStatus.PAID)
            .map(Order::getTotal) // Only subtotal + tax, no tips
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate percentage change between two values
     */
    private Double calculatePercentageChange(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) > 0 ? 100.0 : 0.0;
        }
        
        BigDecimal change = current.subtract(previous);
        BigDecimal percentage = change
            .divide(previous, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
        
        return percentage.doubleValue();
    }

    /**
     * Count unique customers from orders
     * Only counts orders with PAID status (completed visits)
     * Each PAID order = one customer/group that came, consumed, and left
     */
    private Long countUniqueCustomers(List<Order> orders) {
        return orders.stream()
            .filter(order -> order.getStatus() == OrderStatus.PAID)
            .count();
    }

    /**
     * Calculate total historical revenue from all PAID orders (all time)
     * MULTI-TENANT: Always filter by company - no global fallback
     */
    private BigDecimal calculateTotalHistoricalRevenue() {
        // MULTI-TENANT: Require company context - no fallback to global data
        Company company = CompanyContext.requireCurrentCompany();
        return orderRepository.getTotalIncomeByCompany(company);
    }

    /**
     * Get top 10 popular items
     * Only counts items from PAID orders (excludes cancelled, pending, etc.)
     */
    private List<PopularItemDTO> getPopularItems(List<Order> todayOrders) {
        // Count items ordered today - ONLY from PAID orders
        // Combo children are excluded — only the combo parent is counted as a sold item
        Map<String, Long> itemCounts = new HashMap<>();
        
        for (Order order : todayOrders) {
            // Only count items from PAID orders
            if (order.getStatus() != OrderStatus.PAID) {
                continue;
            }
            if (order.getOrderDetails() != null) {
                for (OrderDetail detail : order.getOrderDetails()) {
                    if (detail.getItemMenu() != null && !detail.isComboChild()) {
                        String itemName = detail.getItemMenu().getName();
                        Long quantity = detail.getQuantity().longValue();
                        itemCounts.merge(itemName, quantity, Long::sum);
                    }
                }
            }
        }
        
        // If no items today, try to get from all-time PAID orders
        if (itemCounts.isEmpty()) {
            // MULTI-TENANT: Require company context - no fallback to global data
            Company company = CompanyContext.requireCurrentCompany();
            List<Order> allOrders = orderRepository.findByCompany(company);
            for (Order order : allOrders) {
                // Only count items from PAID orders
                if (order.getStatus() != OrderStatus.PAID) {
                    continue;
                }
                if (order.getOrderDetails() != null) {
                    for (OrderDetail detail : order.getOrderDetails()) {
                        if (detail.getItemMenu() != null && !detail.isComboChild()) {
                            String itemName = detail.getItemMenu().getName();
                            Long quantity = detail.getQuantity().longValue();
                            itemCounts.merge(itemName, quantity, Long::sum);
                        }
                    }
                }
            }
        }
        
        // If still no items, return empty list
        if (itemCounts.isEmpty()) {
            return List.of();
        }
        
        // Sort by count and get top 10
        List<Map.Entry<String, Long>> sortedItems = itemCounts.entrySet()
            .stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(10)
            .collect(Collectors.toList());
        
        // Get max count for percentage calculation
        Long maxCount = sortedItems.isEmpty() ? 1L : sortedItems.get(0).getValue();
        
        // Create DTOs
        List<PopularItemDTO> popularItems = new ArrayList<>();
        String[] colors = {"primary", "blue-500", "purple-500", "orange-500", "pink-500", "indigo-500", "teal-500", "amber-500", "rose-500", "cyan-500"};
        String[] gradients = {
            "from-primary to-primary-dark",
            "from-blue-400 to-blue-600",
            "from-purple-400 to-purple-600",
            "from-orange-400 to-orange-600",
            "from-pink-400 to-pink-600",
            "from-indigo-400 to-indigo-600",
            "from-teal-400 to-teal-600",
            "from-amber-400 to-amber-600",
            "from-rose-400 to-rose-600",
            "from-cyan-400 to-cyan-600"
        };
        
        for (int i = 0; i < sortedItems.size(); i++) {
            Map.Entry<String, Long> entry = sortedItems.get(i);
            Double percentage = (entry.getValue().doubleValue() / maxCount.doubleValue()) * 100;
            
            popularItems.add(PopularItemDTO.builder()
                .rank(i + 1)
                .itemName(entry.getKey())
                .orderCount(entry.getValue())
                .maxOrderCount(maxCount)
                .percentage(percentage)
                .color(colors[i % colors.length])
                .badgeGradient(gradients[i % gradients.length])
                .build());
        }
        
        return popularItems;
    }

    /**
     * Get inventory alerts (out of stock, low stock, healthy stock)
     * Returns top 3 items with most critical status
     */
    private List<InventoryAlertDTO> getInventoryAlerts() {
        // MULTI-TENANT: Get active ingredients filtered by company
        Company company = CompanyContext.requireCurrentCompany();
        List<Ingredient> ingredients = ingredientRepository.findByActiveTrueAndCompany(company);
        
        // If no ingredients, return empty list
        if (ingredients.isEmpty()) {
            return List.of();
        }
        
        // Separate by status
        List<Ingredient> outOfStock = new ArrayList<>();
        List<Ingredient> lowStock = new ArrayList<>();
        List<Ingredient> healthyStock = new ArrayList<>();
        
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isOutOfStock()) {
                outOfStock.add(ingredient);
            } else if (ingredient.isLowStock()) {
                lowStock.add(ingredient);
            } else if (ingredient.isHealthyStock()) {
                healthyStock.add(ingredient);
            }
        }
        
        // Build alert list (prioritize: out of stock > low stock > healthy)
        List<InventoryAlertDTO> alerts = new ArrayList<>();
        
        // Add out of stock (red)
        outOfStock.stream()
            .limit(5)
            .forEach(ingredient -> alerts.add(InventoryAlertDTO.builder()
                .ingredientName(ingredient.getName())
                .status("out-of-stock")
                .statusText("Agotado")
                .icon("error")
                .colorClass("red")
                .build()));
        
        // Add low stock (yellow) if we have less than 5 items
        if (alerts.size() < 5) {
            lowStock.stream()
                .limit(5 - alerts.size())
                .forEach(ingredient -> alerts.add(InventoryAlertDTO.builder()
                    .ingredientName(ingredient.getName())
                    .status("low-stock")
                    .statusText("Bajo stock")
                    .icon("warning")
                    .colorClass("yellow")
                    .build()));
        }
        
        // Add healthy stock (green) if we still have less than 5 items
        if (alerts.size() < 5) {
            healthyStock.stream()
                .limit(5 - alerts.size())
                .forEach(ingredient -> alerts.add(InventoryAlertDTO.builder()
                    .ingredientName(ingredient.getName())
                    .status("healthy")
                    .statusText("En stock")
                    .icon("check_circle")
                    .colorClass("green")
                    .build()));
        }
        
        return alerts;
    }

    private List<HourlySalesDTO> getHourlySales(List<Order> todayOrders) {
        Map<Integer, HourlySalesDTO> hourlySalesMap = new java.util.LinkedHashMap<>();
        
        // Initialize all hours (0-23) with zero values
        for (int hour = 0; hour < 24; hour++) {
            hourlySalesMap.put(hour, new HourlySalesDTO(hour, BigDecimal.ZERO, 0L));
        }
        
        // Process only PAID orders
        todayOrders.stream()
            .filter(order -> order.getStatus() == OrderStatus.PAID)
            .forEach(order -> {
                LocalDateTime orderDate = order.getUpdatedAt() != null ? order.getUpdatedAt() : order.getCreatedAt();
                int hour = orderDate.getHour();
                
                HourlySalesDTO currentData = hourlySalesMap.get(hour);
                hourlySalesMap.put(hour, new HourlySalesDTO(
                    hour,
                    currentData.getSales().add(order.getTotal()),
                    currentData.getOrderCount() + 1
                ));
            });
        
        return new ArrayList<>(hourlySalesMap.values());
    }

    private TableStatusDTO getTableStatus() {
        // MULTI-TENANT: Require company context - no fallback to global data
        Company company = CompanyContext.requireCurrentCompany();
        List<RestaurantTable> allTables = tableRepository.findByCompany(company);
        
        int totalTables = allTables.size();
        long available = allTables.stream()
            .filter(table -> table.getStatus() == TableStatus.AVAILABLE)
            .count();
        long occupied = allTables.stream()
            .filter(table -> table.getStatus() == TableStatus.OCCUPIED)
            .count();
        long outOfService = allTables.stream()
            .filter(table -> table.getStatus() == TableStatus.OUT_OF_SERVICE)
            .count();
        
        // Note: RESERVED status no longer exists - tables are only AVAILABLE, OCCUPIED, or OUT_OF_SERVICE
        return new TableStatusDTO(
            totalTables,
            (int) available,
            (int) occupied,
            (int) outOfService
        );
    }

    private PendingOrdersDTO getPendingOrders() {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        
        // MULTI-TENANT: Get active orders for today filtered by company
        Company company = CompanyContext.requireCurrentCompany();
        List<Order> activeOrders = orderRepository.findByDateRangeAndCompany(company, today, now);
        
        long pending = activeOrders.stream()
            .filter(order -> order.getStatus() == OrderStatus.PENDING)
            .count();
        long inPreparation = activeOrders.stream()
            .filter(order -> order.getStatus() == OrderStatus.IN_PREPARATION)
            .count();
        long ready = activeOrders.stream()
            .filter(order -> order.getStatus() == OrderStatus.READY)
            .count();
        long onTheWay = activeOrders.stream()
            .filter(order -> order.getStatus() == OrderStatus.ON_THE_WAY)
            .count();
        
        // Calculate average preparation time for completed orders today
        List<Order> completedOrders = activeOrders.stream()
            .filter(order -> order.getStatus() == OrderStatus.PAID && order.getUpdatedAt() != null)
            .collect(Collectors.toList());
        
        double avgPreparationTime = 0.0;
        if (!completedOrders.isEmpty()) {
            long totalMinutes = completedOrders.stream()
                .mapToLong(order -> Duration.between(order.getCreatedAt(), order.getUpdatedAt()).toMinutes())
                .sum();
            avgPreparationTime = (double) totalMinutes / completedOrders.size();
        }
        
        return new PendingOrdersDTO(
            pending,
            inPreparation,
            ready,
            onTheWay,
            avgPreparationTime
        );
    }

    private List<ReservationDTO> getTodayReservations() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        
        // MULTI-TENANT: Get reservations for today filtered by company
        Company company = CompanyContext.requireCurrentCompany();
        List<Reservation> todayReservations = reservationRepository.findByReservationDateAndCompanyOrderByReservationTimeAsc(today, company);
        
        // Filter active reservations (only PENDING - not cancelled or completed)
        List<Reservation> activeReservations = todayReservations.stream()
            .filter(reservation -> reservation.getStatus() == ReservationStatus.PENDING)
            .collect(Collectors.toList());
        
        // If no active reservations at all, return empty list
        if (activeReservations.isEmpty()) {
            return List.of();
        }
        
        // Try to get upcoming reservations (next 4 hours)
        LocalTime endTime = now.plusHours(4);
        List<Reservation> upcomingReservations = activeReservations.stream()
            .filter(reservation -> {
                LocalTime resTime = reservation.getReservationTime();
                return resTime.isAfter(now) && resTime.isBefore(endTime);
            })
            .collect(Collectors.toList());
        
        // If no upcoming reservations, show all active reservations for today
        List<Reservation> reservationsToShow = upcomingReservations.isEmpty() 
            ? activeReservations 
            : upcomingReservations;
        
        // Map to DTO
        return reservationsToShow.stream()
            .sorted(Comparator.comparing(Reservation::getReservationTime))
            .limit(6) // Show max 6 reservations
            .map(reservation -> {
                String status = formatReservationStatus(reservation.getStatus());
                String statusColor = getReservationStatusColor(reservation.getStatus());
                String tableNumber = reservation.getRestaurantTable() != null ? 
                    String.valueOf(reservation.getRestaurantTable().getTableNumber()) : "Por asignar";
                
                return new ReservationDTO(
                    reservation.getId(),
                    reservation.getCustomerName(),
                    reservation.getReservationTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                    reservation.getNumberOfGuests(),
                    tableNumber,
                    status,
                    statusColor
                );
            })
            .collect(Collectors.toList());
    }

    private String formatReservationStatus(ReservationStatus status) {
        switch (status) {
            case PENDING: return "Pendiente";
            case COMPLETED: return "Completada";
            case CANCELLED: return "Cancelada";
            default: return status.name();
        }
    }

    private String getReservationStatusColor(ReservationStatus status) {
        switch (status) {
            case PENDING: return "bg-blue-100 text-blue-800";
            case COMPLETED: return "bg-green-100 text-green-800";
            case CANCELLED: return "bg-gray-100 text-gray-800";
            default: return "bg-gray-100 text-gray-800";
        }
    }
    
    @Override
    public List<PopularItemDTO> getPopularItemsByPeriod(String period) {
        LocalDateTime startDate;
        LocalDateTime endDate = LocalDateTime.now();
        
        switch (period.toLowerCase()) {
            case "week":
                startDate = LocalDate.now().minusWeeks(1).atStartOfDay();
                break;
            case "month":
                startDate = LocalDate.now().minusMonths(1).atStartOfDay();
                break;
            case "today":
            default:
                startDate = LocalDate.now().atStartOfDay();
                break;
        }
        
        // MULTI-TENANT: Get orders for the period filtered by company
        Company company = CompanyContext.requireCurrentCompany();
        List<Order> orders = orderRepository.findByDateRangeAndCompany(company, startDate, endDate);
        
        return getPopularItems(orders);
    }
}
