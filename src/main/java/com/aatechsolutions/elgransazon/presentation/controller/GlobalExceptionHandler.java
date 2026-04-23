package com.aatechsolutions.elgransazon.presentation.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;
import java.util.stream.Collectors;

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

    /**
     * Handle JPA transaction failures caused by bean validation constraints.
     * Extracts user-friendly messages from ConstraintViolationException.
     */
    @ExceptionHandler(TransactionSystemException.class)
    public String handleTransactionSystemException(
            TransactionSystemException ex,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        // Walk the cause chain looking for ConstraintViolationException
        Throwable cause = ex.getCause();
        while (cause != null && !(cause instanceof ConstraintViolationException)) {
            cause = cause.getCause();
        }

        if (cause instanceof ConstraintViolationException cve) {
            String messages = cve.getConstraintViolations().stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(". "));
            log.warn("Constraint violations on {}: {}", request.getRequestURI(), messages);
            redirectAttributes.addFlashAttribute("errorMessage", messages);
        } else {
            log.error("Transaction error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error al guardar los datos. Verifique que los campos cumplan con los requisitos.");
        }

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

        return "redirect:" + request.getRequestURI();
    }

    /**
     * Utility: extract a user-friendly message from an exception,
     * checking for nested ConstraintViolationException.
     */
    public static String extractConstraintMessages(Exception ex) {
        Throwable cause = ex;
        while (cause != null && !(cause instanceof ConstraintViolationException)) {
            cause = cause.getCause();
        }
        if (cause instanceof ConstraintViolationException cve) {
            return cve.getConstraintViolations().stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(". "));
        }
        return null;
    }
}
