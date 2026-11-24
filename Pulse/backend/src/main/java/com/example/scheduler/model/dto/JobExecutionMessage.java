package com.example.scheduler.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Message put on a Kafka topic when a job becomes due, or republished to
 * resume a pipeline past an approval gate / completed callback.
 *
 * SINGLE mode uses webhookUrl/httpMethod/headers/payload/credentialName
 * directly. PIPELINE mode carries a snapshot of the stage list in `stages`
 * and ignores those top-level webhook fields.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class JobExecutionMessage {
    private UUID jobId;
    private String jobName;

    // --- SINGLE-mode fields ---
    private String webhookUrl;
    private String httpMethod;
    private Map<String, String> headers;
    private Map<String, Object> payload;
    private String credentialName;

    // --- shared ---
    private Integer timeoutSeconds;
    private Integer maxRetries;
    private Integer attemptNumber;
    private String dispatchedBy; // instance id of the node that dispatched this
    private String workerTag;    // which agent pool / Kafka topic this was routed to

    // --- PIPELINE-mode fields ---
    private List<JobStage> stages;
    private UUID pipelineRunId;        // groups every stage execution of one trigger
    private Integer currentStageIndex; // which stage to run/resume at (0-based)
    private Integer approvedStageIndex; // stage whose manual approval was already granted (don't gate it again)

    // --- resolved at trigger time, available as {{params.x}} ---
    private Map<String, String> params;

    // --- downstream-trigger loop guard ---
    @Builder.Default
    private Integer dispatchDepth = 0;
}
