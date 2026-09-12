package com.ratel.rbms.controller;

import com.ratel.rbms.dto.AiChannelConnectionResponse;
import com.ratel.rbms.dto.AiChannelStatusResponse;
import com.ratel.rbms.dto.ConnectWhatsAppRequest;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.entity.enums.Role;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.security.JwtService;
import com.ratel.rbms.service.WhatsAppApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Talia Unified Platform, Phase 5D Stage 0 hardening §1 — proves the business-facing channel
 * management endpoints enforce role gating and tenant isolation through the REAL Spring Security
 * filter chain and REAL {@code @PreAuthorize} method security, not merely by calling the service
 * directly. Uses a real embedded HTTP server ({@code webEnvironment = RANDOM_PORT}) and real JWTs
 * minted by the actual {@link JwtService} the production {@code JwtAuthenticationFilter} verifies
 * — the same mechanism every real login already uses, not a security-test stub.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AiChannelConnectionSecurityTest {

    @LocalServerPort
    private int port;

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JwtService jwtService;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private UserRepository userRepository;

    @MockBean private WhatsAppApiClient whatsAppApiClient;

    // The default TestRestTemplate's JDK-URLConnection-based request factory doesn't support
    // PATCH at all ("Invalid HTTP method: PATCH") — swapping in the JDK 11+ java.net.http-based
    // factory (built into Spring Framework, no new dependency) is the standard fix, needed only
    // for this test's real HTTP exercise of the PATCH .../active endpoint.
    @BeforeEach
    void supportPatchRequests() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    private UUID newBusinessWithAi(String label) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Business business = businessRepository.save(Business.builder()
                .name("Phase5D Security " + label + " " + unique)
                .slug("phase5d-security-" + label.toLowerCase() + "-" + unique)
                .industry(Industry.OTHER).currency("GHS")
                .enabledModules(List.of("AI"))
                .build());
        return business.getId();
    }

    private String tokenFor(UUID businessId, Role role) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .businessId(businessId)
                .fullName(role + " Test User " + unique)
                .email(role.name().toLowerCase() + "-" + unique + "@example.com")
                .passwordHash("not-a-real-hash")
                .role(role)
                .build());
        return jwtService.generateToken(user.getId(), businessId, role.name());
    }

    private <T> ResponseEntity<T> call(HttpMethod method, String path, String jwt, Object body, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        if (jwt != null) {
            headers.setBearerAuth(jwt);
        }
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange("http://localhost:" + port + path, method, entity, responseType);
    }

    // ==================== Role matrix — view status ====================

    @Test
    void viewStatus_allowedForFourRoles_rejectedForStaff() {
        UUID businessId = newBusinessWithAi("ViewStatus");
        for (Role role : List.of(Role.OWNER, Role.MANAGER, Role.SALES_PERSON, Role.ACCOUNTANT)) {
            String jwt = tokenFor(businessId, role);
            ResponseEntity<AiChannelStatusResponse[]> response = call(HttpMethod.GET, "/api/ai/channels", jwt, null, AiChannelStatusResponse[].class);
            assertEquals(HttpStatus.OK, response.getStatusCode(), role + " must be able to view channel status");
        }
        String staffJwt = tokenFor(businessId, Role.STAFF);
        ResponseEntity<String> staffResponse = call(HttpMethod.GET, "/api/ai/channels", staffJwt, null, String.class);
        assertEquals(HttpStatus.FORBIDDEN, staffResponse.getStatusCode(), "STAFF must never view channel status");
    }

    // ==================== Role matrix — connect (also covers rotate) ====================

    @Test
    void connect_allowedForOwnerAndManager_rejectedForEveryoneElse() {
        when(whatsAppApiClient.validatePhoneNumber(anyString(), anyString()))
                .thenReturn(new WhatsAppApiClient.PhoneNumberMetadata(true, null, null, null));

        for (Role role : List.of(Role.OWNER, Role.MANAGER)) {
            UUID businessId = newBusinessWithAi("Connect" + role);
            String jwt = tokenFor(businessId, role);
            ConnectWhatsAppRequest req = new ConnectWhatsAppRequest(null, "phone-" + UUID.randomUUID(), "Line", "token");
            ResponseEntity<AiChannelConnectionResponse> response =
                    call(HttpMethod.POST, "/api/ai/channels/whatsapp/connect", jwt, req, AiChannelConnectionResponse.class);
            assertEquals(HttpStatus.OK, response.getStatusCode(), role + " must be able to connect a channel");
        }

        for (Role role : List.of(Role.SALES_PERSON, Role.ACCOUNTANT, Role.STAFF)) {
            UUID businessId = newBusinessWithAi("Connect" + role);
            String jwt = tokenFor(businessId, role);
            ConnectWhatsAppRequest req = new ConnectWhatsAppRequest(null, "phone-" + UUID.randomUUID(), "Line", "token");
            ResponseEntity<String> response = call(HttpMethod.POST, "/api/ai/channels/whatsapp/connect", jwt, req, String.class);
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(), role + " must never be able to connect a channel");
        }
    }

    // ==================== Role matrix — test ====================

    @Test
    void test_allowedForFourRoles_rejectedForStaff() {
        when(whatsAppApiClient.validatePhoneNumber(anyString(), anyString()))
                .thenReturn(new WhatsAppApiClient.PhoneNumberMetadata(true, null, null, null));
        UUID businessId = newBusinessWithAi("TestAction");
        String ownerJwt = tokenFor(businessId, Role.OWNER);
        call(HttpMethod.POST, "/api/ai/channels/whatsapp/connect", ownerJwt,
                new ConnectWhatsAppRequest(null, "phone-" + UUID.randomUUID(), "Line", "token"), AiChannelConnectionResponse.class);

        for (Role role : List.of(Role.OWNER, Role.MANAGER, Role.SALES_PERSON, Role.ACCOUNTANT)) {
            String jwt = role == Role.OWNER ? ownerJwt : tokenFor(businessId, role);
            ResponseEntity<AiChannelConnectionResponse> response =
                    call(HttpMethod.POST, "/api/ai/channels/whatsapp/test", jwt, null, AiChannelConnectionResponse.class);
            assertEquals(HttpStatus.OK, response.getStatusCode(), role + " must be able to test a channel");
        }

        String staffJwt = tokenFor(businessId, Role.STAFF);
        ResponseEntity<String> staffResponse = call(HttpMethod.POST, "/api/ai/channels/whatsapp/test", staffJwt, null, String.class);
        assertEquals(HttpStatus.FORBIDDEN, staffResponse.getStatusCode(), "STAFF must never be able to test a channel");
    }

    // ==================== Role matrix — activate/deactivate (disconnect) ====================

    @Test
    void setActive_allowedForOwnerAndManager_rejectedForEveryoneElse() {
        when(whatsAppApiClient.validatePhoneNumber(anyString(), anyString()))
                .thenReturn(new WhatsAppApiClient.PhoneNumberMetadata(true, null, null, null));

        for (Role role : List.of(Role.OWNER, Role.MANAGER)) {
            UUID businessId = newBusinessWithAi("Deactivate" + role);
            String ownerJwt = tokenFor(businessId, Role.OWNER);
            call(HttpMethod.POST, "/api/ai/channels/whatsapp/connect", ownerJwt,
                    new ConnectWhatsAppRequest(null, "phone-" + UUID.randomUUID(), "Line", "token"), AiChannelConnectionResponse.class);
            String jwt = role == Role.OWNER ? ownerJwt : tokenFor(businessId, role);

            ResponseEntity<AiChannelConnectionResponse> response = call(HttpMethod.PATCH, "/api/ai/channels/whatsapp/active", jwt,
                    new AiChannelController.SetChannelActiveRequest(false), AiChannelConnectionResponse.class);
            assertEquals(HttpStatus.OK, response.getStatusCode(), role + " must be able to activate/deactivate a channel");
        }

        for (Role role : List.of(Role.SALES_PERSON, Role.ACCOUNTANT, Role.STAFF)) {
            UUID businessId = newBusinessWithAi("Deactivate" + role);
            String ownerJwt = tokenFor(businessId, Role.OWNER);
            call(HttpMethod.POST, "/api/ai/channels/whatsapp/connect", ownerJwt,
                    new ConnectWhatsAppRequest(null, "phone-" + UUID.randomUUID(), "Line", "token"), AiChannelConnectionResponse.class);
            String jwt = tokenFor(businessId, role);

            ResponseEntity<String> response = call(HttpMethod.PATCH, "/api/ai/channels/whatsapp/active", jwt,
                    new AiChannelController.SetChannelActiveRequest(false), String.class);
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(), role + " must never be able to activate/deactivate a channel");
        }
    }

    // ==================== No token at all ====================

    @Test
    void noTokenAtAllIsRejected() {
        ResponseEntity<String> response = call(HttpMethod.GET, "/api/ai/channels", null, null, String.class);
        assertTrue(response.getStatusCode().is4xxClientError(), "an unauthenticated request must never succeed");
    }

    // ==================== Cross-tenant isolation, through the real HTTP layer ====================

    @Test
    void businessAsConnectionIsInvisibleAndUnreachableFromBusinessBsAuthenticatedSession() {
        when(whatsAppApiClient.validatePhoneNumber(anyString(), anyString()))
                .thenReturn(new WhatsAppApiClient.PhoneNumberMetadata(true, "+233 24 000 0000", "Business A Resort", null));

        UUID businessA = newBusinessWithAi("TenantA");
        String ownerAJwt = tokenFor(businessA, Role.OWNER);
        call(HttpMethod.POST, "/api/ai/channels/whatsapp/connect", ownerAJwt,
                new ConnectWhatsAppRequest("waba-a", "phone-" + UUID.randomUUID(), "Business A Line", "token-a"),
                AiChannelConnectionResponse.class);

        // Every endpoint derives businessId exclusively from the caller's OWN JWT claim — there is
        // no request parameter anywhere a Business B session could supply to even attempt to name
        // Business A. This test proves the practical consequence: Business B's own view of "its"
        // WhatsApp binding is unaffected by, and shows nothing of, Business A's real connection.
        UUID businessB = newBusinessWithAi("TenantB");
        String ownerBJwt = tokenFor(businessB, Role.OWNER);

        // getDetail() legitimately returns 200/NOT_CONNECTED for "no binding exists" (not an
        // error) — the tenant-isolation proof is that it shows NOT_CONNECTED, never business A's
        // real CONNECTED state or any of its data.
        ResponseEntity<AiChannelConnectionResponse> businessBDetail =
                call(HttpMethod.GET, "/api/ai/channels/whatsapp", ownerBJwt, null, AiChannelConnectionResponse.class);
        assertEquals(HttpStatus.OK, businessBDetail.getStatusCode());
        assertEquals("NOT_CONNECTED", businessBDetail.getBody().connectionState(), "business B must never see business A's connection");
        assertNull(businessBDetail.getBody().externalAccountId());

        // Business B cannot test, disconnect, or otherwise act on a binding it has no way to name.
        ResponseEntity<String> businessBTest = call(HttpMethod.POST, "/api/ai/channels/whatsapp/test", ownerBJwt, null, String.class);
        assertEquals(HttpStatus.NOT_FOUND, businessBTest.getStatusCode());

        ResponseEntity<String> businessBDeactivate = call(HttpMethod.PATCH, "/api/ai/channels/whatsapp/active", ownerBJwt,
                new AiChannelController.SetChannelActiveRequest(false), String.class);
        assertEquals(HttpStatus.NOT_FOUND, businessBDeactivate.getStatusCode());

        // Business A's own connection remains completely intact throughout.
        ResponseEntity<AiChannelConnectionResponse> businessADetail =
                call(HttpMethod.GET, "/api/ai/channels/whatsapp", ownerAJwt, null, AiChannelConnectionResponse.class);
        assertEquals(HttpStatus.OK, businessADetail.getStatusCode());
        assertEquals("CONNECTED", businessADetail.getBody().connectionState());
    }
}
