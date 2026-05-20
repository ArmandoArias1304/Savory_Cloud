package com.aatechsolutions.elgransazon.application.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.domain.entity.EmailVerificationToken;
import com.aatechsolutions.elgransazon.domain.repository.EmailVerificationTokenRepository;
import com.aatechsolutions.elgransazon.domain.entity.Customer;
import com.aatechsolutions.elgransazon.domain.repository.CustomerRepository;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;

/**
 * Service for email verification
 * MULTI-TENANT: Tokens are now scoped per company
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private static final int TOKEN_BYTES = 48; // 64 chars aprox Base64 URL
    private static final int EXP_MINUTES = 15; // Duración del token en minutos

    private final CustomerRepository customerRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Genera o reutiliza un token vigente para el cliente.
     * MULTI-TENANT: Token is associated with customer's company
     * 
     * @param customer Cliente destino (debe tener company asignada)
     * @return true si se generó y envió un NUEVO token, false si ya existía uno
     *         vigente y no se reenvió.
     *
     * IMPORTANT: This method is transactional WITHOUT noRollbackFor, so if the
     * email provider fails (e.g. SendGrid/Brevo throws), the token insert is rolled
     * back. This guarantees we never persist a "valid" token whose email was not
     * actually delivered (which would block resends for 15 minutes).
     */
    @Transactional
    public boolean createOrReuseToken(Customer customer) {
        log.info("Creating or reusing verification token for customer: {} in company: {}", 
            customer.getEmail(), customer.getCompany().getIdCompany());
        
        Company company = customer.getCompany();
        if (company == null) {
            throw new IllegalStateException("Customer must have a company assigned for email verification");
        }
        
        var existingOpt = emailVerificationTokenRepository.findByCustomerAndCompany(customer, company);
        if (existingOpt.isPresent()) {
            EmailVerificationToken existing = existingOpt.get();
            if (existing.getExpiration().isAfter(LocalDateTime.now())) {
                log.info("Valid token already exists for customer: {} in company: {}", 
                    customer.getEmail(), company.getIdCompany());
                // Token vigente: no reenviar
                return false;
            }
            // Expirado: eliminar para reemplazar
            log.info("Token expired, deleting old token for customer: {} in company: {}", 
                customer.getEmail(), company.getIdCompany());
            emailVerificationTokenRepository.delete(existing);
        }

        // Generar nuevo token - MULTI-TENANT: associate with company
        String token = generateSecureToken();
        EmailVerificationToken evt = EmailVerificationToken.builder()
                .customer(customer)
                .company(company)
                .token(token)
                .expiration(LocalDateTime.now().plusMinutes(EXP_MINUTES))
                .build();
        emailVerificationTokenRepository.save(evt);

        log.info("New verification token generated for customer: {} in company: {}", 
            customer.getEmail(), company.getIdCompany());

        // Enviar email
        emailService.sendEmailVerification(customer.getEmail(), token);
        return true;
    }

    /**
     * Enviar email de verificación a un cliente por su email
     * MULTI-TENANT: Searches customer by email AND current company
     */
    public void sendVerificationEmail(String email) {
        log.info("Attempting to send verification email to: {}", email);
        
        Company currentCompany = CompanyContext.getCurrentCompany();
        if (currentCompany == null) {
            log.warn("No company context available for email verification");
            return;
        }
        
        customerRepository.findByUsernameOrEmailAndCompany(email, currentCompany)
                .ifPresent(this::createOrReuseToken);
    }

    /**
     * Verificar email usando el token
     * Note: Token lookup is global since tokens are unique across all companies
     */
    @Transactional
    public void verifyEmail(String token) {
        log.info("Verifying email with token");
        
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token de verificación no proporcionado.");
        }

        EmailVerificationToken evt = emailVerificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token de verificación inválido o expirado."));

        if (evt.getExpiration().isBefore(LocalDateTime.now())) {
            log.warn("Token expired for customer: {} in company: {}", 
                evt.getCustomer().getEmail(), evt.getCompany().getIdCompany());
            throw new IllegalArgumentException("Token de verificación inválido o expirado.");
        }

        Customer customer = evt.getCustomer();
        if (Boolean.TRUE.equals(customer.getEmailVerified())) {
            log.info("Email already verified for customer: {} in company: {}", 
                customer.getEmail(), evt.getCompany().getIdCompany());
            // Ya verificado: eliminar token redundante
            emailVerificationTokenRepository.delete(evt);
            return;
        }

        customer.setEmailVerified(true);
        customerRepository.save(customer);
        log.info("Email verified successfully for customer: {} in company: {}", 
            customer.getEmail(), evt.getCompany().getIdCompany());

        // Consumido: eliminar token
        emailVerificationTokenRepository.delete(evt);
    }

    /**
     * Get remaining minutes until the existing verification token expires.
     * Returns 0 if no token exists.
     */
    public long getTokenMinutesRemaining(Customer customer) {
        Company company = customer.getCompany();
        if (company == null) return 0;
        return emailVerificationTokenRepository
                .findByCustomerAndCompany(customer, company)
                .map(token -> Math.max(0,
                        java.time.temporal.ChronoUnit.MINUTES.between(LocalDateTime.now(), token.getExpiration())))
                .orElse(0L);
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
