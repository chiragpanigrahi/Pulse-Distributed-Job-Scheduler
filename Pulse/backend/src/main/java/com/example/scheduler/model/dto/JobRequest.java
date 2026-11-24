package com.example.scheduler.model.dto;

import com.example.scheduler.model.enums.ExecutionMode;
import com.example.scheduler.model.enums.JobType;
import com.example.scheduler.model.enums.TriggerCondition;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class JobRequest {

    @NotBlank(message = "Job name is required")
    @Size(max = 150)
    private String name;

    @Size(max = 1000)
    private String description;

    @NotNull(message = "Job type is required")
    private JobType type;

    // Required when type == RECURRING
    private String cronExpression;

    // Required when type == ONE_TIME
    private Instant runAt;

    @NotNull(message = "Execution mode is required")
    private ExecutionMode executionMode = ExecutionMode.SINGLE;

    // --- SINGLE mode (required when executionMode == SINGLE; checked in JobService.validate) ---

    @Pattern(regexp = "^https?://.+", message = "Webhook URL must start with http:// or https://")
    private String webhookUrl;

    @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE", message = "httpMethod must be one of GET, POST, PUT, PATCH, DELETE")
    private String httpMethod = "POST";

    private Map<String, String> headers;

    private Map<String, Object> payload;

    /** Named credential (see /api/credentials) attached to the request. SINGLE mode only. */
    private String credentialName;

    @Min(1) @Max(300)
    private Integer timeoutSeconds = 30;

    @Min(0) @Max(10)
    private Integer maxRetries = 3;

    // --- PIPELINE mode (required when executionMode == PIPELINE) ---

    private List<JobStage> stages;

    // --- shared ---

    private List<JobParameterDefinition> parameters;

    @Size(max = 50)
    private String workerTag = "default";

    private List<UUID> downstreamJobIds;

    private TriggerCondition downstreamTriggerCondition = TriggerCondition.ON_SUCCESS;
}
