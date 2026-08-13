package com.ratel.rbms.service;

import com.ratel.rbms.dto.MobileMoneyChargeRequest;
import com.ratel.rbms.dto.MobileMoneyChargeResponse;
import com.ratel.rbms.dto.RecordPaymentRequest;
import com.ratel.rbms.dto.RefundRequest;
import com.ratel.rbms.dto.SaleItemRequest;
import com.ratel.rbms.dto.SaleItemResponse;
import com.ratel.rbms.dto.SaleRequest;
import com.ratel.rbms.dto.SaleResponse;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.BusinessIntegrations;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.PaymentTransaction;
import com.ratel.rbms.entity.Product;
import com.ratel.rbms.entity.Sale;
import com.ratel.rbms.entity.SaleItem;
import com.ratel.rbms.entity.ServiceCatalogItem;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.MovementType;
import com.ratel.rbms.entity.enums.PaymentMethod;
import com.ratel.rbms.entity.enums.SaleItemType;
import com.ratel.rbms.dto.StockAdjustmentRequest;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessIntegrationsRepository;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.SaleItemRepository;
import com.ratel.rbms.repository.SaleRepository;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SaleService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final UserRepository userRepository;
    private final ProductService productService;
    private final ServiceCatalogItemRepository serviceCatalogItemRepository;
    private final CustomerService customerService;
    private final ActivityLogService activityLogService;
    private final BusinessIntegrationsRepository businessIntegrationsRepository;
    private final BusinessRepository businessRepository;
    private final PaystackService paystackService;
    private final PaymentTransactionService paymentTransactionService;

    public SaleService(
            SaleRepository saleRepository,
            SaleItemRepository saleItemRepository,
            UserRepository userRepository,
            ProductService productService,
            ServiceCatalogItemRepository serviceCatalogItemRepository,
            CustomerService customerService,
            ActivityLogService activityLogService,
            BusinessIntegrationsRepository businessIntegrationsRepository,
            BusinessRepository businessRepository,
            PaystackService paystackService,
            PaymentTransactionService paymentTransactionService
    ) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.userRepository = userRepository;
        this.productService = productService;
        this.serviceCatalogItemRepository = serviceCatalogItemRepository;
        this.customerService = customerService;
        this.activityLogService = activityLogService;
        this.businessIntegrationsRepository = businessIntegrationsRepository;
        this.businessRepository = businessRepository;
        this.paystackService = paystackService;
        this.paymentTransactionService = paymentTransactionService;
    }

    @Transactional
    public SaleResponse createSale(SaleRequest req) {
        UUID businessId = TenantContext.getBusinessId();
        UUID cashierId = TenantContext.getUserId();
        User cashier = userRepository.findById(cashierId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Cashier account not found."));

        Customer customer = null;
        if (req.customerId() != null) {
            customer = customerService.getOwned(req.customerId());
        }

        // CASH/MOBILE_MONEY_DIRECT are assumed collected the instant the sale
        // is rung up — only MOBILE_MONEY (the Paystack-gateway one) defers to
        // a real gateway charge or manual mark-paid afterward.
        boolean collectedInPerson = req.paymentMethod() == PaymentMethod.CASH
                || req.paymentMethod() == PaymentMethod.MOBILE_MONEY_DIRECT;

        Sale sale = Sale.builder()
                .businessId(businessId)
                .customerId(req.customerId())
                .cashierId(cashierId)
                .paymentMethod(req.paymentMethod())
                .paymentStatus(collectedInPerson ? "PAID" : "UNPAID")
                .totalAmount(BigDecimal.ZERO)
                .build();
        sale = saleRepository.save(sale); // flush needed so sale_number + id are available below
        saleRepository.flush();

        BigDecimal runningTotal = BigDecimal.ZERO;
        List<SaleItemResponse> itemResponses = new java.util.ArrayList<>();

        for (SaleItemRequest itemReq : req.items()) {
            if ((itemReq.productId() == null) == (itemReq.serviceCatalogId() == null)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Each cart line must be either a product or a service.");
            }

            SaleItemType itemType;
            UUID productId = null;
            UUID serviceCatalogId = null;
            String itemName;
            BigDecimal unitPrice;

            if (itemReq.productId() != null) {
                Product product = productService.getOwned(itemReq.productId());

                // Deducting stock through ProductService keeps this sale's stock
                // change in the same stock_movements audit trail as manual adjustments,
                // tagged with the sale number so it's traceable back to this transaction.
                productService.adjustStock(
                        product.getId(),
                        new StockAdjustmentRequest(MovementType.REMOVE, itemReq.quantity(), "Sale #" + sale.getSaleNumber())
                );

                itemType = SaleItemType.PRODUCT;
                productId = product.getId();
                itemName = product.getName();
                unitPrice = product.getSellingPrice();
            } else {
                ServiceCatalogItem service = serviceCatalogItemRepository.findByIdAndBusinessId(itemReq.serviceCatalogId(), businessId)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service not found."));

                itemType = SaleItemType.SERVICE;
                serviceCatalogId = service.getId();
                itemName = service.getName();
                unitPrice = service.getPrice();
            }

            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(itemReq.quantity()));
            boolean gift = Boolean.TRUE.equals(itemReq.gift());
            // A gift line always nets to zero — force the discount to the full
            // line price rather than trusting whatever discountAmount was sent,
            // so it can never end up mismatched (e.g. a stale value from before
            // quantity changed).
            BigDecimal discount = gift ? lineTotal : (itemReq.discountAmount() != null ? itemReq.discountAmount() : BigDecimal.ZERO);
            if (discount.compareTo(lineTotal) > 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Discount on \"" + itemName + "\" can't exceed its line total.");
            }
            BigDecimal subtotal = lineTotal.subtract(discount);
            runningTotal = runningTotal.add(subtotal);

            SaleItem saleItem = SaleItem.builder()
                    .businessId(businessId)
                    .saleId(sale.getId())
                    .itemType(itemType)
                    .productId(productId)
                    .serviceCatalogId(serviceCatalogId)
                    .productName(itemName)
                    .unitPrice(unitPrice)
                    .quantity(itemReq.quantity())
                    .discountAmount(discount)
                    .subtotal(subtotal)
                    .gift(gift)
                    .build();
            saleItem = saleItemRepository.save(saleItem);
            itemResponses.add(SaleItemResponse.from(saleItem));
        }

        sale.setTotalAmount(runningTotal);
        sale.setAmountPaid(collectedInPerson ? runningTotal : BigDecimal.ZERO);

        // Snapshotted now, from the cashier's rate at this exact moment — see
        // the comment on Sale.commissionAmount for why this isn't recalculated later.
        BigDecimal commissionAmount = runningTotal
                .multiply(cashier.getCommissionRate())
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        sale.setCommissionAmount(commissionAmount);

        sale = saleRepository.save(sale);

        activityLogService.log(
                "Recorded sale #" + sale.getSaleNumber() + " for GH₵" + runningTotal
                        + (customer != null ? " (" + customer.getFullName() + ")" : " (walk-in)"),
                "SALE", sale.getId()
        );

        String customerName = customer != null ? customer.getFullName() : null;

        return SaleResponse.from(sale, customerName, cashier.getFullName(), itemResponses);
    }

    public List<SaleResponse> listAll() {
        UUID businessId = TenantContext.getBusinessId();
        return saleRepository.findAllByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .map(this::toResponseWithItems)
                .toList();
    }

    public SaleResponse get(UUID id) {
        Sale sale = getOwned(id);
        return toResponseWithItems(sale);
    }

    // Charges the customer's mobile money wallet directly by phone number —
    // same "input the number, they get a prompt" flow as ServiceOrderService's
    // equivalent method.
    public MobileMoneyChargeResponse chargeMobileMoney(UUID id, MobileMoneyChargeRequest req) {
        Sale sale = getOwned(id);
        if ("PAID".equals(sale.getPaymentStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This sale is already paid.");
        }
        Customer customer = customerService.getOrNull(sale.getCustomerId());
        String customerEmail = resolveCustomerEmail(customer, sale.getBusinessId(), sale.getId());

        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(sale.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "This business hasn't set up online payment yet."));
        if (integrations.getPaystackSecretKey() == null || integrations.getPaystackSecretKey().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This business hasn't set up online payment yet.");
        }

        long amountMinorUnits = sale.getTotalAmount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
        String reference = "SALE-MOMO-" + sale.getBusinessId().toString().substring(0, 8) + "-" + UUID.randomUUID().toString().substring(0, 8);

        PaystackService.MobileMoneyChargeResult result = paystackService.chargeMobileMoney(
                integrations.getPaystackSecretKey(), customerEmail, amountMinorUnits, req.phone(), req.provider(), reference
        );

        sale.setPaystackReference(result.reference());
        if (result.success()) {
            sale.setPaymentStatus("PAID");
            sale.setAmountPaid(sale.getTotalAmount());
            activityLogService.log("Charged mobile money for sale #" + sale.getSaleNumber(), "SALE", sale.getId());
        }
        saleRepository.save(sale);

        paymentTransactionService.record(
                sale.getBusinessId(), PaymentTransaction.Direction.INCOMING, PaymentTransaction.SourceType.SALE,
                sale.getId(), "PAYSTACK", "MOBILE_MONEY", sale.getTotalAmount(), result.success() ? "SUCCESS" : "PENDING",
                result.reference(), sale.getCustomerId(), req.phone(), null, TenantContext.getUserId()
        );

        return new MobileMoneyChargeResponse(result.reference(), result.status(), result.displayText());
    }

    // Same "customer reads back the SMS code" completion step as
    // ServiceOrderService's equivalent — see its comment for why this exists.
    @Transactional
    public SaleResponse submitMobileMoneyOtp(String reference, String otp) {
        Sale sale = saleRepository.findByPaystackReferenceAndBusinessId(reference, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Unknown payment reference."));

        if ("PAID".equals(sale.getPaymentStatus())) {
            return toResponseWithItems(sale);
        }

        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(sale.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "This business hasn't set up online payment."));

        PaystackService.ChargeResult result = paystackService.submitOtp(integrations.getPaystackSecretKey(), otp, reference);

        if (result.success()) {
            sale.setPaymentStatus("PAID");
            sale.setAmountPaid(sale.getTotalAmount());
            sale = saleRepository.save(sale);
            activityLogService.log("Charged mobile money for sale #" + sale.getSaleNumber(), "SALE", sale.getId());
            paymentTransactionService.updateStatusByReference(sale.getBusinessId(), reference, "SUCCESS");
        } else if ("failed".equalsIgnoreCase(result.status())) {
            sale.setPaymentStatus("FAILED");
            sale = saleRepository.save(sale);
            paymentTransactionService.updateStatusByReference(sale.getBusinessId(), reference, "FAILED");
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    result.message() != null && !result.message().isBlank() ? result.message() : "That code didn't work.");
        }

        return toResponseWithItems(sale);
    }

    @Transactional
    public SaleResponse verifyPayment(String reference) {
        Sale sale = saleRepository.findByPaystackReferenceAndBusinessId(reference, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Unknown payment reference."));

        if ("PAID".equals(sale.getPaymentStatus())) {
            return toResponseWithItems(sale);
        }

        BusinessIntegrations integrations = businessIntegrationsRepository.findByBusinessId(sale.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "This business hasn't set up online payment."));

        PaystackService.VerifyResult verify = paystackService.verifyTransaction(integrations.getPaystackSecretKey(), reference);

        sale.setPaymentStatus(verify.success() ? "PAID" : "FAILED");
        if (verify.success()) {
            sale.setAmountPaid(sale.getTotalAmount());
        }
        sale = saleRepository.save(sale);

        if (verify.success()) {
            activityLogService.log("Confirmed payment for sale #" + sale.getSaleNumber(), "SALE", sale.getId());
        }

        paymentTransactionService.record(
                sale.getBusinessId(), PaymentTransaction.Direction.INCOMING, PaymentTransaction.SourceType.SALE,
                sale.getId(), "PAYSTACK", "MOBILE_MONEY", sale.getTotalAmount(), verify.success() ? "SUCCESS" : "FAILED",
                reference, sale.getCustomerId(), null, null, TenantContext.getUserId()
        );

        return toResponseWithItems(sale);
    }

    // A plain manual action (no Paystack) for cash/other payment collected
    // after the fact — mirrors ServiceOrderService.markPaid(). Thin wrapper
    // over recordPayment(): "mark paid" just means "the whole remaining
    // balance came in, in cash."
    @Transactional
    public SaleResponse markPaid(UUID id) {
        Sale sale = getOwned(id);
        BigDecimal balanceDue = sale.getTotalAmount().subtract(sale.getAmountPaid()).max(BigDecimal.ZERO);
        return recordPayment(id, new RecordPaymentRequest(balanceDue, PaymentMethod.CASH, "Marked paid manually"));
    }

    // Records a payment against a sale's outstanding balance — the manual
    // "type in what came in" counterpart to the gateway flows above, and the
    // only path that can produce PARTIALLY_PAID.
    @Transactional
    public SaleResponse recordPayment(UUID id, RecordPaymentRequest req) {
        Sale sale = getOwned(id);
        BigDecimal balanceDue = sale.getTotalAmount().subtract(sale.getAmountPaid()).max(BigDecimal.ZERO);
        if (balanceDue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This sale is already fully paid.");
        }
        if (req.amount().compareTo(balanceDue) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "That's more than the outstanding balance of GH₵" + balanceDue + ".");
        }

        BigDecimal newAmountPaid = sale.getAmountPaid().add(req.amount());
        sale.setAmountPaid(newAmountPaid);
        sale.setPaymentStatus(derivePaymentStatus(newAmountPaid, sale.getTotalAmount()));
        sale = saleRepository.save(sale);

        activityLogService.log(
                "Recorded a GH₵" + req.amount() + " payment on sale #" + sale.getSaleNumber(), "SALE", sale.getId());

        paymentTransactionService.record(
                sale.getBusinessId(), PaymentTransaction.Direction.INCOMING, PaymentTransaction.SourceType.SALE,
                sale.getId(), "MANUAL", req.method().name(), req.amount(), "SUCCESS",
                null, sale.getCustomerId(), null, req.note(), TenantContext.getUserId()
        );

        return toResponseWithItems(sale);
    }

    // Full refund only — no partial-refund reconciliation. amountPaid is left
    // as-is (a record of what was actually collected); REFUNDED overrides
    // paymentStatus so the ledger's OUTGOING row is the source of truth for
    // the refund event itself.
    @Transactional
    public SaleResponse refund(UUID id, RefundRequest req) {
        Sale sale = getOwned(id);
        if (sale.getAmountPaid().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nothing has been collected on this sale to refund.");
        }

        BigDecimal refundedAmount = sale.getAmountPaid();
        sale.setPaymentStatus("REFUNDED");
        sale = saleRepository.save(sale);

        activityLogService.log("Refunded GH₵" + refundedAmount + " on sale #" + sale.getSaleNumber(), "SALE", sale.getId());

        paymentTransactionService.record(
                sale.getBusinessId(), PaymentTransaction.Direction.OUTGOING, PaymentTransaction.SourceType.SALE,
                sale.getId(), "MANUAL", null, refundedAmount, "SUCCESS",
                null, sale.getCustomerId(), null, req.note() != null && !req.note().isBlank() ? req.note() : "Refunded",
                TenantContext.getUserId()
        );

        return toResponseWithItems(sale);
    }

    private static String derivePaymentStatus(BigDecimal amountPaid, BigDecimal total) {
        if (amountPaid.compareTo(BigDecimal.ZERO) <= 0) return "UNPAID";
        if (amountPaid.compareTo(total) >= 0) return "PAID";
        return "PARTIALLY_PAID";
    }

    private Sale getOwned(UUID id) {
        return saleRepository.findByIdAndBusinessId(id, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sale not found."));
    }

    // Same placeholder-email fallback as ServiceOrderService — Paystack's
    // charge/checkout API requires an email even for a walk-in paying by
    // phone number alone.
    private String resolveCustomerEmail(Customer customer, UUID businessId, UUID saleId) {
        if (customer != null && customer.getEmail() != null && !customer.getEmail().isBlank()) {
            return customer.getEmail();
        }
        Business business = businessRepository.findById(businessId).orElse(null);
        String slug = business != null && business.getSlug() != null && !business.getSlug().isBlank()
                ? business.getSlug()
                : businessId.toString().substring(0, 8);
        return "sale-" + saleId.toString().substring(0, 8) + "@" + slug + ".ratelsystems.tech";
    }

    private SaleResponse toResponseWithItems(Sale sale) {
        List<SaleItemResponse> items = saleItemRepository.findAllBySaleId(sale.getId()).stream()
                .map(SaleItemResponse::from)
                .toList();

        String cashierName = sale.getCashierId() != null
                ? userRepository.findById(sale.getCashierId()).map(User::getFullName).orElse("Unknown")
                : "Unknown";

        String customerName = sale.getCustomerId() != null
                ? customerService.getNameOrNull(sale.getCustomerId())
                : null;

        return SaleResponse.from(sale, customerName, cashierName, items);
    }
}
