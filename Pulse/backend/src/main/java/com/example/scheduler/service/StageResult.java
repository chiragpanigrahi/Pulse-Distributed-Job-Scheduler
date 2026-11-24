package com.example.scheduler.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * In-memory result of one executed stage, used to feed later stages'
 * {{stages.name.response...}} templates and to decide pipeline success.
 */
record StageResult(
        boolean success,
        Integer statusCode,
        String rawBody,
        JsonNode parsedBody, // null if the body wasn't valid JSON
        Map<String, String> responseHeaders
) {
}
