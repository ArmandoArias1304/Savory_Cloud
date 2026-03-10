package com.aatechsolutions.elgransazon.presentation.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;

/**
 * Global exception handler for the application.
 * Catches specific exceptions and provides user-friendly error messages.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handle file upload size exceeded exception.
     * Redirects back to the referring page with a friendly error message
     * instead of showing the default error page.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        log.warn("File upload exceeded maximum size. Remote: {}, URI: {}",
                request.getRemoteAddr(), request.getRequestURI());

        redirectAttributes.addFlashAttribute("errorMessage",
                "La imagen que intentas subir excede el tamaño máximo permitido (5MB). " +
                "Te recomendamos reducir el peso de la imagen o usar un formato más ligero como WEBP antes de intentarlo de nuevo.");

        // Redirect back to the referring page
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isEmpty()) {
            try {
                String path = new URI(referer).getPath();
                if (path != null && !path.isEmpty()) {
                    return "redirect:" + path;
                }
            } catch (Exception e) {
                log.debug("Could not parse referer URI: {}", referer);
            }
        }

        // Fallback: redirect to the request URI itself (the form action)
        return "redirect:" + request.getRequestURI();
    }
}
