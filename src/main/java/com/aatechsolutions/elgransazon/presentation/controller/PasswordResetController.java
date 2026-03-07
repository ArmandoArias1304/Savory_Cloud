package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.PasswordResetService;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for password reset (client/customer)
 * MULTI-TENANT: Requires company context for all operations
 */
@Controller
@RequestMapping("/client")
@RequiredArgsConstructor
@Slf4j
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    /**
     * Mostrar formulario para solicitar restablecimiento de contraseña
     * MULTI-TENANT: Requires company context
     */
    @GetMapping("/forgot-password")
    public String showForgotPasswordForm() {
        // MULTI-TENANT: Requires company context
        if (CompanyContext.getCurrentCompany() == null) {
            log.warn("No company context for forgot-password. Redirecting to login.");
            return "redirect:/login";
        }
        return "client/forgot-password";
    }

    /**
     * Procesar solicitud de restablecimiento
     * MULTI-TENANT: Requires company context
     */
    @PostMapping("/password-reset/request")
    public String requestPasswordReset(@RequestParam String email, RedirectAttributes redirectAttributes) {
        // MULTI-TENANT: Requires company context
        if (CompanyContext.getCurrentCompany() == null) {
            log.warn("No company context for password-reset request. Redirecting to login.");
            return "redirect:/login";
        }
        
        log.info("Password reset request for: {}", email);
        
        try {
            passwordResetService.requestPasswordReset(email);
            redirectAttributes.addFlashAttribute("success", true);
            redirectAttributes.addFlashAttribute("message", 
                "Si el correo existe en nuestro sistema, se ha enviado un enlace de restablecimiento.");
            
        } catch (Exception e) {
            log.error("Error processing password reset request", e);
            redirectAttributes.addFlashAttribute("error", true);
            redirectAttributes.addFlashAttribute("message", "Error al procesar la solicitud. Intenta nuevamente.");
        }
        
        return "redirect:/client/forgot-password";
    }

    /**
     * Mostrar formulario para ingresar nueva contraseña
     * MULTI-TENANT: Requires company context
     */
    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam String token, Model model) {
        // MULTI-TENANT: Requires company context
        if (CompanyContext.getCurrentCompany() == null) {
            log.warn("No company context for reset-password. Redirecting to login.");
            return "redirect:/login";
        }
        model.addAttribute("token", token);
        return "client/reset-password";
    }

    /**
     * Procesar confirmación de nueva contraseña
     * MULTI-TENANT: Requires company context
     */
    @PostMapping("/password-reset/confirm")
    public String confirmPasswordReset(
            @RequestParam String token, 
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            RedirectAttributes redirectAttributes) {
        
        // MULTI-TENANT: Requires company context
        if (CompanyContext.getCurrentCompany() == null) {
            log.warn("No company context for password-reset confirm. Redirecting to login.");
            return "redirect:/login";
        }
        
        log.info("Password reset confirmation attempt");
        
        try {
            // Validar que las contraseñas coincidan
            if (!newPassword.equals(confirmPassword)) {
                throw new IllegalArgumentException("Las contraseñas no coinciden.");
            }
            
            passwordResetService.confirmPasswordReset(token, newPassword);
            
            redirectAttributes.addFlashAttribute("success", true);
            redirectAttributes.addFlashAttribute("message", 
                "¡Contraseña restablecida con éxito! Ahora puedes iniciar sesión.");
            
            return "redirect:/client/login";
            
        } catch (IllegalArgumentException e) {
            log.error("Password reset failed: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", true);
            redirectAttributes.addFlashAttribute("message", e.getMessage());
            redirectAttributes.addAttribute("token", token);
            return "redirect:/client/reset-password";
        }
    }
}
