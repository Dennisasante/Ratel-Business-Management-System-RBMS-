package com.ratel.rbms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WhatsAppApiClient against a tiny local HTTP stub standing in for Meta's
 * Graph API (no WireMock/MockWebServer dependency in this project — the
 * JDK's own HttpServer is enough for a handful of fixed responses). Proves
 * the outbound request shape (spec §19/§34 "outbound send") and that a
 * simulated Meta failure (spec §34/§35 "Meta API failure") is handled
 * gracefully rather than thrown, crashed on, or reported as a success.
 */
class WhatsAppApiClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendTextMessageHitsTheCorrectEndpointWithTheCorrectBodyAndAuth() throws IOException {
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<String> capturedAuthHeader = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            capturedPath.set(exchange.getRequestURI().toString());
            capturedAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            String response = "{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.ABC123\"}]}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();

        WhatsAppApiClient client = new WhatsAppApiClient(new ObjectMapper(), "v21.0", "http://localhost:" + server.getAddress().getPort());
        WhatsAppApiClient.WhatsAppSendResult result = client.sendTextMessage("PHONE_NUMBER_ID_123", "secret-access-token-xyz", "233244000001", "Hello from Tallia!");

        assertTrue(result.success());
        assertEquals("wamid.ABC123", result.whatsappMessageId());

        assertEquals("/v21.0/PHONE_NUMBER_ID_123/messages", capturedPath.get());
        assertEquals("Bearer secret-access-token-xyz", capturedAuthHeader.get());
        assertTrue(capturedBody.get().contains("\"to\":\"233244000001\""));
        assertTrue(capturedBody.get().contains("\"body\":\"Hello from Tallia!\""));
        assertTrue(capturedBody.get().contains("\"type\":\"text\""));
    }

    @Test
    void aRejectedSendIsReportedAsFailureNeverAsSuccessAndNeverThrows() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String errorBody = "{\"error\":{\"message\":\"Invalid OAuth access token\",\"code\":190}}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(401, errorBody.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(errorBody.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();

        WhatsAppApiClient client = new WhatsAppApiClient(new ObjectMapper(), "v21.0", "http://localhost:" + server.getAddress().getPort());
        WhatsAppApiClient.WhatsAppSendResult result = assertDoesNotThrow(() ->
                client.sendTextMessage("PHONE_NUMBER_ID_123", "a-now-expired-token", "233244000001", "Hi"));

        assertFalse(result.success());
        assertNotNull(result.errorMessage());
        // Never echoes the access token back in the failure it reports.
        assertFalse(result.errorMessage().contains("a-now-expired-token"));
    }

    @Test
    void anUnreachableServerIsHandledGracefullyNotThrown() {
        // Nothing listening on this port — simulates a network-level failure.
        WhatsAppApiClient client = new WhatsAppApiClient(new ObjectMapper(), "v21.0", "http://localhost:1");
        WhatsAppApiClient.WhatsAppSendResult result = assertDoesNotThrow(() ->
                client.sendTextMessage("PHONE_NUMBER_ID_123", "some-token", "233244000001", "Hi"));
        assertFalse(result.success());
    }

    @Test
    void validatePhoneNumberReturnsSafeMetadataOnSuccess() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String response = "{\"display_phone_number\":\"+233 24 400 0000\",\"verified_name\":\"Paradise Beach Resort\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();

        WhatsAppApiClient client = new WhatsAppApiClient(new ObjectMapper(), "v21.0", "http://localhost:" + server.getAddress().getPort());
        WhatsAppApiClient.PhoneNumberMetadata result = client.validatePhoneNumber("PHONE_NUMBER_ID_123", "some-token");

        assertTrue(result.valid());
        assertEquals("Paradise Beach Resort", result.verifiedName());
    }
}
