package com.aatechsolutions.elgransazon.application.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Service
public class PrinterAgentPresenceService {

    private static final Duration ACTIVE_WINDOW = Duration.ofMinutes(2);

    private final Map<Long, Map<String, AgentPresence>> agentsByCompany = new HashMap<>();

    public synchronized ActivationResult activate(
            Long companyId, String agentId, String username, boolean force) {
        Map<String, AgentPresence> companyAgents = agentsByCompany.computeIfAbsent(
                companyId, ignored -> new HashMap<>());
        removeExpiredAgents(companyAgents);

        AgentPresence activeAgent = companyAgents.values().stream()
                .filter(agent -> !agent.agentId().equals(agentId))
                .findFirst()
                .orElse(null);
        if (activeAgent != null && !force) {
            return new ActivationResult(false, activeAgent.username());
        }

        companyAgents.put(agentId, new AgentPresence(agentId, username, Instant.now()));
        return new ActivationResult(true, null);
    }

    public synchronized boolean heartbeat(Long companyId, String agentId, String username) {
        Map<String, AgentPresence> companyAgents = agentsByCompany.get(companyId);
        if (companyAgents == null) {
            return false;
        }

        removeExpiredAgents(companyAgents);
        AgentPresence agent = companyAgents.get(agentId);
        if (agent == null || !agent.username().equals(username)) {
            return false;
        }

        companyAgents.put(agentId, new AgentPresence(agentId, username, Instant.now()));
        return true;
    }

    public synchronized String findActiveUsername(Long companyId) {
        Map<String, AgentPresence> companyAgents = agentsByCompany.get(companyId);
        if (companyAgents == null) {
            return null;
        }

        removeExpiredAgents(companyAgents);
        String username = companyAgents.values().stream()
                .map(AgentPresence::username)
                .findFirst()
                .orElse(null);
        if (companyAgents.isEmpty()) {
            agentsByCompany.remove(companyId);
        }
        return username;
    }

    public synchronized void deactivate(Long companyId, String agentId, String username) {
        Map<String, AgentPresence> companyAgents = agentsByCompany.get(companyId);
        if (companyAgents == null) {
            return;
        }

        AgentPresence agent = companyAgents.get(agentId);
        if (agent != null && agent.username().equals(username)) {
            companyAgents.remove(agentId);
        }
        if (companyAgents.isEmpty()) {
            agentsByCompany.remove(companyId);
        }
    }

    private void removeExpiredAgents(Map<String, AgentPresence> companyAgents) {
        Instant expiresBefore = Instant.now().minus(ACTIVE_WINDOW);
        Iterator<AgentPresence> agents = companyAgents.values().iterator();
        while (agents.hasNext()) {
            if (agents.next().lastSeen().isBefore(expiresBefore)) {
                agents.remove();
            }
        }
    }

    public record ActivationResult(boolean activated, String activeUsername) {}

    private record AgentPresence(String agentId, String username, Instant lastSeen) {}
}
