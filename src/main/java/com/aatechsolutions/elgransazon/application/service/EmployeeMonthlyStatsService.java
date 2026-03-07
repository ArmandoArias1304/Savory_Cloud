package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.EmployeeMonthlyStats;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing employee monthly statistics
 * Handles "Employee of the Month" feature logic
 */
public interface EmployeeMonthlyStatsService {

    /**
     * Get or create statistics record for an employee in a specific month/year
     * If record doesn't exist, creates it with zero values
     */
    EmployeeMonthlyStats getOrCreateStats(Employee employee, Integer month, Integer year);

    /**
     * Get or create statistics record for current month
     */
    EmployeeMonthlyStats getOrCreateStatsForCurrentMonth(Employee employee);

    /**
     * Update waiter sales (add amount to total_sales)
     * Used when an order is PAID
     */
    void updateWaiterSales(Employee waiter, BigDecimal amount, Integer month, Integer year);

    /**
     * Update chef orders count (increment total_orders)
     * Used when an order is PAID
     */
    void updateChefOrders(Employee chef, Integer month, Integer year);

    /**
     * Update barista orders count (increment total_orders)
     * Used when an order is PAID
     */
    void updateBaristaOrders(Employee barista, Integer month, Integer year);

    /**
     * Update cashier sales (add amount to total_sales)
     * Used when an order is PAID by a cashier
     */
    void updateCashierSales(Employee cashier, BigDecimal amount, Integer month, Integer year);

    /**
     * Get waiter of the month (highest sales)
     */
    Optional<EmployeeMonthlyStats> getWaiterOfMonth(Integer month, Integer year);

    /**
     * Get waiter of current month
     */
    Optional<EmployeeMonthlyStats> getWaiterOfCurrentMonth();

    /**
     * Get chef of the month (most orders)
     */
    Optional<EmployeeMonthlyStats> getChefOfMonth(Integer month, Integer year);

    /**
     * Get chef of current month
     */
    Optional<EmployeeMonthlyStats> getChefOfCurrentMonth();

    /**
     * Get barista of the month (most orders)
     */
    Optional<EmployeeMonthlyStats> getBaristaOfMonth(Integer month, Integer year);

    /**
     * Get barista of current month
     */
    Optional<EmployeeMonthlyStats> getBaristaOfCurrentMonth();

    /**
     * Get cashier of the month (highest sales)
     */
    Optional<EmployeeMonthlyStats> getCashierOfMonth(Integer month, Integer year);

    /**
     * Get cashier of current month
     */
    Optional<EmployeeMonthlyStats> getCashierOfCurrentMonth();

    /**
     * Get top N waiters of the month
     */
    List<EmployeeMonthlyStats> getTopWaitersOfMonth(Integer month, Integer year, Integer limit);

    /**
     * Get top N chefs of the month
     */
    List<EmployeeMonthlyStats> getTopChefsOfMonth(Integer month, Integer year, Integer limit);

    /**
     * Get top N cashiers of the month
     */
    List<EmployeeMonthlyStats> getTopCashiersOfMonth(Integer month, Integer year, Integer limit);

    /**
     * Get all statistics for a specific month/year
     */
    List<EmployeeMonthlyStats> getAllStatsForMonth(Integer month, Integer year);

    /**
     * Get statistics history for a specific employee
     */
    List<EmployeeMonthlyStats> getEmployeeHistory(Long employeeId);

    /**
     * Initialize statistics for all active employees in current month
     * Called at the beginning of each month
     */
    void initializeCurrentMonth();
}
