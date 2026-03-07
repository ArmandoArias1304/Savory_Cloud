package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.BusinessHours;
import com.aatechsolutions.elgransazon.domain.entity.DayOfWeek;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for BusinessHours business logic
 */
public interface BusinessHoursService {

    /**
     * Get all business hours for the system
     */
    List<BusinessHours> getAllBusinessHours();

    /**
     * Get business hours for a specific day
     */
    Optional<BusinessHours> getBusinessHoursForDay(DayOfWeek day);

    /**
     * Get business hours by ID
     */
    Optional<BusinessHours> getBusinessHoursById(Long id);

    /**
     * Create or update business hours for a day
     * @param businessHours the business hours to save
     * @return the saved business hours
     */
    BusinessHours saveBusinessHours(BusinessHours businessHours);

    /**
     * Create or update multiple business hours
     */
    List<BusinessHours> saveAllBusinessHours(List<BusinessHours> businessHoursList);

    /**
     * Delete business hours by ID
     */
    void deleteBusinessHours(Long id);

    /**
     * Delete all business hours
     */
    void deleteAllBusinessHours();

    /**
     * Update business hours for a specific day
     */
    BusinessHours updateBusinessHoursForDay(DayOfWeek day, LocalTime openTime, LocalTime closeTime, Boolean isClosed);

    /**
     * Check if restaurant is open at a specific day and time
     */
    boolean isOpenAt(DayOfWeek day, LocalTime time);

    /**
     * Check if restaurant is currently open
     * Uses current date and time to determine if restaurant is open
     */
    boolean isOpenNow();

    /**
     * Get all active business hours (not closed days)
     */
    List<BusinessHours> getActiveBusinessHours();

    /**
     * Validate that business hours are within work days
     */
    void validateBusinessHoursWithWorkDays(List<BusinessHours> businessHoursList);
}
