package com.ratel.rbms.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Invoice;
import com.ratel.rbms.entity.InvoiceItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.List;

// A deliberately more polished document than ReceiptService's plain-text
// receipt — this is customer-facing collateral a business hands to a client,
// not an internal till slip. Same OpenPDF library, no new dependency.
@Service
public class InvoicePdfService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final Color BRAND_COLOR = new Color(0, 74, 173); // matches the app's accent token
    private static final Color LIGHT_GRAY = new Color(243, 244, 246);

    private final String uploadDir;

    public InvoicePdfService(@Value("${app.upload-dir}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public byte[] generate(Business business, Invoice invoice, List<InvoiceItem> items) {
        Document document = new Document(PageSize.A4, 40, 40, 50, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, BRAND_COLOR);
            Font businessFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8);
            Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);

            document.add(buildHeader(business, invoice, brandFont, businessFont, normalFont, labelFont));
            document.add(new Paragraph(" "));
            document.add(buildBillTo(invoice, labelFont, normalFont));
            document.add(new Paragraph(" "));
            document.add(buildItemsTable(items, business.getCurrency(), boldFont, normalFont));
            document.add(new Paragraph(" "));
            document.add(buildTotals(invoice, business.getCurrency(), normalFont, boldFont, totalFont));

            if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
                document.add(new Paragraph(" "));
                Paragraph notesLabel = new Paragraph("Notes", boldFont);
                document.add(notesLabel);
                document.add(new Paragraph(invoice.getNotes(), normalFont));
            }

            document.add(new Paragraph(" "));
            document.add(new Paragraph(" "));
            Paragraph footer = new Paragraph("Powered by Tallia", smallFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate invoice PDF", e);
        }

        return out.toByteArray();
    }

    private PdfPTable buildHeader(Business business, Invoice invoice, Font brandFont, Font businessFont, Font normalFont, Font labelFont) throws Exception {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{1.3f, 1});

        PdfPCell left = new PdfPCell();
        left.setBorder(0);
        Image logo = loadLogo(business.getLogoUrl());
        if (logo != null) {
            logo.scaleToFit(90, 90);
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
        header.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(0);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph invoiceTitle = new Paragraph("INVOICE", brandFont);
        invoiceTitle.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(invoiceTitle);
        Paragraph invoiceNumber = new Paragraph("#" + invoice.getInvoiceNumber(), businessFont);
        invoiceNumber.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(invoiceNumber);
        right.addElement(new Paragraph(" "));
        right.addElement(labeledLine("Issue date", invoice.getIssueDate().format(DATE_FORMAT), labelFont, normalFont));
        if (invoice.getDueDate() != null) {
            right.addElement(labeledLine("Due date", invoice.getDueDate().format(DATE_FORMAT), labelFont, normalFont));
        }
        header.addCell(right);

        return header;
    }

    private Paragraph labeledLine(String label, String value, Font labelFont, Font normalFont) {
        Paragraph p = new Paragraph();
        p.setAlignment(Element.ALIGN_RIGHT);
        p.add(new Phrase(label + ": ", labelFont));
        p.add(new Phrase(value, normalFont));
        return p;
    }

    private PdfPTable buildBillTo(Invoice invoice, Font labelFont, Font normalFont) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(0);
        cell.setPadding(0);
        cell.addElement(new Paragraph("BILL TO", labelFont));
        cell.addElement(new Paragraph(invoice.getCustomerName() != null ? invoice.getCustomerName() : "—", normalFont));
        if (invoice.getCustomerAddress() != null && !invoice.getCustomerAddress().isBlank()) {
            cell.addElement(new Paragraph(invoice.getCustomerAddress(), normalFont));
        }
        if (invoice.getCustomerEmail() != null) {
            cell.addElement(new Paragraph(invoice.getCustomerEmail(), normalFont));
        }
        if (invoice.getCustomerPhone() != null) {
            cell.addElement(new Paragraph(invoice.getCustomerPhone(), normalFont));
        }
        table.addCell(cell);
        return table;
    }

    private PdfPTable buildItemsTable(List<InvoiceItem> items, String currency, Font boldFont, Font normalFont) {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{3.2f, 0.8f, 1.2f, 1, 1.2f});

        addHeaderCell(table, "Description", boldFont);
        addHeaderCell(table, "Qty", boldFont);
        addHeaderCell(table, "Unit price", boldFont);
        addHeaderCell(table, "Discount", boldFont);
        addHeaderCell(table, "Subtotal", boldFont);

        for (InvoiceItem item : items) {
            table.addCell(new PdfPCell(new Phrase(item.getDescription(), normalFont)));
            table.addCell(alignedCell(String.valueOf(item.getQuantity()), normalFont, Element.ALIGN_CENTER));
            table.addCell(alignedCell(currency + " " + item.getUnitPrice(), normalFont, Element.ALIGN_RIGHT));
            table.addCell(alignedCell(currency + " " + item.getDiscountAmount(), normalFont, Element.ALIGN_RIGHT));
            table.addCell(alignedCell(currency + " " + item.getSubtotal(), normalFont, Element.ALIGN_RIGHT));
        }
        return table;
    }

    private PdfPCell alignedCell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        return cell;
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(LIGHT_GRAY);
        table.addCell(cell);
    }

    private PdfPTable buildTotals(Invoice invoice, String currency, Font normalFont, Font boldFont, Font totalFont) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(45);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.setWidths(new float[]{1, 1});

        addTotalRow(table, "Subtotal", currency + " " + invoice.getSubtotal(), normalFont, normalFont);
        addTotalRow(table, "Discount", currency + " " + invoice.getDiscountAmount(), normalFont, normalFont);
        addTotalRow(table, "Total", currency + " " + invoice.getTotalAmount(), boldFont, totalFont);
        return table;
    }

    private void addTotalRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(0);
        labelCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(0);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    // Reads the logo straight off local disk — logoUrl is always a relative
    // "/uploads/..." path served by WebConfig's static mapping, never a
    // remote URL, so no HTTP round-trip is needed. Same strip-the-prefix
    // idiom as ServiceOrderPhotoService.delete(); also strips the cache-bust
    // "?v=..." query string BusinessService.uploadLogo() appends.
    private Image loadLogo(String logoUrl) {
        if (logoUrl == null || logoUrl.isBlank()) {
            return null;
        }
        try {
            String relativePath = logoUrl.replaceFirst("^/uploads/", "").replaceFirst("\\?.*$", "");
            Path path = Paths.get(uploadDir, relativePath);
            if (!Files.exists(path)) {
                return null;
            }
            return Image.getInstance(Files.readAllBytes(path));
        } catch (Exception e) {
            return null; // a missing/corrupt logo shouldn't block invoice generation
        }
    }
}
