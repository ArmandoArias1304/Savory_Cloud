package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.BusinessHours;
import com.aatechsolutions.elgransazon.domain.entity.DayOfWeek;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.domain.repository.BusinessHoursRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of BusinessHoursService
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BusinessHoursServiceImpl implements BusinessHoursService {

    private final BusinessHoursRepository businessHoursRepository;
    private final SystemConfigurationService configurationService;

    @Override
    @Transactional(readOnly = true)
    public List<BusinessHours> getAllBusinessHours() {
        log.debug("Fetching all business hours");
        SystemConfiguration config = configurationService.getConfiguration();
        return businessHoursRepository.findBySystemConfigurationId(config.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BusinessHours> getBusinessHoursForDay(DayOfWeek day) {
        log.debug("Fetching business hours for day: {}", day);
        SystemConfiguration config = configurationService.getConfiguration();
        return businessHoursRepository.findBySystemConfigurationIdAndDayOfWeek(config.getId(), day);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BusinessHours> getBusinessHoursById(Long id) {
        log.debug("Fetching business hours by ID: {}", id);
        return businessHoursRepository.findById(id);
    }

    @Override
    public BusinessHours saveBusinessHours(BusinessHours businessHours) {
        log.info("Saving business hours for day: {}", businessHours.getDayOfWeek());
        
        // Get system configuration
        SystemConfiguration config = configurationService.getConfiguration();
        
        // Check if business hours already exist for this day
        Optional<BusinessHours> existingHours = businessHoursRepository
                .findBySystemConfigurationIdAndDayOfWeek(config.getId(), businessHours.getDayOfWeek());
        
        if (existingHours.isPresent() && !existingHours.get().getId().equals(businessHours.getId())) {
            throw new IllegalArgumentException("Ya existen horarios para el día " + businessHours.getDayOfWeek().getDisplayName());
        }
        
        // Set system configuration
        businessHours.setSystemConfiguration(config);
        
        BusinessHours saved = businessHoursRepository.save(businessHours);
        log.info("Business hours saved successfully");
        return saved;
    }

    @Override
    public List<BusinessHours> saveAllBusinessHours(List<BusinessHours> businessHoursList) {
        log.info("Saving {} business hours", businessHoursList.size());
        
        // Validate all business hours
        validateBusinessHoursWithWorkDays(businessHoursList);
        
        // Get system configuration
        SystemConfiguration config = configurationService.getConfiguration();
        
        // Set system configuration for all
        businessHoursList.forEach(hours -> hours.setSystemConfiguration(config));
        
        List<BusinessHours> saved = businessHoursRepository.saveAll(businessHoursList);
        log.info("All business hours saved successfully");
        return saved;
    }

    @Override
    public void deleteBusinessHours(Long id) {
        log.info("Deleting business hours with ID: {}", id);
        
        if (!businessHoursRepository.existsById(id)) {
            throw new IllegalArgumentException("Horario no encontrado con ID: " + id);
        }
        
        businessHoursRepository.deleteById(id);
        log.info("Business hours deleted successfully");
    }

    @Override
    public void deleteAllBusinessHours() {
        log.info("Deleting all business hours");
        SystemConfiguration config = configurationService.getConfiguration();
        businessHoursRepository.deleteBySystemConfigurationId(config.getId());
        log.info("All business hours deleted successfully");
    }

    @Override
    public BusinessHours updateBusinessHoursForDay(DayOfWeek day, LocalTime openTime, LocalTime closeTime, Boolean isClosed) {
        log.info("Updating business hours for day: {}", day);
        
        SystemConfiguration config = configurationService.getConfiguration();
        Optional<BusinessHours> existingHours = businessHoursRepository
                .findBySystemConfigurationIdAndDayOfWeek(config.getId(), day);
        
        BusinessHours hours;
        if (existingHours.isPresent()) {
            hours = existingHours.get();
        } else {
            hours = new BusinessHours();
            hours.setDayOfWeek(day);
            hours.setSystemConfiguration(config);
        }
        
        hours.setOpenTime(openTime);
        hours.setCloseTime(closeTime);
        hours.setIsClosed(isClosed != null ? isClosed : false);
        
        BusinessHours saved = businessHoursRepository.save(hours);
        log.info("Business hours updated successfully");
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOpenAt(DayOfWeek day, LocalTime time) {
        log.debug("Checking if open at day: {} time: {}", day, time);
        
        Optional<BusinessHours> hours = getBusinessHoursForDay(day);
        if (hours.isEmpty()) {
            return false;
        }
        
        return hours.get().isOpenAt(time);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOpenNow() {
        log.debug("Checking if restaurant is open now");
        
        // Get current day and time
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.DayOfWeek javaDayOfWeek = now.getDayOfWeek();
        LocalTime currentTime = now.toLocalTime();
        
        // Convert java.time.DayOfWeek to our custom DayOfWeek
        DayOfWeek customDayOfWeek = DayOfWeek.valueOf(javaDayOfWeek.name());
        
        // Check if open at current day and time
        boolean isOpen = isOpenAt(customDayOfWeek, currentTime);
        
        log.debug("Restaurant is {} at {} on {}", isOpen ? "open" : "closed", currentTime, customDayOfWeek.getDisplayName());
        
        return isOpen;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BusinessHours> getActiveBusinessHours() {
        log.debug("Fetching active business hours");
        SystemConfiguration config = configurationService.getConfiguration();
        return businessHoursRepository.findActiveBySystemConfigurationId(config.getId());
    }

    @Override
    public void validateBusinessHoursWithWorkDays(List<BusinessHours> businessHoursList) {
        log.debug("Validating business hours list");
        
        // Validate that all business hours have required data
        for (BusinessHours hours : businessHoursList) {
            if (hours.getDayOfWeek() == null) {
                throw new IllegalArgumentException("El día de la semana es requerido");
            }
            
            // If not closed, validate times
            if (!hours.getIsClosed()) {
                if (hours.getOpenTime() == null || hours.getCloseTime() == null) {
                    throw new IllegalArgumentException(
                        "Para días abiertos, debe especificar hora de apertura y cierre"
                    );
                }
                
                if (hours.getOpenTime().isAfter(hours.getCloseTime()) || 
                    hours.getOpenTime().equals(hours.getCloseTime())) {
                    throw new IllegalArgumentException(
                        "La hora de apertura debe ser anterior a la hora de cierre"
                    );
                }
            }
        }
        
        log.debug("Business hours validation passed");
    }
}
