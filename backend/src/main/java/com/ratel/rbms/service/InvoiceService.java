package com.ratel.rbms.service;

import com.ratel.rbms.dto.InvoiceItemRequest;
import com.ratel.rbms.dto.InvoiceItemResponse;
import com.ratel.rbms.dto.InvoiceRequest;
import com.ratel.rbms.dto.InvoiceResponse;
import com.ratel.rbms.dto.InvoiceSummaryResponse;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.Invoice;
import com.ratel.rbms.entity.InvoiceItem;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.CustomerRepository;
import com.ratel.rbms.repository.InvoiceItemRepository;
import com.ratel.rbms.repository.InvoiceRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class InvoiceService {

    private static final Set<String> VALID_STATUSES = Set.of("DRAFT", "SENT", "PAID", "OVERDUE");

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final BusinessRepository businessRepository;
    private final CustomerRepository customerRepository;
    private final ActivityLogService activityLogService;
    private final InvoicePdfService invoicePdfService;
    private final EmailService emailService;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            InvoiceItemRepository invoiceItemRepository,
            BusinessRepository businessRepository,
            CustomerRepository customerRepository,
            ActivityLogService activityLogService,
            InvoicePdfService invoicePdfService,
            EmailService emailService
    ) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.businessRepository = businessRepository;
        this.customerRepository = customerRepository;
        this.activityLogService = activityLogService;
        this.invoicePdfService = invoicePdfService;
        this.emailService = emailService;
    }

    public List<InvoiceSummaryResponse> listAll() {
        return list(null, null);
    }

    // Date-range filter for the Invoices page (defaults to Today, see
    // DateRangeFilter) — both-or-neither; null on both means "all time."
    // Filters on issueDate, same field the page's own "Date" column shows.
    public List<InvoiceSummaryResponse> list(LocalDate from, LocalDate to) {
        UUID businessId = TenantContext.getBusinessId();
        List<Invoice> invoices = (from == null && to == null)
                ? invoiceRepository.findAllByBusinessIdOrderByIssueDateDesc(businessId)
                : invoiceRepository.findAllByBusinessIdAndIssueDateBetween(businessId, from, to).stream()
                        .sorted(Comparator.comparing(Invoice::getIssueDate).reversed())
                        .toList();
        return invoices.stream().map(InvoiceSummaryResponse::from).toList();
    }

    public InvoiceResponse get(UUID id) {
        return toResponse(getOwned(id));
    }

    @Transactional
    public InvoiceResponse create(InvoiceRequest req) {
        UUID businessId = TenantContext.getBusinessId();

        // Locks the business row first — findMaxInvoiceNumber() below must see
        // a consistent snapshot, or two concurrent invoice creations for the
        // same business could both compute the same "next" number and collide
        // on the unique (business_id, invoice_number) constraint. Same pattern
        // BillingService.verifyPayment() already uses for its own read-then-increment.
        Business business = businessRepository.findByIdForUpdate(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
        long nextNumber = invoiceRepository.findMaxInvoiceNumber(businessId) + 1;

        String customerName = req.customerName();
        String customerEmail = req.customerEmail();
        String customerPhone = req.customerPhone();
        if (req.customerId() != null) {
            Customer customer = customerRepository.findByIdAndBusinessId(req.customerId(), businessId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer not found."));
            customerName = customer.getFullName();
            customerEmail = customer.getEmail();
            customerPhone = customer.getPhone();
        }

        Invoice invoice = Invoice.builder()
                .businessId(businessId)
                .invoiceNumber(nextNumber)
                .customerId(req.customerId())
                .customerName(customerName)
                .customerEmail(customerEmail)
                .customerPhone(customerPhone)
                .customerAddress(req.customerAddress())
                .issueDate(req.issueDate())
                .dueDate(req.dueDate())
                .notes(req.notes())
                .termsAndConditions(req.termsAndConditions() != null ? req.termsAndConditions() : business.getDefaultTermsAndConditions())
                .taxRate(req.taxRate())
                .shippingAmount(req.shippingAmount() != null ? req.shippingAmount() : BigDecimal.ZERO)
                .createdBy(TenantContext.getUserId())
                .build();
        invoice = invoiceRepository.save(invoice);

        applyItems(invoice, req.items());

        activityLogService.log("Created invoice #" + invoice.getInvoiceNumber() + " for GH₵" + invoice.getTotalAmount(), "INVOICE", invoice.getId());

        return toResponse(invoice);
    }

    @Transactional
    public InvoiceResponse update(UUID id, InvoiceRequest req) {
        Invoice invoice = getOwned(id);
        if (!"DRAFT".equals(invoice.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Can't edit an invoice that's already been sent.");
        }

        UUID businessId = invoice.getBusinessId();
        String customerName = req.customerName();
        String customerEmail = req.customerEmail();
        String customerPhone = req.customerPhone();
        if (req.customerId() != null) {
            Customer customer = customerRepository.findByIdAndBusinessId(req.customerId(), businessId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer not found."));
            customerName = customer.getFullName();
            customerEmail = customer.getEmail();
            customerPhone = customer.getPhone();
        }

        invoice.setCustomerId(req.customerId());
        invoice.setCustomerName(customerName);
        invoice.setCustomerEmail(customerEmail);
        invoice.setCustomerPhone(customerPhone);
        invoice.setCustomerAddress(req.customerAddress());
        invoice.setIssueDate(req.issueDate());
        invoice.setDueDate(req.dueDate());
        invoice.setNotes(req.notes());
        invoice.setTermsAndConditions(req.termsAndConditions());
        invoice.setTaxRate(req.taxRate());
        invoice.setShippingAmount(req.shippingAmount() != null ? req.shippingAmount() : BigDecimal.ZERO);
        invoice = invoiceRepository.save(invoice);

        invoiceItemRepository.deleteAllByInvoiceId(invoice.getId());
        applyItems(invoice, req.items());

        activityLogService.log("Updated invoice #" + invoice.getInvoiceNumber(), "INVOICE", invoice.getId());

        return toResponse(invoice);
    }

    @Transactional
    public InvoiceResponse updateStatus(UUID id, String status) {
        if (!VALID_STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unrecognized status.");
        }
        Invoice invoice = getOwned(id);
        invoice.setStatus(status);
        invoice = invoiceRepository.save(invoice);

        activityLogService.log("Marked invoice #" + invoice.getInvoiceNumber() + " as " + status.toLowerCase(), "INVOICE", invoice.getId());

        return toResponse(invoice);
    }

    @Transactional
    public void delete(UUID id) {
        Invoice invoice = getOwned(id);
        if (!"DRAFT".equals(invoice.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Can't delete an invoice that's already been sent — change its status instead.");
        }
        long invoiceNumber = invoice.getInvoiceNumber();
        invoiceItemRepository.deleteAllByInvoiceId(id);
        invoiceRepository.delete(invoice);
        activityLogService.log("Deleted invoice #" + invoiceNumber, "INVOICE", id);
    }

    // Emails the same PDF Download produces, as an attachment. Flips a DRAFT
    // invoice to SENT (matching what "Send" implies); re-sending an
    // already-SENT/PAID/OVERDUE invoice just re-delivers it without touching
    // status, since that's presumably a deliberate resend, not a first send.
    @Transactional
    public InvoiceResponse send(UUID id) {
        Invoice invoice = getOwned(id);
        if (invoice.getCustomerEmail() == null || invoice.getCustomerEmail().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This invoice has no customer email on file to send to.");
        }
        if (!emailService.isConfigured()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Email isn't set up for this business yet — ask your Owner to add SMTP settings.");
        }

        Business business = getBusiness(invoice.getBusinessId());
        List<InvoiceItem> items = getItems(invoice.getId());
        byte[] pdf = invoicePdfService.generate(business, invoice, items);
        emailService.sendInvoice(invoice.getCustomerEmail(), business.getName(), invoice.getInvoiceNumber(),
                business.getCurrency() + " " + invoice.getTotalAmount(), pdf);

        if ("DRAFT".equals(invoice.getStatus())) {
            invoice.setStatus("SENT");
            invoice = invoiceRepository.save(invoice);
        }

        activityLogService.log("Sent invoice #" + invoice.getInvoiceNumber() + " to " + invoice.getCustomerEmail(), "INVOICE", invoice.getId());

        return toResponse(invoice);
    }

    // A fresh DRAFT with the same customer/line items — today's issue date,
    // no due date/status carried over, since those are specific to the
    // original billing cycle, not the client relationship being repeated.
    @Transactional
    public InvoiceResponse duplicate(UUID id) {
        Invoice original = getOwned(id);
        List<InvoiceItem> originalItems = getItems(original.getId());
        UUID businessId = original.getBusinessId();

        businessRepository.findByIdForUpdate(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
        long nextNumber = invoiceRepository.findMaxInvoiceNumber(businessId) + 1;

        Invoice copy = Invoice.builder()
                .businessId(businessId)
                .invoiceNumber(nextNumber)
                .customerId(original.getCustomerId())
                .customerName(original.getCustomerName())
                .customerEmail(original.getCustomerEmail())
                .customerPhone(original.getCustomerPhone())
                .customerAddress(original.getCustomerAddress())
                .issueDate(LocalDate.now())
                .notes(original.getNotes())
                .termsAndConditions(original.getTermsAndConditions())
                .taxRate(original.getTaxRate())
                .shippingAmount(original.getShippingAmount())
                .createdBy(TenantContext.getUserId())
                .build();
        copy = invoiceRepository.save(copy);

        List<InvoiceItemRequest> itemRequests = originalItems.stream()
                .map(i -> new InvoiceItemRequest(i.getDescription(), i.getQuantity(), i.getUnitPrice(), i.getDiscountAmount()))
                .toList();
        applyItems(copy, itemRequests);

        activityLogService.log("Duplicated invoice #" + original.getInvoiceNumber() + " as #" + copy.getInvoiceNumber(), "INVOICE", copy.getId());

        return toResponse(copy);
    }

    public Invoice getOwnedForPdf(UUID id) {
        return getOwned(id);
    }

    public Business getBusiness(UUID businessId) {
        return businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
    }

    public List<InvoiceItem> getItems(UUID invoiceId) {
        return invoiceItemRepository.findAllByInvoiceIdOrderBySortOrderAsc(invoiceId);
    }

    // Recomputes and persists every line item plus the invoice's own
    // subtotal/discountAmount/totalAmount from them — shared by create() and
    // update() so the two can never drift into different rounding/derivation
    // logic.
    private void applyItems(Invoice invoice, List<InvoiceItemRequest> itemRequests) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        int sortOrder = 0;

        for (InvoiceItemRequest itemReq : itemRequests) {
            BigDecimal lineTotal = itemReq.unitPrice().multiply(BigDecimal.valueOf(itemReq.quantity()));
            BigDecimal discount = itemReq.discountAmount() != null ? itemReq.discountAmount() : BigDecimal.ZERO;
            if (discount.compareTo(lineTotal) > 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Discount on \"" + itemReq.description() + "\" can't exceed its line total.");
            }
            BigDecimal itemSubtotal = lineTotal.subtract(discount);
            subtotal = subtotal.add(lineTotal);
            discountTotal = discountTotal.add(discount);

            InvoiceItem item = InvoiceItem.builder()
                    .invoiceId(invoice.getId())
                    .description(itemReq.description())
                    .quantity(itemReq.quantity())
                    .unitPrice(itemReq.unitPrice())
                    .discountAmount(discount)
                    .subtotal(itemSubtotal)
                    .sortOrder(sortOrder++)
                    .build();
            invoiceItemRepository.save(item);
        }

        // Tax applies over (subtotal - discount), not the raw subtotal —
        // shipping is added after, untaxed (a simplifying assumption; this
        // isn't a full tax-jurisdiction engine).
        BigDecimal taxableBase = subtotal.subtract(discountTotal);
        BigDecimal taxAmount = invoice.getTaxRate() != null
                ? taxableBase.multiply(invoice.getTaxRate()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal shipping = invoice.getShippingAmount() != null ? invoice.getShippingAmount() : BigDecimal.ZERO;

        invoice.setSubtotal(subtotal);
        invoice.setDiscountAmount(discountTotal);
        invoice.setTaxAmount(taxAmount);
        invoice.setTotalAmount(taxableBase.add(taxAmount).add(shipping));
        invoiceRepository.save(invoice);
    }

    private Invoice getOwned(UUID id) {
        return invoiceRepository.findByIdAndBusinessId(id, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invoice not found."));
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        List<InvoiceItemResponse> items = invoiceItemRepository.findAllByInvoiceIdOrderBySortOrderAsc(invoice.getId()).stream()
                .map(InvoiceItemResponse::from)
                .toList();
        return InvoiceResponse.from(invoice, items);
    }
}
