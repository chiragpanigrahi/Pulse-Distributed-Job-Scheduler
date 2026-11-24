package com.example.scheduler.model;

import com.example.scheduler.model.enums.ExecutionMode;
import com.example.scheduler.model.enums.JobStatus;
import com.example.scheduler.model.enums.JobType;
import com.example.scheduler.model.enums.TriggerCondition;
import com.example.scheduler.model.dto.JobStage;
import com.example.scheduler.model.dto.JobParameterDefinition;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "jobs", indexes = {
        @Index(name = "idx_job_status_next_run", columnList = "status,nextRunAt"),
        @Index(name = "idx_job_created_by", columnList = "createdBy")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobType type;

    // Standard cron expression (e.g. "0 */5 * * * *"), required when type == RECURRING
    @Column(name = "cron_expression", length = 100)
    private String cronExpression;

    // Target time for a one-time job, required when type == ONE_TIME
    @Column(name = "run_at")
    private Instant runAt;

    // Required when executionMode == SINGLE; unused (stages carry their own
    // webhook config) when executionMode == PIPELINE.
    @Column(name = "webhook_url", length = 2048)
    private String webhookUrl;

    @Column(name = "http_method", length = 10)
    @Builder.Default
    private String httpMethod = "POST";

    /** Which named credential (if any) SINGLE-mode jobs attach to their request. */
    @Column(name = "credential_name", length = 150)
    private String credentialName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> headers;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "timeout_seconds", nullable = false)
    @Builder.Default
    private Integer timeoutSeconds = 30;

    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private Integer maxRetries = 3;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private JobStatus status = JobStatus.SCHEDULED;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", nullable = false, length = 20, columnDefinition = "varchar(20) default 'SINGLE'")
    @Builder.Default
    private ExecutionMode executionMode = ExecutionMode.SINGLE;

    // Ordered stages, only populated when executionMode == PIPELINE.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<JobStage> stages;

    // "Trigger with parameters" definitions; scheduled dispatches fall back to defaultValue.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<JobParameterDefinition> parameters;

    // Routes execution to the Kafka topic for this tag - the "agent pool"
    // this job runs on (see kafka/KafkaTopicConfig).
    @Column(name = "worker_tag", length = 50)
    @Builder.Default
    private String workerTag = "default";

    // Jobs to trigger when this one finishes, gated by downstreamTriggerCondition.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<UUID> downstreamJobIds;

    @Enumerated(EnumType.STRING)
    @Column(name = "downstream_trigger_condition", length = 20)
    @Builder.Default
    private TriggerCondition downstreamTriggerCondition = TriggerCondition.ON_SUCCESS;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @JsonIgnore
    private Long version;
}
