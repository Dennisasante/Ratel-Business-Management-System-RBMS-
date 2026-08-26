package com.ratel.rbms.service;

import com.ratel.rbms.dto.CustomerRequest;
import com.ratel.rbms.dto.CustomerResponse;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.Sale;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.CustomerRepository;
import com.ratel.rbms.repository.SaleRepository;
import com.ratel.rbms.tenant.TenantContext;
import com.ratel.rbms.util.PhoneUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
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
        return getNameOrNull(id, TenantContext.getBusinessId());
    }

    // Same as above but takes businessId explicitly — for callers (like Super Admin's
    // per-business payment transactions view) that resolve a specific business's data
    // without TenantContext being populated, since platform-admin requests never carry
    // a tenant business id.
    public String getNameOrNull(UUID id, UUID businessId) {
        if (id == null) return null;
        return customerRepository.findByIdAndBusinessId(id, businessId)
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
        UUID businessId = TenantContext.getBusinessId();
        if (req.phone() != null && !req.phone().isBlank()) {
            customerRepository.findFirstByBusinessIdAndPhoneNormalized(businessId, PhoneUtils.normalize(req.phone())).ifPresent(existing -> {
                throw new ApiException(HttpStatus.CONFLICT,
                        "A customer named \"" + existing.getFullName() + "\" already uses this phone number.");
            });
        }
        Customer customer = Customer.builder()
                .businessId(businessId)
                .fullName(req.fullName())
                .phone(req.phone())
                .email(req.email())
                .notes(req.notes())
                .source(req.source())
                .build();
        customer = customerRepository.save(customer);
        activityLogService.log("Added customer \"" + customer.getFullName() + "\"", "CUSTOMER", customer.getId());
        return toResponse(customer, customer.getBusinessId());
    }

    // Added for Tallia AI's findCustomer tool — read-only lookup, never
    // creates. Returns empty rather than throwing when nothing matches or
    // the phone doesn't parse, since "not found" is an ordinary answer here,
    // not an error.
    public Optional<CustomerResponse> findByPhone(String phone) {
        if (phone == null || phone.isBlank() || !PhoneUtils.isValid(phone)) {
            return Optional.empty();
        }
        UUID businessId = TenantContext.getBusinessId();
        return customerRepository.findFirstByBusinessIdAndPhoneNormalized(businessId, PhoneUtils.normalize(phone))
                .map(c -> toResponse(c, businessId));
    }

    // Added for Tallia AI's findCustomer/createCustomer tools — the AI layer
    // must never be able to spawn a duplicate Customer for a phone number
    // that already resolves to one, so this is find-or-create rather than
    // create()'s own create-or-409. Same phone validation and normalization
    // as every other resolution path (Booking/CustomWigRequest/EcommerceOrder).
    public CustomerResponse findOrCreate(String fullName, String phone, String email, String source) {
        UUID businessId = TenantContext.getBusinessId();
        if (phone == null || phone.isBlank() || !PhoneUtils.isValid(phone)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a valid phone number.");
        }
        Customer customer = customerRepository.findFirstByBusinessIdAndPhoneNormalized(businessId, PhoneUtils.normalize(phone))
                .orElseGet(() -> {
                    Customer created = customerRepository.save(Customer.builder()
                            .businessId(businessId)
                            .fullName(fullName == null || fullName.isBlank() ? "Customer" : fullName.trim())
                            .phone(phone)
                            .email(email)
                            .source(source)
                            .build());
                    activityLogService.log("Added customer \"" + created.getFullName() + "\"", "CUSTOMER", created.getId());
                    return created;
                });
        return toResponse(customer, businessId);
    }

    private CustomerResponse toResponse(Customer customer, UUID businessId) {
        List<Sale> sales = saleRepository.findAllByBusinessIdAndCustomerId(businessId, customer.getId());
        BigDecimal totalSpent = sales.stream()
                .map(Sale::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return CustomerResponse.from(customer, totalSpent, sales.size());
    }
}
