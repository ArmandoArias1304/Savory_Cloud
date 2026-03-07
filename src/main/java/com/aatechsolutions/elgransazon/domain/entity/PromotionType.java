package com.aatechsolutions.elgransazon.domain.entity;

/**
 * Enum representing the types of promotions available in the system
 */
public enum PromotionType {
    /**
     * Buy X items, pay for Y items
     * Example: Buy 2, pay 1 (2x1) or Buy 3, pay 2 (3x2)
     */
    BUY_X_PAY_Y("Compra X Paga Y", "2x1, 3x2, etc."),

    /**
     * Percentage discount on item price
     * Example: 20% off, 50% off
     */
    PERCENTAGE_DISCOUNT("Descuento Porcentual", "20% OFF, 50% OFF, etc."),

    /**
     * Fixed amount discount on item price
     * Example: $5 off, $10 off
     */
    FIXED_AMOUNT_DISCOUNT("Descuento Valor Fijo", "$5 OFF, $10 OFF, etc.");

    private final String displayName;
    private final String description;

    PromotionType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get a user-friendly label for display in UI
     */
    public String getLabel() {
        return displayName;
    }
}
