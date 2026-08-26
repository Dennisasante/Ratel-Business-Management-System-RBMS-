package com.ratel.rbms.service;

import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.BusinessRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The AI module must behave exactly like every other toggleable module —
 * off by default for a business that's never had it granted, and rejecting
 * with 403 (not silently allowing) until a Super Admin explicitly adds "AI"
 * to that business's enabledModules. @Transactional rolls every test back,
 * so nothing here touches real data permanently.
 */
@SpringBootTest
@Transactional
class ModuleAccessServiceTest {

    @Autowired
    private ModuleAccessService moduleAccessService;

    @Autowired
    private BusinessRepository businessRepository;

    @Test
    void aiModuleIsNotEnabledByDefaultForANewBusiness() {
        Business business = businessRepository.save(newBusinessWithoutAi());

        ApiException ex = assertThrows(ApiException.class,
                () -> moduleAccessService.requireModule(business.getId(), "AI"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals(false, moduleAccessService.hasModule(business.getId(), "AI"));
    }

    @Test
    void aiModuleWorksOnceExplicitlyGranted() {
        Business business = newBusinessWithoutAi();
        business.setEnabledModules(List.of("INVENTORY", "SALES", "CUSTOMERS", "EXPENSES", "AI"));
        business = businessRepository.save(business);

        // Must not throw.
        moduleAccessService.requireModule(business.getId(), "AI");
        assertEquals(true, moduleAccessService.hasModule(business.getId(), "AI"));
    }

    @Test
    void aGrantOnOneBusinessNeverLeaksToAnother() {
        Business withAi = newBusinessWithoutAi();
        withAi.setEnabledModules(List.of("INVENTORY", "SALES", "CUSTOMERS", "EXPENSES", "AI"));
        withAi = businessRepository.save(withAi);

        Business withoutAi = businessRepository.save(newBusinessWithoutAi());

        moduleAccessService.requireModule(withAi.getId(), "AI");
        assertThrows(ApiException.class, () -> moduleAccessService.requireModule(withoutAi.getId(), "AI"));
    }

    private Business newBusinessWithoutAi() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return Business.builder()
                .name("Test Business " + unique)
                .slug("test-business-" + unique)
                .industry(Industry.OTHER)
                .currency("GHS")
                .build();
    }
}
