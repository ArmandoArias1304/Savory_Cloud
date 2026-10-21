package com.aatechsolutions.elgransazon.domain.entity;

/**
 * Lifecycle of an internal production record.
 *
 * <p>An internal item (buffet dish, staff meal) is never part of an {@link Order}: it is
 * produced in batches and each batch is registered here. The batch either stands
 * ({@link #PREPARED}) or is undone ({@link #CANCELLED}), which returns its ingredients to
 * stock.</p>
 */
public enum PreparationStatus {

    PREPARED("Preparada"),
    CANCELLED("Cancelada");

    private final String displayName;

    PreparationStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
