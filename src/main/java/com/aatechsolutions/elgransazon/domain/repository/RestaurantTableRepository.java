package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.RestaurantTable;
import com.aatechsolutions.elgransazon.domain.entity.TableStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for RestaurantTable entity
 */
@Repository
public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, Long> {

    /**
     * Find table by table number
     */
    Optional<RestaurantTable> findByTableNumber(Integer tableNumber);

    /**
     * Find all tables by status
     */
    List<RestaurantTable> findByStatus(TableStatus status);

    /**
     * Find all tables by location
     */
    List<RestaurantTable> findByLocation(String location);

    /**
     * Find tables with capacity greater than or equal to specified value
     */
    List<RestaurantTable> findByCapacityGreaterThanEqual(Integer capacity);

    /**
     * Count tables by status
     */
    long countByStatus(TableStatus status);

    /**
     * Check if table number exists
     */
    boolean existsByTableNumber(Integer tableNumber);

    /**
     * Check if table number exists excluding a specific id (for updates)
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM RestaurantTable t " +
           "WHERE t.tableNumber = :tableNumber AND t.id <> :excludeId")
    boolean existsByTableNumberAndIdNot(@Param("tableNumber") Integer tableNumber, 
                                        @Param("excludeId") Long excludeId);

    /**
     * Find all tables ordered by table number
     */
    @Query("SELECT t FROM RestaurantTable t ORDER BY t.tableNumber ASC")
    List<RestaurantTable> findAllOrderByTableNumber();

    /**
     * Find available tables ordered by capacity
     */
    @Query("SELECT t FROM RestaurantTable t WHERE t.status = 'AVAILABLE' ORDER BY t.capacity ASC")
    List<RestaurantTable> findAvailableTablesOrderByCapacity();

    /**
     * Get all distinct locations
     */
    @Query("SELECT DISTINCT t.location FROM RestaurantTable t WHERE t.location IS NOT NULL ORDER BY t.location")
    List<String> findDistinctLocations();

    /**
     * Get all distinct locations by company
     */
    @Query("SELECT DISTINCT t.location FROM RestaurantTable t WHERE t.company = :company AND t.location IS NOT NULL ORDER BY t.location")
    List<String> findDistinctLocationsByCompany(@Param("company") Company company);

    /**
     * Find the maximum table number
     */
    @Query("SELECT MAX(t.tableNumber) FROM RestaurantTable t")
    Integer findMaxTableNumber();

    /**
     * Find the maximum table number by company
     */
    @Query("SELECT MAX(t.tableNumber) FROM RestaurantTable t WHERE t.company = :company")
    Integer findMaxTableNumberByCompany(@Param("company") Company company);

    // ========== Multi-Tenant Methods (by Company) ==========

    /**
     * Find all tables by company
     */
    List<RestaurantTable> findByCompany(Company company);

    /**
     * Find table by ID and company (for security validation)
     */
    Optional<RestaurantTable> findByIdAndCompany(Long id, Company company);

    /**
     * Find table by table number and company
     */
    Optional<RestaurantTable> findByTableNumberAndCompany(Integer tableNumber, Company company);

    /**
     * Find all tables by status and company
     */
    List<RestaurantTable> findByStatusAndCompany(TableStatus status, Company company);

    /**
     * Find all tables by company ordered by table number
     */
    @Query("SELECT t FROM RestaurantTable t WHERE t.company = :company ORDER BY t.tableNumber ASC")
    List<RestaurantTable> findAllByCompanyOrderByTableNumber(@Param("company") Company company);

    /**
     * Check if table number exists for company
     */
    boolean existsByTableNumberAndCompany(Integer tableNumber, Company company);

    /**
     * Count tables by status and company
     */
    long countByStatusAndCompany(TableStatus status, Company company);

    /**
     * Find available tables by company
     */
    @Query("SELECT t FROM RestaurantTable t WHERE t.company = :company AND t.status = 'AVAILABLE' ORDER BY t.capacity ASC")
    List<RestaurantTable> findAvailableTablesByCompany(@Param("company") Company company);

    /**
     * Find tables by location and company
     * MULTI-TENANT: Filters by company
     */
    List<RestaurantTable> findByLocationAndCompany(String location, Company company);

    /**
     * Find tables by minimum capacity and company
     * MULTI-TENANT: Filters by company
     */
    List<RestaurantTable> findByCapacityGreaterThanEqualAndCompany(Integer capacity, Company company);

    // ========== Migration Helper Methods ==========

    /**
     * Find all tables without company (for data migration)
     */
    List<RestaurantTable> findByCompanyIsNull();
}
