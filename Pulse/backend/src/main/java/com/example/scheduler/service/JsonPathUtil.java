package com.example.scheduler.service;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Minimal dot-path traversal over a parsed JSON tree - e.g. "data.order.id"
 * or "items.0.sku". Deliberately not full JSONPath (no wildcards/filters).
 */
final class JsonPathUtil {

    private JsonPathUtil() {
    }

    static JsonNode getAtPath(JsonNode root, String dotPath) {
        if (root == null || dotPath == null || dotPath.isBlank()) {
            return root;
        }
        JsonNode current = root;
        for (String segment : dotPath.split("\\.")) {
            if (current == null || current.isMissingNode()) {
                return null;
            }
            if (current.isArray()) {
                try {
                    current = current.get(Integer.parseInt(segment));
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                current = current.get(segment);
            }
        }
        return current;
    }
}
