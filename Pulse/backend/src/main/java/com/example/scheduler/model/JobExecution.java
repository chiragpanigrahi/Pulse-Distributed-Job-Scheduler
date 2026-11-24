package com.example.scheduler.model;

import com.example.scheduler.model.dto.LogEntry;
import com.example.scheduler.model.enums.ExecutionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "job_executions", indexes = {
        @Index(name = "idx_execution_job_id", columnList = "jobId"),
        @Index(name = "idx_execution_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "job_name", nullable = false, length = 150)
    private String jobName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExecutionStatus status;

    @Column(name = "attempt_number", nullable = false)
    @Builder.Default
    private Integer attemptNumber = 1;

    // Identifies which backend/worker instance actually executed this attempt -
    // makes the "distributed" part of the system visible on the dashboard.
    @Column(name = "worker_id", length = 100)
    private String workerId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "response_status_code")
    private Integer responseStatusCode;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    // --- Exchange capture (what Pulse sent, what came back). Secrets are masked before storage. ---

    @Column(name = "request_method", length = 10)
    private String requestMethod;

    @Column(name = "request_url", columnDefinition = "TEXT")
    private String requestUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_headers", columnDefinition = "jsonb")
    private Map<String, String> requestHeaders;

    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_headers", columnDefinition = "jsonb")
    private Map<String, String> responseHeaders;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // --- Pipeline tracking (null for SINGLE-mode jobs) ---

    // Shared by every stage row of one pipeline trigger, so the UI can group
    // them into a single "run".
    @Column(name = "pipeline_run_id")
    private UUID pipelineRunId;

    @Column(name = "stage_name", length = 150)
    private String stageName;

    @Column(name = "stage_index")
    private Integer stageIndex;

    // --- Async / long-running jobs ---

    @Column(name = "callback_token", length = 100)
    private String callbackToken;

    @Column(name = "callback_deadline")
    private Instant callbackDeadline;

    // --- Observability ---

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<LogEntry> logLines;

    // What {{params.*}} resolved to for this run, for audit/debugging.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> paramsSnapshot;
}
