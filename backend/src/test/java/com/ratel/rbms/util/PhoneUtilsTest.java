package com.ratel.rbms.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PhoneUtils is the entire customer-identity mechanism Tallia AI's
 * findCustomer/createCustomer/createBooking tools depend on — worth its own
 * test independent of any AI code, per the spec's explicit call-out.
 */
class PhoneUtilsTest {

    @Test
    void normalizesEveryAcceptedShapeToTheSameCanonicalForm() {
        String canonical = "0244123456";
        assertEquals(canonical, PhoneUtils.normalize("0244123456"));
        assertEquals(canonical, PhoneUtils.normalize("233244123456"));
        assertEquals(canonical, PhoneUtils.normalize("+233244123456"));
        assertEquals(canonical, PhoneUtils.normalize("00233244123456"));
        assertEquals(canonical, PhoneUtils.normalize("244123456"));
        assertEquals(canonical, PhoneUtils.normalize("024-412-3456"));
        assertEquals(canonical, PhoneUtils.normalize("+233 244 123 456"));
    }

    @Test
    void normalizeReturnsNullForNull() {
        assertEquals(null, PhoneUtils.normalize(null));
    }

    @Test
    void isValidAcceptsEveryRealShape() {
        assertTrue(PhoneUtils.isValid("0244123456"));
        assertTrue(PhoneUtils.isValid("+233244123456"));
        assertTrue(PhoneUtils.isValid("00233244123456"));
        assertTrue(PhoneUtils.isValid("244123456"));
    }

    // This is exactly the customer-submitted "fake phone number" case this
    // session's own bug report was about — a bogus number must never
    // silently pass as valid, since it's what an AI tool would otherwise
    // let through into createCustomer/createBooking.
    @Test
    void isValidRejectsObviouslyFakeNumbers() {
        assertFalse(PhoneUtils.isValid("12345"));
        assertFalse(PhoneUtils.isValid("999"));
        assertFalse(PhoneUtils.isValid(""));
        assertFalse(PhoneUtils.isValid(null));
        assertFalse(PhoneUtils.isValid("abcdefghij"));
    }
}
