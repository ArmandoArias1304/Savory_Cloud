package com.aatechsolutions.elgransazon.infrastructure.security;

import com.aatechsolutions.elgransazon.application.service.LicenseService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter to restrict access to customer/client routes based on license package type
 * Only ECOMMERCE package has access to customer module
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PackageAccessFilter extends OncePerRequestFilter {

    private final LicenseService licenseService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        String requestURI = request.getRequestURI();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // Check if accessing client/customer routes
        if (requestURI.startsWith("/client/") && !requestURI.equals("/client/login") && !requestURI.equals("/client/register")) {
            // Check if license has customer module access
            if (!licenseService.hasCustomerModuleAccess()) {
                log.warn("Access denied to {} - License doesn't have customer module (ECOMMERCE required)", requestURI);
                
                // If authenticated as CLIENT, logout and redirect to login with message
                if (authentication != null && authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_CLIENT"))) {
                    log.info("Client user detected without ECOMMERCE license, logging out");
                    SecurityContextHolder.clearContext();
                    request.getSession().invalidate();
                    response.sendRedirect("/login?error=noCustomerModule");
                    return;
                }
                
                // For other users, redirect to forbidden page
                response.sendError(HttpServletResponse.SC_FORBIDDEN, 
                    "Este módulo no está disponible en su licencia. Actualice a ECOMMERCE para acceder.");
                return;
            }
        }
        
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        // Don't filter these paths
        return path.startsWith("/css") ||
               path.startsWith("/js") ||
               path.startsWith("/images") ||
               path.startsWith("/uploads") ||
               path.startsWith("/error") ||
               path.startsWith("/errores") ||
               path.startsWith("/flyers/") ||  // Flyers editor (public static resource)
               path.startsWith("/system-landing/") ||
               path.startsWith("/autofactura/") ||
               path.equals("/client/login") ||
               path.equals("/client/register") ||
               path.startsWith("/ws") ||
               path.startsWith("/topic/") ||
               //excluir favicon
               path.equals("/favicon.ico") ||
               path.startsWith("/programmer");
    }
}
