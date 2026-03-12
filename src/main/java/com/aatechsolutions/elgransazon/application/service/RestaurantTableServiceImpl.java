package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.Reservation;
import com.aatechsolutions.elgransazon.domain.entity.RestaurantTable;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.domain.entity.TableStatus;
import com.aatechsolutions.elgransazon.domain.repository.ReservationRepository;
import com.aatechsolutions.elgransazon.domain.repository.RestaurantTableRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of RestaurantTableService
 * 
 * Table Status Flow:
 * - AVAILABLE -> OCCUPIED: When an order is created for the table
 * - OCCUPIED -> AVAILABLE: When the order is paid
 * - AVAILABLE <-> OUT_OF_SERVICE: Manual change (only when table is AVAILABLE)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RestaurantTableServiceImpl implements RestaurantTableService {

    private final RestaurantTableRepository tableRepository;
    private final ReservationRepository reservationRepository;
    private final SystemConfigurationService systemConfigurationService;
    private final DateTimeService dateTimeService;

    @Override
    public List<RestaurantTable> findAll() {
        log.debug("Fetching all restaurant tables");
        Company company = CompanyContext.requireCurrentCompany();
        return tableRepository.findByCompany(company);
    }

    @Override
    public List<RestaurantTable> findAllOrderByTableNumber() {
        log.debug("Fetching all restaurant tables ordered by table number");
        Company company = CompanyContext.requireCurrentCompany();
        return tableRepository.findAllByCompanyOrderByTableNumber(company);
    }

    @Override
    public Optional<RestaurantTable> findById(Long id) {
        log.debug("Finding restaurant table by ID: {}", id);
        Company company = CompanyContext.requireCurrentCompany();
        return tableRepository.findByIdAndCompany(id, company);
    }

    @Override
    public Optional<RestaurantTable> findByTableNumber(Integer tableNumber) {
        log.debug("Finding restaurant table by table number: {}", tableNumber);
        Company company = CompanyContext.requireCurrentCompany();
        return tableRepository.findByTableNumberAndCompany(tableNumber, company);
    }

    @Override
    @Transactional
    public RestaurantTable create(RestaurantTable table, String username) {
        log.info("Creating new restaurant table: {}", table.getTableNumber());

        // Set company from context
        Company company = CompanyContext.requireCurrentCompany();
        table.setCompany(company);

        // Validate table number is unique for this company
        if (tableRepository.existsByTableNumberAndCompany(table.getTableNumber(), company)) {
            String error = "El número de mesa " + table.getTableNumber() + " ya existe";
            log.error(error);
            throw new IllegalArgumentException(error);
        }

        // Validate capacity
        if (table.getCapacity() == null || table.getCapacity() < 1) {
            String error = "La capacidad debe ser al menos 1 persona";
            log.error(error);
            throw new IllegalArgumentException(error);
        }

        // Set audit fields
        table.setCreatedBy(username);
        table.setUpdatedBy(username);
        table.setCreatedAt(LocalDateTime.now());
        table.setUpdatedAt(LocalDateTime.now());

        // Set default status if not provided
        if (table.getStatus() == null) {
            table.setStatus(TableStatus.AVAILABLE);
        }

        RestaurantTable saved = tableRepository.save(table);
        log.info("Restaurant table created successfully with ID: {}", saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public RestaurantTable update(Long id, RestaurantTable table, String username) {
        log.info("Updating restaurant table with ID: {}", id);

        Company company = CompanyContext.requireCurrentCompany();

        RestaurantTable existing = tableRepository.findByIdAndCompany(id, company)
                .orElseThrow(() -> {
                    String error = "Mesa no encontrada con ID: " + id;
                    log.error(error);
                    return new IllegalArgumentException(error);
                });

        // Validate table number is unique for this company (excluding current table)
        if (!existing.getTableNumber().equals(table.getTableNumber()) &&
            tableRepository.existsByTableNumberAndCompany(table.getTableNumber(), company)) {
            String error = "El número de mesa " + table.getTableNumber() + " ya existe";
            log.error(error);
            throw new IllegalArgumentException(error);
        }

        // Validate capacity
        if (table.getCapacity() == null || table.getCapacity() < 1) {
            String error = "La capacidad debe ser al menos 1 persona";
            log.error(error);
            throw new IllegalArgumentException(error);
        }

        // IMPORTANT: Status can only be changed manually between AVAILABLE and OUT_OF_SERVICE
        // If current status is OCCUPIED, we cannot change it manually
        TableStatus requestedStatus = table.getStatus();
        if (existing.getStatus() == TableStatus.OCCUPIED) {
            // Table is OCCUPIED - cannot change status manually
            if (requestedStatus != null && requestedStatus != TableStatus.OCCUPIED) {
                String error = "No se puede cambiar el estado de una mesa ocupada. Primero debe pagarse el pedido.";
                log.error(error);
                throw new IllegalStateException(error);
            }
            // Keep current OCCUPIED status
            requestedStatus = TableStatus.OCCUPIED;
        } else {
            // Table is AVAILABLE or OUT_OF_SERVICE - can only toggle between these two
            if (requestedStatus != null && requestedStatus == TableStatus.OCCUPIED) {
                String error = "No se puede marcar una mesa como ocupada manualmente. El estado OCUPADA se asigna al crear un pedido.";
                log.error(error);
                throw new IllegalStateException(error);
            }
        }

        // Update fields
        existing.setTableNumber(table.getTableNumber());
        existing.setCapacity(table.getCapacity());
        existing.setLocation(table.getLocation());
        existing.setStatus(requestedStatus != null ? requestedStatus : existing.getStatus());
        existing.setComments(table.getComments());
        existing.setUpdatedBy(username);
        existing.setUpdatedAt(LocalDateTime.now());

        RestaurantTable updated = tableRepository.save(existing);
        log.info("Restaurant table updated successfully: {}", updated.getId());
        return updated;
    }

    @Override
    @Transactional
    public RestaurantTable changeStatus(Long id, TableStatus status, String username) {
        log.info("Changing status of table ID {} to {}", id, status);

        Company company = CompanyContext.requireCurrentCompany();

        RestaurantTable table = tableRepository.findByIdAndCompany(id, company)
                .orElseThrow(() -> {
                    String error = "Mesa no encontrada con ID: " + id;
                    log.error(error);
                    return new IllegalArgumentException(error);
                });

        table.setStatus(status);
        table.setUpdatedBy(username);
        table.setUpdatedAt(LocalDateTime.now());

        RestaurantTable updated = tableRepository.save(table);
        log.info("Table status changed successfully: {} -> {}", id, status);
        return updated;
    }

    @Override
    public List<RestaurantTable> findByStatus(TableStatus status) {
        log.debug("Finding tables by status: {}", status);
        Company company = CompanyContext.requireCurrentCompany();
        return tableRepository.findByStatusAndCompany(status, company);
    }

    @Override
    public List<RestaurantTable> findAvailableTables() {
        log.debug("Finding available tables");
        Company company = CompanyContext.requireCurrentCompany();
        return tableRepository.findAvailableTablesByCompany(company);
    }

    @Override
    public List<RestaurantTable> findReservableTables() {
        log.debug("Finding tables that can be reserved (only AVAILABLE)");
        Company company = CompanyContext.requireCurrentCompany();
        return tableRepository.findByStatusAndCompany(TableStatus.AVAILABLE, company);
    }

    @Override
    public List<RestaurantTable> findByLocation(String location) {
        log.debug("Finding tables by location: {}", location);
        Company company = CompanyContext.requireCurrentCompany();
        return tableRepository.findByLocationAndCompany(location, company);
    }

    @Override
    public List<RestaurantTable> findByMinimumCapacity(Integer capacity) {
        log.debug("Finding tables with minimum capacity: {}", capacity);
        Company company = CompanyContext.requireCurrentCompany();
        return tableRepository.findByCapacityGreaterThanEqualAndCompany(capacity, company);
    }

    @Override
    public long countByStatus(TableStatus status) {
        log.debug("Counting tables by status: {}", status);
        Company company = CompanyContext.requireCurrentCompany();
        return tableRepository.countByStatusAndCompany(status, company);
    }

    @Override
    public long countAllOccupiedTables() {
        log.debug("Counting all occupied tables (status = OCCUPIED)");
        Company company = CompanyContext.requireCurrentCompany();
        return tableRepository.countByStatusAndCompany(TableStatus.OCCUPIED, company);
    }

    @Override
    public long countAll() {
        log.debug("Counting all tables");
        Company company = CompanyContext.requireCurrentCompany();
        return tableRepository.findByCompany(company).size();
    }

    @Override
    public boolean existsByTableNumber(Integer tableNumber) {
        Company company = CompanyContext.requireCurrentCompany();
        return tableRepository.existsByTableNumberAndCompany(tableNumber, company);
    }

    @Override
    public boolean existsByTableNumberAndIdNot(Integer tableNumber, Long excludeId) {
        return tableRepository.existsByTableNumberAndIdNot(tableNumber, excludeId);
    }

    @Override
    public List<String> getDistinctLocations() {
        log.debug("Fetching distinct locations for company");
        Company company = CompanyContext.requireCurrentCompany();
        return tableRepository.findDistinctLocationsByCompany(company);
    }

    @Override
    public Integer getNextTableNumber() {
        log.debug("Calculating next table number for company");
        Company company = CompanyContext.requireCurrentCompany();
        Integer maxTableNumber = tableRepository.findMaxTableNumberByCompany(company);
        
        // If no tables exist, start from 1
        if (maxTableNumber == null) {
            log.debug("No existing tables found for company, starting from table number 1");
            return 1;
        }
        
        Integer nextNumber = maxTableNumber + 1;
        log.debug("Max table number: {}, next number: {}", maxTableNumber, nextNumber);
        return nextNumber;
    }

    @Override
    public RestaurantTable findByIdOrThrow(Long id) {
        return findById(id)
                .orElseThrow(() -> {
                    String error = "Mesa no encontrada con ID: " + id;
                    log.error(error);
                    return new IllegalArgumentException(error);
                });
    }

    @Override
    @Transactional
    public RestaurantTable save(RestaurantTable table) {
        log.debug("Saving restaurant table: {}", table.getId());
        // Set company from context if not already set
        if (table.getCompany() == null) {
            Company company = CompanyContext.requireCurrentCompany();
            table.setCompany(company);
        }
        return tableRepository.save(table);
    }

    @Override
    public boolean canTableBeUsedForOrder(Long tableId) {
        log.debug("Checking if table {} can be used for a new order", tableId);

        RestaurantTable table = findByIdOrThrow(tableId);

        // 1. Table must be AVAILABLE
        if (table.getStatus() != TableStatus.AVAILABLE) {
            log.debug("Table {} is not AVAILABLE (status={}), cannot be used for order", 
                     tableId, table.getStatus());
            return false;
        }

        // 2. Check if table is blocked by an upcoming reservation
        if (isTableBlockedByReservation(tableId)) {
            log.debug("Table {} is blocked by an upcoming reservation", tableId);
            return false;
        }

        log.debug("Table {} can be used for a new order", tableId);
        return true;
    }

    @Override
    public boolean isTableBlockedByReservation(Long tableId) {
        log.debug("Checking if table {} is blocked by a reservation", tableId);

        // Get system configuration for average consumption time
        SystemConfiguration config = systemConfigurationService.getConfiguration();
        Integer avgConsumptionMinutes = config.getAverageConsumptionTimeMinutes();

        LocalDate today = dateTimeService.todayLocal();
        LocalTime now = dateTimeService.nowLocal().toLocalTime();

        // Find the closest PENDING reservation for this table
        Optional<Reservation> closestReservationOpt = reservationRepository
                .findClosestPendingReservationForTable(tableId, today, now);

        if (closestReservationOpt.isEmpty()) {
            // No pending reservations, table is not blocked
            log.debug("Table {} has no pending reservations, not blocked", tableId);
            return false;
        }

        Reservation closestReservation = closestReservationOpt.get();
        LocalDate reservationDate = closestReservation.getReservationDate();
        LocalTime reservationTime = closestReservation.getReservationTime();
        LocalDateTime reservationDateTime = LocalDateTime.of(reservationDate, reservationTime);
        LocalDateTime currentDateTime = dateTimeService.nowLocal();

        // Calculate seconds until reservation (use seconds for precise comparison)
        long secondsUntilReservation = Duration.between(currentDateTime, reservationDateTime).toSeconds();
        long minutesUntilReservation = secondsUntilReservation / 60;

        log.debug("Closest reservation for table {}: {} at {}. Seconds until: {}, Minutes: {}, Avg consumption: {}", 
                 tableId, reservationDate, reservationTime, secondsUntilReservation, minutesUntilReservation, avgConsumptionMinutes);

        // If reservation time has already passed (negative seconds) or is NOW (0 seconds), table is NOT blocked (reservation is active)
        // We consider "now" only when seconds <= 0, not when minutes = 0 (which could be 59 seconds away)
        if (secondsUntilReservation <= 0) {
            log.debug("Reservation time has passed/arrived. Table {} is NOT blocked (reservation is active)", tableId);
            return false;
        }

        // If minutes until reservation is LESS THAN OR EQUAL TO average consumption time, table IS blocked
        // Use ceiling division to be conservative (e.g., 30 seconds = 1 minute for this check)
        // We use <= because if time equals avg consumption, there's no margin, so it should be blocked
        long effectiveMinutes = (secondsUntilReservation + 59) / 60; // Ceiling division
        if (effectiveMinutes <= avgConsumptionMinutes) {
            log.debug("Table {} IS BLOCKED: only {} minutes (~{} seconds) until reservation (need > {} min for consumption)", 
                     tableId, effectiveMinutes, secondsUntilReservation, avgConsumptionMinutes);
            return true;
        }

        // Enough time before reservation, table is not blocked
        log.debug("Table {} is NOT blocked: {} minutes until reservation (enough time)", 
                 tableId, minutesUntilReservation);
        return false;
    }

    @Override
    public boolean isTableUsableWithReservation(Long tableId) {
        log.debug("Checking if table {} is usable with reservation (Puede Ocuparse)", tableId);

        RestaurantTable table = findByIdOrThrow(tableId);

        // Only applies to AVAILABLE tables
        if (table.getStatus() != TableStatus.AVAILABLE) {
            return false;
        }

        LocalDate today = dateTimeService.todayLocal();
        LocalTime now = dateTimeService.nowLocal().toLocalTime();

        // FIRST: Check if there's already an active reservation (time has arrived)
        // If so, the table is simply AVAILABLE for that customer, not "Puede Ocuparse"
        Optional<Reservation> activeReservationOpt = reservationRepository
                .findFirstActivePendingReservationForTable(tableId, today, now);
        
        if (activeReservationOpt.isPresent()) {
            log.debug("Table {} has an active reservation (time arrived), not 'Puede Ocuparse'", tableId);
            return false;
        }

        // Get system configuration for average consumption time
        SystemConfiguration config = systemConfigurationService.getConfiguration();
        Integer avgConsumptionMinutes = config.getAverageConsumptionTimeMinutes();

        // Find the closest PENDING reservation for this table (future only)
        Optional<Reservation> closestReservationOpt = reservationRepository
                .findClosestPendingReservationForTable(tableId, today, now);

        if (closestReservationOpt.isEmpty()) {
            // No pending reservations
            return false;
        }

        Reservation closestReservation = closestReservationOpt.get();
        LocalDateTime reservationDateTime = LocalDateTime.of(
            closestReservation.getReservationDate(), 
            closestReservation.getReservationTime()
        );
        LocalDateTime currentDateTime = dateTimeService.nowLocal();

        // Calculate seconds until reservation
        long secondsUntilReservation = Duration.between(currentDateTime, reservationDateTime).toSeconds();

        // If reservation time has already passed, this is not "usable with reservation" scenario
        if (secondsUntilReservation <= 0) {
            return false;
        }

        // Use ceiling division for minutes
        long effectiveMinutes = (secondsUntilReservation + 59) / 60;

        // "Puede Ocuparse" = there's MORE time than avg consumption before reservation (strictly greater)
        // If time equals avg consumption, there's no margin, so it should NOT be "Puede Ocuparse"
        if (effectiveMinutes > avgConsumptionMinutes) {
            log.debug("Table {} CAN BE USED (Puede Ocuparse): {} minutes until reservation (> {} min avg consumption)", 
                     tableId, effectiveMinutes, avgConsumptionMinutes);
            return true;
        }

        return false;
    }

    @Override
    public boolean isTableInConflict(Long tableId) {
        log.debug("Checking if table {} is in conflict", tableId);

        RestaurantTable table = findByIdOrThrow(tableId);

        // Only applies to OCCUPIED tables
        if (table.getStatus() != TableStatus.OCCUPIED) {
            return false;
        }

        LocalDate today = dateTimeService.todayLocal();
        LocalTime now = dateTimeService.nowLocal().toLocalTime();

        // Check if there's a PENDING reservation whose time has arrived or passed
        // We need to find reservations where reservationTime <= now
        Optional<Reservation> activeReservationOpt = reservationRepository
                .findFirstActivePendingReservationForTable(tableId, today, now);

        if (activeReservationOpt.isPresent()) {
            Reservation reservation = activeReservationOpt.get();
            log.debug("Table {} IS IN CONFLICT: OCCUPIED but reservation at {} has arrived/passed", 
                     tableId, reservation.getReservationTime());
            return true;
        }

        return false;
    }

    @Override
    public boolean isTableReservedNow(Long tableId) {
        log.debug("Checking if table {} is reserved now", tableId);

        RestaurantTable table = findByIdOrThrow(tableId);

        // Only applies to AVAILABLE tables
        if (table.getStatus() != TableStatus.AVAILABLE) {
            return false;
        }

        LocalDate today = dateTimeService.todayLocal();
        LocalTime now = dateTimeService.nowLocal().toLocalTime();

        // Check if there's a PENDING reservation whose time has arrived or passed
        Optional<Reservation> activeReservationOpt = reservationRepository
                .findFirstActivePendingReservationForTable(tableId, today, now);

        if (activeReservationOpt.isPresent()) {
            Reservation reservation = activeReservationOpt.get();
            log.debug("Table {} IS RESERVED NOW: AVAILABLE but reservation at {} has arrived/passed", 
                     tableId, reservation.getReservationTime());
            return true;
        }

        return false;
    }

    @Override
    public Long getMinutesUntilNextReservation(Long tableId) {
        log.debug("Getting minutes until next reservation for table {}", tableId);

        LocalDate today = dateTimeService.todayLocal();
        LocalTime now = dateTimeService.nowLocal().toLocalTime();

        // Find the closest PENDING reservation for this table
        Optional<Reservation> closestReservationOpt = reservationRepository
                .findClosestPendingReservationForTable(tableId, today, now);

        if (closestReservationOpt.isEmpty()) {
            return null;
        }

        Reservation closestReservation = closestReservationOpt.get();
        LocalDateTime reservationDateTime = LocalDateTime.of(
            closestReservation.getReservationDate(), 
            closestReservation.getReservationTime()
        );
        LocalDateTime currentDateTime = dateTimeService.nowLocal();

        long secondsUntilReservation = Duration.between(currentDateTime, reservationDateTime).toSeconds();
        
        if (secondsUntilReservation <= 0) {
            return 0L;
        }

        // Ceiling division to get minutes
        return (secondsUntilReservation + 59) / 60;
    }

    @Override
    public String getActiveReservationCustomerName(Long tableId) {
        log.debug("Getting active reservation customer name for table {}", tableId);

        LocalDate today = dateTimeService.todayLocal();
        LocalTime now = dateTimeService.nowLocal().toLocalTime();

        // Find active pending reservation (time has arrived or passed)
        Optional<Reservation> activeReservationOpt = reservationRepository
                .findFirstActivePendingReservationForTable(tableId, today, now);

        if (activeReservationOpt.isEmpty()) {
            return null;
        }

        Reservation activeReservation = activeReservationOpt.get();
        String customerName = activeReservation.getCustomerName();
        
        if (customerName == null || customerName.isBlank()) {
            log.warn("Active reservation {} for table {} has no customer name", 
                     activeReservation.getId(), tableId);
            return null;
        }

        log.debug("Active reservation for table {}: customer is '{}'", tableId, customerName);
        return customerName;
    }
}
