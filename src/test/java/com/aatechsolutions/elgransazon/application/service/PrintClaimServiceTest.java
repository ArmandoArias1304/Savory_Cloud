package com.aatechsolutions.elgransazon.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Broadcast print events must print exactly once: the first agent that asks for the
 * claim prints the ticket, the others skip it, and a crashed agent does not block
 * the ticket forever.
 */
class PrintClaimServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-13T15:00:00Z");

    @Test
    @DisplayName("Solo el primer agente gana el claim del ticket")
    void onlyTheFirstAgentWinsTheClaim() {
        PrintClaimService service = new PrintClaimService(Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.claim("ticket:84")).isTrue();
        assertThat(service.claim("ticket:84")).isFalse();
        // Another order is a different claim
        assertThat(service.claim("ticket:85")).isTrue();
    }

    @Test
    @DisplayName("Un claim viejo lo puede tomar otra PC (el agente se cayó)")
    void staleClaimCanBeTakenOver() {
        Clock start = Clock.fixed(NOW, ZoneOffset.UTC);
        PrintClaimService service = new PrintClaimService(start);

        assertThat(service.claim("ticket:84")).isTrue();

        // Simulate a later agent: the fixed clock is replaced by moving time forward
        PrintClaimService later = new PrintClaimService(
                Clock.fixed(NOW.plus(PrintClaimService.CLAIM_TTL).plus(Duration.ofSeconds(1)), ZoneOffset.UTC));
        assertThat(later.claim("ticket:84")).isTrue();
    }

    @Test
    @DisplayName("Liberar el claim permite reintentar la impresión")
    void releasedClaimCanBeTakenAgain() {
        PrintClaimService service = new PrintClaimService(Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.claim("ticket:84")).isTrue();
        service.release("ticket:84");
        assertThat(service.claim("ticket:84")).isTrue();
    }

    @Test
    @DisplayName("Una llave vacía nunca gana el claim")
    void blankKeyIsNeverClaimed() {
        PrintClaimService service = new PrintClaimService(Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.claim(null)).isFalse();
        assertThat(service.claim("  ")).isFalse();
    }
}
