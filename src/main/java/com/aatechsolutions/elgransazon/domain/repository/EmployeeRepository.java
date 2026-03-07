package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Employee entity
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /**
     * Find an employee by their username (used for login)
     * 
     * @param username the employee's username
     * @return Optional containing the employee if found
     */
    Optional<Employee> findByUsername(String username);

    /**
     * Check if an employee exists by username
     * 
     * @param username the employee's username
     * @return true if employee exists, false otherwise
     */
    boolean existsByUsername(String username);

    /**
     * Find an employee by their phone number
     * 
     * @param telefono the employee's phone number
     * @return Optional containing the employee if found
     */
    Optional<Employee> findByTelefono(String telefono);

    /**
     * Check if an employee exists by phone number
     * 
     * @param telefono the employee's phone number
     * @return true if employee exists, false otherwise
     */
    boolean existsByTelefono(String telefono);

    /**
     * Find all enabled employees
     * 
     * @return List of enabled employees
     */
    List<Employee> findByEnabledTrue();

    /**
     * Count enabled employees
     * 
     * @return Number of enabled employees
     */
    long countByEnabledTrue();

    /**
     * Find employees by supervisor
     * 
     * @param supervisorId Supervisor's employee ID
     * @return List of employees supervised by this employee
     */
    List<Employee> findBySupervisorIdEmpleado(Long supervisorId);

    /**
     * Update last access timestamp for an employee by username
     * Uses native query to avoid validation
     * 
     * @param username Employee's username
     * @param lastAccess New last access timestamp
     */
    @Modifying
    @Query("UPDATE Employee e SET e.lastAccess = :lastAccess WHERE e.username = :username")
    void updateLastAccessByUsername(@Param("username") String username, @Param("lastAccess") LocalDateTime lastAccess);

    /**
     * Count enabled employees excluding PROGRAMMER role
     * Used for license user limit validation
     * PROGRAMMER is not counted towards the license user limit
     * 
     * @return Number of enabled employees excluding PROGRAMMER
     */
    @Query("SELECT COUNT(DISTINCT e.id) FROM Employee e " +
           "LEFT JOIN e.roles r " +
           "WHERE e.enabled = true " +
           "AND (r.nombreRol IS NULL OR r.nombreRol != 'ROLE_PROGRAMMER')")
    long countEnabledExcludingProgrammer();

    /**
     * Count all employees excluding PROGRAMMER role
     * PROGRAMMER is not included in employee statistics
     * 
     * @return Number of all employees excluding PROGRAMMER
     */
    @Query("SELECT COUNT(DISTINCT e.id) FROM Employee e " +
           "LEFT JOIN e.roles r " +
           "WHERE (r.nombreRol IS NULL OR r.nombreRol != 'ROLE_PROGRAMMER')")
    long countAllExcludingProgrammer();

    // ========== Multi-Tenant Methods (by Company) ==========

    /**
     * Find employee by username and company
     * Note: PROGRAMMER has company=null, so use findByUsernameAndCompanyIsNull for global search
     */
    Optional<Employee> findByUsernameAndCompany(String username, Company company);

    /**
     * Find employee by username where company is null (for PROGRAMMER role)
     */
    Optional<Employee> findByUsernameAndCompanyIsNull(String username);

    /**
     * Check if username exists for a company
     */
    boolean existsByUsernameAndCompany(String username, Company company);

    /**
     * Check if username exists for a company (case-insensitive)
     */
    boolean existsByUsernameIgnoreCaseAndCompany(String username, Company company);

    /**
     * Check if phone exists for a company
     */
    boolean existsByTelefonoAndCompany(String telefono, Company company);

    /**
     * Find all enabled employees by company
     */
    List<Employee> findByEnabledTrueAndCompany(Company company);

    /**
     * Find all employees by company
     */
    List<Employee> findByCompany(Company company);

    /**
     * Count enabled employees by company
     */
    long countByEnabledTrueAndCompany(Company company);

    /**
     * Find employees by supervisor and company
     */
    List<Employee> findBySupervisorIdEmpleadoAndCompany(Long supervisorId, Company company);

    /**
     * Find employee by ID and company (for security validation)
     */
    Optional<Employee> findByIdEmpleadoAndCompany(Long idEmpleado, Company company);

    /**
     * Count enabled employees by company excluding PROGRAMMER role
     */
    @Query("SELECT COUNT(DISTINCT e.idEmpleado) FROM Employee e " +
           "LEFT JOIN e.roles r " +
           "WHERE e.enabled = true AND e.company = :company " +
           "AND (r.nombreRol IS NULL OR r.nombreRol != 'ROLE_PROGRAMMER')")
    long countEnabledByCompanyExcludingProgrammer(@Param("company") Company company);
}
