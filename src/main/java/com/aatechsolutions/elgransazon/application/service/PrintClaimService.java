package com.aatechsolutions.elgransazon.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exactly-once guard for company-wide print events.
 *
 * <p>A ticket print request is broadcast to every printer agent of the company
 * ({@code /topic/print/ticket/{companyId}}). Agents that do not have the ticket
 * printer physically connected skip the job by themselves, but two PCs sharing
 * the same printer (or the same printer being installed twice) would print the
 * ticket twice. Each agent asks for the claim first: only the agent that gets
 * {@code true} prints.
 *
 * <p>Claims are kept in memory with a short TTL: if the app restarts before an
 * agent consumes the event, the ticket can be printed again manually.
 */
@Service
@Slf4j
public class PrintClaimService {

    /** How long a claim blocks other agents before it is considered stale. */
    static final Duration CLAIM_TTL = Duration.ofSeconds(90);

    private final Map<String, Instant> claims = new ConcurrentHashMap<>();
    private final Clock clock;

    public PrintClaimService() {
        this(Clock.systemUTC());
    }

    /** Visible for tests: inject a fixed clock to exercise the TTL. */
    PrintClaimService(Clock clock) {
        this.clock = clock;
    }

    /**
     * Tries to reserve {@code key} for the caller.
     *
     * @return true when the caller won the claim and should perform the print,
     *         false when another agent already claimed it (or it is still fresh).
     */
    public boolean claim(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        purgeExpired();
        Instant now = clock.instant();
        Instant previous = claims.putIfAbsent(key, now);
        if (previous == null) {
            log.debug("Print claim granted: {}", key);
            return true;
        }
        if (Duration.between(previous, now).compareTo(CLAIM_TTL) > 0) {
            // Stale claim (owner crashed before printing): allow this agent to take over.
            claims.put(key, now);
            log.info("Stale print claim taken over: {}", key);
            return true;
        }
        log.debug("Print claim already taken: {}", key);
        return false;
    }

    /** Removes a claim (used by tests and manual reprint flows). */
    public void release(String key) {
        if (key != null) {
            claims.remove(key);
        }
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        claims.entrySet().removeIf(e -> Duration.between(e.getValue(), now).compareTo(CLAIM_TTL) > 0);
    }
}
