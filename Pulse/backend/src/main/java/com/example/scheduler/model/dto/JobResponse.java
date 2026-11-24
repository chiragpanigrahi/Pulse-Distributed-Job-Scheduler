package com.example.scheduler.model.dto;

import com.example.scheduler.model.Job;
import com.example.scheduler.model.enums.ExecutionMode;
import com.example.scheduler.model.enums.JobStatus;
import com.example.scheduler.model.enums.JobType;
import com.example.scheduler.model.enums.TriggerCondition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobResponse {
    private UUID id;
    private String name;
    private String description;
    private JobType type;
    private String cronExpression;
    private Instant runAt;
    private ExecutionMode executionMode;
    private String webhookUrl;
    private String httpMethod;
    private Map<String, String> headers;
    private Map<String, Object> payload;
    private String credentialName;
    private Integer timeoutSeconds;
    private Integer maxRetries;
    private List<JobStage> stages;
    private List<JobParameterDefinition> parameters;
    private String workerTag;
    private List<UUID> downstreamJobIds;
    private TriggerCondition downstreamTriggerCondition;
    private JobStatus status;
    private Instant nextRunAt;
    private Instant lastRunAt;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public static JobResponse fromEntity(Job job) {
        return JobResponse.builder()
                .id(job.getId())
                .name(job.getName())
                .description(job.getDescription())
                .type(job.getType())
                .cronExpression(job.getCronExpression())
                .runAt(job.getRunAt())
                .executionMode(job.getExecutionMode())
                .webhookUrl(job.getWebhookUrl())
                .httpMethod(job.getHttpMethod())
                .headers(job.getHeaders())
                .payload(job.getPayload())
                .credentialName(job.getCredentialName())
                .timeoutSeconds(job.getTimeoutSeconds())
                .maxRetries(job.getMaxRetries())
                .stages(job.getStages())
                .parameters(job.getParameters())
                .workerTag(job.getWorkerTag())
                .downstreamJobIds(job.getDownstreamJobIds())
                .downstreamTriggerCondition(job.getDownstreamTriggerCondition())
                .status(job.getStatus())
                .nextRunAt(job.getNextRunAt())
                .lastRunAt(job.getLastRunAt())
                .createdBy(job.getCreatedBy())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
