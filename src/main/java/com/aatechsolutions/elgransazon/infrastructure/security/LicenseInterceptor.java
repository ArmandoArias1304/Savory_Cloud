package com.aatechsolutions.elgransazon.infrastructure.security;

import com.aatechsolutions.elgransazon.application.service.LicenseService;
import com.aatechsolutions.elgransazon.domain.entity.SystemLicense;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.aatechsolutions.elgransazon.infrastructure.util.CompanyLocalTime;

/**
 * Filter to check license validity on each request
 * Blocks access if license is expired or invalid
 * Adds warning attributes if license is about to expire
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LicenseInterceptor extends OncePerRequestFilter {

    private final LicenseService licenseService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        SystemLicense license = licenseService.getLicense();

        // If no license exists or is suspended (not checking expired here - LicenseValidationFilter handles that)
        // This filter only blocks for missing or suspended licenses
        if (license == null || license.getStatus() == SystemLicense.LicenseStatus.SUSPENDED) {
            
            String reason = license == null ? "missing" : "suspended";
            log.warn("License is {}. Blocking access to: {}", reason, request.getRequestURI());
            response.sendRedirect(request.getContextPath() + "/license-expired");
            return;
        }

        // If license is about to expire, add warning attributes
        long daysLeft = license.daysUntilExpiration();
        if (daysLeft <= 5 && daysLeft >= 0) {
            request.setAttribute("showLicenseWarning", true);
            request.setAttribute("daysLeft", daysLeft);
            LocalDateTime localExpiration = license.getExpirationDate()
                .atZone(ZoneId.of("UTC"))
                .withZoneSameInstant(CompanyLocalTime.getZone())
                .toLocalDateTime();
            request.setAttribute("expirationDate", 
                localExpiration.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
            request.setAttribute("billingCycle", license.getBillingCycle().getDisplayName());
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        
        // Don't filter these paths
        return path.equals("/") ||             // System landing page (no company)
               path.startsWith("/home") ||     // System landing page alternative
               path.startsWith("/login") ||
               path.startsWith("/client/login") ||
               path.startsWith("/programmer/") ||  // Allow programmer access
               path.startsWith("/license-expired") ||
               path.startsWith("/css/") ||
               path.startsWith("/js/") ||
               path.startsWith("/images/") ||
               path.startsWith("/fonts/") ||
               path.startsWith("/webjars/") ||
               path.startsWith("/favicon.ico") ||
               path.startsWith("/system-landing/") ||
               path.startsWith("/ws") ||
               path.startsWith("/topic/") ||
               path.startsWith("/sounds/") ||
               path.startsWith("/flyers/") ||  // Flyers editor (public static resource)
               path.equals("/logout") ||
               path.equals("/perform_login") ||
               path.endsWith(".css") ||
               path.endsWith(".js") ||
               path.endsWith(".map") ||
               path.endsWith(".png") ||
               path.endsWith(".jpg") ||
               path.endsWith(".jpeg") ||
               path.endsWith(".svg") ||
               path.endsWith(".ico") ||
               path.endsWith(".woff") ||
               path.endsWith(".woff2") ||
               path.endsWith(".ttf") ||
               path.endsWith(".mp3");
    }
}
