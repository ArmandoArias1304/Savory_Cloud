package com.aatechsolutions.elgransazon.domain.entity;

/**
 * Enum representing days of the week with Spanish display names
 */
public enum DayOfWeek {
    MONDAY("Lunes"),
    TUESDAY("Martes"),
    WEDNESDAY("Miércoles"),
    THURSDAY("Jueves"),
    FRIDAY("Viernes"),
    SATURDAY("Sábado"),
    SUNDAY("Domingo");

    private final String displayName;

    DayOfWeek(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get DayOfWeek from Spanish display name
     */
    public static DayOfWeek fromDisplayName(String displayName) {
        for (DayOfWeek day : values()) {
            if (day.displayName.equalsIgnoreCase(displayName)) {
                return day;
            }
        }
        throw new IllegalArgumentException("No enum constant for display name: " + displayName);
    }

    /**
     * Convert to java.time.DayOfWeek
     */
    public java.time.DayOfWeek toJavaDayOfWeek() {
        return java.time.DayOfWeek.valueOf(this.name());
    }
}
