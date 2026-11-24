package com.example.scheduler.service;

import com.example.scheduler.model.Credential;
import com.example.scheduler.model.enums.CredentialType;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Exercises the real HTTP path against a local server and checks secrets never reach the captured exchange. */
class WebhookCallServiceTest {

    private static final String SECRET = "sk_live_supersecret";

    private HttpServer server;
    private WebhookCallService service;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", exchange -> {
            // A badly behaved receiver that echoes the secret back in its response.
            byte[] body = ("{\"echo\":\"" + exchange.getRequestHeaders().getFirst("Authorization") + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("Set-Cookie", "session=abc123");
            exchange.sendResponseHeaders(201, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        CredentialService credentials = mock(CredentialService.class);
        Credential credential = Credential.builder().name("pay").type(CredentialType.BEARER).build();
        when(credentials.getByNameOrThrow("pay")).thenReturn(credential);
        when(credentials.decryptPrimary(credential)).thenReturn(SECRET);
        when(credentials.allSecretValues()).thenReturn(Set.of(SECRET));
        service = new WebhookCallService(credentials);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void capturesRequestAndResponseWithSecretsMasked() throws Exception {
        WebhookCallService.Exchange exchange = new WebhookCallService.Exchange();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/echo";

        WebhookCallService.Result result = service.call(new WebhookCallService.Call(
                url, "POST", Map.of("X-Trace", "t-1"), Map.of("orderId", "42"), 5, "pay", exchange));

        assertEquals(201, result.statusCode());
        assertEquals("POST", exchange.method);
        assertEquals(url, exchange.url);
        assertEquals("Bearer " + WebhookCallService.MASK, exchange.requestHeaders.get("Authorization"));
        assertEquals("t-1", exchange.requestHeaders.get("X-Trace"));
        assertEquals("application/json", exchange.requestHeaders.get("Content-Type"));
        assertTrue(exchange.requestBody.contains("\"orderId\""));

        assertNotNull(exchange.responseBody);
        assertFalse(exchange.responseBody.contains(SECRET), "echoed secret must be masked");
        String setCookie = exchange.responseHeaders.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase("set-cookie"))
                .map(Map.Entry::getValue).findFirst().orElse(null);
        assertEquals(WebhookCallService.MASK, setCookie);
    }

    @Test
    void keepsTheRequestSideWhenTheCallFails() {
        WebhookCallService.Exchange exchange = new WebhookCallService.Exchange();
        try {
            service.call(new WebhookCallService.Call(
                    "http://127.0.0.1:1/unreachable", "GET", Map.of(), null, 2, null, exchange));
        } catch (Exception expected) {
            // the connection is refused
        }
        assertEquals("GET", exchange.method);
        assertEquals("http://127.0.0.1:1/unreachable", exchange.url);
        assertEquals(null, exchange.responseBody);
    }
}
