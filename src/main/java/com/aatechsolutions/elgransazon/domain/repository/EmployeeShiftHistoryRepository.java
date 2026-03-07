package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.EmployeeShiftHistory;
import com.aatechsolutions.elgransazon.domain.entity.ShiftAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for EmployeeShiftHistory entity
 */
@Repository
public interface EmployeeShiftHistoryRepository extends JpaRepository<EmployeeShiftHistory, Long> {

    /**
     * Find history by employee ID ordered by action date descending
     */
    @Query("SELECT h FROM EmployeeShiftHistory h WHERE h.employee.idEmpleado = :employeeId ORDER BY h.actionDate DESC")
    List<EmployeeShiftHistory> findByEmployeeIdOrderByActionDateDesc(@Param("employeeId") Long employeeId);

    /**
     * Find history by shift ID ordered by action date descending
     */
    @Query("SELECT h FROM EmployeeShiftHistory h WHERE h.shift.id = :shiftId ORDER BY h.actionDate DESC")
    List<EmployeeShiftHistory> findByShiftIdOrderByActionDateDesc(@Param("shiftId") Long shiftId);

    /**
     * Find history by employee and shift
     */
    @Query("SELECT h FROM EmployeeShiftHistory h WHERE h.employee.idEmpleado = :employeeId AND h.shift.id = :shiftId ORDER BY h.actionDate DESC")
    List<EmployeeShiftHistory> findByEmployeeIdAndShiftId(@Param("employeeId") Long employeeId, @Param("shiftId") Long shiftId);

    /**
     * Find recent history with limit
     */
    @Query("SELECT h FROM EmployeeShiftHistory h ORDER BY h.actionDate DESC LIMIT :limit")
    List<EmployeeShiftHistory> findRecentHistory(@Param("limit") int limit);

    /**
     * Find history by action date between
     */
    @Query("SELECT h FROM EmployeeShiftHistory h WHERE h.actionDate BETWEEN :start AND :end ORDER BY h.actionDate DESC")
    List<EmployeeShiftHistory> findByActionDateBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Find history by action type
     */
    @Query("SELECT h FROM EmployeeShiftHistory h WHERE h.action = :action ORDER BY h.actionDate DESC")
    List<EmployeeShiftHistory> findByAction(@Param("action") ShiftAction action);

    /**
     * Find all history ordered by action date descending
     */
    @Query("SELECT h FROM EmployeeShiftHistory h ORDER BY h.actionDate DESC")
    List<EmployeeShiftHistory> findAllOrderByActionDateDesc();

    /**
     * Count history records for employee
     */
    @Query("SELECT COUNT(h) FROM EmployeeShiftHistory h WHERE h.employee.idEmpleado = :employeeId")
    long countByEmployeeId(@Param("employeeId") Long employeeId);

    /**
     * Count history records for shift
     */
    @Query("SELECT COUNT(h) FROM EmployeeShiftHistory h WHERE h.shift.id = :shiftId")
    long countByShiftId(@Param("shiftId") Long shiftId);

    /**
     * Find history by action performed by a specific employee
     */
    @Query("SELECT h FROM EmployeeShiftHistory h WHERE h.actionBy.idEmpleado = :actionById ORDER BY h.actionDate DESC")
    List<EmployeeShiftHistory> findByActionById(@Param("actionById") Long actionById);

    /**
     * Get most recent action for employee and shift
     */
    @Query("SELECT h FROM EmployeeShiftHistory h WHERE h.employee.idEmpleado = :employeeId AND h.shift.id = :shiftId ORDER BY h.actionDate DESC LIMIT 1")
    EmployeeShiftHistory findMostRecentActionForEmployeeAndShift(@Param("employeeId") Long employeeId, @Param("shiftId") Long shiftId);
}
