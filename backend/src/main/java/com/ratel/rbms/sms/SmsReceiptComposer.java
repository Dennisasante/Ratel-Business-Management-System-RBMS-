package com.ratel.rbms.sms;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Builds the SMS receipt. Laid out as a few short lines so it reads cleanly on a phone:
 *
 *   Thank you for shopping with us, Ama!
 *
 *   Receipt #1042
 *   Total: GHS 85.00 (saved 10.00)
 *   Points: 7 (reach 10 to earn a reward)
 *
 *   Book an appointment or check your points:
 *   app.example.com/p/k3x9a2f81m
 *
 * The business name is deliberately not in the text: it already shows as the sender name on
 * the customer's phone. A message is billed per 160-character segment, and this layout is
 * normally two segments (the cost is worked out from the real text before sending).
 */
public final class SmsReceiptComposer {

    private SmsReceiptComposer() {
    }

    public record Input(
            String firstName,        // null/blank -> no name in the thank-you
            long receiptNumber,
            String currency,
            BigDecimal total,
            BigDecimal saved,        // null or zero -> no discount shown
            String pointsLine,       // e.g. "Points: 7 (reach 10 to earn a reward)"; null when there is no points program
            String link,             // e.g. "app.example.com/p/k3x9a2f81m"; null -> no link
            String linkLabel         // what the link is for, e.g. "Book an appointment:"; null -> "Book an appointment:"
    ) {
    }

    public static String compose(Input in) {
        StringBuilder sb = new StringBuilder("Thank you for shopping with us");
        if (in.firstName() != null && !in.firstName().isBlank()) {
            sb.append(", ").append(in.firstName().trim());
        }
        sb.append("!\n\nReceipt #").append(in.receiptNumber())
                .append("\nTotal: ").append(in.currency()).append(' ').append(money(in.total()));
        if (in.saved() != null && in.saved().compareTo(BigDecimal.ZERO) > 0) {
            sb.append(" (saved ").append(money(in.saved())).append(')');
        }
        if (in.pointsLine() != null && !in.pointsLine().isBlank()) {
            sb.append('\n').append(in.pointsLine().trim());
        }
        if (in.link() != null && !in.link().isBlank()) {
            String label = in.linkLabel() == null || in.linkLabel().isBlank() ? "Book an appointment:" : in.linkLabel().trim();
            sb.append("\n\n").append(label).append('\n').append(in.link().trim());
        }
        return sb.toString();
    }

    // The ₵ sign would force the whole message into the 70-character encoding, so the
    // currency is always written as its code (GHS).
    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
