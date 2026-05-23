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

import org.springframework.beans.factory.annotation.Value;

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

    @Value("${app.base-domain:localhost}")
    private String baseDomain;

    // Paths that don't require company context (static resources only)
    private static final Set<String> EXCLUDED_PATHS = Set.of(
        "/css/", "/js/", "/images/", "/flyers/", "/sounds/", "/system-landing/",
        "/error", "/errores/",
        "/favicon.ico",
        "/.well-known/",
        "/qz/"
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
            } else if (isRootDomain(host)) {
                // Bare domain (savorycloud.com, localhost) without company context
                // Allow auth paths so programmer can login
                if (!isAllowedWithoutCompany(requestPath)) {
                    log.info("Root domain {} - path {} not allowed without company. Redirecting to /.", host, requestPath);
                    response.sendRedirect("/");
                    return;
                }
                log.debug("Root domain {} - serving path {} without company context.", host, requestPath);
            } else {
                // Invalid subdomain (e.g., pizzam5ax.savorycloud.com) - redirect to system landing
                // Only allow / and /home so system-landing can render
                if (!"/".equals(requestPath) && !"/home".equals(requestPath)) {
                    log.info("Invalid subdomain for host: {} (path: {}). Redirecting to system landing.", host, requestPath);
                    response.sendRedirect("/");
                    return;
                }
                log.debug("Invalid subdomain for host: {} - serving landing path {}.", host, requestPath);
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

    /**
     * Paths allowed on the root domain without company context.
     * Auth paths for programmer, landing pages, etc.
     */
    private boolean isAllowedWithoutCompany(String path) {
        return "/".equals(path) ||
               "/home".equals(path) ||
               path.startsWith("/login") ||
               path.startsWith("/perform_login") ||
               path.startsWith("/logout") ||
               path.startsWith("/help") ||
               path.startsWith("/support") ||
               path.startsWith("/license-expired") ||
               path.startsWith("/system-landing") ||
               path.startsWith("/ws");  // WebSocket — auth is handled by Spring Security session
    }

    /**
     * Checks if the host is the root/bare domain (no subdomain).
     * e.g., "localhost", "127.0.0.1", "savorycloud.com" → true
     *       "pizzamax.localhost", "pizzamax.savorycloud.com" → false
     */
    private boolean isRootDomain(String host) {
        String cleanHost = host.contains(":") ? host.split(":")[0] : host;
        return "localhost".equalsIgnoreCase(cleanHost) ||
               "127.0.0.1".equals(cleanHost) ||
               cleanHost.equalsIgnoreCase(baseDomain);
    }
}
