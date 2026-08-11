package com.ratel.rbms.service;

import com.ratel.rbms.dto.SaleItemRequest;
import com.ratel.rbms.dto.SaleItemResponse;
import com.ratel.rbms.dto.SaleRequest;
import com.ratel.rbms.dto.SaleResponse;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.Product;
import com.ratel.rbms.entity.Sale;
import com.ratel.rbms.entity.SaleItem;
import com.ratel.rbms.entity.ServiceCatalogItem;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.MovementType;
import com.ratel.rbms.entity.enums.SaleItemType;
import com.ratel.rbms.dto.StockAdjustmentRequest;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.SaleItemRepository;
import com.ratel.rbms.repository.SaleRepository;
import com.ratel.rbms.repository.ServiceCatalogItemRepository;
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
    private final ServiceCatalogItemRepository serviceCatalogItemRepository;
    private final CustomerService customerService;
    private final ActivityLogService activityLogService;

    public SaleService(
            SaleRepository saleRepository,
            SaleItemRepository saleItemRepository,
            UserRepository userRepository,
            ProductService productService,
            ServiceCatalogItemRepository serviceCatalogItemRepository,
            CustomerService customerService,
            ActivityLogService activityLogService
    ) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.userRepository = userRepository;
        this.productService = productService;
        this.serviceCatalogItemRepository = serviceCatalogItemRepository;
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
            BigDecimal discount = itemReq.discountAmount() != null ? itemReq.discountAmount() : BigDecimal.ZERO;
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
