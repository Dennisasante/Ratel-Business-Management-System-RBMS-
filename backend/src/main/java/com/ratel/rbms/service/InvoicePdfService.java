package com.ratel.rbms.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Invoice;
import com.ratel.rbms.entity.InvoiceItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

// A deliberately more polished document than ReceiptService's plain-text
// receipt — this is customer-facing collateral a business hands to a client,
// not an internal till slip. Same OpenPDF library, no new dependency.
@Service
public class InvoicePdfService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a").withZone(ZoneOffset.UTC);
    private static final Color BRAND_COLOR = new Color(0, 74, 173); // matches the app's accent token
    private static final Color BRAND_DARK = new Color(15, 32, 61); // header-row background — dark, not pure black
    private static final Color BORDER_COLOR = new Color(214, 219, 226);
    private static final Color ROW_ALT_COLOR = new Color(247, 249, 251);
    private static final Color TOTAL_ROW_COLOR = new Color(234, 240, 249);

    private static final java.util.Map<String, Color> STATUS_COLORS = java.util.Map.of(
            "DRAFT", new Color(107, 114, 128),
            "SENT", new Color(37, 99, 235),
            "PAID", new Color(21, 128, 61),
            "OVERDUE", new Color(185, 28, 28)
    );

    private final String uploadDir;

    public InvoicePdfService(@Value("${app.upload-dir}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public byte[] generate(Business business, Invoice invoice, List<InvoiceItem> items) {
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            // Drawn at a fixed spot on every page regardless of how much
            // content precedes it — without this, a short invoice (one line
            // item, no notes) left a large blank gap below the totals box
            // that read as unfinished/"isolated" rather than a designed page.
            writer.setPageEvent(new BottomBarEvent());
            document.open();

            Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, BRAND_DARK);
            Font businessFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, Color.WHITE);
            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
            Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BRAND_DARK);
            Font statusFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);

            document.add(buildHeader(business, invoice, brandFont, businessFont, normalFont, labelFont, statusFont));
            document.add(ruleBelow());
            document.add(spacer(14));
            document.add(buildBillTo(invoice, labelFont, boldFont, normalFont));
            document.add(spacer(16));
            document.add(buildItemsTable(items, business.getCurrency(), tableHeaderFont, normalFont));
            document.add(spacer(10));
            document.add(buildTotals(invoice, business.getCurrency(), normalFont, boldFont, totalFont));

            if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
                document.add(spacer(18));
                document.add(new Paragraph("NOTES", labelFont));
                document.add(new Paragraph(invoice.getNotes(), normalFont));
            }

            if (invoice.getTermsAndConditions() != null && !invoice.getTermsAndConditions().isBlank()) {
                document.add(spacer(14));
                document.add(thinRule());
                document.add(spacer(8));
                document.add(new Paragraph("TERMS & CONDITIONS", labelFont));
                document.add(new Paragraph(invoice.getTermsAndConditions(), normalFont));
            }

            Image signature = loadImage(business.getSignatureUrl());
            if (signature != null) {
                document.add(spacer(24));
                document.add(buildSignatureBlock(signature, labelFont));
            }

            document.add(spacer(28));
            document.add(footerRule());
            Paragraph footer = new Paragraph("Powered by Tallia", smallFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(6);
            document.add(footer);
            Paragraph generated = new Paragraph("Generated " + TIMESTAMP_FORMAT.format(Instant.now()), smallFont);
            generated.setAlignment(Element.ALIGN_CENTER);
            document.add(generated);

            document.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate invoice PDF", e);
        }

        return out.toByteArray();
    }

    private Paragraph spacer(float size) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(0);
        p.setLeading(size);
        return p;
    }

    private PdfPTable ruleBelow() {
        PdfPTable rule = new PdfPTable(1);
        rule.setWidthPercentage(100);
        rule.setSpacingBefore(10);
        PdfPCell cell = new PdfPCell();
        cell.setFixedHeight(2.5f);
        cell.setBackgroundColor(BRAND_DARK);
        cell.setBorder(0);
        rule.addCell(cell);
        return rule;
    }

    // Optional — only rendered when the business has uploaded one (see
    // Business.signatureUrl / BusinessService.uploadSignature). Right-aligned
    // over a thin rule with a small caption underneath, so it reads as a
    // signature block rather than a stray floating image.
    private PdfPTable buildSignatureBlock(Image signature, Font labelFont) throws Exception {
        signature.scaleToFit(140, 55);

        PdfPTable wrapper = new PdfPTable(1);
        wrapper.setWidthPercentage(35);
        wrapper.setHorizontalAlignment(Element.ALIGN_RIGHT);

        PdfPCell imageCell = new PdfPCell(signature, false);
        imageCell.setBorder(0);
        imageCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        imageCell.setPaddingBottom(4);
        wrapper.addCell(imageCell);

        PdfPCell lineCell = new PdfPCell();
        lineCell.setBorder(Rectangle.TOP);
        lineCell.setBorderColor(BORDER_COLOR);
        lineCell.setFixedHeight(1f);
        wrapper.addCell(lineCell);

        PdfPCell captionCell = new PdfPCell(new Phrase("Authorized Signature", labelFont));
        captionCell.setBorder(0);
        captionCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        captionCell.setPaddingTop(4);
        wrapper.addCell(captionCell);

        return wrapper;
    }

    // A lighter divider than ruleBelow()/footerRule() — sits above the
    // Terms & Conditions block the way the reference invoice used a plain
    // horizontal line above its own terms section.
    private PdfPTable thinRule() {
        PdfPTable rule = new PdfPTable(1);
        rule.setWidthPercentage(40);
        rule.setHorizontalAlignment(Element.ALIGN_LEFT);
        PdfPCell cell = new PdfPCell();
        cell.setFixedHeight(0.75f);
        cell.setBackgroundColor(BORDER_COLOR);
        cell.setBorder(0);
        rule.addCell(cell);
        return rule;
    }

    private PdfPTable footerRule() {
        PdfPTable rule = new PdfPTable(1);
        rule.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setFixedHeight(0.75f);
        cell.setBackgroundColor(BORDER_COLOR);
        cell.setBorder(0);
        rule.addCell(cell);
        return rule;
    }

    private PdfPTable buildHeader(
            Business business, Invoice invoice, Font brandFont, Font businessFont, Font normalFont, Font labelFont, Font statusFont
    ) throws Exception {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{1.3f, 1});

        PdfPCell left = new PdfPCell();
        left.setBorder(0);
        Image logo = loadImage(business.getLogoUrl());
        if (logo != null) {
            logo.scaleToFit(85, 85);
            left.addElement(logo);
            left.addElement(new Paragraph(" "));
        }
        left.addElement(new Paragraph(business.getName(), businessFont));
        if (business.getLocation() != null) {
            left.addElement(new Paragraph(business.getLocation(), normalFont));
        }
        if (business.getContactEmail() != null) {
            left.addElement(new Paragraph(business.getContactEmail(), normalFont));
        }
        if (business.getContactPhone() != null) {
            left.addElement(new Paragraph(business.getContactPhone(), normalFont));
        }
        if (business.getTaxId() != null && !business.getTaxId().isBlank()) {
            left.addElement(new Paragraph("Tax ID: " + business.getTaxId(), normalFont));
        }
        header.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(0);
        right.setBackgroundColor(ROW_ALT_COLOR);
        right.setPadding(12);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph invoiceTitle = new Paragraph("INVOICE", brandFont);
        invoiceTitle.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(invoiceTitle);
        Paragraph invoiceNumber = new Paragraph("#" + invoice.getInvoiceNumber(), businessFont);
        invoiceNumber.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(invoiceNumber);
        right.addElement(statusBadge(invoice.getStatus(), statusFont));
        right.addElement(new Paragraph(" "));
        right.addElement(labeledLine("Issue date", invoice.getIssueDate().format(DATE_FORMAT), labelFont, normalFont));
        if (invoice.getDueDate() != null) {
            right.addElement(labeledLine("Due date", invoice.getDueDate().format(DATE_FORMAT), labelFont, normalFont));
        }
        header.addCell(right);

        return header;
    }

    // A small colored pill (e.g. green "PAID", red "OVERDUE") right under the
    // invoice number — the fastest thing a client's eye finds on the page.
    private PdfPTable statusBadge(String status, Font statusFont) {
        PdfPTable badgeWrapper = new PdfPTable(1);
        badgeWrapper.setWidthPercentage(38);
        badgeWrapper.setHorizontalAlignment(Element.ALIGN_RIGHT);
        badgeWrapper.setSpacingBefore(4);

        PdfPCell cell = new PdfPCell(new Phrase(status.toUpperCase(Locale.ROOT), statusFont));
        cell.setBorder(0);
        cell.setBackgroundColor(STATUS_COLORS.getOrDefault(status, Color.GRAY));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(4);
        badgeWrapper.addCell(cell);
        return badgeWrapper;
    }

    private Paragraph labeledLine(String label, String value, Font labelFont, Font normalFont) {
        Paragraph p = new Paragraph();
        p.setAlignment(Element.ALIGN_RIGHT);
        p.setSpacingBefore(3);
        p.add(new Phrase(label + ": ", labelFont));
        p.add(new Phrase(value, normalFont));
        return p;
    }

    private PdfPTable buildBillTo(Invoice invoice, Font labelFont, Font boldFont, Font normalFont) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.LEFT);
        cell.setBorderColor(BRAND_COLOR);
        cell.setBorderWidth(2.5f);
        cell.setPadding(10);
        cell.setBackgroundColor(ROW_ALT_COLOR);
        cell.addElement(new Paragraph("BILL TO", labelFont));
        cell.addElement(new Paragraph(invoice.getCustomerName() != null ? invoice.getCustomerName() : "—", boldFont));
        if (invoice.getCustomerAddress() != null && !invoice.getCustomerAddress().isBlank()) {
            cell.addElement(new Paragraph(invoice.getCustomerAddress(), normalFont));
        }
        if (invoice.getCustomerEmail() != null) {
            cell.addElement(new Paragraph(invoice.getCustomerEmail(), normalFont));
        }
        if (invoice.getCustomerPhone() != null) {
            cell.addElement(new Paragraph(invoice.getCustomerPhone(), normalFont));
        }
        if (invoice.getCustomerTaxId() != null && !invoice.getCustomerTaxId().isBlank()) {
            cell.addElement(new Paragraph("Tax ID: " + invoice.getCustomerTaxId(), normalFont));
        }
        table.addCell(cell);
        return table;
    }

    private PdfPTable buildItemsTable(List<InvoiceItem> items, String currency, Font headerFont, Font normalFont) {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{3.2f, 0.8f, 1.2f, 1, 1.2f});

        addHeaderCell(table, "Description", headerFont);
        addHeaderCell(table, "Qty", headerFont);
        addHeaderCell(table, "Unit price", headerFont);
        addHeaderCell(table, "Discount", headerFont);
        addHeaderCell(table, "Subtotal", headerFont);

        boolean shaded = false;
        for (InvoiceItem item : items) {
            Color rowColor = shaded ? ROW_ALT_COLOR : Color.WHITE;
            table.addCell(descriptionCell(item.getDescription(), normalFont, rowColor));
            table.addCell(bodyCell(String.valueOf(item.getQuantity()), normalFont, Element.ALIGN_CENTER, rowColor));
            table.addCell(bodyCell(currency + " " + item.getUnitPrice(), normalFont, Element.ALIGN_RIGHT, rowColor));
            table.addCell(bodyCell(currency + " " + item.getDiscountAmount(), normalFont, Element.ALIGN_RIGHT, rowColor));
            table.addCell(bodyCell(currency + " " + item.getSubtotal(), normalFont, Element.ALIGN_RIGHT, rowColor));
            shaded = !shaded;
        }
        return table;
    }

    // The line-item description is the one field a business might genuinely
    // want multi-line (a product name plus a couple of spec lines) — a plain
    // Phrase doesn't reliably break on an embedded "\n", so this splits it
    // into one Paragraph per line instead, same idiom as the multi-line
    // BILL TO / header cells above.
    private PdfPCell descriptionCell(String text, Font font, Color background) {
        PdfPCell cell = new PdfPCell();
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(7);
        cell.setBorderColor(BORDER_COLOR);
        cell.setBackgroundColor(background);
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            Paragraph p = new Paragraph(lines[i], font);
            if (i > 0) p.setSpacingBefore(2);
            cell.addElement(p);
        }
        return cell;
    }

    private PdfPCell bodyCell(String text, Font font, int alignment, Color background) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(7);
        cell.setBorderColor(BORDER_COLOR);
        cell.setBackgroundColor(background);
        return cell;
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(BRAND_DARK);
        cell.setPadding(8);
        cell.setBorderColor(BRAND_DARK);
        table.addCell(cell);
    }

    private PdfPTable buildTotals(Invoice invoice, String currency, Font normalFont, Font boldFont, Font totalFont) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(48);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.setWidths(new float[]{1, 1});

        addTotalRow(table, "Subtotal", currency + " " + invoice.getSubtotal(), normalFont, normalFont, false);
        addTotalRow(table, "Discount", currency + " " + invoice.getDiscountAmount(), normalFont, normalFont, false);
        // Hidden entirely (not shown as 0%) when the invoice never had a VAT
        // rate set at all — the common case for a business that doesn't
        // charge VAT shouldn't see a clutter row for it.
        if (invoice.getTaxRate() != null) {
            String label = "VAT (" + invoice.getTaxRate().stripTrailingZeros().toPlainString() + "%)";
            addTotalRow(table, label, currency + " " + invoice.getTaxAmount(), normalFont, normalFont, false);
        }
        if (invoice.getShippingAmount() != null && invoice.getShippingAmount().compareTo(BigDecimal.ZERO) > 0) {
            addTotalRow(table, "Shipping", currency + " " + invoice.getShippingAmount(), normalFont, normalFont, false);
        }
        addTotalRow(table, "Total due", currency + " " + invoice.getTotalAmount(), boldFont, totalFont, true);
        return table;
    }

    private void addTotalRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont, boolean highlight) {
        Color background = highlight ? TOTAL_ROW_COLOR : Color.WHITE;

        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(highlight ? Rectangle.TOP : 0);
        labelCell.setBorderColor(BORDER_COLOR);
        labelCell.setBackgroundColor(background);
        labelCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        labelCell.setPadding(6);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(highlight ? Rectangle.TOP : 0);
        valueCell.setBorderColor(BORDER_COLOR);
        valueCell.setBackgroundColor(background);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setPadding(6);
        table.addCell(valueCell);
    }

    // Reads a business-uploaded image (logo or signature) straight off local
    // disk — both URLs are always a relative "/uploads/..." path served by
    // WebConfig's static mapping, never a remote URL, so no HTTP round-trip
    // is needed. Same strip-the-prefix idiom as ServiceOrderPhotoService.delete();
    // also strips the cache-bust "?v=..." query string BusinessService's
    // uploadLogo()/uploadSignature() append.
    private Image loadImage(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String relativePath = url.replaceFirst("^/uploads/", "").replaceFirst("\\?.*$", "");
            Path path = Paths.get(uploadDir, relativePath);
            if (!Files.exists(path)) {
                return null;
            }
            return Image.getInstance(Files.readAllBytes(path));
        } catch (Exception e) {
            return null; // a missing/corrupt image shouldn't block invoice generation
        }
    }

    // Fixed-position accent bar drawn directly onto every page's content
    // stream, independent of how much flowed content precedes it — a page
    // with just one line item and no notes would otherwise end with a large
    // blank gap under the totals box, reading as unfinished rather than a
    // deliberately designed (if short) document.
    private static class BottomBarEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContentUnder();
            float barHeight = 4f;
            float y = document.bottom() - 18;
            canvas.saveState();
            canvas.setColorFill(BRAND_DARK);
            canvas.rectangle(document.left(), y, document.right() - document.left(), barHeight);
            canvas.fill();
            canvas.restoreState();
        }
    }
}
