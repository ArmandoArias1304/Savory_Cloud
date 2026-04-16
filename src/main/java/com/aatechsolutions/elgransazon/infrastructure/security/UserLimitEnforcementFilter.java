package com.aatechsolutions.elgransazon.infrastructure.security;

import com.aatechsolutions.elgransazon.application.service.LicenseService;
import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that enforces user limit when the PROGRAMMER reduces maxUsers
 * below the current number of active employees.
 * 
 * Behavior:
 * - PROGRAMMER: Always allowed (they manage the license)
 * - ADMIN: Allowed ONLY to /admin/employees (and related sub-routes) so they can deactivate excess users.
 *          A session flag "userLimitExceeded" is set so the frontend can show a SweetAlert and lock the sidebar.
 * - All other employees (MANAGER, CHEF, WAITER, etc.): Logged out immediately.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserLimitEnforcementFilter extends OncePerRequestFilter {

    private final LicenseService licenseService;
    private final EmployeeRepository employeeRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Only check for authenticated, non-anonymous users
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal().toString())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Determine user role
        boolean isProgrammer = hasRole(auth, "ROLE_PROGRAMMER");
        boolean isAdmin = hasRole(auth, "ROLE_ADMIN");

        // PROGRAMMER is always exempt
        if (isProgrammer) {
            filterChain.doFilter(request, response);
            return;
        }

        // MULTI-TENANT: Check if user limit is exceeded for the current company
        Company company = CompanyContext.getCurrentCompany();
        long currentActiveUsers = company != null 
            ? employeeRepository.countEnabledByCompanyExcludingProgrammer(company)
            : employeeRepository.countEnabledExcludingProgrammer();
        boolean limitExceeded = licenseService.isUserLimitExceeded(currentActiveUsers);

        if (!limitExceeded) {
            // Limit is fine — clear any session flag and proceed normally
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.removeAttribute("userLimitExceeded");
                session.removeAttribute("userLimitExceededInfo");
            }
            filterChain.doFilter(request, response);
            return;
        }

        // === LIMIT IS EXCEEDED ===

        if (isAdmin) {
            // ADMIN is allowed, but ONLY to employee management pages
            String path = request.getRequestURI();
            HttpSession session = request.getSession(true);
            
            // Set session attributes for frontend SweetAlert and sidebar lock
            Integer maxUsers = licenseService.getMaxUsers();
            session.setAttribute("userLimitExceeded", true);
            session.setAttribute("userLimitExceededInfo", 
                String.format("El límite de usuarios se ha reducido a %d, pero actualmente hay %d empleados activos. " +
                    "Debes desactivar %d empleado(s) para continuar usando el sistema normalmente.",
                    maxUsers, currentActiveUsers, currentActiveUsers - maxUsers));
            session.setAttribute("userLimitMax", maxUsers);
            session.setAttribute("userLimitCurrent", currentActiveUsers);
            session.setAttribute("userLimitExcess", (int)(currentActiveUsers - maxUsers));

            // Allow only employee management routes and API endpoints
            if (isAllowedPathForAdmin(path)) {
                filterChain.doFilter(request, response);
                return;
            }

            // Redirect to employees page for any other admin route
            log.warn("User limit exceeded. Redirecting ADMIN {} from {} to /admin/employees", 
                     auth.getName(), path);
            response.sendRedirect(request.getContextPath() + "/admin/employees");
            return;
        }

        // All other roles (MANAGER, CHEF, WAITER, CASHIER, DELIVERY, BARISTA):
        // Force logout since the limit is exceeded
        log.warn("User limit exceeded. Forcing logout for employee: {} (role is not ADMIN/PROGRAMMER)", 
                 auth.getName());
        new SecurityContextLogoutHandler().logout(request, response, auth);
        response.sendRedirect(request.getContextPath() + "/login?error=userLimitExceeded");
    }

    /**
     * Check if the given path is allowed for ADMIN when user limit is exceeded.
     * Only employee management routes are permitted.
     */
    private boolean isAllowedPathForAdmin(String path) {
        return path.startsWith("/admin/employees") ||
               path.equals("/logout") ||
               path.startsWith("/api/") ||
               path.startsWith("/css/") ||
               path.startsWith("/js/") ||
               path.startsWith("/images/") ||
               path.startsWith("/fonts/") ||
               path.endsWith(".css") ||
               path.endsWith(".js");
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(r -> r.equals(role));
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/login") ||
               path.startsWith("/client/login") ||
               path.startsWith("/client/register") ||
               path.startsWith("/client/verify-email") ||
               path.startsWith("/programmer/") ||
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
               path.startsWith("/flyers/") ||
               path.startsWith("/.well-known/") ||  // Flyers editor (public static resource)
               path.startsWith("/autofactura/") ||
               path.equals("/logout") ||
               path.equals("/perform_login") ||
               path.startsWith("/home") ||
               path.equals("/") ||
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
