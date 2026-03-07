package com.aatechsolutions.elgransazon.presentation.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Custom Error Controller
 * Handles HTTP errors and displays custom error pages
 * Replaces default Spring Boot error pages
 */
@Controller
@Slf4j
public class CustomErrorController implements ErrorController {

    /**
     * Main error handling method
     * Routes to specific error pages based on HTTP status code
     * 
     * @param request HTTP servlet request
     * @return View name for the corresponding error page
     */
    @RequestMapping("/error")
    public String handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        
        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());
            
            log.error("Error {} occurred. URI: {}", statusCode, 
                    request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));
            
            // Handle specific error codes
            if (statusCode == HttpStatus.BAD_REQUEST.value()) {
                // 400 - Bad Request
                return "errores/400";
            } else if (statusCode == HttpStatus.UNAUTHORIZED.value()) {
                // 401 - Unauthorized
                return "errores/401";
            } else if (statusCode == HttpStatus.FORBIDDEN.value()) {
                // 403 - Forbidden
                return "errores/403";
            } else if (statusCode == HttpStatus.NOT_FOUND.value()) {
                // 404 - Not Found
                return "errores/404";
            } else if (statusCode == HttpStatus.REQUEST_TIMEOUT.value()) {
                // 408 - Request Timeout
                return "errores/408";
            } else if (statusCode == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
                // 500 - Internal Server Error
                return "errores/500";
            } else if (statusCode == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                // 503 - Service Unavailable
                return "errores/503";
            }
        }
        
        // Default error page for unhandled status codes
        log.error("Unhandled error occurred. Status: {}, URI: {}", 
                status, request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));
        return "errores/404"; // Show 404 as default
    }
}
