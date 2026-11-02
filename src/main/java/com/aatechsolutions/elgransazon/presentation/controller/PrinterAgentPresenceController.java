package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.PrinterAgentPresenceService;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/printer-agent")
@PreAuthorize(PrinterController.STAFF)
public class PrinterAgentPresenceController {

    private static final String AGENT_STATUS_ROLES =
            "hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_CASHIER')";

    private final PrinterAgentPresenceService presenceService;

    public PrinterAgentPresenceController(PrinterAgentPresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @GetMapping("/status")
    @PreAuthorize(AGENT_STATUS_ROLES)
    public ResponseEntity<StatusResponse> status() {
        String activeUsername = presenceService.findActiveUsername(
                CompanyContext.requireCurrentCompanyId());
        return ResponseEntity.ok(new StatusResponse(activeUsername != null));
    }

    @PostMapping("/activate")
    public ResponseEntity<ActivationResponse> activate(
            @RequestBody(required = false) ActivationRequest request, Principal principal) {
        if (request == null || request.agentId() == null || request.agentId().isBlank()
                || request.agentId().length() > 100) {
            return ResponseEntity.badRequest().build();
        }

        PrinterAgentPresenceService.ActivationResult result = presenceService.activate(
                CompanyContext.requireCurrentCompanyId(),
                request.agentId(),
                principal.getName(),
                request.force());
        if (!result.activated()) {
            return ResponseEntity.status(409)
                    .body(new ActivationResponse(false, result.activeUsername()));
        }
        return ResponseEntity.ok(new ActivationResponse(true, null));
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(
            @RequestParam String agentId, Principal principal) {
        if (agentId.isBlank() || agentId.length() > 100) {
            return ResponseEntity.badRequest().build();
        }
        if (!presenceService.heartbeat(
                CompanyContext.requireCurrentCompanyId(), agentId, principal.getName())) {
            return ResponseEntity.status(409).build();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/deactivate")
    public ResponseEntity<Void> deactivate(
            @RequestParam String agentId, Principal principal) {
        if (agentId.isBlank() || agentId.length() > 100) {
            return ResponseEntity.badRequest().build();
        }
        presenceService.deactivate(
                CompanyContext.requireCurrentCompanyId(), agentId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    public record ActivationRequest(String agentId, boolean force) {}

    public record ActivationResponse(boolean activated, String activeUsername) {}

    public record StatusResponse(boolean active) {}
}
