package com.example.scheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemplateResolverTest {

    private final TemplateResolver resolver = new TemplateResolver();
    private final ObjectMapper mapper = new ObjectMapper();

    private TemplateResolver.Context ctx(Map<String, StageResult> stages) {
        return TemplateResolver.Context.of(
                Map.of("orderId", "42"), stages,
                name -> "api-key".equals(name) ? "s3cret" : null,
                Map.of("url", "http://cb/x"));
    }

    @Test
    void resolvesParamsSecretsAndCallback() {
        assertEquals("o=42 k=s3cret cb=http://cb/x",
                resolver.resolveString("o={{params.orderId}} k={{ secrets.api-key }} cb={{callback.url}}", ctx(Map.of())));
    }

    @Test
    void unknownTokensBecomeEmpty() {
        assertEquals("[]", resolver.resolveString("[{{params.nope}}{{stages.x.response.body.id}}{{what.ever}}]", ctx(Map.of())));
    }

    @Test
    void resolvesStageResponseFields() throws Exception {
        StageResult r = new StageResult(true, 201, "{\"id\":7,\"items\":[{\"sku\":\"A\"}]}",
                mapper.readTree("{\"id\":7,\"items\":[{\"sku\":\"A\"}]}"), Map.of("X-Id", "h1"));
        var c = ctx(Map.of("create", r));
        assertEquals("7", resolver.resolveString("{{stages.create.response.body.id}}", c));
        assertEquals("A", resolver.resolveString("{{stages.create.response.body.items.0.sku}}", c));
        assertEquals("201", resolver.resolveString("{{stages.create.response.statusCode}}", c));
        assertEquals("SUCCESS", resolver.resolveString("{{stages.create.response.status}}", c));
        assertEquals("h1", resolver.resolveString("{{stages.create.response.headers.X-Id}}", c));
    }

    @Test
    void resolvesNestedPayloadValues() {
        Map<String, Object> payload = Map.of("a", "{{params.orderId}}", "list", List.of("{{params.orderId}}", 5));
        Map<String, Object> out = resolver.resolvePayload(payload, ctx(Map.of()));
        assertEquals("42", out.get("a"));
        assertEquals(List.of("42", 5), out.get("list"));
    }
}
