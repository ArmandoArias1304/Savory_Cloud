package com.aatechsolutions.elgransazon.domain.entity;

/**
 * How the price of a shop product is announced in the store.
 *
 * <p>Not every service has one price: a training session can cost 200 in the
 * city and 500 outside it, and others cannot be priced until the programmer
 * knows what the restaurant needs. Instead of forcing a fake number, the
 * programmer picks how the price is published.</p>
 */
public enum ShopPricingMode {

    /** One single price for every restaurant. */
    FIXED("Precio fijo", "Un precio único para todos los restaurantes."),

    /** A minimum and a maximum, so the restaurant knows the ballpark. */
    RANGE("Rango de precios", "Un precio mínimo y un máximo según el trabajo."),

    /** No price at all: the restaurant asks and the programmer quotes it. */
    QUOTE("Precio a evaluar", "Sin precio: el restaurante pide cotización.");

    private final String label;
    private final String description;

    ShopPricingMode(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /** Short name shown on the cards and the detail page. */
    public String getLabel() {
        return label;
    }

    /** One line explaining how the price works. */
    public String getDescription() {
        return description;
    }
}
