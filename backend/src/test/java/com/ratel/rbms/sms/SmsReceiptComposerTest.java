package com.ratel.rbms.sms;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmsReceiptComposerTest {

    private static SmsReceiptComposer.Input input(String business, String name, String saved, String points, String link) {
        return new SmsReceiptComposer.Input(business, name, 1042, "GHS", new BigDecimal("770"),
                saved == null ? null : new BigDecimal(saved), points, link, null);
    }

    @Test
    void theCompactReceiptFitsInOneCreditWithEverythingKept() {
        String text = SmsReceiptComposer.compose(input("Glow Salon", "Ama", "20", null, "app.example.com/book/glow"));

        assertEquals("Glow Salon: Hi Ama, receipt #1042 GHS 770.00 (saved 20.00). Book: app.example.com/book/glow", text);
        assertEquals(1, SmsSegmentCalculator.calculate(text).segments());
    }

    @Test
    void noDiscountMeansNoSavedBracket() {
        String text = SmsReceiptComposer.compose(input("Glow Salon", "Ama", null, null, null));

        assertEquals("Glow Salon: Hi Ama, receipt #1042 GHS 770.00.", text);
        assertFalse(text.contains("saved"));
    }

    @Test
    void aZeroDiscountIsNotShown() {
        assertFalse(SmsReceiptComposer.compose(input("Glow Salon", "Ama", "0.00", null, null)).contains("saved"));
    }

    @Test
    void aWalkInWithoutANameGetsNoGreeting() {
        String text = SmsReceiptComposer.compose(input("Glow Salon", null, null, null, null));

        assertFalse(text.contains("Hi "));
        assertTrue(text.startsWith("Glow Salon: receipt #1042"));
    }

    @Test
    void whenTooLongTheGreetingGoesFirstThenTheLink() {
        // Sized so the full version is 161 characters (one over) and only the greeting has to go.
        String business = "B".repeat(67);
        String link = "app.ratelsystems.tech/book/glow-studio";

        String text = SmsReceiptComposer.compose(input(business, "Ama", "20", null, link));

        assertEquals(1, SmsSegmentCalculator.calculate(text).segments());
        assertFalse(text.contains("Hi Ama"), "the greeting is the first thing dropped");
        // The receipt itself is never dropped.
        assertTrue(text.contains("#1042") && text.contains("770.00") && text.contains("saved 20.00"));
    }

    @Test
    void thePointsLineIsKeptAheadOfTheLinkWhenSpaceIsTight() {
        // Sized so dropping the greeting still leaves 161 characters: the link has to go too, the points stay.
        String text = SmsReceiptComposer.compose(input("B".repeat(62), "Ama", "20", "Points 7/10",
                "app.ratelsystems.tech/book/glow-studio"));

        assertEquals(1, SmsSegmentCalculator.calculate(text).segments());
        assertTrue(text.contains("Points 7/10"));
        assertFalse(text.contains("Book:"));
    }

    @Test
    void anImpossiblyLongBusinessNameStillSendsTheFullReceipt() {
        String text = SmsReceiptComposer.compose(input("A".repeat(150), "Ama", "20", null, null));

        assertTrue(text.contains("#1042 GHS 770.00 (saved 20.00)"), "information is never dropped to save a credit");
    }

    @Test
    void aPointsLineThatAlreadyEndsInPunctuationIsNotGivenASecondOne() {
        String due = SmsReceiptComposer.compose(input("Glow Salon", "Ama", null, "Reward due!", null));
        String counting = SmsReceiptComposer.compose(input("Glow Salon", "Ama", null, "Points 7/10", null));

        assertTrue(due.contains("Reward due!") && !due.contains("!."), due);
        assertTrue(counting.contains("Points 7/10."), counting);
    }

    @Test
    void theCurrencyIsWrittenAsACodeNeverTheCediSign() {
        String text = SmsReceiptComposer.compose(input("Glow Salon", "Ama", "20", null, null));

        assertFalse(SmsSegmentCalculator.calculate(text).unicode());
        assertFalse(text.contains("₵"));
    }
}
