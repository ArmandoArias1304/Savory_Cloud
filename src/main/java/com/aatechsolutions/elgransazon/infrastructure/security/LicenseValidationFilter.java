package com.aatechsolutions.elgransazon.infrastructure.security;

import com.aatechsolutions.elgransazon.application.service.LicenseService;
import com.aatechsolutions.elgransazon.domain.entity.SystemLicense;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter to validate license expiration for all users except PROGRAMMER
 * PROGRAMMER role can always access to renew expired licenses
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LicenseValidationFilter extends OncePerRequestFilter {

    private final LicenseService licenseService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        // Get current authentication
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        // Only validate for authenticated users
        if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
            
            // Check if user has PROGRAMMER role
            boolean isProgrammer = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_PROGRAMMER"));
            
            // Skip license validation for PROGRAMMER and certain paths
            String requestPath = request.getRequestURI();
            if (isProgrammer || isExcludedPath(requestPath)) {
                filterChain.doFilter(request, response);
                return;
            }
            
            // Validate license
            SystemLicense license = licenseService.getLicense();
            if (license != null && license.isExpired()) {
                log.warn("License expired. Blocking access for user: {}", auth.getName());

                // Sync DB status if the job hasn't run yet (status still ACTIVE in DB)
                if (license.getStatus() != SystemLicense.LicenseStatus.EXPIRED) {
                    licenseService.markAsExpired();
                }

                // Invalidate session
                request.getSession().invalidate();
                SecurityContextHolder.clearContext();
                
                // Redirect to license expired page
                response.sendRedirect("/license-expired");
                return;
            }
        }
        
        filterChain.doFilter(request, response);
    }

    /**
     * Check if path should be excluded from license validation
     */
    private boolean isExcludedPath(String path) {
        return path.startsWith("/login") ||
               path.startsWith("/logout") ||
               path.startsWith("/error") ||
               path.startsWith("/css") ||
               path.startsWith("/js") ||
               path.startsWith("/images") ||
               path.startsWith("/system-landing") ||
               path.startsWith("/license-expired") ||
               path.startsWith("/programmer") ||
               path.startsWith("/client/login") ||
               path.startsWith("/client/register") ||
               path.startsWith("/client/verify-email") ||
               path.startsWith("/ws") ||
               path.startsWith("/topic/") ||
               path.startsWith("/flyers/") ||  // Flyers editor (public static resource)
               path.startsWith("/home") ||
               path.equals("/");  // Only exact root path, not all paths
    }
}
