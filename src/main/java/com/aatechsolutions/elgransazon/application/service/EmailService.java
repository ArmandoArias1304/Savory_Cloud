package com.aatechsolutions.elgransazon.application.service;

import java.io.IOException;

import com.aatechsolutions.elgransazon.domain.entity.Company;
import com.aatechsolutions.elgransazon.infrastructure.context.CompanyContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;

/**
 * Email service using SendGrid
 * MULTI-TENANT: Uses CompanyContext to get sender email/name from the current company
 */
@Service
@Slf4j
public class EmailService {

    @Value("${spring.email.password}")
    private String sendGridApiKey;

    // Fallback values when no company context (e.g., PROGRAMMER operations)
    @Value("${mail.from.email}")
    private String defaultFromEmail;

    @Value("${mail.from.name}")
    private String defaultFromName;

    @Value("${app.protocol}")
    private String appProtocol;

    @Value("${app.domain}")
    private String appDomain;

    @Value("${app.port}")
    private String appPort;

    @Value("${app.base-domain:localhost}")
    private String baseDomain;

    /**
     * Get sender email for the current company
     * MULTI-TENANT: Uses company's senderEmail if available
     */
    private String getSenderEmail() {
        Company company = CompanyContext.getCurrentCompany();
        if (company != null && company.getSenderEmail() != null) {
            return company.getSenderEmail();
        }
        return defaultFromEmail;
    }

    /**
     * Get sender name for the current company
     * MULTI-TENANT: Uses company's senderName or name if available
     */
    private String getSenderName() {
        Company company = CompanyContext.getCurrentCompany();
        if (company != null) {
            return company.getDisplayName();
        }
        return defaultFromName;
    }

    /**
     * Build base URL from environment variables or company domain
     * MULTI-TENANT: Uses company's custom domain or subdomain if available
     * Examples:
     * - Development: http://localhost:8080
     * - Development (subdomain): http://elbuensazon.localhost:8080
     * - Development (IP slug): http://192.168.1.76:8080
     * - Production (subdomain): https://pizzamax.savorycloud.com
     * - Production (custom domain): https://www.pizzamax.com
     */
    private String getBaseUrl() {
        Company company = CompanyContext.getCurrentCompany();
        
        StringBuilder baseUrl = new StringBuilder();
        baseUrl.append(appProtocol).append("://");
        
        if (company != null) {
            // Use custom domain if available
            if (company.hasCustomDomain()) {
                baseUrl.append(company.getCustomDomain());
            } else if (isIpAddress(company.getSlug())) {
                // If slug is an IP address, use it directly (for LAN testing)
                baseUrl.append(company.getSlug());
            } else {
                // Normal case: subdomain.baseDomain
                baseUrl.append(company.getSlug()).append(".").append(baseDomain);
            }
        } else {
            // Fallback to configured domain
            baseUrl.append(appDomain);
        }
        
        // Only add port if it's not empty and not the default ports (80 for http, 443 for https)
        if (appPort != null && !appPort.isEmpty() 
            && !("http".equals(appProtocol) && "80".equals(appPort))
            && !("https".equals(appProtocol) && "443".equals(appPort))) {
            baseUrl.append(":").append(appPort);
        }
        
        return baseUrl.toString();
    }

    /**
     * Check if a string looks like an IP address (for LAN testing with IP-based slugs)
     */
    private boolean isIpAddress(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            try {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * Send password reset email
     */
    public void sendPasswordResetEmail(String toEmail, String token) {
        log.info("Sending password reset email to: {}", toEmail);
        
        // Build reset URL dynamically from environment variables
        String resetUrl = getBaseUrl() + "/client/reset-password?token=" + token;
        String companyName = getSenderName();

        Email from = new Email(getSenderEmail(), companyName);
        Email to = new Email(toEmail);
        String subject = "Recupera tu acceso - " + companyName;
        
        String htmlContent = buildPasswordResetEmailHtml(resetUrl, companyName);
        Content content = new Content("text/html", htmlContent);

        sendEmail(from, to, subject, content);
    }

    /**
     * Send email verification email
     */
    public void sendEmailVerification(String toEmail, String token) {
        log.info("Sending email verification to: {}", toEmail);
        
        // Build verification URL dynamically from environment variables
        String verificationUrl = getBaseUrl() + "/client/verify-email?token=" + token;
        String companyName = getSenderName();

        Email from = new Email(getSenderEmail(), companyName);
        Email to = new Email(toEmail);
        String subject = "Confirma tu correo - " + companyName;
        
        String htmlContent = buildEmailVerificationHtml(verificationUrl, companyName);
        Content content = new Content("text/html", htmlContent);

        sendEmail(from, to, subject, content);
    }

    /**
     * Send email using SendGrid
     */
    private void sendEmail(Email from, Email to, String subject, Content content) {
        Mail mail = new Mail(from, subject, to, content);
        SendGrid sg = new SendGrid(sendGridApiKey);
        Request request = new Request();

        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);

            log.info("SendGrid Response Status: {}", response.getStatusCode());

            if (response.getStatusCode() >= 400) {
                log.error("Error sending email. Status: {}, Body: {}", 
                    response.getStatusCode(), response.getBody());
                throw new RuntimeException("Error al enviar email. Status: " + response.getStatusCode());
            }
            
            log.info("Email sent successfully to: {}", to.getEmail());
            
        } catch (IOException e) {
            log.error("Error sending email via SendGrid", e);
            throw new RuntimeException("Error al enviar email a través de SendGrid", e);
        }
    }

    /**
     * Build HTML content for password reset email
     */
    private String buildPasswordResetEmailHtml(String resetUrl, String companyName) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <link href="https://fonts.googleapis.com/css2?family=Work+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
                <style>
                    body { 
                        font-family: 'Work Sans', Arial, sans-serif; 
                        background-color: #f4f4f4; 
                        padding: 20px; 
                        margin: 0;
                    }
                    .container { 
                        max-width: 600px; 
                        margin: 0 auto; 
                        background: white; 
                        padding: 40px 30px; 
                        border-radius: 16px;
                        box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
                    }
                    .header { 
                        text-align: center; 
                        margin-bottom: 30px;
                    }
                    .header h1 {
                        color: #38e07b;
                        font-size: 28px;
                        font-weight: 800;
                        margin: 0;
                    }
                    .content {
                        color: #374151;
                        line-height: 1.7;
                    }
                    .content h2 {
                        color: #1f2937;
                        font-size: 22px;
                        font-weight: 700;
                        margin-bottom: 20px;
                    }
                    .content p {
                        font-size: 15px;
                        margin-bottom: 16px;
                    }
                    .button-container {
                        text-align: center;
                        margin: 32px 0;
                    }
                    .button { 
                        display: inline-block; 
                        padding: 16px 40px; 
                        background: linear-gradient(135deg, #38e07b 0%%, #2bc866 100%%);
                        color: white !important; 
                        text-decoration: none; 
                        border-radius: 12px;
                        font-weight: 700;
                        font-size: 16px;
                        box-shadow: 0 4px 12px rgba(56, 224, 123, 0.3);
                    }
                    .warning {
                        background-color: #fef3c7;
                        border-left: 4px solid #f59e0b;
                        padding: 12px 16px;
                        border-radius: 0 8px 8px 0;
                        font-size: 14px;
                        color: #92400e;
                        margin: 24px 0;
                    }
                    .footer { 
                        text-align: center; 
                        color: #9ca3af; 
                        font-size: 13px; 
                        margin-top: 32px;
                        padding-top: 24px;
                        border-top: 1px solid #e5e7eb;
                    }
                    .footer p {
                        margin: 4px 0;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🍽️ %s</h1>
                    </div>
                    <div class="content">
                        <h2>¿Olvidaste tu contraseña?</h2>
                        <p>¡No te preocupes! Esto le pasa a cualquiera 😊</p>
                        <p>Recibimos una solicitud para restablecer la contraseña de tu cuenta. Solo tienes que hacer clic en el botón de abajo para crear una nueva:</p>
                        <div class="button-container">
                            <a href="%s" class="button">Crear nueva contraseña</a>
                        </div>
                        <div class="warning">
                            ⏰ <strong>Importante:</strong> Este enlace expira en 15 minutos por tu seguridad.
                        </div>
                        <p>Si no solicitaste cambiar tu contraseña, puedes ignorar este correo con total tranquilidad. Tu cuenta sigue segura.</p>
                    </div>
                    <div class="footer">
                        <p>Con cariño,</p>
                        <p><strong>El equipo de %s</strong></p>
                        <p style="margin-top: 16px; font-size: 12px; color: #38e07b; font-weight: 500;">by SavoryCloud</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(companyName, resetUrl, companyName);
    }

    /**
     * Build HTML content for email verification
     */
    private String buildEmailVerificationHtml(String verificationUrl, String companyName) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <link href="https://fonts.googleapis.com/css2?family=Work+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
                <style>
                    body { 
                        font-family: 'Work Sans', Arial, sans-serif; 
                        background-color: #f4f4f4; 
                        padding: 20px; 
                        margin: 0;
                    }
                    .container { 
                        max-width: 600px; 
                        margin: 0 auto; 
                        background: white; 
                        padding: 40px 30px; 
                        border-radius: 16px;
                        box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
                    }
                    .header { 
                        text-align: center; 
                        margin-bottom: 30px;
                    }
                    .header h1 {
                        color: #38e07b;
                        font-size: 28px;
                        font-weight: 800;
                        margin: 0;
                    }
                    .content {
                        color: #374151;
                        line-height: 1.7;
                    }
                    .content h2 {
                        color: #1f2937;
                        font-size: 22px;
                        font-weight: 700;
                        margin-bottom: 20px;
                    }
                    .content p {
                        font-size: 15px;
                        margin-bottom: 16px;
                    }
                    .button-container {
                        text-align: center;
                        margin: 32px 0;
                    }
                    .button { 
                        display: inline-block; 
                        padding: 16px 40px; 
                        background: linear-gradient(135deg, #38e07b 0%%, #2bc866 100%%);
                        color: white !important; 
                        text-decoration: none; 
                        border-radius: 12px;
                        font-weight: 700;
                        font-size: 16px;
                        box-shadow: 0 4px 12px rgba(56, 224, 123, 0.3);
                    }
                    .info {
                        background-color: #ecfdf5;
                        border-left: 4px solid #38e07b;
                        padding: 12px 16px;
                        border-radius: 0 8px 8px 0;
                        font-size: 14px;
                        color: #065f46;
                        margin: 24px 0;
                    }
                    .footer { 
                        text-align: center; 
                        color: #9ca3af; 
                        font-size: 13px; 
                        margin-top: 32px;
                        padding-top: 24px;
                        border-top: 1px solid #e5e7eb;
                    }
                    .footer p {
                        margin: 4px 0;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🍽️ %s</h1>
                    </div>
                    <div class="content">
                        <h2>¡Bienvenido a la familia de %s! 🎉</h2>
                        <p>Estamos muy emocionados de tenerte con nosotros.</p>
                        <p><strong>¡Hola!</strong> 👋</p>
                        <p>Gracias por registrarte en %s. Solo falta un pequeño paso para completar tu registro.</p>
                        <p>Verifica tu correo electrónico haciendo clic en el botón de abajo:</p>
                        <div class="button-container">
                            <a href="%s" class="button">Verificar mi correo</a>
                        </div>
                        <div class="info">
                            ✨ <strong>Tip:</strong> Una vez verificado, podrás hacer pedidos, ver nuestro menú y mucho más.
                        </div>
                        <p>Si no creaste una cuenta con nosotros, simplemente ignora este mensaje.</p>
                    </div>
                    <div class="footer">
                        <p>¡Te esperamos pronto!</p>
                        <p><strong>El equipo de %s</strong></p>
                        <p style="margin-top: 16px; font-size: 12px; color: #38e07b; font-weight: 500;">by SavoryCloud</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(companyName, companyName, companyName, verificationUrl, companyName);
    }
}
