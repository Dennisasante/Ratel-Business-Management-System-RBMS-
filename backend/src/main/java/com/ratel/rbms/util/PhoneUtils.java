package com.ratel.rbms.util;

// Ghana-specific phone normalization for duplicate-customer detection —
// "0244123456" / "+233244123456" / "233 244 123 456" / "024-412-3456" all
// represent the same real number but wouldn't match as raw strings. Logic
// here MUST stay in sync by hand with the SQL backfill in
// V41__customer_phone_normalization.sql.
public final class PhoneUtils {

    private PhoneUtils() {
    }

    // Normalizes to Ghana local format: "0" + 9 digits (10 digits total).
    // Accepts 0XXXXXXXXX, 233XXXXXXXXX, +233XXXXXXXXX, 00233XXXXXXXXX, and
    // bare 9-digit input. Unrecognized shapes fall back to the stripped
    // digit string so exact-match dedupe is never made worse than today.
    public static String normalize(String rawPhone) {
        if (rawPhone == null) return null;
        String digits = rawPhone.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;
        if (digits.length() == 14 && digits.startsWith("00233")) return "0" + digits.substring(5);
        if (digits.length() == 12 && digits.startsWith("233")) return "0" + digits.substring(3);
        if (digits.length() == 10 && digits.startsWith("0")) return digits;
        if (digits.length() == 9) return "0" + digits;
        return digits;
    }
}
