package com.aatechsolutions.elgransazon.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for Dashboard Statistics
 * Contains aggregated data for the admin dashboard
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatsDTO {

    // ========== Sales Statistics ==========
    
    /**
     * Total sales for today
     */
    private BigDecimal todaySales;
    
    /**
     * Sales percentage change compared to yesterday
     */
    private Double salesChangePercentage;
    
    /**
     * Whether sales increased or decreased
     */
    private boolean salesIncreased;

    // ========== Orders Statistics ==========
    
    /**
     * Total orders for today
     */
    private Long todayOrders;
    
    /**
     * Orders percentage change compared to yesterday
     */
    private Double ordersChangePercentage;
    
    /**
     * Whether orders increased or decreased
     */
    private boolean ordersIncreased;

    // ========== Customers Statistics ==========
    
    /**
     * Total unique customers for today
     */
    private Long todayCustomers;
    
    /**
     * Customers percentage change compared to yesterday
     */
    private Double customersChangePercentage;
    
    /**
     * Whether customers increased or decreased
     */
    private boolean customersIncreased;

    // ========== Total Historical Revenue ==========
    
    /**
     * Total revenue from all time (all PAID orders ever)
     */
    private BigDecimal totalHistoricalRevenue;

    // ========== Popular Items ==========
    
    /**
     * List of most popular menu items today
     */
    private List<PopularItemDTO> popularItems;

    // ========== Active Employees ==========
    
    /**
     * Number of employees currently working
     */
    private Integer activeEmployees;
    
    /**
     * Total employee capacity
     */
    private Integer totalEmployees;
    
    /**
     * Capacity percentage
     */
    private Double capacityPercentage;
    
    /**
     * List of active employee initials
     */
    private List<String> employeeInitials;

    // ========== Inventory Alerts ==========
    
    /**
     * List of ingredients with stock issues (low stock, out of stock)
     */
    private List<InventoryAlertDTO> inventoryAlerts;

    // ========== Sales by Hour ==========
    
    /**
     * Hourly sales distribution for today
     */
    private List<HourlySalesDTO> hourlySales;

    // ========== Table Status ==========
    
    /**
     * Real-time table status
     */
    private TableStatusDTO tableStatus;

    // ========== Pending Orders ==========
    
    /**
     * Orders pending completion
     */
    private PendingOrdersDTO pendingOrders;

    // ========== Today's Reservations ==========
    
    /**
     * Upcoming reservations for today
     */
    private List<ReservationDTO> todayReservations;

    /**
     * DTO for inventory alerts
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InventoryAlertDTO {
        
        /**
         * Ingredient name
         */
        private String ingredientName;
        
        /**
         * Stock status: "OUT_OF_STOCK", "LOW_STOCK", "HEALTHY"
         */
        private String status;
        
        /**
         * Status display text
         */
        private String statusText;
        
        /**
         * Icon name for Material Symbols
         */
        private String icon;
        
        /**
         * Color class for styling (red, yellow, green)
         */
        private String colorClass;
    }

    /**
     * DTO for popular menu items
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PopularItemDTO {
        
        /**
         * Ranking position (1-4)
         */
        private Integer rank;
        
        /**
         * Item name
         */
        private String itemName;
        
        /**
         * Number of orders for this item
         */
        private Long orderCount;
        
        /**
         * Maximum order count (for calculating percentage)
         */
        private Long maxOrderCount;
        
        /**
         * Percentage relative to the top item
         */
        private Double percentage;
        
        /**
         * Color for the progress bar
         */
        private String color;
        
        /**
         * Color gradient for the rank badge
         */
        private String badgeGradient;
    }

    /**
     * DTO for hourly sales data
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HourlySalesDTO {
        private Integer hour;
        private BigDecimal sales;
        private Long orderCount;
    }

    /**
     * DTO for table status summary
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TableStatusDTO {
        private Integer totalTables;
        private Integer available;
        private Integer occupied;
        private Integer outOfService;
    }

    /**
     * DTO for pending orders summary
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PendingOrdersDTO {
        private Long pending;
        private Long inPreparation;
        private Long ready;
        private Long onTheWay;
    }

    /**
     * DTO for reservation information
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReservationDTO {
        private Long reservationId;
        private String customerName;
        private String time;
        private Integer partySize;
        private String tableNumber;
        private String status;
        private String statusColor;
    }
}
