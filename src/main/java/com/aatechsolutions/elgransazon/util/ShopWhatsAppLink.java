package com.aatechsolutions.elgransazon.util;

import com.aatechsolutions.elgransazon.domain.entity.ShopBillingType;
import com.aatechsolutions.elgransazon.domain.entity.ShopProduct;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Builds the wa.me link used by restaurants to ask about a catalogue product.
 *
 * <p>The message always identifies the restaurant and the product, so the
 * programmer knows who is writing and what they want without asking back.</p>
 */
public final class ShopWhatsAppLink {

    private static final Locale SPANISH_MEXICO = Locale.forLanguageTag("es-MX");

    private ShopWhatsAppLink() {
    }

    /**
     * Normalizes a phone for wa.me: keeps digits only, drops an international
     * {@code 00} prefix and adds Mexico's {@code 52} when the number arrives
     * with the local 10 digits.
     */
    public static String normalizePhone(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        String digits = phoneNumber.replaceAll("[^0-9]", "");
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        if (digits.length() == 10) {
            digits = "52" + digits;
        }
        return digits.isEmpty() ? null : digits;
    }

    /**
     * A usable wa.me number has 10 to 15 digits after normalization.
     */
    public static boolean isValidPhone(String phoneNumber) {
        String phone = normalizePhone(phoneNumber);
        return phone != null && phone.length() >= 10 && phone.length() <= 15;
    }

    /**
     * @return the wa.me URL, or {@code null} when no usable phone is configured.
     */
    public static String build(String phoneNumber, String restaurantName,
                               String productName, String priceLabel) {
        String phone = normalizePhone(phoneNumber);
        if (phone == null) {
            return null;
        }

        String restaurant = isBlank(restaurantName) ? "un restaurante" : restaurantName.trim();
        StringBuilder message = new StringBuilder()
                .append("Hola, soy de ").append(restaurant)
                .append(". Me interesa el producto \"").append(productName).append("\"");
        if (!isBlank(priceLabel)) {
            message.append(" (").append(priceLabel).append(")");
        }
        message.append(". ¿Me pueden dar más información?");

        String encoded = URLEncoder.encode(message.toString(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "https://wa.me/" + phone + "?text=" + encoded;
    }

    /**
     * Price label that states how the restaurant is charged, so the WhatsApp
     * message cannot be mistaken for a one-time purchase:
     * {@code $ 1,250.00} or {@code $ 150.00 / mes}.
     */
    public static String formatPrice(BigDecimal price, String currency, ShopBillingType billingType) {
        return appendBillingPeriod(formatPrice(price, currency), billingType);
    }

    /**
     * Price label for the WhatsApp message, honoring the pricing mode: a
     * range is quoted as {@code $ 200.00 – $ 500.00} and a quote asks for a
     * price instead of inventing one.
     */
    public static String priceLabel(ShopProduct product) {
        ShopBillingType billing = product.getBillingTypeOrDefault();
        if (product.isQuote()) {
            return billing == ShopBillingType.MONTHLY
                    ? "precio a evaluar, cobro mensual"
                    : "precio a evaluar";
        }
        if (product.isPriceRange() && product.getMaxPrice() != null) {
            String range = formatPrice(product.getPrice(), product.getCurrency())
                    + " – " + formatPrice(product.getMaxPrice(), product.getCurrency());
            return appendBillingPeriod(range, billing);
        }
        return formatPrice(product.getFinalPrice(), product.getCurrency(), billing);
    }

    private static String appendBillingPeriod(String label, ShopBillingType billingType) {
        if (label.isEmpty() || billingType != ShopBillingType.MONTHLY) {
            return label;
        }
        return label + " / mes";
    }

    /**
     * Price as shown in the catalogue, e.g. {@code $ 1,250.00}.
     */
    public static String formatPrice(BigDecimal price, String currency) {
        if (price == null) {
            return "";
        }
        NumberFormat format = NumberFormat.getNumberInstance(SPANISH_MEXICO);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "$ " + format.format(price);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
