package com.ratel.rbms.service;

import com.ratel.rbms.dto.ImportPreviewResponse;
import com.ratel.rbms.dto.ImportResultResponse;
import com.ratel.rbms.dto.ImportRow;
import com.ratel.rbms.dto.ImportSkip;
import com.ratel.rbms.dto.ProductRequest;
import com.ratel.rbms.entity.ProductCategory;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.ProductCategoryRepository;
import com.ratel.rbms.repository.ProductRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Preview-then-confirm inventory import — parses in memory (no disk
// storage; there's nothing to clean up between requests), never trusting
// the client's own valid/errors flags on confirm() since the frontend's
// checkboxes are just a UX convenience, not a security boundary.
@Service
public class ProductImportService {

    private static final int MAX_ROWS = 2000;
    private static final List<String> EXPECTED_HEADERS = List.of(
            "name", "category", "sku", "costprice", "sellingprice", "quantity", "lowstockthreshold", "suppliername"
    );

    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductService productService;

    public ProductImportService(
            ProductRepository productRepository,
            ProductCategoryRepository productCategoryRepository,
            ProductService productService
    ) {
        this.productRepository = productRepository;
        this.productCategoryRepository = productCategoryRepository;
        this.productService = productService;
    }

    public byte[] template() {
        String csv = "name,category,sku,costPrice,sellingPrice,quantity,lowStockThreshold,supplierName\n"
                + "613 Blonde Bundle,Wigs,SKU-001,80.00,150.00,10,5,Acme Hair Supplies\n";
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    public ImportPreviewResponse preview(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No file was uploaded.");
        }

        UUID businessId = TenantContext.getBusinessId();
        List<List<String>> rawRows;
        try {
            rawRows = isSpreadsheet(file) ? parseSpreadsheet(file.getInputStream()) : parseCsv(file.getInputStream());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Couldn't read that file. Make sure it's a valid CSV or Excel file.");
        }

        if (rawRows.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That file has no data rows.");
        }
        if (rawRows.size() - 1 > MAX_ROWS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That file has more than " + MAX_ROWS + " rows — split it into smaller batches.");
        }

        List<String> header = rawRows.get(0).stream().map(h -> h.trim().toLowerCase()).toList();
        if (!header.containsAll(EXPECTED_HEADERS)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "That file's headers don't match the expected template — download the template below and use its exact column names.");
        }

        int nameCol = header.indexOf("name");
        int categoryCol = header.indexOf("category");
        int skuCol = header.indexOf("sku");
        int costCol = header.indexOf("costprice");
        int sellCol = header.indexOf("sellingprice");
        int qtyCol = header.indexOf("quantity");
        int lowStockCol = header.indexOf("lowstockthreshold");
        int supplierCol = header.indexOf("suppliername");

        Set<String> seenSkus = new HashSet<>();
        List<ImportRow> rows = new ArrayList<>();
        int validCount = 0;

        for (int i = 1; i < rawRows.size(); i++) {
            List<String> cells = rawRows.get(i);
            String name = cellAt(cells, nameCol);
            String category = cellAt(cells, categoryCol);
            String sku = cellAt(cells, skuCol);
            String supplierName = cellAt(cells, supplierCol);

            List<String> errors = new ArrayList<>();
            if (name == null || name.isBlank()) {
                errors.add("Name is required");
            }

            BigDecimal costPrice = parseNonNegativeDecimal(cellAt(cells, costCol), "Cost price", errors);
            BigDecimal sellingPrice = parseNonNegativeDecimal(cellAt(cells, sellCol), "Selling price", errors);
            Integer quantity = parseNonNegativeInt(cellAt(cells, qtyCol), "Quantity", errors);
            Integer lowStockThreshold = parseNonNegativeInt(cellAt(cells, lowStockCol), "Low stock threshold", errors);

            if (sku != null && !sku.isBlank()) {
                if (!seenSkus.add(sku)) {
                    errors.add("Duplicate SKU in this file");
                } else if (productRepository.existsByBusinessIdAndSku(businessId, sku)) {
                    errors.add("SKU already exists");
                }
            }

            boolean valid = errors.isEmpty();
            if (valid) validCount++;

            rows.add(new ImportRow(
                    i, name, blankToNull(category), blankToNull(sku),
                    costPrice, sellingPrice, quantity, lowStockThreshold, blankToNull(supplierName),
                    valid, errors
            ));
        }

        return new ImportPreviewResponse(rows, validCount, rows.size() - validCount);
    }

    @Transactional
    public ImportResultResponse confirm(List<ImportRow> rows) {
        UUID businessId = TenantContext.getBusinessId();
        int imported = 0;
        List<ImportSkip> skipped = new ArrayList<>();

        for (ImportRow row : rows) {
            String reason = revalidate(row, businessId);
            if (reason != null) {
                skipped.add(new ImportSkip(row.rowNumber(), row.name(), reason));
                continue;
            }

            UUID categoryId = row.category() != null ? resolveCategory(businessId, row.category()) : null;
            productService.create(new ProductRequest(
                    row.name(), null, categoryId, row.sku(),
                    row.costPrice() != null ? row.costPrice() : BigDecimal.ZERO,
                    row.sellingPrice() != null ? row.sellingPrice() : BigDecimal.ZERO,
                    row.quantity() != null ? row.quantity() : 0,
                    row.lowStockThreshold() != null ? row.lowStockThreshold() : 5,
                    row.supplierName(), null, false
            ));
            imported++;
        }

        return new ImportResultResponse(imported, skipped.size(), skipped);
    }

    // Re-checked against the database as it stands right now — the
    // frontend's valid/errors fields reflect what preview() saw, which may
    // be stale by the time confirm() actually runs (another row in the same
    // batch might have just claimed the same SKU, or someone else created a
    // matching product in between the two requests).
    private String revalidate(ImportRow row, UUID businessId) {
        if (row.name() == null || row.name().isBlank()) {
            return "Name is required";
        }
        if (row.sku() != null && !row.sku().isBlank() && productRepository.existsByBusinessIdAndSku(businessId, row.sku())) {
            return "SKU already exists";
        }
        return null;
    }

    private UUID resolveCategory(UUID businessId, String categoryName) {
        return productCategoryRepository.findByBusinessIdAndNameIgnoreCase(businessId, categoryName)
                .map(ProductCategory::getId)
                .orElseGet(() -> productCategoryRepository.save(
                        ProductCategory.builder().businessId(businessId).name(categoryName).build()
                ).getId());
    }

    private BigDecimal parseNonNegativeDecimal(String raw, String label, List<String> errors) {
        if (raw == null || raw.isBlank()) return null;
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            if (value.compareTo(BigDecimal.ZERO) < 0) {
                errors.add(label + " can't be negative");
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            errors.add(label + " isn't a valid number: \"" + raw + "\"");
            return null;
        }
    }

    private Integer parseNonNegativeInt(String raw, String label, List<String> errors) {
        if (raw == null || raw.isBlank()) return null;
        try {
            int value = (int) Double.parseDouble(raw.trim());
            if (value < 0) {
                errors.add(label + " can't be negative");
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            errors.add(label + " isn't a valid number: \"" + raw + "\"");
            return null;
        }
    }

    private String cellAt(List<String> cells, int index) {
        return index >= 0 && index < cells.size() ? cells.get(index).trim() : null;
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private boolean isSpreadsheet(MultipartFile file) {
        String type = file.getContentType();
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(type)
                || "application/vnd.ms-excel".equals(type)
                || filename.endsWith(".xlsx") || filename.endsWith(".xls");
    }

    private List<List<String>> parseSpreadsheet(InputStream inputStream) throws IOException {
        List<List<String>> result = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                boolean rowHasContent = false;
                short lastCellNum = row.getLastCellNum();
                for (int c = 0; c < lastCellNum; c++) {
                    Cell cell = row.getCell(c);
                    String value = cell != null ? formatter.formatCellValue(cell).trim() : "";
                    if (!value.isEmpty()) rowHasContent = true;
                    cells.add(value);
                }
                if (rowHasContent) result.add(cells);
            }
        }
        return result;
    }

    // Simple RFC4180-ish CSV line parser — handles quoted fields (including
    // embedded commas and escaped "" quotes), which a plain String.split(",")
    // would break on for something like a product name containing a comma.
    private List<List<String>> parseCsv(InputStream inputStream) throws IOException {
        List<List<String>> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stripBom(inputStream), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                result.add(parseCsvLine(line));
            }
        }
        return result;
    }

    private InputStream stripBom(InputStream inputStream) throws IOException {
        // Excel-exported CSVs are frequently UTF-8-with-BOM; the BOM would
        // otherwise land inside the first header cell's text and break the
        // header-name match.
        PushbackInputStream pushback = new PushbackInputStream(inputStream, 3);
        byte[] bom = new byte[3];
        int read = pushback.read(bom, 0, 3);
        if (read == 3 && (bom[0] & 0xFF) == 0xEF && (bom[1] & 0xFF) == 0xBB && (bom[2] & 0xFF) == 0xBF) {
            return pushback;
        }
        if (read > 0) pushback.unread(bom, 0, read);
        return pushback;
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }
}
