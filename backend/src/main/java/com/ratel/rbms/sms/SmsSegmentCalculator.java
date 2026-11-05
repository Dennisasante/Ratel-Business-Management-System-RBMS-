package com.ratel.rbms.sms;

/**
 * How many SMS "segments" (billing units) a message costs, using the standard
 * GSM rules networks bill by: a message made only of GSM 7-bit characters fits
 * 160 per single segment (153 each once it has to be split); a message with
 * ANY character outside that set — the cedi sign, an emoji, curly quotes —
 * switches the whole message to UCS-2, which fits only 70 (67 when split).
 * That second case is the surprising one for owners, which is why the UI shows
 * the segment count live as they type.
 *
 * One credit = one segment per recipient. mNotify's own page-counting is
 * assumed to follow the same standard; if it ever differs, this is the single
 * place to change.
 */
public final class SmsSegmentCalculator {

    public record Result(int segments, boolean unicode, int length) {
    }

    // Written with \\u escapes (not literal characters) so the table can't be
    // corrupted by a source-file encoding mix-up. \n and \r are GSM characters too.
    private static final String GSM_BASIC =
            "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨ"
                    + "ΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡"
                    + "ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyz"
                    + "äöñüà";

    // Each of these costs two GSM positions (an escape plus the character).
    private static final String GSM_EXTENDED = "^{}\\[~]|€\f";

    private SmsSegmentCalculator() {
    }

    public static Result calculate(String message) {
        if (message == null || message.isEmpty()) {
            return new Result(0, false, 0);
        }

        int gsmUnits = 0;
        boolean gsmOnly = true;
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (GSM_BASIC.indexOf(c) >= 0) {
                gsmUnits += 1;
            } else if (GSM_EXTENDED.indexOf(c) >= 0) {
                gsmUnits += 2;
            } else {
                gsmOnly = false;
                break;
            }
        }

        if (gsmOnly) {
            int segments = gsmUnits <= 160 ? 1 : (int) Math.ceil(gsmUnits / 153.0);
            return new Result(segments, false, gsmUnits);
        }

        int length = message.length(); // UTF-16 code units — what UCS-2 billing counts
        int segments = length <= 70 ? 1 : (int) Math.ceil(length / 67.0);
        return new Result(segments, true, length);
    }
}
