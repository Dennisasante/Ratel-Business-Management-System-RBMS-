package com.ratel.rbms.service;

import com.ratel.rbms.dto.AiChannelConnectionResponse;
import com.ratel.rbms.dto.WhatsAppBindingResponse;
import com.ratel.rbms.dto.WhatsAppConnectionTestResponse;
import com.ratel.rbms.entity.AiChannelBinding;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.PlatformAdmin;
import com.ratel.rbms.entity.enums.AiChannel;
import com.ratel.rbms.entity.enums.ChannelConnectionMethod;
import com.ratel.rbms.entity.enums.ChannelConnectionState;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.AiChannelBindingRepository;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.PlatformAdminRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Talia Unified Platform, Phase 5D Stage 0 — business-facing (OWNER/MANAGER) channel connection
 * management, tenant-scoped via {@code TenantContext} exactly like every other business-facing
 * service in this codebase (real PostgreSQL throughout, {@code AiChannelBinding} unmodified as an
 * entity shape, only the four Stage 0 columns are new). {@code @PreAuthorize} role gating itself
 * lives on {@code AiChannelController} — this test suite, like every other service-level test in
 * this codebase, exercises the service directly; no existing test anywhere in this codebase
 * simulates Spring Security's method-security layer, so this file does not invent that pattern
 * either (see the Stage 0 implementation report for this explicit scoping note).
 */
@SpringBootTest
@Transactional
class AiChannelConnectionServiceTest {

    @Autowired private AiChannelConnectionService aiChannelConnectionService;
    @Autowired private WhatsAppBindingService whatsAppBindingService; // the pre-existing Super-Admin surface, unmodified in behavior
    @Autowired private BusinessRepository businessRepository;
    @Autowired private AiChannelBindingRepository aiChannelBindingRepository;
    @Autowired private PlatformAdminRepository platformAdminRepository;

    @MockBean private WhatsAppApiClient whatsAppApiClient;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    private UUID newBusinessWithAiTenant() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Business business = businessRepository.save(Business.builder()
                .name("Phase5D Stage0 Business " + unique)
                .slug("phase5d-stage0-" + unique)
                .industry(Industry.OTHER).currency("GHS")
                .enabledModules(java.util.List.of("AI"))
                .build());
        TenantContext.setBusinessId(business.getId());
        return business.getId();
    }

    // ==================== Connect (create) ====================

    @Test
    void connectWithNoExistingBindingCreatesOneAndRunsAnImmediateHealthCheck() {
        newBusinessWithAiTenant();
        when(whatsAppApiClient.validatePhoneNumber(anyString(), anyString()))
                .thenReturn(new WhatsAppApiClient.PhoneNumberMetadata(true, "+233 24 000 0000", "Serenity Beach Resort", null));

        AiChannelConnectionResponse response = aiChannelConnectionService.connect(AiChannel.WHATSAPP,
                new ConnectionParams(Map.of(
                        "phoneNumberId", "phone-" + UUID.randomUUID(),
                        "accessToken", "real-token",
                        "whatsappBusinessAccountId", "waba-1",
                        "displayName", "Front Desk"
                )));

        assertEquals("WHATSAPP", response.channel());
        assertEquals("CONNECTED", response.connectionState(), "a successful immediate health check must produce CONNECTED, not merely NOT_CONNECTED->something");
        assertEquals("MANUAL", response.connectionMethod());
        assertTrue(response.active());
        assertTrue(response.configured());
        assertNotNull(response.lastVerifiedAt());
        assertNull(response.lastFailureAt());
    }

    @Test
    void connectWithAFailingHealthCheckStillSavesTheBindingButSurfacesDegraded() {
        newBusinessWithAiTenant();
        when(whatsAppApiClient.validatePhoneNumber(anyString(), anyString()))
                .thenReturn(new WhatsAppApiClient.PhoneNumberMetadata(false, null, null, "Invalid OAuth access token."));

        AiChannelConnectionResponse response = aiChannelConnectionService.connect(AiChannel.WHATSAPP,
                new ConnectionParams(Map.of("phoneNumberId", "phone-" + UUID.randomUUID(), "accessToken", "bad-token")));

        assertEquals("DEGRADED", response.connectionState(), "credentials exist and channel is active, but the immediate health check failed");
        assertTrue(response.configured(), "a credential was still saved even though it doesn't currently work");
        assertNotNull(response.lastFailureAt());
        assertNull(response.lastVerifiedAt());
    }

    @Test
    void connectingWithoutAnAccessTokenWhenNoBindingExistsIsRejected() {
        newBusinessWithAiTenant();
        ApiException ex = assertThrows(ApiException.class, () -> aiChannelConnectionService.connect(AiChannel.WHATSAPP,
                new ConnectionParams(Map.of("phoneNumberId", "phone-" + UUID.randomUUID()))));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void reusingAnotherBusinessesPhoneNumberIdIsRejected() {
        UUID businessA = newBusinessWithAiTenant();
        String sharedPhoneNumberId = "shared-" + UUID.randomUUID();
        when(whatsAppApiClient.validatePhoneNumber(anyString(), anyString()))
                .thenReturn(new WhatsAppApiClient.PhoneNumberMetadata(true, null, null, null));
        aiChannelConnectionService.connect(AiChannel.WHATSAPP,
                new ConnectionParams(Map.of("phoneNumberId", sharedPhoneNumberId, "accessToken", "token-a")));

        newBusinessWithAiTenant(); // switches TenantContext to a fresh business B
        ApiException ex = assertThrows(ApiException.class, () -> aiChannelConnectionService.connect(AiChannel.WHATSAPP,
                new ConnectionParams(Map.of("phoneNumberId", sharedPhoneNumberId, "accessToken", "token-b"))));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    // ==================== Reconnect / update credentials ====================

    @Test
    void reconnectingWithoutSupplyingANewAccessTokenLeavesTheStoredTokenUntouched() {
        UUID businessId = newBusinessWithAiTenant();
        when(whatsAppApiClient.validatePhoneNumber(anyString(), anyString()))
                .thenReturn(new WhatsAppApiClient.PhoneNumberMetadata(true, null, null, null));
        String phoneNumberId = "phone-" + UUID.randomUUID();
        aiChannelConnectionService.connect(AiChannel.WHATSAPP,
                new ConnectionParams(Map.of("phoneNumberId", phoneNumberId, "accessToken", "original-token")));

        // Reconnect: same phoneNumberId, no accessToken supplied, just a display-name change.
        aiChannelConnectionService.connect(AiChannel.WHATSAPP,
                new ConnectionParams(Map.of("phoneNumberId", phoneNumberId, "displayName", "Updated Name")));

        AiChannelBinding stored = aiChannelBindingRepository.findByBusinessIdAndChannel(businessId, AiChannel.WHATSAPP).orElseThrow();
        assertEquals("original-token", stored.getCredentialsEncrypted());
        assertEquals("Updated Name", stored.getDisplayName());
    }

    // ==================== Test connection ====================

    @Test
    void testRefreshesTheHealthSignalAndConnectionStateOnAnExistingBinding() {
        UUID businessId = newBusinessWithAiTenant();
        when(whatsAppApiClient.validatePhoneNumber(anyString(), anyString()))
                .thenReturn(new WhatsAppApiClient.PhoneNumberMetadata(true, null, null, null));
        aiChannelConnectionService.connect(AiChannel.WHATSAPP,
                new ConnectionParams(Map.of("phoneNumberId", "phone-" + UUID.randomUUID(), "accessToken", "token")));

        // Provider now reports the token has been revoked.
        when(whatsAppApiClient.validatePhoneNumber(anyString(), anyString()))
                .thenReturn(new WhatsAppApiClient.PhoneNumberMetadata(false, null, null, "Token revoked."));

        AiChannelConnectionResponse afterTest = aiChannelConnectionService.test(AiChannel.WHATSAPP);
        assertEquals("DEGRADED", afterTest.connectionState());
        assertNotNull(afterTest.lastFailureAt());

        AiChannelBinding stored = aiChannelBindingRepository.findByBusinessIdAndChannel(businessId, AiChannel.WHATSAPP).orElseThrow();
        assertEquals(ChannelConnectionState.DEGRADED, stored.getConnectionState());
    }

    @Test
    void testWithNoBindingConfiguredReturns404() {
        newBusinessWithAiTenant();
        ApiException ex = assertThrows(ApiException.class, () -> aiChannelConnectionService.test(AiChannel.WHATSAPP));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    // ==================== Activate / deactivate (= disconnect) ====================

    @Test
    void deactivatingProducesDisconnectedRegardlessOfHealthSignal() {
        UUID businessId = newBusinessWithAiTenant();
        when(whatsAppApiClient.validatePhoneNumber(anyString(), anyString()))
                .thenReturn(new WhatsAppApiClient.PhoneNumberMetadata(true, null, null, null));
        aiChannelConnectionService.connect(AiChannel.WHATSAPP,
                new ConnectionParams(Map.of("phoneNumberId", "phone-" + UUID.randomUUID(), "accessToken", "token")));

        AiChannelConnectionResponse deactivated = aiChannelConnectionService.setActive(AiChannel.WHATSAPP, false);
        assertEquals("DISCONNECTED", deactivated.connectionState());
        assertFalse(deactivated.active());

        AiChannelConnectionResponse reactivated = aiChannelConnectionService.setActive(AiChannel.WHATSAPP, true);
        assertEquals("CONNECTED", reactivated.connectionState(), "reactivating a still-healthy connection returns to CONNECTED, not NOT_CONNECTED");
    }

    // ==================== Detail read ====================

    @Test
    void getDetailWithNoBindingReturnsNotConnectedRatherThanThrowing() {
        newBusinessWithAiTenant();
        AiChannelConnectionResponse response = aiChannelConnectionService.getDetail(AiChannel.WHATSAPP);
        assertEquals("NOT_CONNECTED", response.connectionState());
        assertFalse(response.configured());
    }

    // ==================== Tenant isolation ====================

    @Test
    void oneBusinesssConnectionIsInvisibleToAnother() {
        UUID businessA = newBusinessWithAiTenant();
        when(whatsAppApiClient.validatePhoneNumber(anyString(), anyString()))
                .thenReturn(new WhatsAppApiClient.PhoneNumberMetadata(true, null, null, null));
        aiChannelConnectionService.connect(AiChannel.WHATSAPP,
                new ConnectionParams(Map.of("phoneNumberId", "phone-" + UUID.randomUUID(), "accessToken", "token-a")));

        newBusinessWithAiTenant(); // business B
        AiChannelConnectionResponse businessBDetail = aiChannelConnectionService.getDetail(AiChannel.WHATSAPP);
        assertEquals("NOT_CONNECTED", businessBDetail.connectionState(), "business B must never see business A's connection");
    }

    // ==================== Consistency between the two management surfaces ====================

    @Test
    void connectionStateNeverDriftsBetweenTheBusinessFacingAndSuperAdminSurfaces() {
        UUID businessId = newBusinessWithAiTenant();
        when(whatsAppApiClient.validatePhoneNumber(anyString(), anyString()))
                .thenReturn(new WhatsAppApiClient.PhoneNumberMetadata(true, null, null, null));

        // Connected via the NEW business-facing path.
        aiChannelConnectionService.connect(AiChannel.WHATSAPP,
                new ConnectionParams(Map.of("phoneNumberId", "phone-" + UUID.randomUUID(), "accessToken", "token")));
        assertEquals(ChannelConnectionState.CONNECTED,
                aiChannelBindingRepository.findByBusinessIdAndChannel(businessId, AiChannel.WHATSAPP).orElseThrow().getConnectionState());

        // The provider now fails. The Super-Admin surface (unmodified public contract) runs a test.
        when(whatsAppApiClient.validatePhoneNumber(anyString(), anyString()))
                .thenReturn(new WhatsAppApiClient.PhoneNumberMetadata(false, null, null, "expired"));
        UUID adminId = platformAdmin();
        WhatsAppConnectionTestResponse superAdminTest = whatsAppBindingService.testConnection(businessId);
        assertFalse(superAdminTest.success());

        // The business-facing detail view must reflect the SAME row's now-degraded state — proving
        // no independent, drifted copy of connectionState exists on either surface.
        AiChannelConnectionResponse detail = aiChannelConnectionService.getDetail(AiChannel.WHATSAPP);
        assertEquals("DEGRADED", detail.connectionState());

        // And the reverse: deactivating via the Super-Admin surface must be visible business-facing too.
        WhatsAppBindingResponse deactivated = whatsAppBindingService.setActive(adminId, businessId, false);
        assertFalse(deactivated.active());
        AiChannelConnectionResponse afterAdminDeactivate = aiChannelConnectionService.getDetail(AiChannel.WHATSAPP);
        assertEquals("DISCONNECTED", afterAdminDeactivate.connectionState());
    }

    // ==================== Hardening §4: credential rotation safety ====================

    @Test
    void rotatingToAValidReplacementSucceedsAndKeepsTheSameBindingId() {
        UUID businessId = newBusinessWithAiTenant();
        when(whatsAppApiClient.validatePhoneNumber(anyString(), anyString()))
                .thenReturn(new WhatsAppApiClient.PhoneNumberMetadata(true, null, null, null));
        String phoneNumberId = "phone-" + UUID.randomUUID();
        aiChannelConnectionService.connect(AiChannel.WHATSAPP,
                new ConnectionParams(Map.of("phoneNumberId", phoneNumberId, "accessToken", "original-token")));
        UUID originalId = aiChannelBindingRepository.findByBusinessIdAndChannel(businessId, AiChannel.WHATSAPP).orElseThrow().getId();

        // A valid replacement token.
        AiChannelConnectionResponse rotated = aiChannelConnectionService.connect(AiChannel.WHATSAPP,
                new ConnectionParams(Map.of("phoneNumberId", phoneNumberId, "accessToken", "replacement-token")));

        assertEquals("CONNECTED", rotated.connectionState());
        AiChannelBinding stored = aiChannelBindingRepository.findByBusinessIdAndChannel(businessId, AiChannel.WHATSAPP).orElseThrow();
        assertEquals(originalId, stored.getId(), "rotation must update the SAME binding row, not create a new one");
        assertEquals("replacement-token", stored.getCredentialsEncrypted());
    }

    @Test
    void rotatingAWorkingConnectionToAnInvalidReplacementIsRejectedAndTheOldCredentialSurvives() {
        UUID businessId = newBusinessWithAiTenant();
        when(whatsAppApiClient.validatePhoneNumber(anyString(), anyString()))
                .thenReturn(new WhatsAppApiClient.PhoneNumberMetadata(true, null, null, null));
        String phoneNumberId = "phone-" + UUID.randomUUID();
        aiChannelConnectionService.connect(AiChannel.WHATSAPP,
                new ConnectionParams(Map.of("phoneNumberId", phoneNumberId, "accessToken", "good-token")));
        assertEquals(ChannelConnectionState.CONNECTED,
                aiChannelBindingRepository.findByBusinessIdAndChannel(businessId, AiChannel.WHATSAPP).orElseThrow().getConnectionState());

        // Now the provider rejects a proposed replacement.
        when(whatsAppApiClient.validatePhoneNumber(anyString(), anyString()))
                .thenReturn(new WhatsAppApiClient.PhoneNumberMetadata(false, null, null, "Invalid OAuth access token."));

        ApiException ex = assertThrows(ApiException.class, () -> aiChannelConnectionService.connect(AiChannel.WHATSAPP,
                new ConnectionParams(Map.of("phoneNumberId", phoneNumberId, "accessToken", "bad-replacement-token"))));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());

        // The OLD, working credential must be completely untouched — never overwritten by the
        // rejected candidate.
        AiChannelBinding stored = aiChannelBindingRepository.findByBusinessIdAndChannel(businessId, AiChannel.WHATSAPP).orElseThrow();
        assertEquals("good-token", stored.getCredentialsEncrypted());
        assertEquals(ChannelConnectionState.CONNECTED, stored.getConnectionState(),
                "a rejected rotation attempt must never degrade the still-working connection's own state");
        assertNull(stored.getLastFailureAt(), "the rejected candidate's failure must never be recorded against the real, untouched binding");
    }

    @Test
    void rotatingAnAlreadyDegradedConnectionWithAnotherBadCredentialStillSavesForIteration() {
        UUID businessId = newBusinessWithAiTenant();
        when(whatsAppApiClient.validatePhoneNumber(anyString(), anyString()))
                .thenReturn(new WhatsAppApiClient.PhoneNumberMetadata(false, null, null, "first failure"));
        String phoneNumberId = "phone-" + UUID.randomUUID();
        aiChannelConnectionService.connect(AiChannel.WHATSAPP,
                new ConnectionParams(Map.of("phoneNumberId", phoneNumberId, "accessToken", "already-bad-token")));
        assertEquals(ChannelConnectionState.DEGRADED,
                aiChannelBindingRepository.findByBusinessIdAndChannel(businessId, AiChannel.WHATSAPP).orElseThrow().getConnectionState());

        // Nothing was working before, so a second failed attempt is saved (not rejected) — there
        // is no working connection to protect, only an iterative debugging attempt.
        when(whatsAppApiClient.validatePhoneNumber(anyString(), anyString()))
                .thenReturn(new WhatsAppApiClient.PhoneNumberMetadata(false, null, null, "second failure"));
        AiChannelConnectionResponse response = aiChannelConnectionService.connect(AiChannel.WHATSAPP,
                new ConnectionParams(Map.of("phoneNumberId", phoneNumberId, "accessToken", "still-bad-token")));
        assertEquals("DEGRADED", response.connectionState());

        AiChannelBinding stored = aiChannelBindingRepository.findByBusinessIdAndChannel(businessId, AiChannel.WHATSAPP).orElseThrow();
        assertEquals("still-bad-token", stored.getCredentialsEncrypted());
    }

    // ==================== Hardening §6: PROVIDER_AUTH is not reachable in Stage 0 ====================

    @Test
    void onlyOneInitiatorIsRegisteredForWhatsAppAndItIsManual(@Autowired java.util.List<ChannelConnectionInitiator> allInitiators) {
        long whatsappInitiators = allInitiators.stream().filter(i -> i.channel() == AiChannel.WHATSAPP).count();
        assertEquals(1, whatsappInitiators, "exactly one initiator may be registered for WhatsApp in Stage 0");
        assertTrue(allInitiators.stream().anyMatch(i -> i instanceof WhatsAppManualConnectionInitiator),
                "the one registered WhatsApp initiator must be the MANUAL implementation");
        assertTrue(allInitiators.stream().noneMatch(i -> i.channel() == AiChannel.INSTAGRAM
                        || i.channel() == AiChannel.PHONE),
                "no initiator may exist yet for a channel Stage 0 does not implement");
    }

    @Test
    void beginConnectionNeverRequiresARedirectForTheManualInitiator() {
        newBusinessWithAiTenant();
        // beginConnection is called internally by connect() before any redirect branch could ever
        // be user-reachable — proven directly here rather than only indirectly via connect()'s own
        // success path.
        WhatsAppManualConnectionInitiator initiator = new WhatsAppManualConnectionInitiator(aiChannelBindingRepository, whatsAppApiClient);
        ConnectionInitiationResult result = initiator.beginConnection(UUID.randomUUID(), new ConnectionParams(Map.of()));
        assertFalse(result.requiresRedirect());
        assertNull(result.redirectUrl());
    }

    // ==================== Hardening §8: no credential ever leaks ====================

    @Test
    void connectionResponseNeverCarriesAnyCredentialShapedField() {
        for (java.lang.reflect.Field field : AiChannelConnectionResponse.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            assertFalse(name.contains("token") || name.contains("secret") || name.contains("credential"),
                    "AiChannelConnectionResponse must never expose a credential-shaped field: " + field.getName());
        }
    }

    @Test
    void aFailedConnectionAttemptsExceptionMessageNeverContainsTheSubmittedToken() {
        newBusinessWithAiTenant();
        when(whatsAppApiClient.validatePhoneNumber(anyString(), anyString()))
                .thenReturn(new WhatsAppApiClient.PhoneNumberMetadata(true, null, null, null));
        aiChannelConnectionService.connect(AiChannel.WHATSAPP,
                new ConnectionParams(Map.of("phoneNumberId", "phone-" + UUID.randomUUID(), "accessToken", "good-token")));

        when(whatsAppApiClient.validatePhoneNumber(anyString(), anyString()))
                .thenReturn(new WhatsAppApiClient.PhoneNumberMetadata(false, null, null, "rejected"));
        String secretSubmittedValue = "super-secret-rejected-token-value-12345";
        ApiException ex = assertThrows(ApiException.class, () -> aiChannelConnectionService.connect(AiChannel.WHATSAPP,
                new ConnectionParams(Map.of("phoneNumberId", "phone-" + UUID.randomUUID(), "accessToken", secretSubmittedValue))));
        assertFalse(ex.getMessage().contains(secretSubmittedValue), "the rejected token value must never appear in an error message");
    }

    private UUID platformAdmin() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        PlatformAdmin admin = platformAdminRepository.save(PlatformAdmin.builder()
                .email("stage0-admin-" + unique + "@example.com")
                .passwordHash("not-a-real-hash")
                .fullName("Test Super Admin")
                .build());
        return admin.getId();
    }
}
