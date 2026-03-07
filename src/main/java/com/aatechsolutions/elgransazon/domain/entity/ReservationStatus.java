package com.aatechsolutions.elgransazon.domain.entity;

/**
 * Enum representing the status of a reservation
 * 
 * States:
 * - PENDING: Customer hasn't arrived yet (waiting)
 * - COMPLETED: An order was created and associated with this reservation
 * - CANCELLED: Reservation was cancelled (manually or no-show)
 * 
 * State transitions:
 * - PENDING -> COMPLETED: When an order is created with this reservation's ID
 * - PENDING -> CANCELLED: Manual cancellation or no-show
 */
public enum ReservationStatus {
    PENDING("Pendiente"),           // Customer hasn't arrived yet
    COMPLETED("Completada"),        // Order was created for this reservation
    CANCELLED("Cancelada");         // Reservation was cancelled

    private final String displayName;

    ReservationStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get status by display name
     */
    public static ReservationStatus fromDisplayName(String displayName) {
        for (ReservationStatus status : values()) {
            if (status.displayName.equalsIgnoreCase(displayName)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown reservation status: " + displayName);
    }

    /**
     * Check if reservation is active (still pending)
     */
    public boolean isActive() {
        return this == PENDING;
    }

    /**
     * Check if reservation can be edited
     * Only PENDING reservations can be edited
     */
    public boolean isEditable() {
        return this == PENDING;
    }

    /**
     * Check if reservation can be cancelled
     * Only PENDING reservations can be cancelled
     */
    public boolean isCancellable() {
        return this == PENDING;
    }

    /**
     * Check if reservation can be completed
     * Only PENDING reservations can be completed
     */
    public boolean canBeCompleted() {
        return this == PENDING;
    }
}
