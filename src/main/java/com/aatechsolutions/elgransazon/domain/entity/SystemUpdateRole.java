package com.aatechsolutions.elgransazon.domain.entity;

/**
 * Roles that can be selected as audience of a system update.
 *
 * <p>PROGRAMMER creates the updates and CLIENT belongs to the public store
 * flow, so neither is selectable here.</p>
 */
public enum SystemUpdateRole {
    ADMIN("Administrador"),
    MANAGER("Gerente"),
    WAITER("Mesero"),
    CHEF("Chef"),
    BARISTA("Barista"),
    PARRILLERO("Parrillero"),
    CASHIER("Cajero"),
    DELIVERY("Repartidor");

    private final String displayName;

    SystemUpdateRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Maps a Spring Security authority (e.g. {@code ROLE_WAITER}) to the enum.
     * Returns {@code null} for roles outside the staff audience.
     */
    public static SystemUpdateRole fromAuthority(String authority) {
        if (authority == null || authority.isBlank()) {
            return null;
        }
        String name = authority.startsWith("ROLE_") ? authority.substring(5) : authority;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
