package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Reservation;
import com.aatechsolutions.elgransazon.domain.entity.ReservationStatus;
import com.aatechsolutions.elgransazon.domain.entity.RestaurantTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Reservation entity
 */
@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * Find all reservations ordered by date and time descending
     */
    List<Reservation> findAllByOrderByReservationDateDescReservationTimeDesc();

    /**
     * Find reservations by table and status
     */
    List<Reservation> findByRestaurantTableAndStatus(RestaurantTable table, ReservationStatus status);

    /**
     * Find reservations by table and status (multiple statuses)
     */
    List<Reservation> findByRestaurantTableAndStatusIn(RestaurantTable table, List<ReservationStatus> statuses);

    /**
     * Find reservations by table and date ordered by time
     */
    List<Reservation> findByRestaurantTableAndReservationDateOrderByReservationTimeAsc(
            RestaurantTable table, LocalDate date);

    /**
     * Find reservations by date range
     */
    List<Reservation> findByReservationDateBetweenOrderByReservationDateAscReservationTimeAsc(
            LocalDate startDate, LocalDate endDate);

    /**
     * Find reservations by date
     */
    List<Reservation> findByReservationDateOrderByReservationTimeAsc(LocalDate date);

    /**
     * Find reservations by status
     */
    List<Reservation> findByStatusOrderByReservationDateDescReservationTimeDesc(ReservationStatus status);

    /**
     * Find active reservations (PENDING only - waiting for customer)
     */
    @Query("SELECT r FROM Reservation r WHERE r.status = 'PENDING' " +
           "ORDER BY r.reservationDate DESC, r.reservationTime DESC")
    List<Reservation> findActiveReservations();

    /**
     * Find upcoming reservations (today or future, PENDING status)
     */
    @Query("SELECT r FROM Reservation r WHERE r.reservationDate >= :today " +
           "AND r.status = 'PENDING' " +
           "ORDER BY r.reservationDate ASC, r.reservationTime ASC")
    List<Reservation> findUpcomingReservations(@Param("today") LocalDate today);

    /**
     * Find reservations for today
     */
    @Query("SELECT r FROM Reservation r WHERE r.reservationDate = :today " +
           "ORDER BY r.reservationTime ASC")
    List<Reservation> findTodayReservations(@Param("today") LocalDate today);

    /**
     * Find next PENDING reservation for a specific table (returns the closest one)
     */
    @Query(value = "SELECT * FROM reservations r WHERE r.id_table = :tableId " +
           "AND r.status = 'PENDING' " +
           "AND (r.reservation_date > :currentDate " +
           "OR (r.reservation_date = :currentDate AND r.reservation_time > :currentTime)) " +
           "ORDER BY r.reservation_date ASC, r.reservation_time ASC " +
           "LIMIT 1", nativeQuery = true)
    Optional<Reservation> findNextReservationForTable(
            @Param("tableId") Long tableId,
            @Param("currentDate") LocalDate currentDate,
            @Param("currentTime") LocalTime currentTime);

    /**
     * Check if there's an overlapping reservation for a table
     * (excluding a specific reservation ID for updates)
     * Only considers PENDING reservations
     */
    @Query(value = "SELECT COUNT(r.id_reservation) FROM reservations r " +
           "WHERE r.id_table = :tableId " +
           "AND r.reservation_date = :date " +
           "AND r.status = 'PENDING' " +
           "AND (:reservationId IS NULL OR r.id_reservation != :reservationId) " +
           "AND r.reservation_time < :endTime " +
           "AND ADDTIME(r.reservation_time, SEC_TO_TIME(:avgConsumptionSeconds)) > :startTime",
           nativeQuery = true)
    Long countOverlappingReservations(
            @Param("tableId") Long tableId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("avgConsumptionSeconds") Integer avgConsumptionSeconds,
            @Param("reservationId") Long reservationId);

    /**
     * Count reservations by status
     */
    long countByStatus(ReservationStatus status);

    /**
     * Count reservations for today
     */
    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.reservationDate = :today")
    long countTodayReservations(@Param("today") LocalDate today);

    /**
     * Count PENDING reservations for today
     */
    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.reservationDate = :today " +
           "AND r.status = 'PENDING'")
    long countTodayActiveReservations(@Param("today") LocalDate today);

    /**
     * Find reservations by customer phone
     */
    List<Reservation> findByCustomerPhoneContainingOrderByReservationDateDescReservationTimeDesc(String phone);

    /**
     * Find reservations by customer name
     */
    List<Reservation> findByCustomerNameContainingIgnoreCaseOrderByReservationDateDescReservationTimeDesc(String name);

    // ========== NEW METHODS FOR RESERVATION RESTRUCTURE ==========

    /**
     * Find the first PENDING reservation for a table on a specific date
     * ordered by time (earliest first) - used to determine which reservation to activate
     */
    @Query("SELECT r FROM Reservation r WHERE r.restaurantTable.id = :tableId " +
           "AND r.reservationDate = :date " +
           "AND r.status = 'PENDING' " +
           "ORDER BY r.reservationTime ASC")
    List<Reservation> findPendingReservationsForTableOnDate(
            @Param("tableId") Long tableId,
            @Param("date") LocalDate date);

    /**
     * Find PENDING reservations for a table that are due now or have passed
     * (reservationDateTime <= now) ordered by time ascending (oldest first)
     * This is used to find which reservation should be activated
     */
    @Query(value = "SELECT * FROM reservations r WHERE r.id_table = :tableId " +
           "AND r.status = 'PENDING' " +
           "AND (r.reservation_date < :currentDate " +
           "OR (r.reservation_date = :currentDate AND r.reservation_time <= :currentTime)) " +
           "ORDER BY r.reservation_date ASC, r.reservation_time ASC " +
           "LIMIT 1", nativeQuery = true)
    Optional<Reservation> findFirstActivePendingReservationForTable(
            @Param("tableId") Long tableId,
            @Param("currentDate") LocalDate currentDate,
            @Param("currentTime") LocalTime currentTime);

    /**
     * Find all PENDING reservations for a table on today or future dates
     * Used to check if there are any upcoming reservations that might block orders
     */
    @Query("SELECT r FROM Reservation r WHERE r.restaurantTable.id = :tableId " +
           "AND r.status = 'PENDING' " +
           "AND r.reservationDate >= :today " +
           "ORDER BY r.reservationDate ASC, r.reservationTime ASC")
    List<Reservation> findPendingReservationsForTable(
            @Param("tableId") Long tableId,
            @Param("today") LocalDate today);

    /**
     * Find the closest PENDING reservation for a table (including today)
     * This helps determine if a table is blocked due to proximity
     */
    @Query(value = "SELECT * FROM reservations r WHERE r.id_table = :tableId " +
           "AND r.status = 'PENDING' " +
           "AND (r.reservation_date > :currentDate " +
           "OR (r.reservation_date = :currentDate AND r.reservation_time >= :currentTime)) " +
           "ORDER BY r.reservation_date ASC, r.reservation_time ASC " +
           "LIMIT 1", nativeQuery = true)
    Optional<Reservation> findClosestPendingReservationForTable(
            @Param("tableId") Long tableId,
            @Param("currentDate") LocalDate currentDate,
            @Param("currentTime") LocalTime currentTime);

    /**
     * Find all reservations for a table on a date (all statuses)
     */
    @Query("SELECT r FROM Reservation r WHERE r.restaurantTable.id = :tableId " +
           "AND r.reservationDate = :date " +
           "ORDER BY r.reservationTime ASC")
    List<Reservation> findAllReservationsForTableOnDate(
            @Param("tableId") Long tableId,
            @Param("date") LocalDate date);

    // ========== Multi-Tenant Methods (by Company) ==========

    /**
     * Find reservation by ID and company (for security validation)
     */
    Optional<Reservation> findByIdAndCompany(Long id, com.aatechsolutions.elgransazon.domain.entity.Company company);

    /**
     * Find all reservations by company ordered by date and time descending
     */
    @Query("SELECT r FROM Reservation r WHERE r.company = :company " +
           "ORDER BY r.reservationDate DESC, r.reservationTime DESC")
    List<Reservation> findAllByCompanyOrderByReservationDateDescReservationTimeDesc(
            @Param("company") com.aatechsolutions.elgransazon.domain.entity.Company company);

    /**
     * Find active reservations (PENDING only) by company
     */
    @Query("SELECT r FROM Reservation r WHERE r.company = :company AND r.status = 'PENDING' " +
           "ORDER BY r.reservationDate DESC, r.reservationTime DESC")
    List<Reservation> findActiveReservationsByCompany(
            @Param("company") com.aatechsolutions.elgransazon.domain.entity.Company company);

    /**
     * Find upcoming reservations (today or future, PENDING status) by company
     */
    @Query("SELECT r FROM Reservation r WHERE r.company = :company AND r.reservationDate >= :today " +
           "AND r.status = 'PENDING' " +
           "ORDER BY r.reservationDate ASC, r.reservationTime ASC")
    List<Reservation> findUpcomingReservationsByCompany(
            @Param("company") com.aatechsolutions.elgransazon.domain.entity.Company company,
            @Param("today") LocalDate today);

    /**
     * Find reservations for today by company
     */
    @Query("SELECT r FROM Reservation r WHERE r.company = :company AND r.reservationDate = :today " +
           "ORDER BY r.reservationTime ASC")
    List<Reservation> findTodayReservationsByCompany(
            @Param("company") com.aatechsolutions.elgransazon.domain.entity.Company company,
            @Param("today") LocalDate today);

    /**
     * Find reservations by date for a specific company
     */
    @Query("SELECT r FROM Reservation r WHERE r.company = :company " +
           "AND r.reservationDate = :date ORDER BY r.reservationTime ASC")
    List<Reservation> findByReservationDateAndCompanyOrderByReservationTimeAsc(
            @Param("date") LocalDate date,
            @Param("company") com.aatechsolutions.elgransazon.domain.entity.Company company);

    /**
     * Find reservations by date range and company
     */
    @Query("SELECT r FROM Reservation r WHERE r.company = :company " +
           "AND r.reservationDate BETWEEN :startDate AND :endDate " +
           "ORDER BY r.reservationDate ASC, r.reservationTime ASC")
    List<Reservation> findByReservationDateBetweenAndCompanyOrderByReservationDateAscReservationTimeAsc(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("company") com.aatechsolutions.elgransazon.domain.entity.Company company);

    /**
     * Find reservations by status and company
     */
    @Query("SELECT r FROM Reservation r WHERE r.company = :company AND r.status = :status " +
           "ORDER BY r.reservationDate DESC, r.reservationTime DESC")
    List<Reservation> findByStatusAndCompanyOrderByReservationDateDescReservationTimeDesc(
            @Param("status") ReservationStatus status,
            @Param("company") com.aatechsolutions.elgransazon.domain.entity.Company company);

    /**
     * Count reservations by status and company
     */
    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.company = :company AND r.status = :status")
    long countByStatusAndCompany(
            @Param("status") ReservationStatus status,
            @Param("company") com.aatechsolutions.elgransazon.domain.entity.Company company);

    /**
     * Count reservations for today by company
     */
    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.company = :company AND r.reservationDate = :today")
    long countTodayReservationsByCompany(
            @Param("company") com.aatechsolutions.elgransazon.domain.entity.Company company,
            @Param("today") LocalDate today);

    /**
     * Count PENDING reservations for today by company
     */
    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.company = :company AND r.reservationDate = :today " +
           "AND r.status = 'PENDING'")
    long countTodayActiveReservationsByCompany(
            @Param("company") com.aatechsolutions.elgransazon.domain.entity.Company company,
            @Param("today") LocalDate today);

    /**
     * Find reservations by date and status for a specific company
     */
    @Query("SELECT r FROM Reservation r WHERE r.company = :company " +
           "AND r.reservationDate = :date AND r.status = :status ORDER BY r.reservationTime ASC")
    List<Reservation> findByReservationDateAndStatusAndCompanyOrderByReservationTimeAsc(
            @Param("date") LocalDate date,
            @Param("status") ReservationStatus status,
            @Param("company") com.aatechsolutions.elgransazon.domain.entity.Company company);

    /**
     * Find reservations by date and status (without company filter)
     */
    @Query("SELECT r FROM Reservation r WHERE r.reservationDate = :date AND r.status = :status " +
           "ORDER BY r.reservationTime ASC")
    List<Reservation> findByReservationDateAndStatusOrderByReservationTimeAsc(
            @Param("date") LocalDate date,
            @Param("status") ReservationStatus status);

    // ========== Migration Helper Methods ==========

    /**
     * Find all reservations without company (for data migration)
     */
    List<Reservation> findByCompanyIsNull();
}
