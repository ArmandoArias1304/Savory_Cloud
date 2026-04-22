package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.BusinessHoursService;
import com.aatechsolutions.elgransazon.application.service.ImageStorageService;
import com.aatechsolutions.elgransazon.application.service.LicenseService;
import com.aatechsolutions.elgransazon.application.service.SocialNetworkService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.domain.entity.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.propertyeditors.CustomNumberEditor;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalTime;
import java.util.*;

/**
 * Controller for managing system configuration
 * Only accessible by ADMIN role
 */
@Controller
@RequestMapping("/admin/system-configuration")
@PreAuthorize("hasRole('ROLE_ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class SystemConfigurationController {

    private final SystemConfigurationService configurationService;
    private final BusinessHoursService businessHoursService;
    private final SocialNetworkService socialNetworkService;
    private final LicenseService licenseService;
    private final ImageStorageService imageStorageService;

    /**
     * Treat empty form inputs as null so the optional Double/Integer fields
     * (restaurantLatitude/Longitude, deliveryMaxDistanceMeters) bind cleanly.
     */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
        // Allow empty form inputs to bind as null on optional numeric fields
        // (restaurantLatitude / restaurantLongitude / deliveryMaxDistanceMeters).
        binder.registerCustomEditor(Double.class, new CustomNumberEditor(Double.class, true));
        binder.registerCustomEditor(Integer.class, new CustomNumberEditor(Integer.class, true));
    }

    /**
     * Display system configuration page
     */
    @GetMapping
    public String showConfiguration(Model model) {
        log.debug("Displaying system configuration page");
        
        SystemConfiguration config = configurationService.getConfiguration();
        List<BusinessHours> businessHours = businessHoursService.getAllBusinessHours();
        List<SocialNetwork> socialNetworks = socialNetworkService.getAllSocialNetworks();
        
        // Ensure businessHoursMap is never null
        Map<DayOfWeek, BusinessHours> businessHoursMap = createBusinessHoursMap(businessHours);
        if (businessHoursMap == null) {
            businessHoursMap = new HashMap<>();
        }
        
        model.addAttribute("configuration", config);
        model.addAttribute("businessHours", businessHours);
        model.addAttribute("businessHoursMap", businessHoursMap);
        model.addAttribute("socialNetworks", socialNetworks);
        model.addAttribute("allDays", DayOfWeek.values());
        model.addAttribute("paymentMethodTypes", PaymentMethodType.values());
        
        // Add license information
        SystemLicense license = licenseService.getLicense();
        model.addAttribute("license", license);
        if (license != null) {
            model.addAttribute("daysRemaining", licenseService.getDaysUntilExpiration());
            // Financial summary for the company in context
            model.addAttribute("totalRevenue", licenseService.getTotalRevenue());
            model.addAttribute("renewalsWithAmount", licenseService.getRenewalEventsWithAmount());
        }
        
        return "admin/system-configuration/form";
    }

    /**
     * Update general configuration
     */
    @PostMapping("/update")
    public String updateConfiguration(
            @Valid @ModelAttribute("configuration") SystemConfiguration configuration,
            BindingResult bindingResult,
            @RequestParam(value = "paymentCash", required = false) Boolean paymentCash,
            @RequestParam(value = "paymentCreditCard", required = false) Boolean paymentCreditCard,
            @RequestParam(value = "paymentDebitCard", required = false) Boolean paymentDebitCard,
            @RequestParam(value = "paymentTransfer", required = false) Boolean paymentTransfer,
            @RequestParam(value = "deliveryPaymentCash", required = false) Boolean deliveryPaymentCash,
            @RequestParam(value = "deliveryPaymentCreditCard", required = false) Boolean deliveryPaymentCreditCard,
            @RequestParam(value = "deliveryPaymentDebitCard", required = false) Boolean deliveryPaymentDebitCard,
            @RequestParam(value = "deliveryPaymentTransfer", required = false) Boolean deliveryPaymentTransfer,
            RedirectAttributes redirectAttributes,
            Model model) {

        log.info("Processing system configuration update");
        log.debug("Received averageConsumptionTimeMinutes: {}", configuration.getAverageConsumptionTimeMinutes());

        if (bindingResult.hasErrors()) {
            log.warn("Validation errors on configuration update");
            log.warn("Total errors: {}", bindingResult.getErrorCount());
            bindingResult.getFieldErrors().forEach(error -> 
                log.warn("Field '{}' has error: {} (rejected value: {})", 
                    error.getField(), 
                    error.getDefaultMessage(), 
                    error.getRejectedValue())
            );
            List<BusinessHours> businessHoursList = businessHoursService.getAllBusinessHours();
            Map<DayOfWeek, BusinessHours> businessHoursMap = createBusinessHoursMap(businessHoursList);
            if (businessHoursMap == null) {
                businessHoursMap = new HashMap<>();
            }
            
            model.addAttribute("businessHours", businessHoursList);
            model.addAttribute("businessHoursMap", businessHoursMap);
            model.addAttribute("socialNetworks", socialNetworkService.getAllSocialNetworks());
            model.addAttribute("allDays", DayOfWeek.values());
            model.addAttribute("paymentMethodTypes", PaymentMethodType.values());
            return "admin/system-configuration/form";
        }

        try {
            // Update restaurant/in-house payment methods
            Map<PaymentMethodType, Boolean> paymentMethods = new HashMap<>();
            paymentMethods.put(PaymentMethodType.CASH, paymentCash != null && paymentCash);
            paymentMethods.put(PaymentMethodType.CREDIT_CARD, paymentCreditCard != null && paymentCreditCard);
            paymentMethods.put(PaymentMethodType.DEBIT_CARD, paymentDebitCard != null && paymentDebitCard);
            paymentMethods.put(PaymentMethodType.TRANSFER, paymentTransfer != null && paymentTransfer);
            configuration.setPaymentMethods(paymentMethods);

            // Update delivery payment methods (separate from restaurant)
            Map<PaymentMethodType, Boolean> deliveryPaymentMethods = new HashMap<>();
            deliveryPaymentMethods.put(PaymentMethodType.CASH, deliveryPaymentCash != null && deliveryPaymentCash);
            deliveryPaymentMethods.put(PaymentMethodType.CREDIT_CARD, deliveryPaymentCreditCard != null && deliveryPaymentCreditCard);
            deliveryPaymentMethods.put(PaymentMethodType.DEBIT_CARD, deliveryPaymentDebitCard != null && deliveryPaymentDebitCard);
            deliveryPaymentMethods.put(PaymentMethodType.TRANSFER, deliveryPaymentTransfer != null && deliveryPaymentTransfer);
            configuration.setDeliveryPaymentMethods(deliveryPaymentMethods);

            configurationService.updateConfiguration(configuration);
            
            log.info("System configuration updated successfully");
            redirectAttributes.addFlashAttribute("successMessage", "Configuración actualizada exitosamente");
            return "redirect:/admin/system-configuration";

        } catch (Exception e) {
            log.error("Error updating system configuration: {}", e.getMessage());
            List<BusinessHours> businessHoursList = businessHoursService.getAllBusinessHours();
            Map<DayOfWeek, BusinessHours> businessHoursMap = createBusinessHoursMap(businessHoursList);
            if (businessHoursMap == null) {
                businessHoursMap = new HashMap<>();
            }
            
            model.addAttribute("errorMessage", "Error al actualizar la configuración: " + e.getMessage());
            model.addAttribute("businessHours", businessHoursList);
            model.addAttribute("businessHoursMap", businessHoursMap);
            model.addAttribute("socialNetworks", socialNetworkService.getAllSocialNetworks());
            model.addAttribute("allDays", DayOfWeek.values());
            model.addAttribute("paymentMethodTypes", PaymentMethodType.values());
            return "admin/system-configuration/form";
        }
    }

    /**
     * Update business hours for all days
     */
    @PostMapping("/business-hours/update")
    public String updateBusinessHours(
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {

        log.info("Processing business hours update");

        try {
            // Update business hours for ALL days (not just work days)
            for (DayOfWeek day : DayOfWeek.values()) {
                String dayName = day.name();
                String openTimeStr = allParams.get("openTime_" + dayName);
                String closeTimeStr = allParams.get("closeTime_" + dayName);
                String isClosedStr = allParams.get("isClosed_" + dayName);

                Boolean isClosed = "on".equals(isClosedStr);
                
                if (openTimeStr != null && closeTimeStr != null && !openTimeStr.isEmpty() && !closeTimeStr.isEmpty()) {
                    LocalTime openTime = LocalTime.parse(openTimeStr);
                    LocalTime closeTime = LocalTime.parse(closeTimeStr);
                    
                    businessHoursService.updateBusinessHoursForDay(day, openTime, closeTime, isClosed);
                } else if (isClosed) {
                    // If marked as closed, set default times but mark as closed
                    businessHoursService.updateBusinessHoursForDay(day, LocalTime.of(8, 0), LocalTime.of(22, 0), true);
                }
            }

            log.info("Business hours updated successfully");
            redirectAttributes.addFlashAttribute("successMessage", "Horarios actualizados exitosamente");
            return "redirect:/admin/system-configuration";

        } catch (Exception e) {
            log.error("Error updating business hours: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al actualizar horarios: " + e.getMessage());
            return "redirect:/admin/system-configuration";
        }
    }

    /**
     * Show form to add new social network
     */
    @GetMapping("/social-networks/new")
    public String showAddSocialNetworkForm(Model model) {
        log.debug("Displaying add social network form");
        model.addAttribute("socialNetwork", new SocialNetwork());
        model.addAttribute("isEdit", false);
        return "admin/system-configuration/social-network-form";
    }

    /**
     * Create new social network
     */
    @PostMapping("/social-networks/create")
    public String createSocialNetwork(
            @Valid @ModelAttribute("socialNetwork") SocialNetwork socialNetwork,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        log.info("Processing social network creation: {}", socialNetwork.getName());

        if (bindingResult.hasErrors()) {
            log.warn("Validation errors on social network creation");
            model.addAttribute("isEdit", false);
            return "admin/system-configuration/social-network-form";
        }

        try {
            socialNetworkService.createSocialNetwork(socialNetwork);
            log.info("Social network created successfully");
            redirectAttributes.addFlashAttribute("successMessage", "Red social agregada exitosamente");
            return "redirect:/admin/system-configuration";

        } catch (Exception e) {
            log.error("Error creating social network: {}", e.getMessage());
            model.addAttribute("errorMessage", "Error al agregar red social: " + e.getMessage());
            model.addAttribute("isEdit", false);
            return "admin/system-configuration/social-network-form";
        }
    }

    /**
     * Show form to edit social network
     */
    @GetMapping("/social-networks/{id}/edit")
    public String showEditSocialNetworkForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("Displaying edit social network form for ID: {}", id);

        return socialNetworkService.getSocialNetworkById(id)
                .map(network -> {
                    model.addAttribute("socialNetwork", network);
                    model.addAttribute("isEdit", true);
                    return "admin/system-configuration/social-network-form";
                })
                .orElseGet(() -> {
                    log.warn("Social network not found with ID: {}", id);
                    redirectAttributes.addFlashAttribute("errorMessage", "Red social no encontrada");
                    return "redirect:/admin/system-configuration";
                });
    }

    /**
     * Update social network
     */
    @PostMapping("/social-networks/{id}/update")
    public String updateSocialNetwork(
            @PathVariable Long id,
            @Valid @ModelAttribute("socialNetwork") SocialNetwork socialNetwork,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        log.info("Processing social network update for ID: {}", id);

        if (bindingResult.hasErrors()) {
            log.warn("Validation errors on social network update");
            model.addAttribute("isEdit", true);
            return "admin/system-configuration/social-network-form";
        }

        try {
            socialNetworkService.updateSocialNetwork(id, socialNetwork);
            log.info("Social network updated successfully");
            redirectAttributes.addFlashAttribute("successMessage", "Red social actualizada exitosamente");
            return "redirect:/admin/system-configuration";

        } catch (Exception e) {
            log.error("Error updating social network: {}", e.getMessage());
            model.addAttribute("errorMessage", "Error al actualizar red social: " + e.getMessage());
            model.addAttribute("isEdit", true);
            return "admin/system-configuration/social-network-form";
        }
    }

    /**
     * Delete social network
     */
    @PostMapping("/social-networks/{id}/delete")
    public String deleteSocialNetwork(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Processing social network deletion for ID: {}", id);

        try {
            socialNetworkService.deleteSocialNetwork(id);
            redirectAttributes.addFlashAttribute("successMessage", "Red social eliminada exitosamente");
        } catch (Exception e) {
            log.error("Error deleting social network: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al eliminar red social: " + e.getMessage());
        }

        return "redirect:/admin/system-configuration";
    }

    /**
     * Toggle social network active status
     */
    @PostMapping("/social-networks/{id}/toggle")
    public String toggleSocialNetwork(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Toggling social network status for ID: {}", id);

        try {
            SocialNetwork network = socialNetworkService.getSocialNetworkById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Red social no encontrada"));

            if (network.getActive()) {
                socialNetworkService.deactivateSocialNetwork(id);
                redirectAttributes.addFlashAttribute("successMessage", "Red social desactivada");
            } else {
                socialNetworkService.activateSocialNetwork(id);
                redirectAttributes.addFlashAttribute("successMessage", "Red social activada");
            }
        } catch (Exception e) {
            log.error("Error toggling social network: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/system-configuration";
    }

    /**
     * Upload restaurant logo image
     */
    @PostMapping("/upload-restaurant-logo")
    public String uploadRestaurantLogo(
            @RequestParam("restaurantLogoFile") MultipartFile file,
            RedirectAttributes redirectAttributes) {

        log.info("Processing restaurant logo upload");

        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No se seleccionó ningún archivo");
            return "redirect:/admin/system-configuration";
        }

        try {
            // Delete previous logo from Cloudinary if exists
            SystemConfiguration config = configurationService.getConfiguration();
            if (config.getRestaurantLogoUrl() != null && !config.getRestaurantLogoUrl().isEmpty()) {
                imageStorageService.deleteImage(config.getRestaurantLogoUrl());
            }

            // Upload to Cloudinary in Home/CompanyLogoImage folder
            String imageUrl = imageStorageService.saveImage(file, "company-logo", "restaurant-logo");
            config.setRestaurantLogoUrl(imageUrl);
            configurationService.updateConfiguration(config);

            log.info("Restaurant logo uploaded successfully: {}", imageUrl);
            redirectAttributes.addFlashAttribute("successMessage", "Logo del restaurante actualizado exitosamente");
        } catch (Exception e) {
            log.error("Error uploading restaurant logo: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al subir el logo: " + e.getMessage());
        }

        return "redirect:/admin/system-configuration";
    }

    /**
     * Helper method to create a map of business hours by day
     */
    private Map<DayOfWeek, BusinessHours> createBusinessHoursMap(List<BusinessHours> businessHoursList) {
        Map<DayOfWeek, BusinessHours> map = new HashMap<>();
        for (BusinessHours hours : businessHoursList) {
            map.put(hours.getDayOfWeek(), hours);
        }
        return map;
    }
}
