package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.EmployeeShiftHistory;
import com.aatechsolutions.elgransazon.domain.entity.Shift;
import com.aatechsolutions.elgransazon.domain.entity.ShiftAction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service interface for EmployeeShiftHistory business logic
 */
public interface EmployeeShiftHistoryService {

    /**
     * Get history by employee ID
     */
    List<EmployeeShiftHistory> getHistoryByEmployee(Long employeeId);

    /**
     * Get history by shift ID
     */
    List<EmployeeShiftHistory> getHistoryByShift(Long shiftId);

    /**
     * Get history by employee and shift
     */
    List<EmployeeShiftHistory> getHistoryByEmployeeAndShift(Long employeeId, Long shiftId);

    /**
     * Get recent history with limit
     */
    List<EmployeeShiftHistory> getRecentHistory(int limit);

    /**
     * Get all history ordered by date
     */
    List<EmployeeShiftHistory> getAllHistory();

    /**
     * Get history by date range
     */
    List<EmployeeShiftHistory> getHistoryByDateRange(LocalDateTime start, LocalDateTime end);

    /**
     * Get history by action type
     */
    List<EmployeeShiftHistory> getHistoryByAction(ShiftAction action);

    /**
     * Create a history record
     */
    EmployeeShiftHistory createHistoryRecord(
            Employee employee,
            Shift shift,
            ShiftAction action,
            Employee actionBy,
            String reason
    );

    /**
     * Create multiple history records (bulk operation)
     */
    List<EmployeeShiftHistory> createHistoryRecords(
            List<Employee> employees,
            Shift shift,
            ShiftAction action,
            Employee actionBy,
            String reason
    );

    /**
     * Count history records for employee
     */
    long countHistoryByEmployee(Long employeeId);

    /**
     * Count history records for shift
     */
    long countHistoryByShift(Long shiftId);

    /**
     * Get shift changes report for date range
     */
    Map<String, Object> getShiftChangesReport(LocalDateTime start, LocalDateTime end);

    /**
     * Get employee shift statistics
     */
    Map<String, Object> getEmployeeShiftStatistics(Long employeeId);

    /**
     * Get most recent action for employee and shift
     */
    EmployeeShiftHistory getMostRecentAction(Long employeeId, Long shiftId);

    /**
     * Get history by action performed by a specific employee
     */
    List<EmployeeShiftHistory> getHistoryByActionBy(Long actionById);
}
