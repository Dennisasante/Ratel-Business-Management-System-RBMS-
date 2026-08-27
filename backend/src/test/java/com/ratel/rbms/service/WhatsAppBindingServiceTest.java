package com.ratel.rbms.service;

import com.ratel.rbms.dto.WhatsAppBindingCreateRequest;
import com.ratel.rbms.dto.WhatsAppBindingResponse;
import com.ratel.rbms.dto.WhatsAppBindingUpdateRequest;
import com.ratel.rbms.dto.WhatsAppConnectionTestResponse;
import com.ratel.rbms.entity.AiChannelBinding;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.PlatformAdmin;
import com.ratel.rbms.entity.enums.AiChannel;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.AiChannelBindingRepository;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.PlatformAdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Super-Admin-configured WhatsApp binding management (spec §6/§7/§29/§30/§31).
 * Never returns the access token, never returns ciphertext, rejects a
 * second binding for the same Phone Number ID across businesses.
 */
@SpringBootTest
@Transactional
class WhatsAppBindingServiceTest {

    @Autowired
    private WhatsAppBindingService whatsAppBindingService;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private AiChannelBindingRepository aiChannelBindingRepository;
    @Autowired
    private PlatformAdminRepository platformAdminRepository;

    @MockBean
    private WhatsAppApiClient whatsAppApiClient;

    // platform_audit_logs.admin_id has a real FK to platform_admins — a
    // bare random UUID would violate it the moment PlatformAuditLogService
    // actually writes a row, so every test needs a genuine admin fixture.
    private UUID adminId;

    @BeforeEach
    void setUpAdmin() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        PlatformAdmin admin = platformAdminRepository.save(PlatformAdmin.builder()
                .email("wa-binding-admin-" + unique + "@example.com")
                .passwordHash("not-a-real-hash")
                .fullName("Test Super Admin")
                .build());
        adminId = admin.getId();
    }

    @Test
    void createReturnsSafeMetadataNeverTheAccessToken() {
        Business business = businessRepository.save(newBusiness());
        WhatsAppBindingCreateRequest req = new WhatsAppBindingCreateRequest(
                "waba-123", "phone-" + UUID.randomUUID(), "Front Desk", "super-secret-access-token", true);

        WhatsAppBindingResponse response = whatsAppBindingService.create(adminId, business.getId(), req);

        assertEquals(req.phoneNumberId(), response.phoneNumberId());
        assertEquals("Front Desk", response.displayName());
        assertTrue(response.configured());
        assertTrue(response.active());

        // Structural guarantee: nothing on this response type can carry the
        // token or raw ciphertext, whatever field names it might grow later.
        for (Field field : WhatsAppBindingResponse.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            assertFalse(name.contains("token") || name.contains("secret") || name.contains("credential"),
                    "WhatsAppBindingResponse must never expose a credential-shaped field: " + field.getName());
        }
    }

    @Test
    void creatingASecondBindingForTheSameBusinessIsRejected() {
        Business business = businessRepository.save(newBusiness());
        whatsAppBindingService.create(adminId, business.getId(),
                new WhatsAppBindingCreateRequest("waba-1", "phone-" + UUID.randomUUID(), "First", "token-1", true));

        ApiException ex = assertThrows(ApiException.class, () -> whatsAppBindingService.create(adminId, business.getId(),
                new WhatsAppBindingCreateRequest("waba-2", "phone-" + UUID.randomUUID(), "Second", "token-2", true)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void reusingAnotherBusinessesPhoneNumberIdIsRejectedByTheUniquenessConstraint() {
        Business businessA = businessRepository.save(newBusiness());
        Business businessB = businessRepository.save(newBusiness());
        String sharedPhoneNumberId = "shared-phone-" + UUID.randomUUID();

        whatsAppBindingService.create(adminId, businessA.getId(),
                new WhatsAppBindingCreateRequest("waba-a", sharedPhoneNumberId, "A", "token-a", true));

        ApiException ex = assertThrows(ApiException.class, () -> whatsAppBindingService.create(adminId, businessB.getId(),
                new WhatsAppBindingCreateRequest("waba-b", sharedPhoneNumberId, "B", "token-b", true)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void updateRotatesTheAccessTokenOnlyWhenANewOneIsSupplied() {
        Business business = businessRepository.save(newBusiness());
        whatsAppBindingService.create(adminId, business.getId(),
                new WhatsAppBindingCreateRequest("waba-1", "phone-" + UUID.randomUUID(), "Original", "original-token", true));

        // displayName-only update — accessToken omitted (null) must leave the stored token untouched.
        WhatsAppBindingResponse updated = whatsAppBindingService.update(adminId, business.getId(),
                new WhatsAppBindingUpdateRequest(null, null, "Updated Name", null, null));
        assertEquals("Updated Name", updated.displayName());
        assertTrue(updated.configured());

        AiChannelBinding stored = aiChannelBindingRepository.findByBusinessIdAndChannel(business.getId(), AiChannel.WHATSAPP).orElseThrow();
        assertEquals("original-token", stored.getCredentialsEncrypted());
    }

    @Test
    void activateAndDeactivateToggleTheBindingState() {
        Business business = businessRepository.save(newBusiness());
        whatsAppBindingService.create(adminId, business.getId(),
                new WhatsAppBindingCreateRequest("waba-1", "phone-" + UUID.randomUUID(), "Line", "token", true));

        WhatsAppBindingResponse deactivated = whatsAppBindingService.setActive(adminId, business.getId(), false);
        assertFalse(deactivated.active());

        WhatsAppBindingResponse reactivated = whatsAppBindingService.setActive(adminId, business.getId(), true);
        assertTrue(reactivated.active());
    }

    @Test
    void getWithNoBindingConfiguredReturns404NotAnEmptyRecord() {
        Business business = businessRepository.save(newBusiness());
        ApiException ex = assertThrows(ApiException.class, () -> whatsAppBindingService.get(business.getId()));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void testConnectionNeverSendsACustomerMessageAndReturnsSafeMetadata() {
        Business business = businessRepository.save(newBusiness());
        whatsAppBindingService.create(adminId, business.getId(),
                new WhatsAppBindingCreateRequest("waba-1", "phone-" + UUID.randomUUID(), "Line", "token", true));

        when(whatsAppApiClient.validatePhoneNumber(anyString(), anyString()))
                .thenReturn(new WhatsAppApiClient.PhoneNumberMetadata(true, "+233 24 000 0000", "Test Resort", null));

        WhatsAppConnectionTestResponse result = whatsAppBindingService.testConnection(business.getId());
        assertTrue(result.success());
        assertEquals("Test Resort", result.verifiedName());

        // Only ever validates metadata — never sends a text message as part of testing.
        org.mockito.Mockito.verify(whatsAppApiClient, org.mockito.Mockito.never())
                .sendTextMessage(anyString(), anyString(), anyString(), anyString());
    }

    private Business newBusiness() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return Business.builder()
                .name("WhatsApp Binding Test Business " + unique)
                .slug("wa-binding-test-" + unique)
                .industry(Industry.OTHER)
                .currency("GHS")
                .build();
    }
}
