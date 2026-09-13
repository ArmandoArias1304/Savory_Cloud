package com.aatechsolutions.elgransazon.domain.entity;

/**
 * Type of kitchen comanda this printer is responsible for printing.
 */
public enum PrinterType {

    KITCHEN("Cocina"),
    BAR("Barra"),
    PARRILLERO("Parrillero");

    private final String displayName;

    PrinterType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the preparationTypeSnapshot value stored in OrderDetail
     * that this printer type is responsible for.
     */
    public String getPreparationTypeSnapshot() {
        return switch (this) {
            case KITCHEN -> "CHEF";
            case BAR -> "BARISTA";
            case PARRILLERO -> "PARRILLERO";
        };
    }
}
