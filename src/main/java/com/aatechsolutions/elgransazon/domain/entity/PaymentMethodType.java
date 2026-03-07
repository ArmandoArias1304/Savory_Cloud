package com.aatechsolutions.elgransazon.domain.entity;

/**
 * Enum representing payment method types
 */
public enum PaymentMethodType {
    CASH("Efectivo", "💵"),
    CREDIT_CARD("Tarjeta de Crédito", "💳"),
    DEBIT_CARD("Tarjeta de Débito", "💳"),
    TRANSFER("Transferencia", "🏦");

    private final String displayName;
    private final String icon;

    PaymentMethodType(String displayName, String icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }

    /**
     * Get PaymentMethodType from display name
     */
    public static PaymentMethodType fromDisplayName(String displayName) {
        for (PaymentMethodType type : values()) {
            if (type.displayName.equalsIgnoreCase(displayName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("No enum constant for display name: " + displayName);
    }
}
