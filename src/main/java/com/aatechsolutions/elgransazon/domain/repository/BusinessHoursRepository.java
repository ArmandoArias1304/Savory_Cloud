package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.BusinessHours;
import com.aatechsolutions.elgransazon.domain.entity.DayOfWeek;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for BusinessHours entity
 */
@Repository
public interface BusinessHoursRepository extends JpaRepository<BusinessHours, Long> {

    /**
     * Find business hours by system configuration ID
     */
    @Query("SELECT bh FROM BusinessHours bh WHERE bh.systemConfiguration.id = :configId ORDER BY bh.dayOfWeek")
    List<BusinessHours> findBySystemConfigurationId(@Param("configId") Long configId);

    /**
     * Find business hours for a specific day
     */
    @Query("SELECT bh FROM BusinessHours bh WHERE bh.systemConfiguration.id = :configId AND bh.dayOfWeek = :day")
    Optional<BusinessHours> findBySystemConfigurationIdAndDayOfWeek(
            @Param("configId") Long configId, 
            @Param("day") DayOfWeek day
    );

    /**
     * Find all active business hours (not closed days)
     */
    @Query("SELECT bh FROM BusinessHours bh WHERE bh.systemConfiguration.id = :configId AND bh.isClosed = false ORDER BY bh.dayOfWeek")
    List<BusinessHours> findActiveBySystemConfigurationId(@Param("configId") Long configId);

    /**
     * Delete all business hours for a system configuration
     */
    void deleteBySystemConfigurationId(Long configId);

    /**
     * Check if business hours exist for a specific day
     */
    boolean existsBySystemConfigurationIdAndDayOfWeek(Long configId, DayOfWeek day);
}
