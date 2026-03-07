package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.SocialNetwork;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.domain.repository.SocialNetworkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Implementation of SocialNetworkService
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SocialNetworkServiceImpl implements SocialNetworkService {

    private final SocialNetworkRepository socialNetworkRepository;
    private final SystemConfigurationService configurationService;

    @Override
    @Transactional(readOnly = true)
    public List<SocialNetwork> getAllSocialNetworks() {
        log.debug("Fetching all social networks");
        SystemConfiguration config = configurationService.getConfiguration();
        return socialNetworkRepository.findBySystemConfigurationId(config.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SocialNetwork> getAllActiveSocialNetworks() {
        log.debug("Fetching all active social networks");
        SystemConfiguration config = configurationService.getConfiguration();
        return socialNetworkRepository.findActiveBySystemConfigurationId(config.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SocialNetwork> getSocialNetworkById(Long id) {
        log.debug("Fetching social network by ID: {}", id);
        return socialNetworkRepository.findById(id);
    }

    @Override
    public SocialNetwork createSocialNetwork(SocialNetwork socialNetwork) {
        log.info("Creating social network: {}", socialNetwork.getName());
        
        // Get system configuration
        SystemConfiguration config = configurationService.getConfiguration();
        
        // Set system configuration
        socialNetwork.setSystemConfiguration(config);
        
        // Set default display order if not set
        if (socialNetwork.getDisplayOrder() == null) {
            long count = socialNetworkRepository.findBySystemConfigurationId(config.getId()).size();
            socialNetwork.setDisplayOrder((int) count + 1);
        }
        
        SocialNetwork saved = socialNetworkRepository.save(socialNetwork);
        log.info("Social network created successfully with ID: {}", saved.getId());
        return saved;
    }

    @Override
    public SocialNetwork updateSocialNetwork(Long id, SocialNetwork socialNetwork) {
        log.info("Updating social network with ID: {}", id);
        
        SocialNetwork existingNetwork = socialNetworkRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Red social no encontrada con ID: " + id));
        
        // Update fields
        existingNetwork.setName(socialNetwork.getName());
        existingNetwork.setUrl(socialNetwork.getUrl());
        existingNetwork.setIcon(socialNetwork.getIcon());
        existingNetwork.setActive(socialNetwork.getActive());
        
        if (socialNetwork.getDisplayOrder() != null) {
            existingNetwork.setDisplayOrder(socialNetwork.getDisplayOrder());
        }
        
        SocialNetwork saved = socialNetworkRepository.save(existingNetwork);
        log.info("Social network updated successfully");
        return saved;
    }

    @Override
    public void deleteSocialNetwork(Long id) {
        log.info("Deleting social network with ID: {}", id);
        
        if (!socialNetworkRepository.existsById(id)) {
            throw new IllegalArgumentException("Red social no encontrada con ID: " + id);
        }
        
        socialNetworkRepository.deleteById(id);
        log.info("Social network deleted successfully");
    }

    @Override
    public void activateSocialNetwork(Long id) {
        log.info("Activating social network with ID: {}", id);
        
        SocialNetwork network = socialNetworkRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Red social no encontrada con ID: " + id));
        
        network.setActive(true);
        socialNetworkRepository.save(network);
        log.info("Social network activated successfully");
    }

    @Override
    public void deactivateSocialNetwork(Long id) {
        log.info("Deactivating social network with ID: {}", id);
        
        SocialNetwork network = socialNetworkRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Red social no encontrada con ID: " + id));
        
        network.setActive(false);
        socialNetworkRepository.save(network);
        log.info("Social network deactivated successfully");
    }

    @Override
    public void reorderSocialNetworks(List<Long> socialNetworkIds) {
        log.info("Reordering {} social networks", socialNetworkIds.size());
        
        for (int i = 0; i < socialNetworkIds.size(); i++) {
            Long id = socialNetworkIds.get(i);
            final int displayOrder = i + 1;
            socialNetworkRepository.findById(id).ifPresent(network -> {
                network.setDisplayOrder(displayOrder);
                socialNetworkRepository.save(network);
            });
        }
        
        log.info("Social networks reordered successfully");
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveSocialNetworks() {
        SystemConfiguration config = configurationService.getConfiguration();
        return socialNetworkRepository.countActiveBySystemConfigurationId(config.getId());
    }
}
