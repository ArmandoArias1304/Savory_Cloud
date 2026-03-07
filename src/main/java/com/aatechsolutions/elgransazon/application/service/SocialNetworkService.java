package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.SocialNetwork;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for SocialNetwork business logic
 */
public interface SocialNetworkService {

    /**
     * Get all social networks
     */
    List<SocialNetwork> getAllSocialNetworks();

    /**
     * Get all active social networks
     */
    List<SocialNetwork> getAllActiveSocialNetworks();

    /**
     * Get social network by ID
     */
    Optional<SocialNetwork> getSocialNetworkById(Long id);

    /**
     * Create a new social network
     */
    SocialNetwork createSocialNetwork(SocialNetwork socialNetwork);

    /**
     * Update an existing social network
     */
    SocialNetwork updateSocialNetwork(Long id, SocialNetwork socialNetwork);

    /**
     * Delete a social network
     */
    void deleteSocialNetwork(Long id);

    /**
     * Activate a social network
     */
    void activateSocialNetwork(Long id);

    /**
     * Deactivate a social network
     */
    void deactivateSocialNetwork(Long id);

    /**
     * Reorder social networks
     */
    void reorderSocialNetworks(List<Long> socialNetworkIds);

    /**
     * Count active social networks
     */
    long countActiveSocialNetworks();
}
