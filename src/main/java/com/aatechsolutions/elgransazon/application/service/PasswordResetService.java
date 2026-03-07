package com.aatechsolutions.elgransazon.application.service;

import java.security.SecureRandom;
import java.util.Base64;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.repository.PasswordResetTokenRepository;
import com.aatechsolutions.elgransazon.domain.repository.CustomerRepository;
import com.aatechsolutions.elgransazon.domain.entity.PasswordResetToken;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;

/**
 * Service for password reset
 * MULTI-TENANT: Tokens are now scoped per company
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {
    
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final CustomerRepository customerRepository;
    private final EmailService emailService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Solicitar restablecimiento de contraseña
     * MULTI-TENANT: Searches customer by email AND current company
     */
    @Transactional
    public void requestPasswordReset(String email) {
        log.info("Password reset requested for email: {}", email);
        
        Company currentCompany = CompanyContext.getCurrentCompany();
        if (currentCompany == null) {
            log.warn("No company context available for password reset");
            // Por seguridad, no revelamos si  el contexto existe o no
            return;
        }
        
        // MULTI-TENANT: Buscar cliente por email/username AND company
        var customerOpt = customerRepository.findByUsernameOrEmailAndCompany(email, currentCompany);
        
        if (customerOpt.isPresent()) {
            var customer = customerOpt.get();
            var token = generateToken();

            // Eliminar tokens anteriores del cliente para esta company
            passwordResetTokenRepository.deleteByCustomerAndCompany(customer, currentCompany);

            // MULTI-TENANT: Associate token with company
            var resetToken = PasswordResetToken.builder()
                    .customer(customer)
                    .company(currentCompany)
                    .token(token)
                    .expiration(java.time.LocalDateTime.now().plusMinutes(15)) // Expira en 15 minutos
                    .used(false)
                    .build();

            passwordResetTokenRepository.save(resetToken);
            log.info("Password reset token generated for customer: {} in company: {}", 
                customer.getEmail(), currentCompany.getIdCompany());

            // Enviar el correo con el token
            emailService.sendPasswordResetEmail(customer.getEmail(), token);
        } else {
            log.warn("Password reset requested for non-existent email in company {}: {}", 
                currentCompany.getIdCompany(), email);
            // Por seguridad, no revelamos si el email existe o no
        }
    }

    /**
     * Confirmar restablecimiento de contraseña
     * Note: Token lookup is global since tokens are unique across all companies
     */
    @Transactional
    public void confirmPasswordReset(String tokenHash, String newPassword) {
        log.info("Confirming password reset with token");
        
        // Validar contraseña
        validatePassword(newPassword);
        
        var tokenOpt = passwordResetTokenRepository.findByToken(tokenHash);
        
        if (tokenOpt.isEmpty()) {
            log.warn("Invalid password reset token");
            throw new IllegalArgumentException("Token inválido.");
        }
        
        var token = tokenOpt.get();
        
        if (token.getExpiration().isBefore(java.time.LocalDateTime.now())) {
            log.warn("Expired password reset token");
            throw new IllegalArgumentException("Token expirado.");
        }
        
        if (Boolean.TRUE.equals(token.getUsed())) {
            log.warn("Already used password reset token");
            throw new IllegalArgumentException("Token ya usado.");
        }
        
        var customer = token.getCustomer();
        customer.setPassword(passwordEncoder.encode(newPassword));
        customerRepository.save(customer);
        log.info("Password reset successfully for customer: {} in company: {}", 
            customer.getEmail(), token.getCompany().getIdCompany());

        // Marcar token como usado y eliminarlo
        token.setUsed(true);
        passwordResetTokenRepository.delete(token);
    }

    private String generateToken() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
    
    private void validatePassword(String password) {
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 6 caracteres.");
        }
        if (password.length() > 100) {
            throw new IllegalArgumentException("La contraseña es demasiado larga.");
        }
    }
}
