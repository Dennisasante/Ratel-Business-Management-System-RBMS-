package com.ratel.rbms.service;

import com.ratel.rbms.dto.CustomerRequest;
import com.ratel.rbms.dto.CustomerResponse;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.Customer;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.CustomerRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Customers can be corrected — in particular given a phone number — without ever colliding with another customer's. */
@SpringBootTest
class CustomerUpdateTest {

    @Autowired private CustomerService customerService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private JdbcTemplate jdbc;

    private UUID businessId;
    private UUID noPhoneId;
    private UUID withPhoneId;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        businessId = businessRepository.save(Business.builder()
                .name("Customer Update Test " + unique).slug("cust-upd-" + unique)
                .industry(Industry.OTHER).currency("GHS").build()).getId();
        noPhoneId = customerRepository.save(Customer.builder().businessId(businessId).fullName("No Phone").build()).getId();
        withPhoneId = customerRepository.save(Customer.builder().businessId(businessId).fullName("Has Phone").phone("0244112233").build()).getId();
        TenantContext.setBusinessId(businessId);
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbc.update("DELETE FROM activity_logs WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM customers WHERE business_id = ?", businessId);
        jdbc.update("DELETE FROM businesses WHERE id = ?", businessId);
    }

    @Test
    void aCustomerWithoutAPhoneCanBeGivenOne() {
        CustomerResponse updated = customerService.update(noPhoneId, new CustomerRequest("No Phone", "020 111 2222", null, null, null));

        assertEquals("020 111 2222", updated.phone());
        assertEquals("0201112222", customerRepository.findById(noPhoneId).orElseThrow().getPhoneNormalized(),
                "the normalised number used for duplicate detection is kept in step");
    }

    @Test
    void aCustomerCanKeepTheirOwnNumberWhileEditingOtherDetails() {
        CustomerResponse updated = customerService.update(withPhoneId, new CustomerRequest("Has Phone Renamed", "0244 112 233", "a@b.com", null, null));

        assertEquals("Has Phone Renamed", updated.fullName());
    }

    @Test
    void anotherCustomersNumberIsRefused() {
        ApiException e = assertThrows(ApiException.class,
                () -> customerService.update(noPhoneId, new CustomerRequest("No Phone", "+233244112233", null, null, null)));

        assertEquals(409, e.getStatus().value());
        assertNull(customerRepository.findById(noPhoneId).orElseThrow().getPhone(), "nothing was saved");
    }

    @Test
    void aBlankPhoneClearsIt() {
        CustomerResponse updated = customerService.update(withPhoneId, new CustomerRequest("Has Phone", "  ", null, null, null));

        assertNull(updated.phone());
    }

    @Test
    void aCustomerOfAnotherBusinessCannotBeEdited() {
        TenantContext.setBusinessId(UUID.randomUUID());

        assertThrows(ApiException.class, () -> customerService.update(withPhoneId, new CustomerRequest("Hijacked", null, null, null, null)));
        assertEquals("Has Phone", customerRepository.findById(withPhoneId).orElseThrow().getFullName());
    }
}
