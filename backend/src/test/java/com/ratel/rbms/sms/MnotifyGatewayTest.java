package com.ratel.rbms.sms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Pins the request we send to mNotify. No real network, no real key. */
class MnotifyGatewayTest {

    private static final String SEND_URL = "https://mnotify.test/api/sms/quick?key=TEST-KEY";

    private MockRestServiceServer server;
    private MnotifyGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://mnotify.test/api");
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new MnotifyGateway("TEST-KEY", builder.build());
    }

    @Test
    void sendsTheDocumentedPayloadAndReadsTheProviderReference() {
        server.expect(requestTo(SEND_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(queryParam("key", "TEST-KEY"))
                .andExpect(jsonPath("$.recipient[0]").value("0244123456"))
                .andExpect(jsonPath("$.sender").value("GLOWSALON"))
                .andExpect(jsonPath("$.message").value("Hello"))
                .andExpect(jsonPath("$.is_schedule").value(false))
                .andRespond(withSuccess("{\"status\":\"success\",\"code\":\"2000\",\"summary\":{\"_id\":\"abc123\"}}",
                        MediaType.APPLICATION_JSON));

        SmsGateway.SendResult result = gateway.send("GLOWSALON", "0244123456", "Hello");

        assertTrue(result.accepted());
        assertEquals("abc123", result.providerRef());
        server.verify();
    }

    @Test
    void aRejectionComesBackAsRejectedNotAnException() {
        server.expect(requestTo(SEND_URL))
                .andRespond(withSuccess("{\"status\":\"error\",\"code\":\"2002\",\"message\":\"Insufficient balance\"}",
                        MediaType.APPLICATION_JSON));

        SmsGateway.SendResult result = gateway.send("GLOWSALON", "0244123456", "Hello");

        assertFalse(result.accepted());
        assertNull(result.providerRef());
    }

    @Test
    void aServerErrorIsARejectionToo() {
        server.expect(requestTo(SEND_URL))
                .andRespond(withServerError().body("{\"status\":\"error\",\"message\":\"boom\"}").contentType(MediaType.APPLICATION_JSON));

        assertFalse(gateway.send("GLOWSALON", "0244123456", "Hello").accepted());
    }

    @Test
    void theKeyNeverAppearsInARejectionMessage() {
        server.expect(requestTo(SEND_URL)).andRespond(withServerError());

        SmsGateway.SendResult result = gateway.send("GLOWSALON", "0244123456", "Hello");

        assertFalse(result.accepted());
        assertFalse(String.valueOf(result.error()).contains("TEST-KEY"));
    }

    @Test
    void readsTheAccountBalance() {
        server.expect(requestTo("https://mnotify.test/api/balance/sms?key=TEST-KEY"))
                .andRespond(withSuccess("{\"status\":\"success\",\"balance\":4000,\"bonus\":70}", MediaType.APPLICATION_JSON));

        SmsGateway.ProviderBalance balance = gateway.balance().orElseThrow();

        assertEquals(0, balance.balance().compareTo(new BigDecimal("4000")));
        assertEquals(0, balance.bonus().compareTo(new BigDecimal("70")));
    }

    @Test
    void withoutAKeyNothingIsSentAndNothingIsConfigured() {
        MnotifyGateway unconfigured = new MnotifyGateway("  ", RestClient.builder().baseUrl("https://mnotify.test/api").build());

        assertFalse(unconfigured.isConfigured());
        assertFalse(unconfigured.send("GLOWSALON", "0244123456", "Hello").accepted());
        assertTrue(unconfigured.balance().isEmpty());
    }

    @Test
    void anApprovedSenderIsRecognised() {
        server.expect(requestTo("https://mnotify.test/api/senderid/status?key=TEST-KEY"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.sender_name").value("GLOWSALON"))
                .andRespond(withSuccess("{\"status\":\"success\",\"summary\":{\"sender_name\":\"GLOWSALON\",\"status\":\"Approved\"}}",
                        MediaType.APPLICATION_JSON));

        assertEquals(SmsGateway.SenderState.APPROVED, gateway.checkSender("GLOWSALON").state());
    }

    @Test
    void aPendingSenderIsRecognised() {
        server.expect(requestTo("https://mnotify.test/api/senderid/status?key=TEST-KEY"))
                .andRespond(withSuccess("{\"status\":\"success\",\"message\":\"Sender ID is pending review\"}", MediaType.APPLICATION_JSON));

        assertEquals(SmsGateway.SenderState.PENDING, gateway.checkSender("GLOWSALON").state());
    }

    @Test
    void aMissingSenderIsRecognised() {
        server.expect(requestTo("https://mnotify.test/api/senderid/status?key=TEST-KEY"))
                .andRespond(withSuccess("{\"status\":\"error\",\"message\":\"Sender ID not found\"}", MediaType.APPLICATION_JSON));

        assertEquals(SmsGateway.SenderState.NOT_FOUND, gateway.checkSender("GLOWSALON").state());
    }

    @Test
    void anAnswerItCannotReadIsUnknownNeverANo() {
        server.expect(requestTo("https://mnotify.test/api/senderid/status?key=TEST-KEY"))
                .andRespond(withSuccess("{\"weird\":true}", MediaType.APPLICATION_JSON));

        assertEquals(SmsGateway.SenderState.UNKNOWN, gateway.checkSender("GLOWSALON").state());
    }

    @Test
    void aProviderOutageWhenCheckingASenderIsUnknownAndDoesNotLeakTheKey() {
        server.expect(requestTo("https://mnotify.test/api/senderid/status?key=TEST-KEY")).andRespond(withServerError());

        SmsGateway.SenderCheck check = gateway.checkSender("GLOWSALON");

        assertEquals(SmsGateway.SenderState.UNKNOWN, check.state());
        assertFalse(check.detail().contains("TEST-KEY"));
    }
}
