package com.aatechsolutions.elgransazon.infrastructure.security;

import com.aatechsolutions.elgransazon.application.service.CompanyService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * Filter that identifies the current company based on the request host.
 * Sets the company in CompanyContext for use throughout the request.
 * 
 * This filter runs early in the chain to ensure company context is available
 * for all other filters and services.
 */
@Component
@Order(1) // Run very early
@RequiredArgsConstructor
@Slf4j
public class CompanyContextFilter extends OncePerRequestFilter {

    private final CompanyService companyService;

    // Paths that don't require company context (static resources only)
    private static final Set<String> EXCLUDED_PATHS = Set.of(
        "/css/", "/js/", "/images/", "/flyers/", "/sounds/", "/system-landing/",
        "/error", "/errores/",
        "/favicon.ico"
        // Note: /login and /perform_login are NOT excluded because
        // authentication needs company context to validate employees
    );

    // Paths that are PROGRAMMER-only (no company needed)
    private static final Set<String> PROGRAMMER_PATHS = Set.of(
        "/programmer/"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String requestPath = request.getRequestURI();
        String host = request.getServerName();

        try {
            // Skip company lookup for static resources and error pages
            if (shouldSkipCompanyLookup(requestPath)) {
                log.trace("Skipping company lookup for path: {}", requestPath);
                filterChain.doFilter(request, response);
                return;
            }

            // Skip company lookup for PROGRAMMER paths
            if (isProgrammerPath(requestPath)) {
                log.trace("Skipping company lookup for programmer path: {}", requestPath);
                filterChain.doFilter(request, response);
                return;
            }

            // Try to find company by host
            log.debug("Looking up company for host: {} (path: {})", host, requestPath);
            Optional<Company> companyOpt = companyService.findByHost(host);

            if (companyOpt.isPresent()) {
                Company company = companyOpt.get();
                CompanyContext.setCurrentCompany(company);
                log.debug("Company context set: {} (ID: {})", company.getSlug(), company.getIdCompany());
            } else {
                // No company found - this might be okay for some public paths
                log.debug("No company found for host: {}", host);
            }

            filterChain.doFilter(request, response);

        } finally {
            // Always clear context at end of request to prevent memory leaks
            CompanyContext.clear();
        }
    }

    private boolean shouldSkipCompanyLookup(String path) {
        if (path == null) return true;
        
        for (String excluded : EXCLUDED_PATHS) {
            if (path.startsWith(excluded)) {
                return true;
            }
        }
        return false;
    }

    private boolean isProgrammerPath(String path) {
        if (path == null) return false;
        
        for (String programmerPath : PROGRAMMER_PATHS) {
            if (path.startsWith(programmerPath)) {
                return true;
            }
        }
        return false;
    }
}
