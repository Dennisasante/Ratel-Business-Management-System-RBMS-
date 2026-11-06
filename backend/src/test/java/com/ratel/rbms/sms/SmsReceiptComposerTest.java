package com.ratel.rbms.sms;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmsReceiptComposerTest {

    private static final String POINTS = "You currently have 7 points (reach 10 points to earn a reward)";

    private static SmsReceiptComposer.Input input(String name, String saved, String points, String link, String label) {
        return new SmsReceiptComposer.Input("Hairnette", name, 1042, "GHS", new BigDecimal("85"),
                saved == null ? null : new BigDecimal(saved), points, link, label);
    }

    @Test
    void theFullReceiptReadsAsShortSeparateLines() {
        String text = SmsReceiptComposer.compose(input("Dennis", "10", POINTS,
                "app.example.com/p/k3x9a2f81m", "To book an appointment or check your points:"));

        assertEquals("Thank you for choosing Hairnette, Dennis!\n"
                + "\n"
                + "Receipt #1042\n"
                + "Total: GHS 85.00 (saved 10.00)\n"
                + "You currently have 7 points (reach 10 points to earn a reward)\n"
                + "\n"
                + "To book an appointment or check your points:\n"
                + "app.example.com/p/k3x9a2f81m", text);
    }

    @Test
    void withoutAPointsProgramTheLinkIsJustTheBookingPage() {
        String text = SmsReceiptComposer.compose(input("Dennis", null, null, "app.example.com/book/glow", null));

        assertEquals("Thank you for choosing Hairnette, Dennis!\n\nReceipt #1042\nTotal: GHS 85.00"
                + "\n\nTo book an appointment:\napp.example.com/book/glow", text);
        assertEquals(1, SmsSegmentCalculator.calculate(text).segments());
    }

    @Test
    void noDiscountMeansNoSavedBracketAndNoLinkMeansNoLinkBlock() {
        String text = SmsReceiptComposer.compose(input("Dennis", null, null, null, null));

        assertEquals("Thank you for choosing Hairnette, Dennis!\n\nReceipt #1042\nTotal: GHS 85.00", text);
    }

    @Test
    void aZeroDiscountIsNotShown() {
        assertFalse(SmsReceiptComposer.compose(input("Dennis", "0.00", null, null, null)).contains("saved"));
    }

    @Test
    void aWalkInWithoutANameStillGetsAThankYou() {
        String text = SmsReceiptComposer.compose(input(null, null, null, null, null));

        assertTrue(text.startsWith("Thank you for choosing Hairnette!\n"), text);
    }

    @Test
    void thePointsLineIsPassedThroughAsIs() {
        String text = SmsReceiptComposer.compose(input("Dennis", null,
                "You currently have 10 points - your reward is ready!", null, null));

        assertTrue(text.contains("\nYou currently have 10 points - your reward is ready!"), text);
    }

    @Test
    void theCurrencyIsWrittenAsACodeAndTheTextStaysInTheCheapEncoding() {
        String text = SmsReceiptComposer.compose(input("Dennis", "10", POINTS, "app.example.com/p/k3x9a2f81m",
                "To book an appointment or check your points:"));

        assertFalse(SmsSegmentCalculator.calculate(text).unicode(), "line breaks and plain letters stay in the 160-character encoding");
        assertFalse(text.contains("₵"));
    }
}
