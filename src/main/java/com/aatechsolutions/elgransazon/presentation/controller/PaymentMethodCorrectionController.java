package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.PaymentMethodCorrectionService;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;

@Controller
@RequestMapping("/{role}/orders")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER')")
@Slf4j
public class PaymentMethodCorrectionController {

    private final PaymentMethodCorrectionService correctionService;

    public PaymentMethodCorrectionController(PaymentMethodCorrectionService correctionService) {
        this.correctionService = correctionService;
    }

    @PostMapping("/{orderId}/correct-payment-method")
    public String correctPaymentMethod(
            @PathVariable String role,
            @PathVariable Long orderId,
            @RequestParam(required = false) Long paymentId,
            @RequestParam PaymentMethodType paymentMethod,
            @RequestParam String reason,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        String normalizedRole = role.toLowerCase(Locale.ROOT);
        if (!normalizedRole.equals("admin") && !normalizedRole.equals("manager")) {
            throw new AccessDeniedException("Solo administradores y gerentes pueden corregir pagos");
        }
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        boolean isManager = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_MANAGER"));
        boolean roleMatches = normalizedRole.equals("admin") ? isAdmin || isManager : isManager;
        if (!roleMatches) {
            throw new AccessDeniedException("El rol de la ruta no coincide con el usuario autenticado");
        }

        try {
            correctionService.correct(orderId, paymentId, paymentMethod, reason, authentication.getName());
            redirectAttributes.addFlashAttribute(
                    "successMessage", "Método de pago corregido. El importe del pedido no fue modificado.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            log.warn("Payment method correction rejected for order {} by {}: {}",
                    orderId, authentication.getName(), exception.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/" + normalizedRole + "/orders/view/" + orderId;
    }
}
