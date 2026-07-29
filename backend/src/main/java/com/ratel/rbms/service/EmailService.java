package com.ratel.rbms.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around Spring's JavaMailSender, deliberately provider-agnostic —
 * point SMTP_HOST/SMTP_USERNAME/SMTP_PASSWORD at Brevo, SendGrid, Mailgun, or
 * even Gmail's SMTP relay and it works without any code changes.
 *
 * If SMTP isn't configured (SMTP_HOST is blank — the default), this logs a
 * clear warning and returns instead of throwing, so the app still runs and
 * password-reset requests still return a normal response. The alternative
 * (crashing the request) would leak whether an email address has an account,
 * which is exactly what forgot-password flows are supposed to avoid.
 */
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final boolean configured;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.host}") String smtpHost,
            @Value("${app.mail.from}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.configured = smtpHost != null && !smtpHost.isBlank();
    }

    public void sendPasswordReset(String toEmail, String resetLink) {
        String subject = "Reset your Ratel password";
        String body = "We received a request to reset your Ratel password.\n\n"
                + "Reset it here: " + resetLink + "\n\n"
                + "This link expires in 30 minutes. If you didn't request this, you can ignore this email — "
                + "your password won't be changed.";
        send(toEmail, subject, body);
    }

    public void sendDigest(String toEmail, String subject, String body) {
        send(toEmail, subject, body);
    }

    public void sendBillingReminder(String toEmail, String businessName, long daysRemaining, String periodLabel) {
        String daysLabel = daysRemaining + " day" + (daysRemaining == 1 ? "" : "s");
        String subject = businessName + ": your " + periodLabel + " ends in " + daysLabel;
        String body = "Hi,\n\n"
                + "Your " + periodLabel + " for " + businessName + " on Ratel ends in " + daysLabel + ".\n\n"
                + "Renew anytime from your Billing page to keep creating and editing without interruption.\n\n"
                + "Already renewed? You can ignore this email.";
        send(toEmail, subject, body);
    }

    public void sendServiceOrderReady(String toEmail, String customerName, long orderNumber, String businessName) {
        String subject = businessName + ": your order is ready for pickup";
        String body = "Hi " + customerName + ",\n\n"
                + "Your order #" + orderNumber + " at " + businessName + " is complete and ready for pickup.\n\n"
                + "See you soon!";
        send(toEmail, subject, body);
    }

    private void send(String to, String subject, String body) {
        if (!configured) {
            System.out.println("[RBMS] Email not sent (SMTP not configured) — would have sent \"" + subject + "\" to " + to);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (MailException e) {
            // Swallow rather than propagate: a broken SMTP config shouldn't surface
            // as a 500 to the end user, or reveal account existence via error timing.
            System.err.println("[RBMS] Failed to send email to " + to + ": " + e.getMessage());
        }
    }
}
