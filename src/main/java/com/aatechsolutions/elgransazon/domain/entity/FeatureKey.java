package com.aatechsolutions.elgransazon.domain.entity;

/**
 * Optional modules that can be sold separately from the restaurant license.
 *
 * <p>Keeping the key in an enum (instead of a free-text column) lets the
 * programmer enable a module for one company without inventing a new package
 * type and without touching the license itself.</p>
 */
public enum FeatureKey {

    LOYALTY("Fidelización", "Tarjeta de sellos y recompensas para clientes registrados");

    private final String displayName;
    private final String description;

    FeatureKey(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
