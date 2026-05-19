package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.EmployeeMonthlyStats;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeMonthlyStatsRepository;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service implementation for managing employee monthly statistics
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeMonthlyStatsServiceImpl implements EmployeeMonthlyStatsService {

    private final EmployeeMonthlyStatsRepository statsRepository;
    private final EmployeeRepository employeeRepository;
    private final DateTimeService dateTimeService;

    @Override
    @Transactional
    public EmployeeMonthlyStats getOrCreateStats(Employee employee, Integer month, Integer year) {
        log.debug("Getting or creating stats for employee {} for {}/{}", employee.getIdEmpleado(), month, year);
        
        return statsRepository.findByEmployeeIdAndMonthAndYear(employee.getIdEmpleado(), month, year)
            .orElseGet(() -> {
                log.info("Creating new stats record for employee {} for {}/{}", employee.getIdEmpleado(), month, year);
                EmployeeMonthlyStats newStats = EmployeeMonthlyStats.builder()
                    .employee(employee)
                    .month(month)
                    .year(year)
                    .totalSales(BigDecimal.ZERO)
                    .totalOrders(0)
                    .build();
                return statsRepository.save(newStats);
            });
    }

    @Override
    @Transactional
    public EmployeeMonthlyStats getOrCreateStatsForCurrentMonth(Employee employee) {
        LocalDate now = dateTimeService.todayLocal();
        return getOrCreateStats(employee, now.getMonthValue(), now.getYear());
    }

    @Override
    @Transactional
    public void updateWaiterSales(Employee waiter, BigDecimal amount, Integer month, Integer year) {
        log.info("Updating waiter {} sales: adding ${} for {}/{}", 
                waiter.getIdEmpleado(), amount, month, year);
        
        EmployeeMonthlyStats stats = getOrCreateStats(waiter, month, year);
        stats.addSales(amount);
        statsRepository.save(stats);
        
        log.info("Waiter {} now has total sales of ${} for {}/{}", 
                waiter.getIdEmpleado(), stats.getTotalSales(), month, year);
    }

    @Override
    @Transactional
    public void updateChefOrders(Employee chef, Integer month, Integer year) {
        log.info("Updating chef {} orders: incrementing for {}/{}", 
                chef.getIdEmpleado(), month, year);
        
        EmployeeMonthlyStats stats = getOrCreateStats(chef, month, year);
        stats.incrementOrders();
        statsRepository.save(stats);
        
        log.info("Chef {} now has {} total orders for {}/{}", 
                chef.getIdEmpleado(), stats.getTotalOrders(), month, year);
    }

    @Override
    @Transactional
    public void updateBaristaOrders(Employee barista, Integer month, Integer year) {
        log.info("Updating barista {} orders: incrementing for {}/{}", 
                barista.getIdEmpleado(), month, year);
        
        EmployeeMonthlyStats stats = getOrCreateStats(barista, month, year);
        stats.incrementOrders();
        statsRepository.save(stats);
        
        log.info("Barista {} now has {} total orders for {}/{}", 
                barista.getIdEmpleado(), stats.getTotalOrders(), month, year);
    }

    @Override
    @Transactional
    public void updateParrilleroOrders(Employee parrillero, Integer month, Integer year) {
        log.info("Updating parrillero {} orders: incrementing for {}/{}", 
                parrillero.getIdEmpleado(), month, year);
        
        EmployeeMonthlyStats stats = getOrCreateStats(parrillero, month, year);
        stats.incrementOrders();
        statsRepository.save(stats);
        
        log.info("Parrillero {} now has {} total orders for {}/{}", 
                parrillero.getIdEmpleado(), stats.getTotalOrders(), month, year);
    }

    @Override
    @Transactional
    public void updateCashierSales(Employee cashier, BigDecimal amount, Integer month, Integer year) {
        log.info("Updating cashier {} sales: adding ${} for {}/{}", 
                cashier.getIdEmpleado(), amount, month, year);
        
        EmployeeMonthlyStats stats = getOrCreateStats(cashier, month, year);
        stats.addSales(amount);
        statsRepository.save(stats);
        
        log.info("Cashier {} now has total sales of ${} for {}/{}", 
                cashier.getIdEmpleado(), stats.getTotalSales(), month, year);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeMonthlyStats> getWaiterOfMonth(Integer month, Integer year) {
        log.debug("Finding waiter of the month for {}/{}", month, year);
        return statsRepository.findTopWaiterOfMonth(month, year);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeMonthlyStats> getWaiterOfCurrentMonth() {
        LocalDate now = dateTimeService.todayLocal();
        return getWaiterOfMonth(now.getMonthValue(), now.getYear());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeMonthlyStats> getChefOfMonth(Integer month, Integer year) {
        log.debug("Finding chef of the month for {}/{}", month, year);
        return statsRepository.findTopChefOfMonth(month, year);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeMonthlyStats> getChefOfCurrentMonth() {
        LocalDate now = dateTimeService.todayLocal();
        return getChefOfMonth(now.getMonthValue(), now.getYear());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeMonthlyStats> getBaristaOfMonth(Integer month, Integer year) {
        log.debug("Finding barista of the month for {}/{}", month, year);
        return statsRepository.findTopBaristaOfMonth(month, year);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeMonthlyStats> getBaristaOfCurrentMonth() {
        LocalDate now = dateTimeService.todayLocal();
        return getBaristaOfMonth(now.getMonthValue(), now.getYear());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeMonthlyStats> getCashierOfMonth(Integer month, Integer year) {
        log.debug("Finding cashier of the month for {}/{}", month, year);
        return statsRepository.findTopCashierOfMonth(month, year);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeMonthlyStats> getCashierOfCurrentMonth() {
        LocalDate now = dateTimeService.todayLocal();
        return getCashierOfMonth(now.getMonthValue(), now.getYear());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeMonthlyStats> getTopWaitersOfMonth(Integer month, Integer year, Integer limit) {
        log.debug("Finding top {} waiters for {}/{}", limit, month, year);
        return statsRepository.findTopWaitersOfMonth(month, year).stream()
            .limit(limit)
            .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeMonthlyStats> getTopChefsOfMonth(Integer month, Integer year, Integer limit) {
        log.debug("Finding top {} chefs for {}/{}", limit, month, year);
        return statsRepository.findTopChefsOfMonth(month, year).stream()
            .limit(limit)
            .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeMonthlyStats> getTopCashiersOfMonth(Integer month, Integer year, Integer limit) {
        log.debug("Finding top {} cashiers for {}/{}", limit, month, year);
        return statsRepository.findTopCashiersOfMonth(month, year).stream()
            .limit(limit)
            .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeMonthlyStats> getAllStatsForMonth(Integer month, Integer year) {
        log.debug("Finding all stats for {}/{}", month, year);
        return statsRepository.findByMonthAndYear(month, year);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeMonthlyStats> getEmployeeHistory(Long employeeId) {
        log.debug("Finding history for employee {}", employeeId);
        return statsRepository.findAllByEmployeeId(employeeId);
    }

    @Override
    @Transactional
    public void initializeCurrentMonth() {
        LocalDate now = dateTimeService.todayLocal();
        Integer currentMonth = now.getMonthValue();
        Integer currentYear = now.getYear();
        
        log.info("Initializing statistics for current month: {}/{}", currentMonth, currentYear);
        
        List<Employee> activeEmployees = employeeRepository.findByEnabledTrue();
        
        for (Employee employee : activeEmployees) {
            // This will create if doesn't exist, or return existing
            getOrCreateStats(employee, currentMonth, currentYear);
        }
        
        log.info("Initialized stats for {} active employees", activeEmployees.size());
    }
}
