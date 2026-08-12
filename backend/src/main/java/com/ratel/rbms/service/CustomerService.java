package com.ratel.rbms.service;

import com.ratel.rbms.dto.CustomerRequest;
import com.ratel.rbms.dto.CustomerResponse;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.Sale;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.CustomerRepository;
import com.ratel.rbms.repository.SaleRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final ActivityLogService activityLogService;

    public CustomerService(
            CustomerRepository customerRepository,
            SaleRepository saleRepository,
            ActivityLogService activityLogService
    ) {
        this.customerRepository = customerRepository;
        this.saleRepository = saleRepository;
        this.activityLogService = activityLogService;
    }

    public List<CustomerResponse> listAll(String search) {
        UUID businessId = TenantContext.getBusinessId();
        List<Customer> customers = search == null || search.isBlank()
                ? customerRepository.findAllByBusinessIdOrderByFullNameAsc(businessId)
                : customerRepository.search(businessId, search);
        return customers.stream().map(c -> toResponse(c, businessId)).toList();
    }

    public CustomerResponse get(UUID id) {
        UUID businessId = TenantContext.getBusinessId();
        Customer customer = getOwned(id);
        return toResponse(customer, businessId);
    }

    public Customer getOwned(UUID id) {
        return customerRepository.findByIdAndBusinessId(id, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer not found."));
    }

    // Used when rendering a sale's customer name: the customer_id on a Sale can end up
    // null (ON DELETE SET NULL) if the customer record is later removed, so this tolerates
    // a missing customer instead of throwing.
    public String getNameOrNull(UUID id) {
        if (id == null) return null;
        return customerRepository.findByIdAndBusinessId(id, TenantContext.getBusinessId())
                .map(Customer::getFullName)
                .orElse(null);
    }

    // Same tolerance as getNameOrNull, but returns the full record — used where the
    // caller also needs the customer's email (e.g. the service order "ready" notification).
    public Customer getOrNull(UUID id) {
        if (id == null) return null;
        return customerRepository.findByIdAndBusinessId(id, TenantContext.getBusinessId()).orElse(null);
    }

    public CustomerResponse create(CustomerRequest req) {
        Customer customer = Customer.builder()
                .businessId(TenantContext.getBusinessId())
                .fullName(req.fullName())
                .phone(req.phone())
                .email(req.email())
                .notes(req.notes())
                .build();
        customer = customerRepository.save(customer);
        activityLogService.log("Added customer \"" + customer.getFullName() + "\"", "CUSTOMER", customer.getId());
        return toResponse(customer, customer.getBusinessId());
    }

    private CustomerResponse toResponse(Customer customer, UUID businessId) {
        List<Sale> sales = saleRepository.findAllByBusinessIdAndCustomerId(businessId, customer.getId());
        BigDecimal totalSpent = sales.stream()
                .map(Sale::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return CustomerResponse.from(customer, totalSpent, sales.size());
    }
}
