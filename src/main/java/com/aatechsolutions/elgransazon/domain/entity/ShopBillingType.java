package com.aatechsolutions.elgransazon.domain.entity;

/**
 * How a restaurant is charged for a shop product.
 *
 * <p>Not every product behaves the same: a printed QR card is bought once,
 * while a digital service is added to the restaurant's monthly subscription.
 * The programmer picks the way they charge when publishing, and the shop shows
 * it next to the price so the restaurant knows what it is agreeing to pay.</p>
 */
public enum ShopBillingType {

    /** Charged once, when the restaurant asks for the product. */
    ONE_TIME("Pago único", "Se cobra una sola vez, al pedirlo."),

    /** Added to the restaurant's monthly subscription. */
    MONTHLY("Mensual", "Se suma a la mensualidad del restaurante.");

    private final String label;
    private final String description;

    ShopBillingType(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /** Short name shown on the cards and the detail page. */
    public String getLabel() {
        return label;
    }

    /** One line explaining how the charge works. */
    public String getDescription() {
        return description;
    }
}
