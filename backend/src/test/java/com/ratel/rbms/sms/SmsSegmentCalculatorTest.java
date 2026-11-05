package com.ratel.rbms.sms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Segment count == credits charged, so the boundaries here are money boundaries. */
class SmsSegmentCalculatorTest {

    @Test
    void plainTextFitsOneSegmentUpTo160Characters() {
        assertEquals(1, SmsSegmentCalculator.calculate("a".repeat(160)).segments());
        assertEquals(2, SmsSegmentCalculator.calculate("a".repeat(161)).segments());
    }

    @Test
    void concatenatedPlainTextHolds153PerSegment() {
        assertEquals(2, SmsSegmentCalculator.calculate("a".repeat(306)).segments());
        assertEquals(3, SmsSegmentCalculator.calculate("a".repeat(307)).segments());
    }

    @Test
    void cediSignForcesUnicodeAndTheSmallerLimit() {
        SmsSegmentCalculator.Result r = SmsSegmentCalculator.calculate("Pay ₵5");
        assertTrue(r.unicode());
        assertEquals(1, r.segments());

        assertEquals(1, SmsSegmentCalculator.calculate("₵" + "a".repeat(69)).segments());
        assertEquals(2, SmsSegmentCalculator.calculate("₵" + "a".repeat(70)).segments());
    }

    @Test
    void plainGhanaStyleTextIsNotUnicode() {
        assertFalse(SmsSegmentCalculator.calculate("Thanks for visiting! You now have 3 points.").unicode());
    }

    @Test
    void emptyMessageHasZeroLength() {
        assertEquals(0, SmsSegmentCalculator.calculate("").length());
    }
}
