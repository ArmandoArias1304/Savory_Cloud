package com.aatechsolutions.elgransazon.infrastructure.email;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Brevo (formerly Sendinblue) implementation of {@link EmailSender}.
 *
 * Uses Brevo's transactional email REST API:
 * {@code POST https://api.brevo.com/v3/smtp/email} with the {@code api-key} header.
 *
 * Active when {@code app.email.provider=brevo}.
 */
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "brevo")
@Slf4j
public class BrevoEmailSender implements EmailSender {

    private static final String BREVO_ENDPOINT = "https://api.brevo.com/v3/smtp/email";

    private final RestTemplate restTemplate;

    @Value("${app.email.brevo.api-key:}")
    private String brevoApiKey;

    public BrevoEmailSender(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public void send(String fromEmail, String fromName, String toEmail, String subject, String htmlContent) {
        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            log.error("Brevo API key is not configured (app.email.brevo.api-key / BREVO_API_KEY)");
            throw new RuntimeException("Brevo API key no está configurada");
        }

        Map<String, Object> body = Map.of(
                "sender", Map.of("email", fromEmail, "name", fromName),
                "to", List.of(Map.of("email", toEmail)),
                "subject", subject,
                "htmlContent", htmlContent
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("api-key", brevoApiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(BREVO_ENDPOINT, entity, String.class);
            HttpStatusCode status = response.getStatusCode();
            log.info("Brevo Response Status: {}", status.value());

            if (status.isError()) {
                log.error("Error sending email via Brevo. Status: {}, Body: {}", status.value(), response.getBody());
                throw new RuntimeException("Error al enviar email. Status: " + status.value());
            }

            log.info("Email sent successfully to: {}", toEmail);

        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            log.error("Brevo API error. Status: {}, Body: {}", status, e.getResponseBodyAsString());
            if (status == 401) {
                log.error("Brevo 401: API key inválida o sin permisos.");
            } else if (status == 400) {
                log.error("Brevo 400: el remitente '{}' puede no estar verificado en Brevo > Senders & IPs.", fromEmail);
            }
            throw new RuntimeException("Error al enviar email a través de Brevo. Status: " + status, e);
        } catch (RestClientException e) {
            log.error("Error sending email via Brevo", e);
            throw new RuntimeException("Error al enviar email a través de Brevo", e);
        }
    }

    @Override
    public String providerName() {
        return "brevo";
    }
}
