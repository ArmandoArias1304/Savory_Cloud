package com.aatechsolutions.elgransazon.domain.entity;

/**
 * Status of a customer review
 */
public enum ReviewStatus {
    PENDING("Pendiente"),
    APPROVED("Aprobada"),
    REJECTED("Rechazada");

    private final String displayName;

    ReviewStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
