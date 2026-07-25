package com.ratel.rbms.service;

import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Expense;
import com.ratel.rbms.entity.Sale;
import com.ratel.rbms.entity.SaleItem;
import com.ratel.rbms.repository.ExpenseRepository;
import com.ratel.rbms.repository.SaleItemRepository;
import com.ratel.rbms.repository.SaleRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class ReportExportService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ExpenseRepository expenseRepository;

    public ReportExportService(SaleRepository saleRepository, SaleItemRepository saleItemRepository, ExpenseRepository expenseRepository) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.expenseRepository = expenseRepository;
    }

    public byte[] exportReport(Business business, LocalDate from, LocalDate to) {
        UUID businessId = TenantContext.getBusinessId();
        var fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        var toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Sale> sales = saleRepository.findAllByBusinessIdAndCreatedAtBetween(businessId, fromInstant, toInstant);
        List<Expense> expenses = expenseRepository.findAllByBusinessIdAndExpenseDateBetween(businessId, from, to);

        BigDecimal revenue = sales.stream().map(Sale::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expenseTotal = expenses.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(workbook);

            writeSummarySheet(workbook, headerStyle, business, from, to, sales.size(), revenue, expenses.size(), expenseTotal);
            writeSalesSheet(workbook, headerStyle, sales);
            writeExpensesSheet(workbook, headerStyle, expenses);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate report export", e);
        }
    }

    private void writeSummarySheet(
            XSSFWorkbook workbook, CellStyle headerStyle, Business business, LocalDate from, LocalDate to,
            int salesCount, BigDecimal revenue, int expenseCount, BigDecimal expenseTotal
    ) {
        Sheet sheet = workbook.createSheet("Summary");
        int r = 0;
        row(sheet, r++, "Business", business.getName());
        row(sheet, r++, "Period", from + " to " + to);
        r++;
        row(sheet, r++, "Sales count", String.valueOf(salesCount));
        row(sheet, r++, "Revenue", revenue.toString());
        row(sheet, r++, "Expense count", String.valueOf(expenseCount));
        row(sheet, r++, "Expenses", expenseTotal.toString());
        row(sheet, r++, "Net", revenue.subtract(expenseTotal).toString());
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void writeSalesSheet(XSSFWorkbook workbook, CellStyle headerStyle, List<Sale> sales) {
        Sheet sheet = workbook.createSheet("Sales");
        Row header = sheet.createRow(0);
        String[] columns = {"Sale #", "Date", "Payment Method", "Total", "Commission", "Items"};
        for (int i = 0; i < columns.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (Sale sale : sales) {
            List<SaleItem> items = saleItemRepository.findAllBySaleId(sale.getId());
            String itemsSummary = items.stream()
                    .map(i -> i.getProductName() + " x" + i.getQuantity())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");

            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(sale.getSaleNumber() != null ? sale.getSaleNumber() : 0);
            row.createCell(1).setCellValue(sale.getCreatedAt().toString());
            row.createCell(2).setCellValue(sale.getPaymentMethod().name());
            row.createCell(3).setCellValue(sale.getTotalAmount().doubleValue());
            row.createCell(4).setCellValue(sale.getCommissionAmount().doubleValue());
            row.createCell(5).setCellValue(itemsSummary);
        }
        for (int i = 0; i < columns.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void writeExpensesSheet(XSSFWorkbook workbook, CellStyle headerStyle, List<Expense> expenses) {
        Sheet sheet = workbook.createSheet("Expenses");
        Row header = sheet.createRow(0);
        String[] columns = {"Date", "Category", "Description", "Amount"};
        for (int i = 0; i < columns.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (Expense expense : expenses) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(expense.getExpenseDate().toString());
            row.createCell(1).setCellValue(expense.getCategory().name());
            row.createCell(2).setCellValue(expense.getDescription() != null ? expense.getDescription() : "");
            row.createCell(3).setCellValue(expense.getAmount().doubleValue());
        }
        for (int i = 0; i < columns.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void row(Sheet sheet, int rowNum, String label, String value) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }
}
