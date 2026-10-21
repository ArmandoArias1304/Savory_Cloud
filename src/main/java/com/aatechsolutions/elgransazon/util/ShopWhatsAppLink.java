package com.aatechsolutions.elgransazon.util;

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
