package com.example.scheduler.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpolation engine. Every String value in a stage's webhookUrl, headers,
 * or payload may contain `{{...}}` tokens:
 *
 *   {{params.orderId}}                         trigger-time parameter
 *   {{secrets.stripe-api-key}}                 decrypted credential value
 *   {{stages.createOrder.response.status}}     "SUCCESS" or "FAILED"
 *   {{stages.createOrder.response.statusCode}} HTTP status code
 *   {{stages.createOrder.response.body.id}}    dot-path into the JSON body
 *   {{stages.createOrder.response.headers.X}}  a response header
 *   {{callback.url}} / {{callback.token}} / {{callback.executionId}}
 *                                              only inside an awaitCallback stage: where the
 *                                              external system must POST when the work is done
 *
 * Unresolvable tokens become an empty string - a template that references a
 * SKIPPED stage is a normal situation, so this never throws.
 */
@Component
public class TemplateResolver {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.\\-]+)\\s*}}");

    public record Context(
            Map<String, String> params,
            Map<String, StageResult> stages,
            Function<String, String> secretResolver,
            Map<String, String> callback
    ) {
        public static Context of(Map<String, String> params, Map<String, StageResult> stages,
                                  Function<String, String> secretResolver) {
            return of(params, stages, secretResolver, null);
        }

        public static Context of(Map<String, String> params, Map<String, StageResult> stages,
                                  Function<String, String> secretResolver, Map<String, String> callback) {
            return new Context(
                    params == null ? Map.of() : params,
                    stages == null ? Map.of() : stages,
                    secretResolver,
                    callback == null ? Map.of() : callback);
        }
    }

    public String resolveString(String template, Context context) {
        if (template == null) return null;
        Matcher matcher = TOKEN_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String resolved = resolveToken(matcher.group(1), context);
            matcher.appendReplacement(result, Matcher.quoteReplacement(resolved == null ? "" : resolved));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public Map<String, String> resolveHeaders(Map<String, String> headers, Context context) {
        if (headers == null) return null;
        Map<String, String> resolved = new LinkedHashMap<>();
        headers.forEach((k, v) -> resolved.put(k, resolveString(v, context)));
        return resolved;
    }

    /** Recursively resolves tokens in every String leaf of a JSON-like value (Map/List/String/other). */
    public Object resolveValue(Object value, Context context) {
        if (value instanceof String s) {
            return resolveString(s, context);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> resolved = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                resolved.put(String.valueOf(e.getKey()), resolveValue(e.getValue(), context));
            }
            return resolved;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(v -> resolveValue(v, context)).toList();
        }
        return value; // numbers, booleans, null - nothing to interpolate
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> resolvePayload(Map<String, Object> payload, Context context) {
        if (payload == null) return null;
        return (Map<String, Object>) resolveValue(payload, context);
    }

    private String resolveToken(String path, Context context) {
        String[] segments = path.split("\\.", 2);
        String namespace = segments[0];
        String rest = segments.length > 1 ? segments[1] : "";

        return switch (namespace) {
            case "params" -> context.params().get(rest);
            case "secrets" -> context.secretResolver() == null ? null : context.secretResolver().apply(rest);
            case "callback" -> context.callback().get(rest);
            case "stages" -> resolveStageToken(rest, context);
            default -> null; // unknown namespace - leave blank rather than guess
        };
    }

    private String resolveStageToken(String rest, Context context) {
        // rest looks like "<stageName>.response.<field...>"
        String[] parts = rest.split("\\.", 3);
        if (parts.length < 3 || !"response".equals(parts[1])) {
            return null;
        }
        StageResult stage = context.stages().get(parts[0]);
        if (stage == null) {
            return null; // referenced stage hasn't run (yet, or was skipped)
        }
        String field = parts[2];

        if ("status".equals(field)) {
            return stage.success() ? "SUCCESS" : "FAILED";
        }
        if ("statusCode".equals(field)) {
            return stage.statusCode() == null ? null : String.valueOf(stage.statusCode());
        }
        if (field.startsWith("headers.")) {
            return stage.responseHeaders() == null ? null : stage.responseHeaders().get(field.substring("headers.".length()));
        }
        if (field.equals("body")) {
            return stage.rawBody();
        }
        if (field.startsWith("body.")) {
            if (stage.parsedBody() == null) return null;
            JsonNode node = JsonPathUtil.getAtPath(stage.parsedBody(), field.substring("body.".length()));
            return node == null || node.isMissingNode() ? null : (node.isTextual() ? node.asText() : node.toString());
        }
        return null;
    }
}
