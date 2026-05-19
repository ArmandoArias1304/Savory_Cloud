package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.EmployeeMonthlyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for EmployeeMonthlyStats entity
 * Handles database operations for employee monthly statistics
 */
@Repository
public interface EmployeeMonthlyStatsRepository extends JpaRepository<EmployeeMonthlyStats, Long> {

    /**
     * Find statistics for a specific employee in a specific month/year
     */
    Optional<EmployeeMonthlyStats> findByEmployeeAndMonthAndYear(Employee employee, Integer month, Integer year);

    /**
     * Find statistics for a specific employee by ID in a specific month/year
     */
    @Query("SELECT ems FROM EmployeeMonthlyStats ems WHERE ems.employee.idEmpleado = :employeeId AND ems.month = :month AND ems.year = :year")
    Optional<EmployeeMonthlyStats> findByEmployeeIdAndMonthAndYear(
        @Param("employeeId") Long employeeId,
        @Param("month") Integer month,
        @Param("year") Integer year
    );

    /**
     * Get all statistics for a specific month/year
     */
    List<EmployeeMonthlyStats> findByMonthAndYear(Integer month, Integer year);

    /**
     * Get top waiter of the month (highest total_sales)
     * Only considers employees with WAITER role
     */
    @Query(value = """
        SELECT ems.* 
        FROM employee_monthly_stats ems
        INNER JOIN employee e ON ems.employee_id = e.id_empleado
        INNER JOIN employee_roles er ON e.id_empleado = er.id_empleado
        INNER JOIN roles r ON er.id_rol = r.id_rol
        WHERE ems.month = :month 
        AND ems.year = :year 
        AND r.nombre_rol = 'ROLE_WAITER'
        AND ems.total_sales > 0
        ORDER BY ems.total_sales DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<EmployeeMonthlyStats> findTopWaiterOfMonth(
        @Param("month") Integer month,
        @Param("year") Integer year
    );

    /**
     * Get top chef of the month (highest total_orders)
     * Only considers employees with CHEF role
     */
    @Query(value = """
        SELECT ems.* 
        FROM employee_monthly_stats ems
        INNER JOIN employee e ON ems.employee_id = e.id_empleado
        INNER JOIN employee_roles er ON e.id_empleado = er.id_empleado
        INNER JOIN roles r ON er.id_rol = r.id_rol
        WHERE ems.month = :month 
        AND ems.year = :year 
        AND r.nombre_rol = 'ROLE_CHEF'
        AND ems.total_orders > 0
        ORDER BY ems.total_orders DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<EmployeeMonthlyStats> findTopChefOfMonth(
        @Param("month") Integer month,
        @Param("year") Integer year
    );

    /**
     * Get the barista of the month (most orders prepared)
     */
    @Query(value = """
        SELECT ems.* 
        FROM employee_monthly_stats ems
        INNER JOIN employee e ON ems.employee_id = e.id_empleado
        INNER JOIN employee_roles er ON e.id_empleado = er.id_empleado
        INNER JOIN roles r ON er.id_rol = r.id_rol
        WHERE ems.month = :month 
        AND ems.year = :year 
        AND r.nombre_rol = 'ROLE_BARISTA'
        AND ems.total_orders > 0
        ORDER BY ems.total_orders DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<EmployeeMonthlyStats> findTopBaristaOfMonth(
        @Param("month") Integer month,
        @Param("year") Integer year
    );

    /**
     * Get the parrillero of the month (most orders prepared)
     */
    @Query(value = """
        SELECT ems.* 
        FROM employee_monthly_stats ems
        INNER JOIN employee e ON ems.employee_id = e.id_empleado
        INNER JOIN employee_roles er ON e.id_empleado = er.id_empleado
        INNER JOIN roles r ON er.id_rol = r.id_rol
        WHERE ems.month = :month 
        AND ems.year = :year 
        AND r.nombre_rol = 'ROLE_PARRILLERO'
        AND ems.total_orders > 0
        ORDER BY ems.total_orders DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<EmployeeMonthlyStats> findTopParrilleroOfMonth(
        @Param("month") Integer month,
        @Param("year") Integer year
    );

    /**
     * Get all waiters of the month ordered by sales (limit in service layer)
     */
    @Query(value = """
        SELECT ems.* 
        FROM employee_monthly_stats ems
        INNER JOIN employee e ON ems.employee_id = e.id_empleado
        INNER JOIN employee_roles er ON e.id_empleado = er.id_empleado
        INNER JOIN roles r ON er.id_rol = r.id_rol
        WHERE ems.month = :month 
        AND ems.year = :year 
        AND r.nombre_rol = 'ROLE_WAITER'
        AND ems.total_sales > 0
        ORDER BY ems.total_sales DESC
        """, nativeQuery = true)
    List<EmployeeMonthlyStats> findTopWaitersOfMonth(
        @Param("month") Integer month,
        @Param("year") Integer year
    );

    /**
     * Get all chefs of the month ordered by orders (limit in service layer)
     */
    @Query(value = """
        SELECT ems.* 
        FROM employee_monthly_stats ems
        INNER JOIN employee e ON ems.employee_id = e.id_empleado
        INNER JOIN employee_roles er ON e.id_empleado = er.id_empleado
        INNER JOIN roles r ON er.id_rol = r.id_rol
        WHERE ems.month = :month 
        AND ems.year = :year 
        AND r.nombre_rol = 'ROLE_CHEF'
        AND ems.total_orders > 0
        ORDER BY ems.total_orders DESC
        """, nativeQuery = true)
    List<EmployeeMonthlyStats> findTopChefsOfMonth(
        @Param("month") Integer month,
        @Param("year") Integer year
    );

    /**
     * Get all parrilleros of the month ordered by orders (limit in service layer)
     */
    @Query(value = """
        SELECT ems.* 
        FROM employee_monthly_stats ems
        INNER JOIN employee e ON ems.employee_id = e.id_empleado
        INNER JOIN employee_roles er ON e.id_empleado = er.id_empleado
        INNER JOIN roles r ON er.id_rol = r.id_rol
        WHERE ems.month = :month 
        AND ems.year = :year 
        AND r.nombre_rol = 'ROLE_PARRILLERO'
        AND ems.total_orders > 0
        ORDER BY ems.total_orders DESC
        """, nativeQuery = true)
    List<EmployeeMonthlyStats> findTopParrillerosOfMonth(
        @Param("month") Integer month,
        @Param("year") Integer year
    );

    /**
     * Get top cashier of the month (highest total_sales)
     * Only considers employees with CASHIER role
     */
    @Query(value = """
        SELECT ems.* 
        FROM employee_monthly_stats ems
        INNER JOIN employee e ON ems.employee_id = e.id_empleado
        INNER JOIN employee_roles er ON e.id_empleado = er.id_empleado
        INNER JOIN roles r ON er.id_rol = r.id_rol
        WHERE ems.month = :month 
        AND ems.year = :year 
        AND r.nombre_rol = 'ROLE_CASHIER'
        AND ems.total_sales > 0
        ORDER BY ems.total_sales DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<EmployeeMonthlyStats> findTopCashierOfMonth(
        @Param("month") Integer month,
        @Param("year") Integer year
    );

    /**
     * Get all cashiers of the month ordered by sales (limit in service layer)
     */
    @Query(value = """
        SELECT ems.* 
        FROM employee_monthly_stats ems
        INNER JOIN employee e ON ems.employee_id = e.id_empleado
        INNER JOIN employee_roles er ON e.id_empleado = er.id_empleado
        INNER JOIN roles r ON er.id_rol = r.id_rol
        WHERE ems.month = :month 
        AND ems.year = :year 
        AND r.nombre_rol = 'ROLE_CASHIER'
        AND ems.total_sales > 0
        ORDER BY ems.total_sales DESC
        """, nativeQuery = true)
    List<EmployeeMonthlyStats> findTopCashiersOfMonth(
        @Param("month") Integer month,
        @Param("year") Integer year
    );

    /**
     * Get all statistics for a specific employee
     */
    @Query("SELECT ems FROM EmployeeMonthlyStats ems WHERE ems.employee.idEmpleado = :employeeId ORDER BY ems.year DESC, ems.month DESC")
    List<EmployeeMonthlyStats> findAllByEmployeeId(@Param("employeeId") Long employeeId);

    /**
     * Delete statistics older than specified months
     * Useful for cleanup of old data
     */
    @Query("DELETE FROM EmployeeMonthlyStats ems WHERE (ems.year * 12 + ems.month) < :cutoffYearMonth")
    void deleteOlderThan(@Param("cutoffYearMonth") Integer cutoffYearMonth);
}
