package com.aatechsolutions.elgransazon.infrastructure.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Custom authentication failure handler
 * Redirects to appropriate login page based on where the login was attempted
 */
@Component
@Slf4j
public class CustomAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        
        String referer = request.getHeader("Referer");
        String loginUrl = "/login?error=true";
        
        // Check if the login attempt came from client login page
        if (referer != null && referer.contains("/client/login")) {
            log.warn("Client login failed: {}", exception.getMessage());
            loginUrl = "/client/login?error=true";
        } else {
            log.warn("Employee login failed: {}", exception.getMessage());
            loginUrl = "/login?error=true";
        }
        
        // Redirect to appropriate login page with error parameter
        getRedirectStrategy().sendRedirect(request, response, loginUrl);
    }
}
