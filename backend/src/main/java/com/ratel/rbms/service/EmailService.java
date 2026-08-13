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
        String subject = "Reset your Tallia password";
        String body = "We received a request to reset your Tallia password.\n\n"
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
                + "Your " + periodLabel + " for " + businessName + " on Tallia ends in " + daysLabel + ".\n\n"
                + "Renew anytime from your Billing page to keep creating and editing without interruption.\n\n"
                + "Already renewed? You can ignore this email.";
        send(toEmail, subject, body);
    }

    // A failed auto-charge doesn't do anything drastic on its own — the
    // regular grace-period sweep picks up the still-expired period on its
    // own next run, same as any business with no saved card — but the Owner
    // should still hear about it so they can fix the card before that happens.
    public void sendAutoChargeFailed(String toEmail, String businessName) {
        String subject = businessName + ": we couldn't renew your subscription automatically";
        String body = "Hi,\n\n"
                + "We tried to renew " + businessName + "'s subscription on Tallia using your saved card, but the charge "
                + "didn't go through.\n\n"
                + "Your account is still fully usable for now, but please renew manually or update your card from the "
                + "Billing page soon to avoid interruption.";
        send(toEmail, subject, body);
    }

    // Sent once, the moment a lapsed subscription enters its 7-day grace
    // window — the account stays fully usable during grace, so this is a
    // heads-up, not a lockout notice like READ_ONLY effectively is.
    public void sendGracePeriodNotice(String toEmail, String businessName, long graceDaysRemaining) {
        String daysLabel = graceDaysRemaining + " day" + (graceDaysRemaining == 1 ? "" : "s");
        String subject = businessName + ": renew within " + daysLabel + " to avoid interruption";
        String body = "Hi,\n\n"
                + "Your subscription for " + businessName + " on Tallia has ended, but you're still fully set up — "
                + "you have " + daysLabel + " left to renew before your account goes read-only.\n\n"
                + "Renew anytime from your Billing page to keep creating and editing without interruption.\n\n"
                + "Already renewed? You can ignore this email.";
        send(toEmail, subject, body);
    }

    public void sendBookingConfirmation(
            String toEmail, String customerName, String businessName, String serviceName,
            String whenLabel, String manageLink
    ) {
        String subject = businessName + ": your booking is confirmed";
        String body = "Hi " + customerName + ",\n\n"
                + "Your booking for " + serviceName + " at " + businessName + " is confirmed for " + whenLabel + ".\n\n"
                + "Need to reschedule or cancel? Use this link any time: " + manageLink + "\n\n"
                + "See you soon!";
        send(toEmail, subject, body);
    }

    public void sendBookingRescheduled(String toEmail, String customerName, String businessName, String serviceName, String whenLabel) {
        String subject = businessName + ": your booking was rescheduled";
        String body = "Hi " + customerName + ",\n\n"
                + "Your booking for " + serviceName + " at " + businessName + " has been moved to " + whenLabel + ".\n\n"
                + "See you then!";
        send(toEmail, subject, body);
    }

    public void sendBookingCancelled(String toEmail, String customerName, String businessName, String serviceName) {
        String subject = businessName + ": your booking was cancelled";
        String body = "Hi " + customerName + ",\n\n"
                + "Your booking for " + serviceName + " at " + businessName + " has been cancelled.\n\n"
                + "Want to book again? Just visit our booking page any time.";
        send(toEmail, subject, body);
    }

    public void sendServiceOrderReady(String toEmail, String customerName, long orderNumber, String businessName) {
        String subject = businessName + ": your order is ready for pickup";
        String body = "Hi " + customerName + ",\n\n"
                + "Your order #" + orderNumber + " at " + businessName + " is complete and ready for pickup.\n\n"
                + "See you soon!";
        send(toEmail, subject, body);
    }

    // Owner-facing — sent to the business's own contact email so someone
    // actually sees the booking arrive, with a one-tap link to message the
    // customer straight away (no WhatsApp API needed, just a wa.me deep link).
    public void sendNewBookingNotification(String toEmail, String customerName, String serviceName, String whenLabel, String whatsappLink) {
        String subject = "New booking: " + customerName + " — " + serviceName;
        String body = customerName + " just booked " + serviceName + " for " + whenLabel + ".\n\n"
                + (whatsappLink != null ? "Message them on WhatsApp: " + whatsappLink + "\n\n" : "")
                + "Full details are in your Tallia dashboard under Service Orders.";
        send(toEmail, subject, body);
    }

    public void sendNewCustomWigRequestNotification(String toEmail, String customerName, String estimateLabel, String whatsappLink) {
        String subject = "New custom wig request from " + customerName;
        String body = customerName + " just submitted a custom wig request, estimated at " + estimateLabel + ".\n\n"
                + (whatsappLink != null ? "Message them on WhatsApp: " + whatsappLink + "\n\n" : "")
                + "Review it and send a quote from your Tallia dashboard under Custom Wig Requests.";
        send(toEmail, subject, body);
    }

    public void sendEcommerceOrderReceived(String toEmail, String customerName, String orderNumber, String businessName) {
        String subject = businessName + ": we've received your order";
        String body = "Hi " + customerName + ",\n\n"
                + "We've received your order #" + orderNumber + " at " + businessName + " and we're getting started on it.\n\n"
                + "We'll let you know as it moves along.";
        send(toEmail, subject, body);
    }

    public void sendEcommerceOrderStatusUpdate(String toEmail, String customerName, String orderNumber, String businessName, String statusMessage) {
        String subject = businessName + ": order #" + orderNumber + " update";
        String body = "Hi " + customerName + ",\n\n"
                + statusMessage + "\n\n"
                + "Order #" + orderNumber + " at " + businessName + ".";
        send(toEmail, subject, body);
    }

    public void sendCustomWigRequestReceived(String toEmail, String customerName, long requestNumber, String businessName, String estimateLabel) {
        String subject = businessName + ": we've received your custom wig request";
        String body = "Hi " + customerName + ",\n\n"
                + "We've received your custom wig request #" + requestNumber + " at " + businessName + ".\n\n"
                + "Based on your selections, the estimated price is " + estimateLabel + ". "
                + "We'll follow up with a final quote shortly.";
        send(toEmail, subject, body);
    }

    public void sendCustomWigQuoteReady(String toEmail, String customerName, long requestNumber, String businessName, String priceLabel, String ownerMessage) {
        String subject = businessName + ": your custom wig quote is ready";
        String body = "Hi " + customerName + ",\n\n"
                + "Your quote for custom wig request #" + requestNumber + " at " + businessName + " is " + priceLabel + ".\n\n"
                + (ownerMessage != null && !ownerMessage.isBlank() ? ownerMessage + "\n\n" : "")
                + "Reply on WhatsApp or email to confirm and we'll get started.";
        send(toEmail, subject, body);
    }

    public void sendCustomWigDeclined(String toEmail, String customerName, long requestNumber, String businessName, String ownerMessage) {
        String subject = businessName + ": about your custom wig request";
        String body = "Hi " + customerName + ",\n\n"
                + "Unfortunately we're not able to take on custom wig request #" + requestNumber + " at " + businessName + " at this time.\n\n"
                + (ownerMessage != null && !ownerMessage.isBlank() ? ownerMessage + "\n\n" : "")
                + "Thanks for thinking of us.";
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
