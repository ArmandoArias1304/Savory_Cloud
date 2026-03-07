package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.ReservationService;
import com.aatechsolutions.elgransazon.application.service.RestaurantTableService;
import com.aatechsolutions.elgransazon.domain.entity.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.*;

/**
 * Controller for Reservation management
 * Accessible by ADMIN and MANAGER roles
 */
@Controller
@RequestMapping("/admin/reservations")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class ReservationController {

    private final ReservationService reservationService;
    private final RestaurantTableService tableService;

    /**
     * Helper method to remove a specific field error from BindingResult
     */
    private BindingResult removeFieldError(BindingResult bindingResult, String fieldName) {
        if (bindingResult.hasFieldErrors(fieldName)) {
            // Create a new BindingResult without the specific field error
            org.springframework.validation.BeanPropertyBindingResult newBindingResult = 
                new org.springframework.validation.BeanPropertyBindingResult(
                    bindingResult.getTarget(), 
                    bindingResult.getObjectName()
                );
            
            // Copy all errors except the one we want to remove
            bindingResult.getAllErrors().forEach(error -> {
                if (error instanceof org.springframework.validation.FieldError) {
                    org.springframework.validation.FieldError fieldError = 
                        (org.springframework.validation.FieldError) error;
                    if (!fieldError.getField().equals(fieldName)) {
                        newBindingResult.addError(fieldError);
                    }
                } else {
                    newBindingResult.addError(error);
                }
            });
            
            return newBindingResult;
        }
        return bindingResult;
    }

    /**
     * Show list of all reservations
     */
    @GetMapping
    public String listReservations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) ReservationStatus status,
            Model model) {
        log.debug("Displaying reservations list - date: {}, status: {}", date, status);

        List<Reservation> reservations;

        if (date != null && status != null) {
            // Filter by both date and status
            reservations = reservationService.findByDateAndStatus(date, status);
        } else if (date != null) {
            reservations = reservationService.findByDate(date);
        } else if (status != null) {
            reservations = reservationService.findByStatus(status);
        } else {
            reservations = reservationService.findAllOrderByDateTimeDesc();
        }

        // Get today's reservations
        List<Reservation> todayReservations = reservationService.findTodayReservations();

        // Statistics (simplified - only PENDING, COMPLETED, CANCELLED exist now)
        long totalCount = reservations.size();
        long todayCount = reservationService.countTodayReservations();
        long todayActiveCount = reservationService.countTodayActiveReservations();
        long pendingCount = reservationService.countByStatus(ReservationStatus.PENDING);
        long completedCount = reservationService.countByStatus(ReservationStatus.COMPLETED);
        long cancelledCount = reservationService.countByStatus(ReservationStatus.CANCELLED);

        model.addAttribute("reservations", reservations);
        model.addAttribute("todayReservations", todayReservations);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("todayCount", todayCount);
        model.addAttribute("todayActiveCount", todayActiveCount);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("completedCount", completedCount);
        model.addAttribute("cancelledCount", cancelledCount);
        model.addAttribute("statuses", ReservationStatus.values());
        model.addAttribute("selectedDate", date);
        model.addAttribute("selectedStatus", status);

        return "admin/reservations/list";
    }

    /**
     * Show form to create a new reservation
     */
    @GetMapping("/new")
    public String newReservationForm(Model model) {
        log.debug("Displaying new reservation form");

        Reservation reservation = new Reservation();
        reservation.setReservationDate(LocalDate.now());

        List<RestaurantTable> tables = tableService.findReservableTables();

        model.addAttribute("reservation", reservation);
        model.addAttribute("tables", tables);
        model.addAttribute("statuses", ReservationStatus.values());
        model.addAttribute("isEdit", false);

        return "admin/reservations/form";
    }

    /**
     * Show form to edit an existing reservation
     */
    @GetMapping("/{id}/edit")
    public String editReservationForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("Displaying edit form for reservation: {}", id);

        try {
            Reservation reservation = reservationService.findByIdOrThrow(id);

            if (!reservation.isEditable()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "No se puede editar una reservación en estado: " + reservation.getStatusDisplayName());
                return "redirect:/admin/reservations";
            }

            List<RestaurantTable> tables = tableService.findReservableTables();

            model.addAttribute("reservation", reservation);
            model.addAttribute("tables", tables);
            model.addAttribute("statuses", ReservationStatus.values());
            model.addAttribute("isEdit", true);

            return "admin/reservations/form";
        } catch (IllegalArgumentException e) {
            log.error("Reservation not found: {}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/reservations";
        }
    }

    /**
     * Create a new reservation
     * Supports multiple tables - creates one reservation per table
     */
    @PostMapping
    public String createReservation(
            @ModelAttribute("reservation") Reservation reservation,
            BindingResult bindingResult,
            @RequestParam(value = "tableIds", required = false) String tableIds,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        log.info("Creating new reservation(s) for customer: {}", reservation.getCustomerName());

        // Validate table selection first
        if (tableIds == null || tableIds.trim().isEmpty()) {
            model.addAttribute("errorMessage", "Debes seleccionar al menos una mesa");
            List<RestaurantTable> tables = tableService.findReservableTables();
            model.addAttribute("tables", tables);
            model.addAttribute("statuses", ReservationStatus.values());
            model.addAttribute("isEdit", false);
            return "admin/reservations/form";
        }

        // Remove restaurantTable validation error since we handle it via tableIds
        bindingResult = removeFieldError(bindingResult, "restaurantTable");

        if (bindingResult.hasErrors()) {
            log.warn("Validation errors creating reservation: {}", bindingResult.getAllErrors());
            List<RestaurantTable> tables = tableService.findReservableTables();
            model.addAttribute("tables", tables);
            model.addAttribute("statuses", ReservationStatus.values());
            model.addAttribute("isEdit", false);
            return "admin/reservations/form";
        }

        try {
            String username = authentication.getName();
            
            // Parse table IDs
            String[] tableIdArray = tableIds.split(",");
            List<Long> tableIdList = new ArrayList<>();
            for (String idStr : tableIdArray) {
                try {
                    tableIdList.add(Long.parseLong(idStr.trim()));
                } catch (NumberFormatException e) {
                    log.error("Invalid table ID: {}", idStr);
                }
            }

            if (tableIdList.isEmpty()) {
                throw new IllegalArgumentException("No se pudieron procesar los IDs de las mesas seleccionadas");
            }

            // Create one reservation per table
            List<Reservation> createdReservations = new ArrayList<>();
            for (Long tableId : tableIdList) {
                RestaurantTable table = tableService.findById(tableId)
                        .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada: " + tableId));

                // Create a copy of the reservation for this table
                Reservation tableReservation = new Reservation();
                tableReservation.setCustomerName(reservation.getCustomerName());
                tableReservation.setCustomerPhone(reservation.getCustomerPhone());
                tableReservation.setCustomerEmail(reservation.getCustomerEmail());
                tableReservation.setNumberOfGuests(reservation.getNumberOfGuests());
                tableReservation.setReservationDate(reservation.getReservationDate());
                tableReservation.setReservationTime(reservation.getReservationTime());
                tableReservation.setSpecialRequests(reservation.getSpecialRequests());
                tableReservation.setRestaurantTable(table);

                Reservation created = reservationService.create(tableReservation, username);
                createdReservations.add(created);
                log.info("Created reservation for table #{}", table.getTableNumber());
            }

            String successMessage;
            if (createdReservations.size() == 1) {
                successMessage = "Reservación creada exitosamente para " + createdReservations.get(0).getCustomerName();
            } else {
                successMessage = String.format("Se crearon %d reservaciones exitosamente para %s", 
                    createdReservations.size(), reservation.getCustomerName());
            }
            
            redirectAttributes.addFlashAttribute("successMessage", successMessage);
            return "redirect:/admin/reservations";
            
        } catch (Exception e) {
            log.error("Error creating reservation(s)", e);
            model.addAttribute("errorMessage", e.getMessage());
            List<RestaurantTable> tables = tableService.findReservableTables();
            model.addAttribute("tables", tables);
            model.addAttribute("statuses", ReservationStatus.values());
            model.addAttribute("isEdit", false);
            return "admin/reservations/form";
        }
    }

    /**
     * Update an existing reservation
     */
    @PostMapping("/{id}")
    public String updateReservation(
            @PathVariable Long id,
            @Valid @ModelAttribute("reservation") Reservation reservation,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        log.info("Updating reservation: {}", id);

        if (bindingResult.hasErrors()) {
            log.warn("Validation errors updating reservation: {}", bindingResult.getAllErrors());
            List<RestaurantTable> tables = tableService.findReservableTables();
            model.addAttribute("reservation", reservation);
            model.addAttribute("tables", tables);
            model.addAttribute("statuses", ReservationStatus.values());
            model.addAttribute("isEdit", true);
            return "admin/reservations/form";
        }

        try {
            String username = authentication.getName();
            reservationService.update(id, reservation, username);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Reservación actualizada exitosamente");
            return "redirect:/admin/reservations";
        } catch (Exception e) {
            log.error("Error updating reservation: {}", id, e);
            model.addAttribute("errorMessage", e.getMessage());
            List<RestaurantTable> tables = tableService.findReservableTables();
            model.addAttribute("reservation", reservation);
            model.addAttribute("tables", tables);
            model.addAttribute("statuses", ReservationStatus.values());
            model.addAttribute("isEdit", true);
            return "admin/reservations/form";
        }
    }

    /**
     * Check-in reservation (AJAX)
     */
    @PostMapping("/{id}/checkin")
    @ResponseBody
    public Map<String, Object> completeReservation(@PathVariable Long id, Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            String username = authentication.getName();
            Reservation completed = reservationService.markAsCompleted(id, username);
            response.put("success", true);
            response.put("message", "Reservación completada exitosamente (cliente llegó)");
            response.put("status", completed.getStatusDisplayName());
            log.info("Reservation {} marked as COMPLETED by user: {}", id, username);
        } catch (Exception e) {
            log.error("Error completing reservation: {}", id, e);
            response.put("success", false);
            response.put("message", e.getMessage());
        }

        return response;
    }

    /**
     * Cancel reservation (AJAX)
     */
    @PostMapping("/{id}/cancel")
    @ResponseBody
    public Map<String, Object> cancelReservation(@PathVariable Long id, Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            String username = authentication.getName();
            Reservation cancelled = reservationService.cancel(id, username);
            response.put("success", true);
            response.put("message", "Reservación cancelada exitosamente");
            response.put("status", cancelled.getStatusDisplayName());
            log.info("Reservation {} cancelled by user: {}", id, username);
        } catch (Exception e) {
            log.error("Error cancelling reservation: {}", id, e);
            response.put("success", false);
            response.put("message", e.getMessage());
        }

        return response;
    }

    /**
     * Get reservation details for modal display (AJAX)
     */
    @GetMapping("/{id}/details")
    @ResponseBody
    public Map<String, Object> getReservationDetails(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();

        try {
            Reservation reservation = reservationService.findByIdOrThrow(id);

            response.put("success", true);
            response.put("id", reservation.getId());
            response.put("customerName", reservation.getCustomerName());
            response.put("customerPhone", reservation.getCustomerPhone());
            response.put("customerEmail", reservation.getCustomerEmail() != null ? reservation.getCustomerEmail() : "N/A");
            response.put("numberOfGuests", reservation.getGuestsDisplay());
            response.put("reservationDate", reservation.getFormattedReservationDate());
            response.put("reservationTime", reservation.getFormattedReservationTime());
            response.put("reservationDateTime", reservation.getFormattedReservationDateTime());
            response.put("table", reservation.getTableDisplayName());
            response.put("status", reservation.getStatusDisplayName());
            response.put("specialRequests", reservation.getSpecialRequests() != null ? reservation.getSpecialRequests() : "Ninguna");
            // isOccupied removed - status is now managed through PENDING/COMPLETED/CANCELLED
            response.put("isActive", reservation.isActive());
            response.put("createdBy", reservation.getCreatedBy());
            response.put("createdAt", reservation.getFormattedCreatedAt());
            response.put("updatedBy", reservation.getUpdatedBy() != null ? reservation.getUpdatedBy() : "N/A");
            response.put("updatedAt", reservation.getUpdatedAt() != null ? reservation.getFormattedUpdatedAt() : "N/A");

            log.debug("Retrieved details for reservation: {}", id);
        } catch (Exception e) {
            log.error("Error getting reservation details: {}", id, e);
            response.put("success", false);
            response.put("message", e.getMessage());
        }

        return response;
    }

    /**
     * Get reservations by date (AJAX for calendar)
     */
    @GetMapping("/api/by-date")
    @ResponseBody
    public Map<String, Object> getReservationsByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Reservation> reservations = reservationService.findByDate(date);
            
            List<Map<String, Object>> reservationList = new ArrayList<>();
            for (Reservation reservation : reservations) {
                Map<String, Object> resData = new HashMap<>();
                resData.put("id", reservation.getId());
                resData.put("customerName", reservation.getCustomerName());
                resData.put("customerPhone", reservation.getCustomerPhone());
                resData.put("numberOfGuests", reservation.getNumberOfGuests());
                resData.put("reservationTime", reservation.getFormattedReservationTime());
                resData.put("table", reservation.getTableDisplayName());
                resData.put("status", reservation.getStatusDisplayName());
                resData.put("statusName", reservation.getStatus().name());
                reservationList.add(resData);
            }

            response.put("success", true);
            response.put("date", date.toString());
            response.put("reservations", reservationList);
            response.put("count", reservationList.size());

            log.debug("Retrieved {} reservations for date: {}", reservationList.size(), date);
        } catch (Exception e) {
            log.error("Error getting reservations by date: {}", date, e);
            response.put("success", false);
            response.put("message", e.getMessage());
        }

        return response;
    }

    /**
     * Get reservation counts by date range (AJAX for calendar)
     */
    @GetMapping("/api/counts-by-month")
    @ResponseBody
    public Map<String, Object> getReservationCountsByMonth(
            @RequestParam int year,
            @RequestParam int month) {
        Map<String, Object> response = new HashMap<>();

        try {
            LocalDate startDate = LocalDate.of(year, month, 1);
            LocalDate endDate = startDate.plusMonths(1).minusDays(1);

            List<Reservation> reservations = reservationService.findByDateRange(startDate, endDate);

            // Group reservations by date
            Map<String, Integer> countsByDate = new HashMap<>();
            for (Reservation reservation : reservations) {
                String dateKey = reservation.getReservationDate().toString();
                countsByDate.put(dateKey, countsByDate.getOrDefault(dateKey, 0) + 1);
            }

            response.put("success", true);
            response.put("counts", countsByDate);

            log.debug("Retrieved reservation counts for {}-{}", year, month);
        } catch (Exception e) {
            log.error("Error getting reservation counts: {}-{}", year, month, e);
            response.put("success", false);
            response.put("message", e.getMessage());
        }

        return response;
    }
}
