package com.example.scheduler.service;

import com.example.scheduler.model.Credential;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Makes the outbound HTTP call for one stage (or a SINGLE-mode job), so both
 * execution paths share identical credential-attachment, timeout and
 * response-parsing behavior.
 */
@Service
@RequiredArgsConstructor
public class WebhookCallService {

    private final CredentialService credentialService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_RESPONSE_BODY_CHARS = 16000;

    /**
     * @param exchange optional capture target. It is filled in as the call
     *                 progresses - request first, response after - so the caller
     *                 still has the request side even when the call throws.
     */
    public record Call(
            String url,
            String httpMethod,
            Map<String, String> headers,
            Map<String, Object> payload,
            int timeoutSeconds,
            String credentialName,
            Exchange exchange
    ) {
        public Call(String url, String httpMethod, Map<String, String> headers, Map<String, Object> payload,
                    int timeoutSeconds, String credentialName) {
            this(url, httpMethod, headers, payload, timeoutSeconds, credentialName, null);
        }
    }

    /**
     * What was sent and received, with secrets already masked. Fields are volatile
     * because the capture is written by the calling thread and read right after
     * the call returns or throws.
     */
    public static class Exchange {
        public volatile String method;
        public volatile String url;
        public volatile Map<String, String> requestHeaders;
        public volatile String requestBody;
        public volatile Map<String, String> responseHeaders;
        public volatile String responseBody;
    }

    static final String MASK = "••••••";
    private static final Set<String> MASKED_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie");

    public record Result(
            int statusCode,
            String rawBody,
            JsonNode parsedBody,
            Map<String, String> responseHeaders
    ) {
    }

    public Result call(Call call) throws Exception {
        Duration timeout = Duration.ofSeconds(call.timeoutSeconds() <= 0 ? 30 : call.timeoutSeconds());
        String body = call.payload() != null ? objectMapper.writeValueAsString(call.payload()) : "";

        Map<String, String> headers = call.headers() != null ? new LinkedHashMap<>(call.headers()) : new LinkedHashMap<>();
        applyCredential(call.credentialName(), headers, body);

        boolean hasContentType = headers.keySet().stream().anyMatch(h -> h.equalsIgnoreCase("Content-Type"));
        if (!hasContentType) {
            headers.put("Content-Type", "application/json");
        }

        String method = call.httpMethod() == null ? "POST" : call.httpMethod().toUpperCase();
        boolean sendsBody = !method.equals("GET") && !method.equals("DELETE");

        Exchange exchange = call.exchange();
        Set<String> secrets = exchange == null ? Set.of() : credentialService.allSecretValues();
        if (exchange != null) {
            exchange.method = method;
            exchange.url = scrub(call.url(), secrets);
            exchange.requestHeaders = redactHeaders(headers, secrets);
            exchange.requestBody = sendsBody ? scrub(body, secrets) : null;
        }

        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(call.url()))
                .timeout(timeout);

        headers.forEach(builder::header);

        HttpRequest request = switch (method) {
            case "GET" -> builder.GET().build();
            case "DELETE" -> builder.DELETE().build();
            case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(body)).build();
            case "PATCH" -> builder.method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build();
            default -> builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
        };

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode parsed = null;
        try {
            parsed = objectMapper.readTree(response.body());
        } catch (Exception ignored) {
            // not JSON - rawBody is still available for substring/template use
        }

        Map<String, String> responseHeaders = new LinkedHashMap<>();
        response.headers().map().forEach((k, v) -> responseHeaders.put(k, String.join(",", v)));

        String truncated = truncate(response.body());
        if (exchange != null) {
            exchange.responseHeaders = redactHeaders(responseHeaders, secrets);
            exchange.responseBody = scrub(truncated, secrets);
        }

        return new Result(response.statusCode(), truncated, parsed, responseHeaders);
    }

    /** Masks credential headers (keeping only the scheme, e.g. "Bearer ••••••") and any known secret value. */
    private Map<String, String> redactHeaders(Map<String, String> headers, Set<String> secrets) {
        Map<String, String> out = new LinkedHashMap<>();
        headers.forEach((name, value) -> {
            if (value != null && MASKED_HEADERS.contains(name.toLowerCase())) {
                int space = value.indexOf(' ');
                String scheme = space > 0 ? value.substring(0, space) : null;
                out.put(name, scheme != null && scheme.length() <= 12 ? scheme + " " + MASK : MASK);
            } else {
                out.put(name, scrub(value, secrets));
            }
        });
        return out;
    }

    /** Replaces every known secret value found in the text with the mask. */
    private String scrub(String text, Set<String> secrets) {
        if (text == null || secrets.isEmpty()) return text;
        String result = text;
        for (String secret : secrets) {
            result = result.replace(secret, MASK);
        }
        return result;
    }

    /**
     * BEARER/BASIC/HEADER add an auth header; HMAC signs the (already
     * templated) request body and attaches "sha256=<hex>" under the
     * credential's header name - the same pattern Stripe/GitHub use so
     * receivers can verify a webhook's authenticity.
     */
    private void applyCredential(String credentialName, Map<String, String> headers, String body) throws Exception {
        if (credentialName == null || credentialName.isBlank()) {
            return;
        }
        Credential credential = credentialService.getByNameOrThrow(credentialName);
        String primary = credentialService.decryptPrimary(credential);

        switch (credential.getType()) {
            case BEARER -> headers.put("Authorization", "Bearer " + primary);
            case BASIC -> {
                String secondary = credentialService.decryptSecondary(credential);
                String combined = primary + ":" + (secondary == null ? "" : secondary);
                headers.put("Authorization", "Basic "
                        + Base64.getEncoder().encodeToString(combined.getBytes(StandardCharsets.UTF_8)));
            }
            case HEADER -> headers.put(credential.getHeaderName(), primary);
            case HMAC -> {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(primary.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                byte[] signature = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
                headers.put(credential.getHeaderName(), "sha256=" + HexFormat.of().formatHex(signature));
            }
        }
    }

    private String truncate(String text) {
        if (text == null) return null;
        return text.length() > MAX_RESPONSE_BODY_CHARS ? text.substring(0, MAX_RESPONSE_BODY_CHARS) + "...(truncated)" : text;
    }
}
