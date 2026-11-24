package com.example.scheduler.model.dto;

import com.example.scheduler.model.enums.StageCondition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * One step of a PIPELINE job. webhookUrl, headers and payload values may
 * reference {{params.x}}, {{stages.otherStage.response.body.field}} or
 * {{secrets.credentialName}}, resolved by TemplateResolver immediately
 * before the HTTP call is made.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobStage {

    private String name;

    private String webhookUrl;

    @Builder.Default
    private String httpMethod = "POST";

    private Map<String, String> headers;

    private Map<String, Object> payload;

    @Builder.Default
    private Integer timeoutSeconds = 30;

    @Builder.Default
    private Integer maxRetries = 3;

    /** Which named credential (if any) to attach to this stage's request. */
    private String credentialName;

    @Builder.Default
    private StageCondition condition = StageCondition.ON_SUCCESS;

    /** Pause the pipeline before this stage until a human approves/rejects it. */
    @Builder.Default
    private boolean requiresApproval = false;

    /**
     * For work bigger than one request/response: after this stage's webhook
     * accepts the call, the stage stays PENDING_CALLBACK until the external
     * system POSTs to /api/callbacks/{executionId}, or the deadline passes.
     */
    @Builder.Default
    private boolean awaitCallback = false;

    private Integer callbackTimeoutSeconds;

    /** Allowed HTTP status codes; empty/null means "any 2xx". */
    private List<Integer> expectedStatusCodes;

    /** A dot-path into the JSON response, or a plain substring of the raw body. */
    private String responseBodyContains;
}
