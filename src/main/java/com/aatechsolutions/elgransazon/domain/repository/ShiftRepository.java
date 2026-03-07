package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Shift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Shift entity
 */
@Repository
public interface ShiftRepository extends JpaRepository<Shift, Long> {

    /**
     * Find all active shifts ordered by name
     */
    @Query("SELECT s FROM Shift s WHERE s.active = true ORDER BY s.name ASC")
    List<Shift> findByActiveTrue();

    /**
     * Find shifts by active status ordered by name
     */
    @Query("SELECT s FROM Shift s WHERE s.active = :active ORDER BY s.name ASC")
    List<Shift> findByActiveOrderByNameAsc(@Param("active") Boolean active);

    /**
     * Find shift by name (case insensitive)
     */
    @Query("SELECT s FROM Shift s WHERE LOWER(s.name) = LOWER(:name)")
    Optional<Shift> findByNameIgnoreCase(@Param("name") String name);

    /**
     * Check if shift name exists (case insensitive)
     */
    @Query("SELECT COUNT(s) > 0 FROM Shift s WHERE LOWER(s.name) = LOWER(:name)")
    boolean existsByNameIgnoreCase(@Param("name") String name);

    /**
     * Check if shift name exists excluding a specific ID
     */
    @Query("SELECT COUNT(s) > 0 FROM Shift s WHERE LOWER(s.name) = LOWER(:name) AND s.id != :id")
    boolean existsByNameIgnoreCaseAndIdNot(@Param("name") String name, @Param("id") Long id);

    /**
     * Find shifts for a specific employee
     */
    @Query("SELECT s FROM Shift s JOIN s.employees e WHERE e.idEmpleado = :employeeId ORDER BY s.name")
    List<Shift> findShiftsForEmployee(@Param("employeeId") Long employeeId);

    /**
     * Find all shifts ordered by name
     */
    @Query("SELECT s FROM Shift s ORDER BY s.name ASC")
    List<Shift> findAllOrderByName();

    /**
     * Count active shifts
     */
    @Query("SELECT COUNT(s) FROM Shift s WHERE s.active = true")
    long countActiveShifts();

    /**
     * Count total shifts
     */
    @Query("SELECT COUNT(s) FROM Shift s")
    long countAllShifts();

    /**
     * Find shifts without employees
     */
    @Query("SELECT s FROM Shift s WHERE s.employees IS EMPTY ORDER BY s.name")
    List<Shift> findShiftsWithoutEmployees();

    /**
     * Count employees in a shift
     */
    @Query("SELECT COUNT(e) FROM Shift s JOIN s.employees e WHERE s.id = :shiftId")
    long countEmployeesByShiftId(@Param("shiftId") Long shiftId);

    // ========== Multi-Tenant Methods (by Company) ==========

    /**
     * Find all shifts by company
     */
    List<Shift> findByCompany(Company company);

    /**
     * Find shift by ID and company (for security validation)
     */
    Optional<Shift> findByIdAndCompany(Long id, Company company);

    /**
     * Find all active shifts by company ordered by name
     */
    @Query("SELECT s FROM Shift s WHERE s.company = :company AND s.active = true ORDER BY s.name ASC")
    List<Shift> findActiveTrueByCompany(@Param("company") Company company);

    /**
     * Find all shifts by company ordered by name
     */
    @Query("SELECT s FROM Shift s WHERE s.company = :company ORDER BY s.name ASC")
    List<Shift> findAllByCompanyOrderByName(@Param("company") Company company);

    /**
     * Check if shift name exists for company (case insensitive)
     */
    @Query("SELECT COUNT(s) > 0 FROM Shift s WHERE s.company = :company AND LOWER(s.name) = LOWER(:name)")
    boolean existsByNameIgnoreCaseAndCompany(@Param("name") String name, @Param("company") Company company);

    /**
     * Find all active shifts by company ordered by name
     */
    @Query("SELECT s FROM Shift s WHERE s.company = :company AND s.active = true ORDER BY s.name ASC")
    List<Shift> findAllActiveByCompany(@Param("company") Company company);

    /**
     * Find shift by name and company
     */
    Optional<Shift> findByNameAndCompany(String name, Company company);

    /**
     * Check if shift name exists for company
     */
    boolean existsByNameAndCompany(String name, Company company);

    /**
     * Check if shift name exists for company excluding a specific ID
     */
    @Query("SELECT COUNT(s) > 0 FROM Shift s WHERE s.name = :name AND s.company = :company AND s.id != :id")
    boolean existsByNameAndCompanyAndIdNot(@Param("name") String name, @Param("company") Company company, @Param("id") Long id);

    /**
     * Count shifts by active status and company
     */
    long countByActiveAndCompany(boolean active, Company company);

    // ========== Migration Helper Methods ==========

    /**
     * Find all shifts without company (for data migration)
     */
    List<Shift> findByCompanyIsNull();
}
