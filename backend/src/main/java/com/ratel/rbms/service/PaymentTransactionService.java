package com.ratel.rbms.service;

import com.ratel.rbms.dto.PaymentTransactionResponse;
import com.ratel.rbms.entity.Booking;
import com.ratel.rbms.entity.PaymentTransaction;
import com.ratel.rbms.entity.PurchaseOrder;
import com.ratel.rbms.entity.Sale;
import com.ratel.rbms.entity.ServiceOrder;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.repository.BookingRepository;
import com.ratel.rbms.repository.PaymentTransactionRepository;
import com.ratel.rbms.repository.PurchaseOrderRepository;
import com.ratel.rbms.repository.SaleRepository;
import com.ratel.rbms.repository.ServiceOrderRepository;
import com.ratel.rbms.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// The write side (record()) is deliberately dumb — every payment flow (Booking,
// ServiceOrder, Sale, PurchaseOrder) calls it from its own success/mark-paid path,
// and it never initiates a charge itself, so it can't get out of sync with the
// actual gateway result. The read side enriches for display (customer name,
// "Service Order #38" instead of a raw UUID) — that's a reporting concern, kept
// separate from the write path's simplicity.
@Service
public class PaymentTransactionService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final CustomerService customerService;
    private final UserRepository userRepository;
    private final ServiceOrderRepository serviceOrderRepository;
    private final SaleRepository saleRepository;
    private final BookingRepository bookingRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    public PaymentTransactionService(
            PaymentTransactionRepository paymentTransactionRepository,
            CustomerService customerService,
            UserRepository userRepository,
            ServiceOrderRepository serviceOrderRepository,
            SaleRepository saleRepository,
            BookingRepository bookingRepository,
            PurchaseOrderRepository purchaseOrderRepository
    ) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.customerService = customerService;
        this.userRepository = userRepository;
        this.serviceOrderRepository = serviceOrderRepository;
        this.saleRepository = saleRepository;
        this.bookingRepository = bookingRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    public void record(
            UUID businessId,
            PaymentTransaction.Direction direction,
            PaymentTransaction.SourceType sourceType,
            UUID sourceId,
            String gateway,
            String method,
            BigDecimal amount,
            String status,
            String gatewayReference,
            UUID customerId,
            String customerPhone,
            String note,
            UUID createdBy
    ) {
        paymentTransactionRepository.save(PaymentTransaction.builder()
                .businessId(businessId)
                .direction(direction)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .gateway(gateway)
                .method(method)
                .amount(amount)
                .status(status)
                .gatewayReference(gatewayReference)
                .customerId(customerId)
                .customerPhone(customerPhone)
                .note(note)
                .createdBy(createdBy)
                .paidAt("SUCCESS".equals(status) ? Instant.now() : null)
                .build());
    }

    public List<PaymentTransactionResponse> search(UUID businessId, PaymentTransaction.Direction direction, String gateway, Instant from, Instant to) {
        return paymentTransactionRepository.search(businessId, direction, gateway, from, to).stream()
                .map(this::toResponse)
                .toList();
    }

    private PaymentTransactionResponse toResponse(PaymentTransaction t) {
        String customerName = t.getCustomerId() != null ? customerService.getNameOrNull(t.getCustomerId()) : null;
        String createdByName = t.getCreatedBy() != null
                ? userRepository.findById(t.getCreatedBy()).map(User::getFullName).orElse(null)
                : null;
        return new PaymentTransactionResponse(
                t.getId(), t.getDirection().name(), t.getSourceType().name(), t.getSourceId(),
                sourceLabel(t.getSourceType(), t.getSourceId()),
                t.getGateway(), t.getMethod(), t.getAmount(), t.getCurrency(), t.getStatus(), t.getGatewayReference(),
                t.getCustomerId(), customerName, t.getCustomerPhone(), t.getNote(), createdByName,
                t.getPaidAt(), t.getCreatedAt()
        );
    }

    private String sourceLabel(PaymentTransaction.SourceType sourceType, UUID sourceId) {
        if (sourceId == null) return null;
        return switch (sourceType) {
            case SERVICE_ORDER -> serviceOrderRepository.findById(sourceId)
                    .map(ServiceOrder::getOrderNumber).map(n -> "Service Order #" + n).orElse(null);
            case SALE -> saleRepository.findById(sourceId)
                    .map(Sale::getSaleNumber).map(n -> "Sale #" + n).orElse(null);
            case BOOKING -> bookingRepository.findById(sourceId)
                    .map(Booking::getBookingNumber).map(n -> "Booking #" + n).orElse(null);
            case PURCHASE_ORDER -> purchaseOrderRepository.findById(sourceId)
                    .map(PurchaseOrder::getPoNumber).map(n -> "Purchase Order #" + n).orElse(null);
        };
    }
}
