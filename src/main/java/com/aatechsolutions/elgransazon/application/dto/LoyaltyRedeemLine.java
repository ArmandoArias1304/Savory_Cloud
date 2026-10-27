package com.aatechsolutions.elgransazon.application.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * One line of the staff redemption basket: a catalog reward and how many times
 * it is redeemed.
 *
 * <p>The basket travels from the screen as a single hidden field, for example
 * {@code "4:2,7:1"} (reward id : quantity). The parser is deliberately strict:
 * a malformed value is rejected instead of silently dropping a line, because a
 * dropped line would mean the customer receives less than the staff selected.</p>
 */
public record LoyaltyRedeemLine(Long rewardId, int quantity) {

    /** Hard limits shared by the screen and the service. */
    public static final int MAX_QUANTITY_PER_REWARD = 10;
    public static final int MAX_DISTINCT_REWARDS = 15;

    /**
     * Parses the hidden field of the redemption form.
     *
     * @param raw {@code "rewardId:quantity,rewardId:quantity"}; blank means an
     *            empty basket and is rejected
     * @throws IllegalArgumentException when the value is blank or malformed
     */
    public static List<LoyaltyRedeemLine> parse(String raw) {
        List<LoyaltyRedeemLine> lines = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Selecciona al menos una recompensa para canjear");
        }

        for (String chunk : raw.split(",")) {
            String token = chunk.trim();
            if (token.isEmpty()) {
                continue;
            }

            String[] parts = token.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("La selección de recompensas no es válida");
            }

            long rewardId;
            int quantity;
            try {
                rewardId = Long.parseLong(parts[0].trim());
                quantity = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("La selección de recompensas no es válida");
            }

            if (rewardId <= 0 || quantity <= 0) {
                throw new IllegalArgumentException("La selección de recompensas no es válida");
            }
            if (quantity > MAX_QUANTITY_PER_REWARD) {
                throw new IllegalArgumentException(
                        "No se puede canjear la misma recompensa más de "
                                + MAX_QUANTITY_PER_REWARD + " veces en un canje");
            }

            lines.add(new LoyaltyRedeemLine(rewardId, quantity));
        }

        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Selecciona al menos una recompensa para canjear");
        }
        return lines;
    }
}
