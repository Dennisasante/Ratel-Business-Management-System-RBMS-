package com.ratel.rbms.sms;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the short SMS receipt. Every message of 160 characters or fewer costs a
 * business one credit and the next tier costs two, so this tries the richest
 * version first and drops pieces, least valuable first, until it fits in one:
 *
 *   1. "Hi {name}," greeting   (dropped first)
 *   2. the booking link
 *   3. the points line
 *
 * The business name, receipt number, total and any discount are never dropped —
 * that is the receipt. If even that doesn't fit in one message (a very long
 * business name) it is sent anyway rather than losing information.
 */
public final class SmsReceiptComposer {

    private SmsReceiptComposer() {
    }

    public record Input(
            String businessName,
            String firstName,        // null/blank -> no greeting
            long receiptNumber,
            String currency,
            BigDecimal total,
            BigDecimal saved,        // null or zero -> no discount shown
            String pointsLine,       // e.g. "Points 7/10"; null until loyalty exists
            String link              // e.g. "app.example.com/book/glow"; null -> none
    ) {
    }

    public static String compose(Input in) {
        List<String> candidates = new ArrayList<>();
        candidates.add(build(in, true, true, true));
        candidates.add(build(in, false, true, true));
        candidates.add(build(in, false, false, true));
        candidates.add(build(in, false, false, false));

        for (String candidate : candidates) {
            if (SmsSegmentCalculator.calculate(candidate).segments() <= 1) {
                return candidate;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private static String build(Input in, boolean greeting, boolean link, boolean points) {
        StringBuilder sb = new StringBuilder();
        sb.append(in.businessName()).append(": ");
        if (greeting && in.firstName() != null && !in.firstName().isBlank()) {
            sb.append("Hi ").append(in.firstName().trim()).append(", receipt");
        } else {
            sb.append("receipt");
        }
        sb.append(" #").append(in.receiptNumber()).append(' ')
                .append(in.currency()).append(' ').append(money(in.total()));
        if (in.saved() != null && in.saved().compareTo(BigDecimal.ZERO) > 0) {
            sb.append(" (saved ").append(money(in.saved())).append(')');
        }
        sb.append('.');
        if (points && in.pointsLine() != null && !in.pointsLine().isBlank()) {
            sb.append(' ').append(in.pointsLine().trim()).append('.');
        }
        if (link && in.link() != null && !in.link().isBlank()) {
            sb.append(" Book: ").append(in.link().trim());
        }
        return sb.toString();
    }

    // The ₵ sign would force the whole message into the 70-character encoding, so the
    // currency is always written as its code (GHS).
    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
