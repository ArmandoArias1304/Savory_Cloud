package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.SocialNetwork;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for SocialNetwork entity
 */
@Repository
public interface SocialNetworkRepository extends JpaRepository<SocialNetwork, Long> {

    /**
     * Find social networks by system configuration ID
     */
    @Query("SELECT sn FROM SocialNetwork sn WHERE sn.systemConfiguration.id = :configId ORDER BY sn.displayOrder, sn.name")
    List<SocialNetwork> findBySystemConfigurationId(@Param("configId") Long configId);

    /**
     * Find all active social networks
     */
    @Query("SELECT sn FROM SocialNetwork sn WHERE sn.systemConfiguration.id = :configId AND sn.active = true ORDER BY sn.displayOrder, sn.name")
    List<SocialNetwork> findActiveBySystemConfigurationId(@Param("configId") Long configId);

    /**
     * Delete all social networks for a system configuration
     */
    void deleteBySystemConfigurationId(Long configId);

    /**
     * Find by name (case insensitive)
     */
    @Query("SELECT sn FROM SocialNetwork sn WHERE sn.systemConfiguration.id = :configId AND LOWER(sn.name) = LOWER(:name)")
    List<SocialNetwork> findBySystemConfigurationIdAndNameIgnoreCase(@Param("configId") Long configId, @Param("name") String name);

    /**
     * Count active social networks
     */
    @Query("SELECT COUNT(sn) FROM SocialNetwork sn WHERE sn.systemConfiguration.id = :configId AND sn.active = true")
    long countActiveBySystemConfigurationId(@Param("configId") Long configId);
}
