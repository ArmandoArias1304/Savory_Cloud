package com.aatechsolutions.elgransazon.infrastructure.email;

import java.io.IOException;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SendGrid implementation of {@link EmailSender}.
 *
 * Active when {@code app.email.provider=sendgrid} (the default).
 */
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "sendgrid", matchIfMissing = true)
@Slf4j
public class SendGridEmailSender implements EmailSender {

    @Value("${spring.email.password}")
    private String sendGridApiKey;

    @Override
    public void send(String fromEmail, String fromName, String toEmail, String subject, String htmlContent) {
        Email from = new Email(fromEmail, fromName);
        Email to = new Email(toEmail);
        Content content = new Content("text/html", htmlContent);
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
                if (response.getStatusCode() == 403) {
                    log.error("SendGrid 403: The 'from' address '{}' is not a verified Sender Identity. " +
                            "Verify it at SendGrid > Settings > Sender Authentication.", fromEmail);
                }
                throw new RuntimeException("Error al enviar email. Status: " + response.getStatusCode());
            }

            log.info("Email sent successfully to: {}", toEmail);

        } catch (IOException e) {
            log.error("Error sending email via SendGrid", e);
            throw new RuntimeException("Error al enviar email a través de SendGrid", e);
        }
    }

    @Override
    public String providerName() {
        return "sendgrid";
    }
}
