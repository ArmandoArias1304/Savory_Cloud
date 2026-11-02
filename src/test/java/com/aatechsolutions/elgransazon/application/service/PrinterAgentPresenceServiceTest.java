package com.aatechsolutions.elgransazon.application.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrinterAgentPresenceServiceTest {

    private final PrinterAgentPresenceService service = new PrinterAgentPresenceService();

    @Test
    void warnsWhenAnotherAgentIsAlreadyActiveForTheSameCompany() {
        assertThat(service.activate(1L, "agent-one", "manager", false).activated()).isTrue();

        PrinterAgentPresenceService.ActivationResult secondAgent =
                service.activate(1L, "agent-two", "admin", false);

        assertThat(secondAgent.activated()).isFalse();
        assertThat(secondAgent.activeUsername()).isEqualTo("manager");
    }

    @Test
    void allowsExplicitActivationAndKeepsCompanyPresenceIsolated() {
        service.activate(1L, "agent-one", "manager", false);

        assertThat(service.activate(1L, "agent-two", "admin", true).activated()).isTrue();
        assertThat(service.activate(2L, "agent-three", "admin", false).activated()).isTrue();
        assertThat(service.findActiveUsername(1L)).isEqualTo("manager");
        assertThat(service.findActiveUsername(2L)).isEqualTo("admin");
        assertThat(service.findActiveUsername(3L)).isNull();
    }

    @Test
    void onlyTheOwningUserCanRefreshOrDeactivateAnAgent() {
        service.activate(1L, "agent-one", "manager", false);

        assertThat(service.heartbeat(1L, "agent-one", "admin")).isFalse();
        service.deactivate(1L, "agent-one", "admin");
        assertThat(service.activate(1L, "agent-two", "admin", false).activated()).isFalse();
        assertThat(service.heartbeat(1L, "agent-one", "manager")).isTrue();
        service.deactivate(1L, "agent-one", "manager");
        assertThat(service.activate(1L, "agent-two", "admin", false).activated()).isTrue();
    }
}
