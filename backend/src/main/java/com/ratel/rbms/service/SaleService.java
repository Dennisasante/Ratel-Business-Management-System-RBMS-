package com.ratel.rbms.service;

import com.ratel.rbms.dto.SaleItemRequest;
import com.ratel.rbms.dto.SaleItemResponse;
import com.ratel.rbms.dto.SaleRequest;
import com.ratel.rbms.dto.SaleResponse;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.Product;
import com.ratel.rbms.entity.Sale;
import com.ratel.rbms.entity.SaleItem;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.MovementType;
import com.ratel.rbms.dto.StockAdjustmentRequest;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.SaleItemRepository;
import com.ratel.rbms.repository.SaleRepository;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class SaleService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final UserRepository userRepository;
    private final ProductService productService;
    private final CustomerService customerService;
    private final ActivityLogService activityLogService;

    public SaleService(
            SaleRepository saleRepository,
            SaleItemRepository saleItemRepository,
            UserRepository userRepository,
            ProductService productService,
            CustomerService customerService,
            ActivityLogService activityLogService
    ) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.userRepository = userRepository;
        this.productService = productService;
        this.customerService = customerService;
        this.activityLogService = activityLogService;
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

        Sale sale = Sale.builder()
                .businessId(businessId)
                .customerId(req.customerId())
                .cashierId(cashierId)
                .paymentMethod(req.paymentMethod())
                .totalAmount(BigDecimal.ZERO)
                .build();
        sale = saleRepository.save(sale); // flush needed so sale_number + id are available below
        saleRepository.flush();

        BigDecimal runningTotal = BigDecimal.ZERO;
        List<SaleItemResponse> itemResponses = new java.util.ArrayList<>();

        for (SaleItemRequest itemReq : req.items()) {
            Product product = productService.getOwned(itemReq.productId());

            // Deducting stock through ProductService keeps this sale's stock
            // change in the same stock_movements audit trail as manual adjustments,
            // tagged with the sale number so it's traceable back to this transaction.
            productService.adjustStock(
                    product.getId(),
                    new StockAdjustmentRequest(MovementType.REMOVE, itemReq.quantity(), "Sale #" + sale.getSaleNumber())
            );

            BigDecimal unitPrice = product.getSellingPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(itemReq.quantity()));
            BigDecimal discount = itemReq.discountAmount() != null ? itemReq.discountAmount() : BigDecimal.ZERO;
            if (discount.compareTo(lineTotal) > 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Discount on \"" + product.getName() + "\" can't exceed its line total.");
            }
            BigDecimal subtotal = lineTotal.subtract(discount);
            runningTotal = runningTotal.add(subtotal);

            SaleItem saleItem = SaleItem.builder()
                    .businessId(businessId)
                    .saleId(sale.getId())
                    .productId(product.getId())
                    .productName(product.getName())
                    .unitPrice(unitPrice)
                    .quantity(itemReq.quantity())
                    .discountAmount(discount)
                    .subtotal(subtotal)
                    .build();
            saleItem = saleItemRepository.save(saleItem);
            itemResponses.add(SaleItemResponse.from(saleItem));
        }

        sale.setTotalAmount(runningTotal);

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
        Sale sale = saleRepository.findByIdAndBusinessId(id, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sale not found."));
        return toResponseWithItems(sale);
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
