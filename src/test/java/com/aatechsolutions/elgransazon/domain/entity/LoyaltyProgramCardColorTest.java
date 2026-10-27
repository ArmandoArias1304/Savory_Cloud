package com.aatechsolutions.elgransazon.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Colours of the client loyalty card: the admin picks a front and a back colour
 * ({@code #RRGGBB}) and the card derives the darker tones of the gradient from them.
 * A missing or malformed stored value must fall back to the palette the card shipped
 * with instead of breaking the screen.
 */
class LoyaltyProgramCardColorTest {

    @Test
    void withoutColoursTheCardUsesTheOriginalPalette() {
        LoyaltyProgram program = LoyaltyProgram.builder().build();

        assertThat(program.getEffectiveCardFrontColor()).isEqualTo("#F2833F");
        assertThat(program.getEffectiveCardBackColor()).isEqualTo("#12A7B5");
    }

    @Test
    void malformedStoredColoursFallBackToTheOriginalPalette() {
        LoyaltyProgram program = LoyaltyProgram.builder()
                .cardFrontColor("orange")
                .cardBackColor("#12345")
                .build();

        assertThat(program.getEffectiveCardFrontColor()).isEqualTo("#F2833F");
        assertThat(program.getEffectiveCardBackColor()).isEqualTo("#12A7B5");
    }

    @Test
    void chosenColoursAreNormalizedAndDriveTheGradientTones() {
        LoyaltyProgram program = LoyaltyProgram.builder()
                .cardFrontColor("#123abc")
                .cardBackColor("#654321")
                .build();

        // Lower case is stored as upper case so the CSS is consistent.
        assertThat(program.getEffectiveCardFrontColor()).isEqualTo("#123ABC");
        assertThat(program.getEffectiveCardBackColor()).isEqualTo("#654321");

        // Two darker tones per face close the gradient (80% and 60% of each channel).
        assertThat(program.getCardFrontDarkColor()).isEqualTo("#0E2E96");
        assertThat(program.getCardFrontDeepColor()).isEqualTo("#0B2371");
        assertThat(program.getCardBackDarkColor()).isEqualTo("#51361A");
        assertThat(program.getCardBackDeepColor()).isEqualTo("#3D2814");
    }

    @Test
    void nullAndInvalidValuesAreNotValidColours() {
        assertThat(LoyaltyProgram.normalizeHex(null)).isNull();
        assertThat(LoyaltyProgram.normalizeHex("")).isNull();
        assertThat(LoyaltyProgram.normalizeHex("#FFF")).isNull();
        assertThat(LoyaltyProgram.normalizeHex("123456")).isNull();
        assertThat(LoyaltyProgram.normalizeHex("#GGGGGG")).isNull();
        assertThat(LoyaltyProgram.normalizeHex("#ABCDEF")).isEqualTo("#ABCDEF");
    }
}
