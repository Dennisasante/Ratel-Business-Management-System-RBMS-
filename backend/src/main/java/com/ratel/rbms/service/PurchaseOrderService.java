package com.ratel.rbms.service;

import com.ratel.rbms.dto.PurchaseOrderItemRequest;
import com.ratel.rbms.dto.PurchaseOrderItemResponse;
import com.ratel.rbms.dto.PurchaseOrderRequest;
import com.ratel.rbms.dto.PurchaseOrderResponse;
import com.ratel.rbms.dto.StockAdjustmentRequest;
import com.ratel.rbms.entity.PaymentTransaction;
import com.ratel.rbms.entity.Product;
import com.ratel.rbms.entity.PurchaseOrder;
import com.ratel.rbms.entity.PurchaseOrderItem;
import com.ratel.rbms.entity.Supplier;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.MovementType;
import com.ratel.rbms.entity.enums.PurchaseOrderStatus;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.PurchaseOrderItemRepository;
import com.ratel.rbms.repository.PurchaseOrderRepository;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final UserRepository userRepository;
    private final ProductService productService;
    private final SupplierService supplierService;
    private final ActivityLogService activityLogService;
    private final PaymentTransactionService paymentTransactionService;
    private final ModuleAccessService moduleAccessService;

    public PurchaseOrderService(
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderItemRepository purchaseOrderItemRepository,
            UserRepository userRepository,
            ProductService productService,
            SupplierService supplierService,
            ActivityLogService activityLogService,
            PaymentTransactionService paymentTransactionService,
            ModuleAccessService moduleAccessService
    ) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
        this.userRepository = userRepository;
        this.productService = productService;
        this.supplierService = supplierService;
        this.activityLogService = activityLogService;
        this.paymentTransactionService = paymentTransactionService;
        this.moduleAccessService = moduleAccessService;
    }

    @Transactional
    public PurchaseOrderResponse create(PurchaseOrderRequest req) {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, "SUPPLIERS_AND_PURCHASING");
        UUID userId = TenantContext.getUserId();

        Supplier supplier = null;
        if (req.supplierId() != null) {
            supplier = supplierService.getOwned(req.supplierId());
        }

        PurchaseOrder po = PurchaseOrder.builder()
                .businessId(businessId)
                .supplierId(req.supplierId())
                .status(PurchaseOrderStatus.PENDING)
                .createdBy(userId)
                .totalAmount(BigDecimal.ZERO)
                .build();
        po = purchaseOrderRepository.save(po);
        purchaseOrderRepository.flush(); // so po_number is readable below

        BigDecimal runningTotal = BigDecimal.ZERO;
        List<PurchaseOrderItemResponse> itemResponses = new ArrayList<>();

        for (PurchaseOrderItemRequest itemReq : req.items()) {
            Product product = productService.getOwned(itemReq.productId());
            BigDecimal subtotal = itemReq.unitCost().multiply(BigDecimal.valueOf(itemReq.quantity()));
            runningTotal = runningTotal.add(subtotal);

            PurchaseOrderItem item = PurchaseOrderItem.builder()
                    .businessId(businessId)
                    .purchaseOrderId(po.getId())
                    .productId(product.getId())
                    .productName(product.getName())
                    .unitCost(itemReq.unitCost())
                    .quantity(itemReq.quantity())
                    .subtotal(subtotal)
                    .build();
            item = purchaseOrderItemRepository.save(item);
            itemResponses.add(PurchaseOrderItemResponse.from(item));
        }

        po.setTotalAmount(runningTotal);
        po = purchaseOrderRepository.save(po);

        activityLogService.log("Created purchase order #" + po.getPoNumber() + " for GH₵" + runningTotal, "PURCHASE_ORDER", po.getId());

        return toResponse(po, supplier, itemResponses);
    }

    public List<PurchaseOrderResponse> listAll() {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, "SUPPLIERS_AND_PURCHASING");
        return purchaseOrderRepository.findAllByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .map(this::toResponseWithItems)
                .toList();
    }

    public PurchaseOrderResponse get(UUID id) {
        return toResponseWithItems(getOwned(id));
    }

    @Transactional
    public PurchaseOrderResponse receive(UUID id) {
        PurchaseOrder po = getOwned(id);
        if (po.getStatus() != PurchaseOrderStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This purchase order has already been " + po.getStatus().name().toLowerCase() + ".");
        }

        List<PurchaseOrderItem> items = purchaseOrderItemRepository.findAllByPurchaseOrderId(po.getId());
        for (PurchaseOrderItem item : items) {
            if (item.getProductId() == null) continue; // product was deleted since the PO was created — nothing to restock
            productService.adjustStock(
                    item.getProductId(),
                    new StockAdjustmentRequest(MovementType.ADD, item.getQuantity(), "PO #" + po.getPoNumber())
            );
        }

        po.setStatus(PurchaseOrderStatus.RECEIVED);
        po.setReceivedAt(Instant.now());
        po = purchaseOrderRepository.save(po);

        activityLogService.log("Received purchase order #" + po.getPoNumber() + " — stock updated", "PURCHASE_ORDER", po.getId());

        return toResponseWithItems(po);
    }

    public PurchaseOrderResponse cancel(UUID id) {
        PurchaseOrder po = getOwned(id);
        if (po.getStatus() != PurchaseOrderStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This purchase order has already been " + po.getStatus().name().toLowerCase() + ".");
        }

        po.setStatus(PurchaseOrderStatus.CANCELLED);
        po = purchaseOrderRepository.save(po);

        activityLogService.log("Cancelled purchase order #" + po.getPoNumber(), "PURCHASE_ORDER", po.getId());

        return toResponseWithItems(po);
    }

    // Manual only — no gateway sends money out to a supplier, this just records
    // that the business has settled the bill.
    @Transactional
    public PurchaseOrderResponse markPaid(UUID id) {
        PurchaseOrder po = getOwned(id);
        po.setPaymentStatus("PAID");
        po = purchaseOrderRepository.save(po);

        activityLogService.log("Marked purchase order #" + po.getPoNumber() + " as paid", "PURCHASE_ORDER", po.getId());

        paymentTransactionService.record(
                po.getBusinessId(), PaymentTransaction.Direction.OUTGOING, PaymentTransaction.SourceType.PURCHASE_ORDER,
                po.getId(), "MANUAL", null, po.getTotalAmount(), "SUCCESS",
                null, null, null, "Marked paid manually", TenantContext.getUserId()
        );

        return toResponseWithItems(po);
    }

    private PurchaseOrder getOwned(UUID id) {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, "SUPPLIERS_AND_PURCHASING");
        return purchaseOrderRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Purchase order not found."));
    }

    private PurchaseOrderResponse toResponseWithItems(PurchaseOrder po) {
        List<PurchaseOrderItemResponse> items = purchaseOrderItemRepository.findAllByPurchaseOrderId(po.getId()).stream()
                .map(PurchaseOrderItemResponse::from)
                .toList();
        Supplier supplier = po.getSupplierId() != null ? findSupplierOrNull(po.getSupplierId()) : null;
        return toResponse(po, supplier, items);
    }

    private Supplier findSupplierOrNull(UUID supplierId) {
        try {
            return supplierService.getOwned(supplierId);
        } catch (ApiException e) {
            return null; // supplier may have been removed since the PO was created
        }
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder po, Supplier supplier, List<PurchaseOrderItemResponse> items) {
        String createdByName = po.getCreatedBy() != null
                ? userRepository.findById(po.getCreatedBy()).map(User::getFullName).orElse("Unknown")
                : "Unknown";
        return PurchaseOrderResponse.from(po, supplier != null ? supplier.getName() : null, createdByName, items);
    }
}
