package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.LicenseService;
import com.aatechsolutions.elgransazon.domain.entity.SystemLicense;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for license expired page
 * Note: GlobalControllerAdvice automatically provides globalSystemConfig to all views
 */
@Controller
@RequiredArgsConstructor
public class LicenseExpiredController {

    private final LicenseService licenseService;

    @GetMapping("/license-expired")
    public String licenseExpired(Model model) {
        SystemLicense license = licenseService.getLicense();
        
        if (license != null) {
            model.addAttribute("isSuspended", license.getStatus() == SystemLicense.LicenseStatus.SUSPENDED);
            model.addAttribute("isExpired", license.isExpired());
            model.addAttribute("expirationDate", license.getExpirationDate());
        } else {
            model.addAttribute("noLicense", true);
        }
        
        // Note: globalSystemConfig is automatically provided by GlobalControllerAdvice
        
        return "license-expired";
    }
}
