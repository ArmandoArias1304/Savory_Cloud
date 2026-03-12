package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.ReservationRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing restaurant reservations
 * 
 * Reservation Status Flow:
 * - PENDING: Initial state - customer hasn't arrived yet
 * - COMPLETED: Order was created with this reservation's ID
 * - CANCELLED: Reservation was cancelled manually
 * 
 * Note: Reservations do NOT change table status directly.
 * Table status is driven by order lifecycle (create order -> OCCUPIED, pay order -> AVAILABLE)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final RestaurantTableService tableService;
    private final SystemConfigurationService systemConfigurationService;
    private final DateTimeService dateTimeService;

    /**
     * Find all reservations ordered by date and time (descending)
     */
    public List<Reservation> findAllOrderByDateTimeDesc() {
        log.debug("Finding all reservations ordered by date and time desc");
        Company company = CompanyContext.requireCurrentCompany();
        return reservationRepository.findAllByCompanyOrderByReservationDateDescReservationTimeDesc(company);
    }

    /**
     * Find reservation by ID
     */
    public Optional<Reservation> findById(Long id) {
        log.debug("Finding reservation by id: {}", id);
        Company company = CompanyContext.requireCurrentCompany();
        return reservationRepository.findByIdAndCompany(id, company);
    }

    /**
     * Find reservation by ID or throw exception
     */
    public Reservation findByIdOrThrow(Long id) {
        return findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservación no encontrada con ID: " + id));
    }

    /**
     * Find active reservations (PENDING status)
     */
    public List<Reservation> findActiveReservations() {
        log.debug("Finding active reservations (PENDING)");
        Company company = CompanyContext.requireCurrentCompany();
        return reservationRepository.findActiveReservationsByCompany(company);
    }

    /**
     * Find upcoming reservations (today or future, PENDING status)
     */
    public List<Reservation> findUpcomingReservations() {
        log.debug("Finding upcoming reservations");
        Company company = CompanyContext.requireCurrentCompany();
        return reservationRepository.findUpcomingReservationsByCompany(company, dateTimeService.todayLocal());
    }

    /**
     * Find reservations for today
     */
    public List<Reservation> findTodayReservations() {
        log.debug("Finding today's reservations");
        Company company = CompanyContext.requireCurrentCompany();
        return reservationRepository.findTodayReservationsByCompany(company, dateTimeService.todayLocal());
    }

    /**
     * Find reservations by date
     */
    public List<Reservation> findByDate(LocalDate date) {
        log.debug("Finding reservations for date: {}", date);
        Company company = CompanyContext.requireCurrentCompany();
        return reservationRepository.findByReservationDateAndCompanyOrderByReservationTimeAsc(date, company);
    }

    /**
     * Find reservations by date range
     */
    public List<Reservation> findByDateRange(LocalDate startDate, LocalDate endDate) {
        log.debug("Finding reservations between {} and {}", startDate, endDate);
        Company company = CompanyContext.requireCurrentCompany();
        return reservationRepository.findByReservationDateBetweenAndCompanyOrderByReservationDateAscReservationTimeAsc(
                startDate, endDate, company);
    }

    /**
     * Find reservations by status
     */
    public List<Reservation> findByStatus(ReservationStatus status) {
        log.debug("Finding reservations with status: {}", status);
        Company company = CompanyContext.requireCurrentCompany();
        return reservationRepository.findByStatusAndCompanyOrderByReservationDateDescReservationTimeDesc(status, company);
    }

    /**
     * Find reservations by date and status
     */
    public List<Reservation> findByDateAndStatus(LocalDate date, ReservationStatus status) {
        log.debug("Finding reservations for date: {} with status: {}", date, status);
        Company company = CompanyContext.requireCurrentCompany();
        return reservationRepository.findByReservationDateAndStatusAndCompanyOrderByReservationTimeAsc(date, status, company);
    }

    /**
     * Find next PENDING reservation for a table (future reservations)
     */
    public Optional<Reservation> findNextReservationForTable(Long tableId) {
        log.debug("Finding next PENDING reservation for table: {}", tableId);
        LocalDate today = dateTimeService.todayLocal();
        LocalTime now = dateTimeService.nowLocal().toLocalTime();
        return reservationRepository.findNextReservationForTable(tableId, today, now);
    }

    /**
     * Find the first PENDING reservation that is due (time has arrived or passed)
     * This is the reservation that should be activated when creating an order
     */
    public Optional<Reservation> findFirstActivePendingReservationForTable(Long tableId) {
        log.debug("Finding first active PENDING reservation for table: {}", tableId);
        LocalDate today = dateTimeService.todayLocal();
        LocalTime now = dateTimeService.nowLocal().toLocalTime();
        return reservationRepository.findFirstActivePendingReservationForTable(tableId, today, now);
    }

    /**
     * Find all PENDING reservations for a table (today and future)
     */
    public List<Reservation> findPendingReservationsForTable(Long tableId) {
        log.debug("Finding all PENDING reservations for table: {}", tableId);
        return reservationRepository.findPendingReservationsForTable(tableId, dateTimeService.todayLocal());
    }

    /**
     * Find reservations by customer phone
     */
    public List<Reservation> findByCustomerPhone(String phone) {
        log.debug("Finding reservations for customer phone: {}", phone);
        return reservationRepository.findByCustomerPhoneContainingOrderByReservationDateDescReservationTimeDesc(phone);
    }

    /**
     * Find reservations by customer name
     */
    public List<Reservation> findByCustomerName(String name) {
        log.debug("Finding reservations for customer name: {}", name);
        return reservationRepository.findByCustomerNameContainingIgnoreCaseOrderByReservationDateDescReservationTimeDesc(name);
    }

    /**
     * Count reservations by status
     */
    public long countByStatus(ReservationStatus status) {
        Company company = CompanyContext.requireCurrentCompany();
        return reservationRepository.countByStatusAndCompany(status, company);
    }

    /**
     * Count today's reservations
     */
    public long countTodayReservations() {
        Company company = CompanyContext.requireCurrentCompany();
        return reservationRepository.countTodayReservationsByCompany(company, dateTimeService.todayLocal());
    }

    /**
     * Count today's PENDING reservations
     */
    public long countTodayActiveReservations() {
        Company company = CompanyContext.requireCurrentCompany();
        return reservationRepository.countTodayActiveReservationsByCompany(company, dateTimeService.todayLocal());
    }

    /**
     * Create a new reservation with validations
     * Only AVAILABLE tables can be reserved
     */
    @Transactional
    public Reservation create(Reservation reservation, String username) {
        log.info("Creating new reservation for customer: {} by user: {}", 
                reservation.getCustomerName(), username);

        // Load full table entity (Spring binding only sets the ID)
        RestaurantTable table = tableService.findByIdOrThrow(reservation.getRestaurantTable().getId());
        reservation.setRestaurantTable(table);

        // Set company from context
        Company company = CompanyContext.requireCurrentCompany();
        reservation.setCompany(company);

        // Set audit fields
        reservation.setCreatedBy(username);
        reservation.setStatus(ReservationStatus.PENDING);

        // Validate reservation
        validateReservation(reservation, null);

        // Save reservation
        Reservation saved = reservationRepository.save(reservation);

        // Note: We do NOT change table status when creating a reservation
        // Table status only changes when orders are created/paid

        log.info("Reservation created successfully with ID: {}", saved.getId());
        return saved;
    }

    /**
     * Update an existing reservation
     */
    @Transactional
    public Reservation update(Long id, Reservation updatedReservation, String username) {
        log.info("Updating reservation: {} by user: {}", id, username);

        Reservation existing = findByIdOrThrow(id);

        // Check if reservation can be edited (only PENDING)
        if (!existing.isEditable()) {
            throw new IllegalStateException("No se puede editar una reservación en estado: " + 
                    existing.getStatusDisplayName());
        }

        // Load full table entity if table is being changed
        Long newTableId = updatedReservation.getRestaurantTable().getId();
        RestaurantTable newTable = tableService.findByIdOrThrow(newTableId);

        // Update fields
        existing.setCustomerName(updatedReservation.getCustomerName());
        existing.setCustomerPhone(updatedReservation.getCustomerPhone());
        existing.setCustomerEmail(updatedReservation.getCustomerEmail());
        existing.setNumberOfGuests(updatedReservation.getNumberOfGuests());
        existing.setReservationDate(updatedReservation.getReservationDate());
        existing.setReservationTime(updatedReservation.getReservationTime());
        existing.setSpecialRequests(updatedReservation.getSpecialRequests());
        existing.setRestaurantTable(newTable);
        existing.setUpdatedBy(username);

        // Validate reservation
        validateReservation(existing, id);

        // Save reservation
        Reservation saved = reservationRepository.save(existing);

        log.info("Reservation updated successfully: {}", id);
        return saved;
    }

    /**
     * Change reservation status
     */
    @Transactional
    public Reservation changeStatus(Long id, ReservationStatus newStatus, String username) {
        log.info("Changing reservation {} status to {} by user: {}", id, newStatus, username);

        Reservation reservation = findByIdOrThrow(id);
        ReservationStatus oldStatus = reservation.getStatus();

        // Validate status transition
        validateStatusTransition(reservation, newStatus);

        // Update status
        reservation.setStatus(newStatus);
        reservation.setUpdatedBy(username);

        // Save reservation
        Reservation saved = reservationRepository.save(reservation);

        log.info("Reservation status changed from {} to {}", oldStatus, newStatus);
        return saved;
    }

    /**
     * Mark a reservation as COMPLETED
     * This is called when an order is created with this reservation's ID
     */
    @Transactional
    public Reservation markAsCompleted(Long id, String username) {
        log.info("Marking reservation {} as COMPLETED by user: {}", id, username);
        return changeStatus(id, ReservationStatus.COMPLETED, username);
    }

    /**
     * Cancel a reservation
     */
    @Transactional
    public Reservation cancel(Long id, String username) {
        log.info("Cancelling reservation {} by user: {}", id, username);
        return changeStatus(id, ReservationStatus.CANCELLED, username);
    }

    /**
     * Delete a reservation (soft delete by cancelling)
     */
    @Transactional
    public void delete(Long id, String username) {
        log.info("Deleting reservation: {} by user: {}", id, username);
        cancel(id, username);
    }

    /**
     * Validate reservation business rules
     */
    private void validateReservation(Reservation reservation, Long excludeId) {
        // 1. Validate table is AVAILABLE (only AVAILABLE tables can be reserved)
        validateTableAvailability(reservation.getRestaurantTable());

        // 2. Validate date is today or future
        validateReservationDate(reservation.getReservationDate());

        // 3. Validate time is within business hours
        validateReservationTime(reservation.getReservationDate(), reservation.getReservationTime());

        // 4. Validate table capacity
        validateTableCapacity(reservation.getRestaurantTable(), reservation.getNumberOfGuests());

        // 5. Validate no overlapping reservations
        validateNoOverlappingReservations(
                reservation.getRestaurantTable().getId(),
                reservation.getReservationDate(),
                reservation.getReservationTime(),
                excludeId
        );
    }

    /**
     * Validate table is available for reservation (only AVAILABLE status)
     */
    private void validateTableAvailability(RestaurantTable table) {
        if (table.getStatus() != TableStatus.AVAILABLE) {
            String statusMsg = table.getStatus() == TableStatus.OCCUPIED 
                ? "está ocupada (tiene un pedido activo)" 
                : "está fuera de servicio";
            throw new IllegalArgumentException(
                    String.format("La mesa %s %s y no se puede reservar",
                            table.getDisplayName(), statusMsg));
        }
    }

    /**
     * Validate reservation date (must be today or future)
     */
    private void validateReservationDate(LocalDate reservationDate) {
        LocalDate today = dateTimeService.todayLocal();
        if (reservationDate.isBefore(today)) {
            throw new IllegalArgumentException("La fecha de reservación debe ser hoy o una fecha futura");
        }
    }

    /**
     * Validate reservation time is within business hours
     */
    private void validateReservationTime(LocalDate reservationDate, LocalTime reservationTime) {
        SystemConfiguration config = systemConfigurationService.getConfiguration();

        // 0. If reservation is for today, check that time is not in the past
        LocalDate today = dateTimeService.todayLocal();
        if (reservationDate.equals(today)) {
            LocalTime currentTime = dateTimeService.nowLocal().toLocalTime();
            if (reservationTime.isBefore(currentTime) || reservationTime.equals(currentTime)) {
                DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
                throw new IllegalArgumentException(
                        String.format("No se puede crear una reservación en el pasado. La hora actual es %s y la hora seleccionada es %s",
                                currentTime.format(timeFormatter),
                                reservationTime.format(timeFormatter)));
            }
        }

        // Convert LocalDate to DayOfWeek
        java.time.DayOfWeek javaDayOfWeek = reservationDate.getDayOfWeek();
        DayOfWeek dayOfWeek = DayOfWeek.valueOf(javaDayOfWeek.name());

        // 1. Check if day is a work day
        if (!config.isWorkDay(dayOfWeek)) {
            throw new IllegalArgumentException("El día seleccionado (" + dayOfWeek.getDisplayName() + 
                    ") no es un día laborable");
        }

        // 2. Get business hours for that day
        BusinessHours businessHours = config.getBusinessHoursForDay(dayOfWeek)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No hay horario configurado para " + dayOfWeek.getDisplayName()));

        // 3. Check if restaurant is closed that day
        if (businessHours.getIsClosed()) {
            throw new IllegalArgumentException("El restaurante está cerrado los " + dayOfWeek.getDisplayName());
        }

        LocalTime openTime = businessHours.getOpenTime();
        LocalTime closeTime = businessHours.getCloseTime();
        Integer avgConsumption = config.getAverageConsumptionTimeMinutes();

        // 4. Calculate last reservation time (closeTime - avgConsumption)
        LocalTime lastReservationTime = closeTime.minusMinutes(avgConsumption);

        // 5. Validate reservation time is within valid range
        if (reservationTime.isBefore(openTime)) {
            throw new IllegalArgumentException(
                    String.format("La hora de reservación debe ser después de las %s (hora de apertura)",
                            openTime.toString()));
        }

        if (reservationTime.isAfter(lastReservationTime)) {
            throw new IllegalArgumentException(
                    String.format("La última reservación permitida es a las %s (considerando %s de tiempo de consumo antes del cierre a las %s)",
                            lastReservationTime.toString(),
                            config.getAverageConsumptionTimeDisplay(),
                            closeTime.toString()));
        }
    }

    /**
     * Validate table capacity
     */
    private void validateTableCapacity(RestaurantTable table, Integer numberOfGuests) {
        if (table.getCapacity() == null) {
            throw new IllegalStateException(
                    String.format("La mesa %s no tiene capacidad definida", table.getDisplayName()));
        }
        
        if (numberOfGuests > table.getCapacity()) {
            throw new IllegalArgumentException(
                    String.format("La mesa %s tiene capacidad para %d personas, no para %d",
                            table.getDisplayName(),
                            table.getCapacity(),
                            numberOfGuests));
        }
    }

    /**
     * Validate no overlapping reservations
     * Only considers PENDING reservations
     */
    private void validateNoOverlappingReservations(Long tableId, LocalDate date, 
                                                   LocalTime startTime, Long excludeId) {
        SystemConfiguration config = systemConfigurationService.getConfiguration();
        Integer avgConsumption = config.getAverageConsumptionTimeMinutes();

        log.debug("=== Validating overlapping reservations ===");
        log.debug("Table ID: {}", tableId);
        log.debug("Date: {}", date);
        log.debug("Start time: {}", startTime);
        log.debug("Avg consumption: {} minutes", avgConsumption);

        // Calculate end time
        LocalTime endTime = startTime.plusMinutes(avgConsumption);
        log.debug("Calculated end time: {}", endTime);

        // Convert average consumption to seconds for the native query
        Integer avgConsumptionSeconds = avgConsumption * 60;
        log.debug("Avg consumption seconds: {}", avgConsumptionSeconds);

        Long overlapCount = reservationRepository.countOverlappingReservations(
                tableId, date, startTime, endTime, avgConsumptionSeconds, excludeId);

        log.debug("Overlap count: {}", overlapCount);

        if (overlapCount > 0) {
            log.warn("Overlap detected! Count: {}, Config time: {} min ({})", 
                overlapCount, avgConsumption, config.getAverageConsumptionTimeDisplay());
            throw new IllegalArgumentException(
                    "Ya existe una reservación para esta mesa en el horario solicitado. " +
                    "Debe haber al menos " + config.getAverageConsumptionTimeDisplay() + 
                    " entre reservaciones.");
        }
        
        log.debug("=== Validation passed - No overlaps detected ===");
    }

    /**
     * Validate status transition
     */
    private void validateStatusTransition(Reservation reservation, ReservationStatus newStatus) {
        ReservationStatus currentStatus = reservation.getStatus();

        // Cannot change from terminal states
        if (currentStatus == ReservationStatus.COMPLETED ||
            currentStatus == ReservationStatus.CANCELLED) {
            throw new IllegalStateException(
                    "No se puede cambiar el estado de una reservación " + 
                    currentStatus.getDisplayName());
        }

        // From PENDING, can only go to COMPLETED or CANCELLED
        if (currentStatus == ReservationStatus.PENDING) {
            if (newStatus != ReservationStatus.COMPLETED && newStatus != ReservationStatus.CANCELLED) {
                throw new IllegalStateException(
                        "Una reservación pendiente solo puede completarse o cancelarse");
            }
        }
    }
}
