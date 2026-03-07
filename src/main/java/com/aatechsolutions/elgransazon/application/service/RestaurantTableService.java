package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.RestaurantTable;
import com.aatechsolutions.elgransazon.domain.entity.TableStatus;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for RestaurantTable management
 * 
 * Table Status Flow:
 * - AVAILABLE -> OCCUPIED: When an order is created for the table
 * - OCCUPIED -> AVAILABLE: When the order is paid
 * - AVAILABLE <-> OUT_OF_SERVICE: Manual change (only when table is AVAILABLE)
 * 
 * Note: Status changes are driven by order lifecycle, not reservations.
 * Reservations don't change table status directly.
 */
public interface RestaurantTableService {

    /**
     * Find all tables
     */
    List<RestaurantTable> findAll();

    /**
     * Find all tables ordered by table number
     */
    List<RestaurantTable> findAllOrderByTableNumber();

    /**
     * Find table by ID
     */
    Optional<RestaurantTable> findById(Long id);

    /**
     * Find table by table number
     */
    Optional<RestaurantTable> findByTableNumber(Integer tableNumber);

    /**
     * Create a new table
     */
    RestaurantTable create(RestaurantTable table, String username);

    /**
     * Update an existing table
     * Note: Status can only be changed between AVAILABLE and OUT_OF_SERVICE
     * OCCUPIED tables cannot have their status changed manually
     */
    RestaurantTable update(Long id, RestaurantTable table, String username);

    /**
     * Change table status (internal use - driven by order lifecycle)
     * This method is called when orders are created or paid
     */
    RestaurantTable changeStatus(Long id, TableStatus status, String username);

    /**
     * Find tables by status
     */
    List<RestaurantTable> findByStatus(TableStatus status);

    /**
     * Find available tables (status = AVAILABLE)
     */
    List<RestaurantTable> findAvailableTables();

    /**
     * Find tables that can be reserved (only AVAILABLE tables)
     */
    List<RestaurantTable> findReservableTables();

    /**
     * Find tables by location
     */
    List<RestaurantTable> findByLocation(String location);

    /**
     * Find tables with minimum capacity
     */
    List<RestaurantTable> findByMinimumCapacity(Integer capacity);

    /**
     * Count tables by status
     */
    long countByStatus(TableStatus status);

    /**
     * Count all occupied tables (status = OCCUPIED)
     */
    long countAllOccupiedTables();

    /**
     * Count all tables
     */
    long countAll();

    /**
     * Check if table number exists
     */
    boolean existsByTableNumber(Integer tableNumber);

    /**
     * Check if table number exists excluding a specific id (for updates)
     */
    boolean existsByTableNumberAndIdNot(Integer tableNumber, Long excludeId);

    /**
     * Get all distinct locations
     */
    List<String> getDistinctLocations();

    /**
     * Get the next available consecutive table number
     */
    Integer getNextTableNumber();

    /**
     * Find table by ID or throw exception
     */
    RestaurantTable findByIdOrThrow(Long id);

    /**
     * Save a table (for internal use)
     */
    RestaurantTable save(RestaurantTable table);

    /**
     * Check if a table can be used for a new order
     * Returns true only if:
     * - Table status is AVAILABLE
     * - There is no upcoming reservation within the average consumption time
     *   OR the current time has reached/passed the reservation time
     */
    boolean canTableBeUsedForOrder(Long tableId);

    /**
     * Check if a table is blocked due to an upcoming reservation
     * Returns true if there's a PENDING reservation within the average consumption time
     * and the reservation time has NOT been reached yet
     */
    boolean isTableBlockedByReservation(Long tableId);

    /**
     * Check if a table has a reservation but can still be used
     * Returns true if table is AVAILABLE and has a PENDING reservation
     * but there's enough time (more than average consumption time) before the reservation
     */
    boolean isTableUsableWithReservation(Long tableId);

    /**
     * Check if a table is in conflict (OCCUPIED but reservation time has arrived/passed)
     * Returns true if table is OCCUPIED and has a PENDING reservation
     * whose time has already arrived or passed
     */
    boolean isTableInConflict(Long tableId);

    /**
     * Check if a table is reserved now (AVAILABLE but reservation time has arrived/passed)
     * Returns true if table is AVAILABLE and has a PENDING reservation
     * whose time has already arrived or passed
     */
    boolean isTableReservedNow(Long tableId);

    /**
     * Get minutes until next reservation for a table
     * Returns null if no pending reservations
     */
    Long getMinutesUntilNextReservation(Long tableId);

    /**
     * Get the customer name for an active reservation on this table
     * Returns the customer name if there's a PENDING reservation whose time has arrived or passed
     * Returns null if no active reservation exists
     */
    String getActiveReservationCustomerName(Long tableId);
}
