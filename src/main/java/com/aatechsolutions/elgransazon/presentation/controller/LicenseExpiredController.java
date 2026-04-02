package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.LicenseService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.domain.entity.SystemLicense;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for license expired page.
 * If the company's license is currently active, redirects away from this page
 * so that users who were previously blocked (expired/suspended) can log back in
 * after the license is renewed or reactivated.
 * Note: GlobalControllerAdvice automatically provides globalSystemConfig to all views.
 */
@Controller
@RequiredArgsConstructor
public class LicenseExpiredController {

    private final LicenseService licenseService;
    private final SystemConfigurationService systemConfigurationService;

    @GetMapping("/license-expired")
    public String licenseExpired(Model model,
            @RequestParam(value = "client", required = false, defaultValue = "false") boolean clientView) {
        SystemLicense license = licenseService.getLicense();

        // If the license is now valid, redirect away from this page.
        // Session is already invalidated by the filter, so use the 'client' query param
        // (preserved in the URL) to determine the correct login page.
        if (license != null
                && !license.isExpired()
                && license.getStatus() != SystemLicense.LicenseStatus.EXPIRED
                && license.getStatus() != SystemLicense.LicenseStatus.SUSPENDED) {

            return clientView ? "redirect:/client/login" : "redirect:/login";
        }

        if (license != null) {
            model.addAttribute("isSuspended", license.getStatus() == SystemLicense.LicenseStatus.SUSPENDED);
            model.addAttribute("isExpired", license.isExpired());
            model.addAttribute("expirationDate", license.getExpirationDate());
        } else {
            model.addAttribute("noLicense", true);
        }

        model.addAttribute("isClientView", clientView);

        // Pass restaurant config for client-facing view
        if (clientView) {
            SystemConfiguration sysConfig = systemConfigurationService.getConfiguration();
            model.addAttribute("restaurantConfig", sysConfig);
        }

        return "license-expired";
    }
}
