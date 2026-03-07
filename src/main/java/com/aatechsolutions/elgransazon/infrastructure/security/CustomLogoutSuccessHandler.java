package com.aatechsolutions.elgransazon.infrastructure.security;

import com.aatechsolutions.elgransazon.domain.entity.Role;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Custom logout success handler
 * Redirects customers to client login and employees to employee login
 */
@Component
@Slf4j
public class CustomLogoutSuccessHandler implements LogoutSuccessHandler {

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException, ServletException {

        String redirectUrl = "/login?logout=true";

        if (authentication != null) {
            boolean isCustomer = authentication.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals(Role.CLIENT));

            if (isCustomer) {
                log.info("Customer {} logged out, redirecting to client login", authentication.getName());
                redirectUrl = "/client/login?logout=true";
            } else {
                log.info("Employee {} logged out, redirecting to employee login", authentication.getName());
            }
        }

        response.sendRedirect(request.getContextPath() + redirectUrl);
    }
}
