package com.ratel.rbms.service;

import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfReader;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Invoice;
import com.ratel.rbms.entity.InvoiceItem;
import com.ratel.rbms.entity.enums.Industry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Renders real PDFs and looks inside them: the logo must actually be on the page, in the chosen style. */
class InvoicePdfServiceTest {

    @TempDir
    Path uploads;

    private InvoicePdfService service;

    @BeforeEach
    void setUp() throws IOException {
        service = new InvoicePdfService(uploads.toString());
        Files.createDirectories(uploads.resolve("logos"));
    }

    @Test
    void headerStyleDrawsTheLogoOnceOnThePage() throws Exception {
        Business business = businessWithPngLogo(Business.LOGO_STYLE_HEADER);

        byte[] pdf = service.generate(business, invoice(), items(2));

        assertEquals(1, imagesOnPage(pdf, 1));
    }

    @Test
    void watermarkStyleDrawsTheLogoOnceOnThePage() throws Exception {
        Business business = businessWithPngLogo(Business.LOGO_STYLE_WATERMARK);

        byte[] pdf = service.generate(business, invoice(), items(2));

        assertEquals(1, imagesOnPage(pdf, 1));
    }

    @Test
    void watermarkRepeatsOnEveryPageOfALongInvoiceButHeaderOnlyOnTheFirst() throws Exception {
        byte[] watermark = service.generate(businessWithPngLogo(Business.LOGO_STYLE_WATERMARK), invoice(), items(80));
        byte[] header = service.generate(businessWithPngLogo(Business.LOGO_STYLE_HEADER), invoice(), items(80));

        int pages = new PdfReader(watermark).getNumberOfPages();
        assertTrue(pages >= 2, "expected a multi-page invoice, got " + pages);
        for (int page = 1; page <= pages; page++) {
            assertEquals(1, imagesOnPage(watermark, page), "watermark missing on page " + page);
        }
        assertEquals(1, imagesOnPage(header, 1));
        assertEquals(0, imagesOnPage(header, 2));
    }

    @Test
    void aBusinessWithoutALogoStillGetsAnInvoiceInEitherStyle() throws Exception {
        for (String style : List.of(Business.LOGO_STYLE_HEADER, Business.LOGO_STYLE_WATERMARK)) {
            Business business = business(style, null);

            byte[] pdf = service.generate(business, invoice(), items(2));

            assertEquals(1, new PdfReader(pdf).getNumberOfPages());
            assertEquals(0, imagesOnPage(pdf, 1));
        }
    }

    @Test
    void anUnreadableLogoDoesNotBreakTheInvoice() throws Exception {
        // e.g. a WebP file: neither OpenPDF nor the JDK can decode it.
        Files.write(uploads.resolve("logos/broken.webp"), "RIFF....WEBPVP8 not-really".getBytes(StandardCharsets.UTF_8));
        Business business = business(Business.LOGO_STYLE_WATERMARK, "/uploads/logos/broken.webp?v=123");

        byte[] pdf = service.generate(business, invoice(), items(2));

        assertEquals(1, new PdfReader(pdf).getNumberOfPages());
        assertEquals(0, imagesOnPage(pdf, 1));
    }

    @Test
    void theSampleInvoiceUsesTheRequestedStyleNotTheSavedOne() throws Exception {
        Business business = businessWithPngLogo(Business.LOGO_STYLE_HEADER);

        byte[] pdf = service.generateSample(business, Business.LOGO_STYLE_WATERMARK);

        assertEquals(1, new PdfReader(pdf).getNumberOfPages());
        assertEquals(1, imagesOnPage(pdf, 1));
    }

    // ---- helpers ----

    private Business businessWithPngLogo(String style) throws IOException {
        BufferedImage image = new BufferedImage(240, 240, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(15, 32, 61));
        g.fillOval(10, 10, 220, 220);
        g.dispose();
        ImageIO.write(image, "png", uploads.resolve("logos/logo.png").toFile());
        return business(style, "/uploads/logos/logo.png?v=1");
    }

    private Business business(String style, String logoUrl) {
        return Business.builder()
                .name("Glow Studio Salon")
                .slug("glow-studio")
                .industry(Industry.SALON)
                .currency("GHS")
                .location("East Legon, Accra")
                .logoUrl(logoUrl)
                .invoiceLogoStyle(style)
                .build();
    }

    private Invoice invoice() {
        return Invoice.builder()
                .invoiceNumber(7L)
                .customerName("Ama Serwaa")
                .issueDate(LocalDate.of(2026, 11, 4))
                .status("SENT")
                .subtotal(new BigDecimal("100.00"))
                .totalAmount(new BigDecimal("100.00"))
                .build();
    }

    private List<InvoiceItem> items(int count) {
        List<InvoiceItem> items = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            items.add(InvoiceItem.builder().description("Item " + i).quantity(1)
                    .unitPrice(new BigDecimal("10.00")).subtotal(new BigDecimal("10.00")).build());
        }
        return items;
    }

    private int imagesOnPage(byte[] pdf, int page) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        // Resources can sit on the page or be inherited from a parent Pages node.
        PdfDictionary resources = null;
        for (PdfDictionary node = reader.getPageN(page); node != null && resources == null; node = node.getAsDict(PdfName.PARENT)) {
            resources = node.getAsDict(PdfName.RESOURCES);
        }
        PdfDictionary xobjects = resources == null ? null : resources.getAsDict(PdfName.XOBJECT);
        if (xobjects == null) return 0;
        int images = 0;
        for (PdfName name : xobjects.getKeys()) {
            // An image is a stream, which getAsDict() doesn't treat as a dictionary.
            if (PdfReader.getPdfObject(xobjects.get(name)) instanceof PdfDictionary xobject
                    && PdfName.IMAGE.equals(xobject.get(PdfName.SUBTYPE))) {
                images++;
            }
        }
        return images;
    }
}
