package com.ratel.rbms.service;

import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * One-tap staff-initiated WhatsApp messages — no API keys, no external calls,
 * just a wa.me deep link with the message pre-filled. This is the whole
 * "automation" story for a business on the free WhatsApp Business app (no
 * send API): staff clicks the link, WhatsApp opens with the message ready
 * to send. Upgrading a specific vendor to full Cloud API automation later
 * is additive, not a rebuild of this.
 */
@Service
public class WhatsAppLinkService {

    public String buildLink(String phone, String message) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return null;
        }
        String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20");
        return "https://wa.me/" + digits + "?text=" + encoded;
    }
}
