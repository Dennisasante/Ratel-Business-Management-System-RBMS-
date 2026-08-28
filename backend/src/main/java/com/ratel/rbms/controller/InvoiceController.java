package com.ratel.rbms.controller;

import com.ratel.rbms.dto.InvoiceRequest;
import com.ratel.rbms.dto.InvoiceResponse;
import com.ratel.rbms.dto.InvoiceStatusUpdateRequest;
import com.ratel.rbms.dto.InvoiceSummaryResponse;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Invoice;
import com.ratel.rbms.entity.InvoiceItem;
import com.ratel.rbms.service.InvoicePdfService;
import com.ratel.rbms.service.InvoiceService;
import com.ratel.rbms.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
@PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON','ACCOUNTANT')")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;

    public InvoiceController(InvoiceService invoiceService, InvoicePdfService invoicePdfService) {
        this.invoiceService = invoiceService;
        this.invoicePdfService = invoicePdfService;
    }

    @GetMapping
    public List<InvoiceSummaryResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return invoiceService.list(from, to);
    }

    @GetMapping("/{id}")
    public InvoiceResponse get(@PathVariable UUID id) {
        return invoiceService.get(id);
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse> create(@Valid @RequestBody InvoiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invoiceService.create(request));
    }

    @PutMapping("/{id}")
    public InvoiceResponse update(@PathVariable UUID id, @Valid @RequestBody InvoiceRequest request) {
        return invoiceService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public InvoiceResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody InvoiceStatusUpdateRequest request) {
        return invoiceService.updateStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        invoiceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/send")
    public InvoiceResponse send(@PathVariable UUID id) {
        return invoiceService.send(id);
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<InvoiceResponse> duplicate(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invoiceService.duplicate(id));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        Invoice invoice = invoiceService.getOwnedForPdf(id);
        Business business = invoiceService.getBusiness(TenantContext.getBusinessId());
        List<InvoiceItem> items = invoiceService.getItems(invoice.getId());
        byte[] pdf = invoicePdfService.generate(business, invoice, items);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=invoice-" + invoice.getInvoiceNumber() + ".pdf")
                .body(pdf);
    }
}
