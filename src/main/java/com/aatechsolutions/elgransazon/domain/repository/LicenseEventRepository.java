package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.LicenseEvent;
import com.aatechsolutions.elgransazon.domain.entity.LicenseEvent.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for LicenseEvent entity
 */
@Repository
public interface LicenseEventRepository extends JpaRepository<LicenseEvent, Long> {

    /**
     * Find events by license ID
     */
    List<LicenseEvent> findByLicenseIdOrderByEventDateDesc(Long licenseId);

    /**
     * Find events by type
     */
    List<LicenseEvent> findByEventType(EventType eventType);

    /**
     * Find recent events (top N)
     */
    List<LicenseEvent> findTop10ByOrderByEventDateDesc();

    /**
     * Find events between dates
     */
    List<LicenseEvent> findByEventDateBetweenOrderByEventDateDesc(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find events by license ID and type
     */
    List<LicenseEvent> findByLicenseIdAndEventTypeOrderByEventDateDesc(Long licenseId, EventType eventType);
}
