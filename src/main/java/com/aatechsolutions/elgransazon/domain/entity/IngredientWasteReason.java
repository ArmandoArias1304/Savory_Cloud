package com.aatechsolutions.elgransazon.domain.entity;

public enum IngredientWasteReason {
    EXPIRED("Vencido"),
    BURNT("Quemado"),
    SPILLED("Derramado"),
    PREPARATION_WASTE("Desperdicio de preparación"),
    OTHER("Otro");

    private final String label;

    IngredientWasteReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
