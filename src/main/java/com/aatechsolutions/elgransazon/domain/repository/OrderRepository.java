package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.RestaurantTable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Order entity
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Find order by order number
     */
    Optional<Order> findByOrderNumber(String orderNumber);

    /**
     * Find order by autofactura key and company (for public autofactura page)
     */
    @Query("SELECT o FROM Order o " +
           "LEFT JOIN FETCH o.orderDetails od " +
           "LEFT JOIN FETCH od.itemMenu " +
           "WHERE o.autofacturaKey = :key AND o.company = :company")
    Optional<Order> findByAutofacturaKeyAndCompany(@Param("key") String key, @Param("company") Company company);

    /**
     * Find order by ID with all relationships loaded (for editing)
     */
    @Query("SELECT o FROM Order o " +
           "LEFT JOIN FETCH o.employee " +
           "LEFT JOIN FETCH o.preparedBy " +
           "LEFT JOIN FETCH o.paidBy " +
           "LEFT JOIN FETCH o.table " +
           "LEFT JOIN FETCH o.orderDetails od " +
           "LEFT JOIN FETCH od.itemMenu " +
           "WHERE o.idOrder = :id")
    Optional<Order> findByIdWithDetails(@Param("id") Long id);

    /**
     * Find order by ID with all details and company filter
     */
    @Query("SELECT o FROM Order o " +
           "LEFT JOIN FETCH o.company " +
           "LEFT JOIN FETCH o.customer " +
           "LEFT JOIN FETCH o.employee " +
           "LEFT JOIN FETCH o.preparedBy " +
           "LEFT JOIN FETCH o.paidBy " +
           "LEFT JOIN FETCH o.table " +
           "LEFT JOIN FETCH o.orderDetails od " +
           "LEFT JOIN FETCH od.itemMenu " +
           "WHERE o.idOrder = :id AND o.company = :company")
    Optional<Order> findByIdWithDetailsAndCompany(@Param("id") Long id, @Param("company") Company company);

    /**
     * Find all orders by table
     */
    List<Order> findByTable(RestaurantTable table);

    /**
     * Find all orders by table ID
     */
    @Query("SELECT o FROM Order o WHERE o.table.id = :tableId ORDER BY o.createdAt DESC")
    List<Order> findByTableId(@Param("tableId") Long tableId);

    /**
     * Find all orders by table ID and company
     */
    @Query("SELECT o FROM Order o WHERE o.table.id = :tableId AND o.company = :company ORDER BY o.createdAt DESC")
    List<Order> findByTableIdAndCompany(@Param("tableId") Long tableId, @Param("company") Company company);

    /**
     * Find active order by table (not cancelled, not delivered)
     */
    @Query("SELECT o FROM Order o WHERE o.table.id = :tableId " +
           "AND o.status NOT IN ('CANCELLED', 'DELIVERED', 'PAID') " +
           "ORDER BY o.createdAt DESC")
    Optional<Order> findActiveOrderByTableId(@Param("tableId") Long tableId);

    /**
     * Find active order by table and company
     */
    @Query("SELECT o FROM Order o WHERE o.table.id = :tableId AND o.company = :company " +
           "AND o.status NOT IN ('CANCELLED', 'DELIVERED', 'PAID') " +
           "ORDER BY o.createdAt DESC")
    Optional<Order> findActiveOrderByTableIdAndCompany(@Param("tableId") Long tableId, @Param("company") Company company);

    /**
     * Find active order by table with employee eagerly fetched
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.employee WHERE o.table.id = :tableId " +
           "AND o.status NOT IN ('CANCELLED', 'DELIVERED', 'PAID') " +
           "ORDER BY o.createdAt DESC")
    List<Order> findActiveOrdersWithEmployeeByTableId(@Param("tableId") Long tableId);

    /**
     * Find all orders by employee ID
     */
    @Query("SELECT o FROM Order o WHERE o.employee.idEmpleado = :employeeId ORDER BY o.createdAt DESC")
    List<Order> findByEmployeeId(@Param("employeeId") Long employeeId);

    /**
     * Find all orders by employee ID and company
     */
    @Query("SELECT o FROM Order o WHERE o.employee.idEmpleado = :employeeId AND o.company = :company ORDER BY o.createdAt DESC")
    List<Order> findByEmployeeIdAndCompany(@Param("employeeId") Long employeeId, @Param("company") Company company);

    /**
     * Find all orders by status
     */
    List<Order> findByStatus(OrderStatus status);

    /**
     * Find all orders by order type
     */
    List<Order> findByOrderType(OrderType orderType);

    /**
     * Find today's orders
     */
    @Query("SELECT o FROM Order o WHERE DATE(o.createdAt) = CURRENT_DATE ORDER BY o.createdAt DESC")
    List<Order> findTodaysOrders();

    /**
     * Find active orders (not cancelled, not delivered, not paid)
     */
    @Query("SELECT o FROM Order o WHERE o.status NOT IN ('CANCELLED', 'DELIVERED', 'PAID') " +
           "ORDER BY o.createdAt DESC")
    List<Order> findActiveOrders();

    /**
     * Find orders by date range
     */
    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY o.createdAt DESC")
    List<Order> findByDateRange(@Param("startDate") LocalDateTime startDate, 
                                 @Param("endDate") LocalDateTime endDate);

    /**
     * Count orders by status
     */
    long countByStatus(OrderStatus status);

    /**
     * Count today's orders
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE DATE(o.createdAt) = CURRENT_DATE")
    long countTodaysOrders();

    /**
     * Count orders for today by status
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE DATE(o.createdAt) = CURRENT_DATE AND o.status = :status")
    long countTodaysOrdersByStatus(@Param("status") OrderStatus status);

    /**
     * Get today's revenue (sum of totals from PAID orders created today)
     */
    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o " +
           "WHERE DATE(o.createdAt) = CURRENT_DATE AND o.status = 'PAID'")
    java.math.BigDecimal getTodaysRevenue();

    /**
     * Get count of orders created today (for generating order number)
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE DATE(o.createdAt) = CURRENT_DATE")
    long countOrdersCreatedToday();

    /**
     * Find the last order number for today (for generating unique order numbers)
     */
    @Query("SELECT o.orderNumber FROM Order o WHERE DATE(o.createdAt) = CURRENT_DATE " +
           "ORDER BY o.orderNumber DESC LIMIT 1")
    Optional<String> findLastOrderNumberToday();

    /**
     * Check if order number exists
     */
    boolean existsByOrderNumber(String orderNumber);

    /**
     * Find all orders ordered by created date desc
     */
    @Query("SELECT o FROM Order o ORDER BY o.createdAt DESC")
    List<Order> findAllOrderByCreatedAtDesc();

    /**
     * Find all orders with details loaded (for chef filtering)
     * Uses FETCH JOIN to load OrderDetails and ItemMenu in a single query
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.orderDetails od " +
           "LEFT JOIN FETCH od.itemMenu " +
           "ORDER BY o.createdAt DESC")
    List<Order> findAllWithDetails();

    /**
     * Find orders that have at least ONE item requiring preparation (Chef view)
     * This query filters at database level instead of loading all orders
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "JOIN FETCH o.orderDetails od " +
           "JOIN FETCH od.itemMenu im " +
           "WHERE im.requiresPreparation = true " +
           "ORDER BY o.createdAt DESC")
    List<Order> findOrdersWithPreparationItems();

    /**
     * Find all orders by customer ID
     */
    @Query("SELECT o FROM Order o WHERE o.customer.idCustomer = :customerId ORDER BY o.createdAt DESC")
    List<Order> findByCustomerId(@Param("customerId") Long customerId);

    /**
     * Find all orders by customer email
     */
    @Query("SELECT o FROM Order o WHERE o.customer.email = :customerEmail ORDER BY o.createdAt DESC")
    List<Order> findByCustomerEmail(@Param("customerEmail") String customerEmail);

    /**
     * Get total income from all PAID orders
     */
    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o WHERE o.status = 'PAID'")
    BigDecimal getTotalIncome();
    
    /**
     * Find order by ID with pessimistic write lock
     * Used to prevent concurrent modifications when accepting delivery orders
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.idOrder = :id")
    Optional<Order> findByIdWithLock(@Param("id") Long id);

    // ========== Multi-Tenant Methods (by Company) ==========

    /**
     * Find all orders by company
     */
    List<Order> findByCompany(Company company);

    /**
     * Find order by ID and company (for security validation)
     */
    Optional<Order> findByIdOrderAndCompany(Long idOrder, Company company);

    /**
     * Find order by order number and company
     */
    Optional<Order> findByOrderNumberAndCompany(String orderNumber, Company company);

    /**
     * Find today's orders by company
     */
    @Query("SELECT o FROM Order o WHERE o.company = :company AND DATE(o.createdAt) = CURRENT_DATE ORDER BY o.createdAt DESC")
    List<Order> findTodaysOrdersByCompany(@Param("company") Company company);

    /**
     * Find active orders by company
     */
    @Query("SELECT o FROM Order o WHERE o.company = :company AND o.status NOT IN ('CANCELLED', 'DELIVERED', 'PAID') ORDER BY o.createdAt DESC")
    List<Order> findActiveOrdersByCompany(@Param("company") Company company);

    /**
     * Find orders by date range and company
     */
    @Query("SELECT o FROM Order o WHERE o.company = :company AND o.createdAt BETWEEN :startDate AND :endDate ORDER BY o.createdAt DESC")
    List<Order> findByDateRangeAndCompany(@Param("company") Company company,
                                           @Param("startDate") LocalDateTime startDate, 
                                           @Param("endDate") LocalDateTime endDate);

    /**
     * Count orders by status and company
     */
    long countByStatusAndCompany(OrderStatus status, Company company);

    /**
     * Count all orders by company
     */
    long countByCompany(Company company);

    /**
     * Count invoiced orders (with CFDI) by company
     */
    long countByCompanyAndFacturamaCfdiIdIsNotNull(Company company);

    /**
     * Count invoiced orders by company using the CFDI timestamp.
     * Preferred over {@link #countByCompanyAndFacturamaCfdiIdIsNotNull(Company)}
     * because it shares the same field used by date-range reports, keeping totals consistent.
     */
    long countByCompanyAndFacturamaCfdiCreatedAtIsNotNull(Company company);

    /**
     * Count invoiced orders (with CFDI) by company and date range (UTC)
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.company = :company " +
           "AND o.facturamaCfdiCreatedAt IS NOT NULL " +
           "AND o.facturamaCfdiCreatedAt >= :startDate " +
           "AND o.facturamaCfdiCreatedAt < :endDate")
    long countCfdisByCompanyAndDateRange(
            @Param("company") Company company,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Aggregate PAID orders for a company within a date range (UTC), broken down by whether
     * the order was invoiced (has a Facturama CFDI) or not.
     *
     * Filters by {@code paidAt} (authoritative payment timestamp; never overwritten after the
     * status transition to PAID, so safe even after autofactura CFDI saves).
     *
     * Returns a single row: [paidCount, paidTotal, invoicedCount, invoicedTotal].
     */
    @Query("SELECT " +
           "  COUNT(o), " +
           "  COALESCE(SUM(o.total), 0), " +
           "  SUM(CASE WHEN o.facturamaCfdiId IS NOT NULL THEN 1 ELSE 0 END), " +
           "  COALESCE(SUM(CASE WHEN o.facturamaCfdiId IS NOT NULL THEN o.total ELSE 0 END), 0) " +
           "FROM Order o " +
           "WHERE o.company = :company " +
           "  AND o.status = com.aatechsolutions.elgransazon.domain.entity.OrderStatus.PAID " +
           "  AND o.paidAt >= :startDate " +
           "  AND o.paidAt < :endDate")
    List<Object[]> sumPaidOrdersByCompanyAndDateRange(
            @Param("company") Company company,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Count today's orders by company
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.company = :company AND DATE(o.createdAt) = CURRENT_DATE")
    long countTodaysOrdersByCompany(@Param("company") Company company);

    /**
     * Get today's revenue by company
     */
    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o WHERE o.company = :company AND DATE(o.createdAt) = CURRENT_DATE AND o.status = 'PAID'")
    BigDecimal getTodaysRevenueByCompany(@Param("company") Company company);

    /**
     * Find orders with preparation items by company
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "JOIN FETCH o.orderDetails od " +
           "JOIN FETCH od.itemMenu im " +
           "WHERE o.company = :company AND im.requiresPreparation = true " +
           "ORDER BY o.createdAt DESC")
    List<Order> findOrdersWithPreparationItemsByCompany(@Param("company") Company company);

    /**
     * Find orders with barista preparation items by company
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "JOIN FETCH o.orderDetails od " +
           "JOIN FETCH od.itemMenu im " +
           "WHERE o.company = :company AND im.requiresBaristaPreparation = true " +
           "ORDER BY o.createdAt DESC")
    List<Order> findOrdersWithBaristaItemsByCompany(@Param("company") Company company);

    /**
     * Find orders with parrillero preparation items by company
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "JOIN FETCH o.orderDetails od " +
           "JOIN FETCH od.itemMenu im " +
           "WHERE o.company = :company AND im.requiresParrilleroPreparation = true " +
           "ORDER BY o.createdAt DESC")
    List<Order> findOrdersWithParrilleroItemsByCompany(@Param("company") Company company);

    /**
     * Get total income by company
     */
    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o WHERE o.company = :company AND o.status = 'PAID'")
    BigDecimal getTotalIncomeByCompany(@Param("company") Company company);

    /**
     * Check if order number exists for company
     */
    boolean existsByOrderNumberAndCompany(String orderNumber, Company company);

    /**
     * Find all orders by company ordered by created date desc
     */
    @Query("SELECT o FROM Order o WHERE o.company = :company ORDER BY o.createdAt DESC")
    List<Order> findAllByCompanyOrderByCreatedAtDesc(@Param("company") Company company);

    /**
     * Find all orders by customer email and company
     */
    @Query("SELECT o FROM Order o WHERE o.customer.email = :customerEmail AND o.company = :company ORDER BY o.createdAt DESC")
    List<Order> findByCustomerEmailAndCompany(@Param("customerEmail") String customerEmail, @Param("company") Company company);

    // ========== Statistics by Date Range (for dynamic cards) ==========

    /**
     * Count PAID orders by date range and company (global - any employee).
     * Filters by {@code paidAt} (authoritative payment timestamp).
     * Used for Admin's "Pedidos Pagados" card.
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.company = :company " +
           "AND o.status = 'PAID' " +
           "AND o.paidAt BETWEEN :startDate AND :endDate")
    long countPaidByDateRangeAndCompany(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("company") Company company);

    /**
     * Find PAID orders by date range and company (global - any employee).
     * Filters by {@code paidAt} (authoritative payment timestamp).
     * Used by the admin dashboard to build sales/customers/popular-items/hourly metrics.
     */
    @Query("SELECT o FROM Order o WHERE o.company = :company " +
           "AND o.status = 'PAID' " +
           "AND o.paidAt BETWEEN :startDate AND :endDate " +
           "ORDER BY o.paidAt ASC")
    List<Order> findPaidByPaidAtRangeAndCompany(
            @Param("company") Company company,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Count PAID orders by date range, paidBy username, and company.
     * Filters by {@code paidAt} (authoritative payment timestamp).
     * Used for Waiter/Cashier's "Pedidos Cobrados" card (only their collections).
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.company = :company " +
           "AND o.status = 'PAID' " +
           "AND o.paidBy.username = :username " +
           "AND o.paidAt BETWEEN :startDate AND :endDate")
    long countPaidByUsernameAndDateRangeAndCompany(
            @Param("username") String username,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("company") Company company);

    /**
     * Count orders by status and date range (by createdAt) and company
     * Used for Pending/In Preparation cards
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.company = :company " +
           "AND o.status = :status " +
           "AND o.createdAt BETWEEN :startDate AND :endDate")
    long countByStatusAndDateRangeAndCompany(
            @Param("status") OrderStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("company") Company company);

    /**
     * Get revenue by date range and company (total of PAID orders).
     * Filters by {@code paidAt} (authoritative payment timestamp).
     * Used for "Ingresos" card.
     */
    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o WHERE o.company = :company " +
           "AND o.status = 'PAID' " +
           "AND o.paidAt BETWEEN :startDate AND :endDate")
    BigDecimal getRevenueByDateRangeAndCompany(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("company") Company company);

    /**
     * Get revenue collected by specific user in date range.
     * Filters by {@code paidAt} (authoritative payment timestamp).
     * Used for Waiter/Cashier's own revenue card.
     */
    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o WHERE o.company = :company " +
           "AND o.status = 'PAID' " +
           "AND o.paidBy.username = :username " +
           "AND o.paidAt BETWEEN :startDate AND :endDate")
    BigDecimal getRevenueByUsernameAndDateRangeAndCompany(
            @Param("username") String username,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("company") Company company);

    /**
     * Get revenue for orders created by user but paid by others in date range.
     * Filters by {@code paidAt} (authoritative payment timestamp).
     * Used for Waiter's "Ingresos Globales" card.
     */
    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o WHERE o.company = :company " +
           "AND o.status = 'PAID' " +
           "AND o.createdBy = :createdByUsername " +
           "AND o.paidBy.username <> :createdByUsername " +
           "AND o.paidAt BETWEEN :startDate AND :endDate")
    BigDecimal getRevenueCreatedByUserPaidByOthersAndDateRangeAndCompany(
            @Param("createdByUsername") String createdByUsername,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("company") Company company);

    /**
     * Get revenue for orders created AND paid by the same user in date range.
     * Filters by {@code paidAt} (authoritative payment timestamp).
     * Used for Waiter/Cashier's "Ingresos Propios" card (orders I created AND I collected).
     */
    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o WHERE o.company = :company " +
           "AND o.status = 'PAID' " +
           "AND o.createdBy = :username " +
           "AND o.paidBy.username = :username " +
           "AND o.paidAt BETWEEN :startDate AND :endDate")
    BigDecimal getRevenueCreatedAndPaidBySameUserAndDateRangeAndCompany(
            @Param("username") String username,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("company") Company company);

    // ========== Migration Helper Methods ==========

    /**
     * Find all orders without company (for data migration)
     */
    List<Order> findByCompanyIsNull();
}
