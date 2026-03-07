package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.DayOfWeek;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Shift;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service interface for Shift business logic
 */
public interface ShiftService {

    /**
     * Get all shifts ordered by name
     */
    List<Shift> getAllShifts();

    /**
     * Get all active shifts ordered by name
     */
    List<Shift> getAllActiveShifts();

    /**
     * Get shift by ID
     */
    Optional<Shift> getShiftById(Long id);

    /**
     * Get shift by name
     */
    Optional<Shift> getShiftByName(String name);

    /**
     * Create a new shift
     */
    Shift createShift(Shift shift);

    /**
     * Update an existing shift
     */
    Shift updateShift(Long id, Shift shift);

    /**
     * Delete a shift
     */
    void deleteShift(Long id);

    /**
     * Activate a shift
     */
    void activateShift(Long id);

    /**
     * Deactivate a shift
     */
    void deactivateShift(Long id);

    /**
     * Check if shift name exists (case insensitive)
     */
    boolean shiftNameExists(String name);

    /**
     * Check if shift name exists excluding a specific ID
     */
    boolean shiftNameExistsExcludingId(String name, Long id);

    /**
     * Count active shifts
     */
    long countActiveShifts();

    /**
     * Count all shifts
     */
    long countAllShifts();

    /**
     * Get shifts for a specific employee
     */
    List<Shift> getShiftsByEmployee(Long employeeId);

    /**
     * Get employees assigned to a shift
     */
    List<Employee> getEmployeesByShift(Long shiftId);

    /**
     * Assign multiple employees to a shift
     */
    void assignEmployeesToShift(Long shiftId, List<Long> employeeIds, Long actionById);

    /**
     * Remove multiple employees from a shift
     */
    void removeEmployeesFromShift(Long shiftId, List<Long> employeeIds, Long actionById, String reason);

    /**
     * Assign a single employee to a shift
     */
    void assignEmployeeToShift(Long shiftId, Long employeeId, Long actionById);

    /**
     * Remove a single employee from a shift
     */
    void removeEmployeeFromShift(Long shiftId, Long employeeId, Long actionById, String reason);

    /**
     * Validate that shift days are within system work days
     */
    void validateShiftDays(Set<DayOfWeek> shiftDays);

    /**
     * Validate that shift hours are within business hours for each day
     */
    void validateShiftHours(Set<DayOfWeek> shiftDays, LocalTime startTime, LocalTime endTime);

    /**
     * Validate complete shift (days and hours)
     */
    void validateShift(Shift shift);

    /**
     * Get shifts without employees
     */
    List<Shift> getShiftsWithoutEmployees();

    /**
     * Count employees in a shift
     */
    long countEmployeesInShift(Long shiftId);
}
