package com.aatechsolutions.elgransazon.domain.entity;

/**
 * Lifecycle of an add-on contracted by a restaurant.
 */
public enum CompanyFeatureStatus {

    ENABLED("Activo"),
    DISABLED("Desactivado"),
    EXPIRED("Vencido"),
    TRIAL("Prueba");

    private final String displayName;

    CompanyFeatureStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
