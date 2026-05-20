package com.aatechsolutions.elgransazon.infrastructure.email;

/**
 * Port (strategy) for sending transactional emails.
 *
 * Implementations (SendGrid, Brevo, etc.) are interchangeable: the active one is
 * selected via the {@code app.email.provider} property. Higher-level email logic
 * (HTML templates, multi-tenant sender resolution, URL building) lives in
 * {@link com.aatechsolutions.elgransazon.application.service.EmailService}.
 *
 * Implementations MUST throw a {@link RuntimeException} when the provider rejects
 * the request, so that callers (e.g. token services) can avoid persisting state
 * for emails that were not actually delivered.
 */
public interface EmailSender {

    /**
     * Send a single HTML email.
     *
     * @param fromEmail   verified sender email (per-company or platform default)
     * @param fromName    display name for the sender
     * @param toEmail     recipient email
     * @param subject     subject line
     * @param htmlContent fully-rendered HTML body
     */
    void send(String fromEmail, String fromName, String toEmail, String subject, String htmlContent);

    /**
     * Provider identifier, useful for logs / diagnostics.
     */
    String providerName();
}
