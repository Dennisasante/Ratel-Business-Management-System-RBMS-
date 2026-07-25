package com.ratel.rbms.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.ratel.rbms.dto.SaleItemResponse;
import com.ratel.rbms.dto.SaleResponse;
import com.ratel.rbms.entity.Business;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
public class ReceiptService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a");

    /** Simple, print-friendly single-page receipt — not trying to be a full invoice template. */
    public byte[] generateReceipt(Business business, SaleResponse sale) {
        Document document = new Document(PageSize.A5);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8);

            Paragraph title = new Paragraph(business.getName(), titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            if (business.getLocation() != null) {
                Paragraph location = new Paragraph(business.getLocation(), smallFont);
                location.setAlignment(Element.ALIGN_CENTER);
                document.add(location);
            }

            document.add(new Paragraph(" "));

            Paragraph meta = new Paragraph(
                    "Receipt #" + sale.saleNumber() + "\n"
                            + sale.createdAt().atZone(ZoneOffset.UTC).format(DATE_FORMAT) + "\n"
                            + "Served by: " + sale.cashierName() + "\n"
                            + "Customer: " + (sale.customerName() != null ? sale.customerName() : "Walk-in"),
                    normalFont
            );
            document.add(meta);
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{4, 1, 1.5f, 1.5f});

            addHeaderCell(table, "Item", boldFont);
            addHeaderCell(table, "Qty", boldFont);
            addHeaderCell(table, "Price", boldFont);
            addHeaderCell(table, "Subtotal", boldFont);

            for (SaleItemResponse item : sale.items()) {
                table.addCell(new PdfPCell(new Phrase(item.productName(), normalFont)));
                table.addCell(new PdfPCell(new Phrase(String.valueOf(item.quantity()), normalFont)));
                table.addCell(new PdfPCell(new Phrase("GH₵" + item.unitPrice(), normalFont)));
                table.addCell(new PdfPCell(new Phrase("GH₵" + item.subtotal(), normalFont)));
            }
            document.add(table);

            document.add(new Paragraph(" "));
            Paragraph total = new Paragraph("TOTAL: GH₵" + sale.totalAmount(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12));
            total.setAlignment(Element.ALIGN_RIGHT);
            document.add(total);

            Paragraph payment = new Paragraph("Payment: " + sale.paymentMethod().replace("_", " "), normalFont);
            payment.setAlignment(Element.ALIGN_RIGHT);
            document.add(payment);

            document.add(new Paragraph(" "));
            Paragraph thanks = new Paragraph("Thank you for your business!", smallFont);
            thanks.setAlignment(Element.ALIGN_CENTER);
            document.add(thanks);

            document.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate receipt PDF", e);
        }

        return out.toByteArray();
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(new Color(243, 244, 246));
        table.addCell(cell);
    }
}
