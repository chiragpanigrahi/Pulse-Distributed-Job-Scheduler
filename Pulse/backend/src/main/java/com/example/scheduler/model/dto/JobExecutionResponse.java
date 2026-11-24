package com.example.scheduler.model.dto;

import com.example.scheduler.model.JobExecution;
import com.example.scheduler.model.enums.ExecutionStatus;
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
public class JobExecutionResponse {
    private UUID id;
    private UUID jobId;
    private String jobName;
    private ExecutionStatus status;
    private Integer attemptNumber;
    private String workerId;
    private Instant startedAt;
    private Instant finishedAt;
    private Long durationMs;
    private Integer responseStatusCode;
    private String responseBody;
    private String errorMessage;
    private Instant createdAt;

    // Pipeline grouping - null for SINGLE-mode executions
    private UUID pipelineRunId;
    private String stageName;
    private Integer stageIndex;

    // Async callback jobs
    private Instant callbackDeadline;

    private List<LogEntry> logLines;
    private Map<String, String> paramsSnapshot;

    public static JobExecutionResponse fromEntity(JobExecution e) {
        return JobExecutionResponse.builder()
                .id(e.getId())
                .jobId(e.getJobId())
                .jobName(e.getJobName())
                .status(e.getStatus())
                .attemptNumber(e.getAttemptNumber())
                .workerId(e.getWorkerId())
                .startedAt(e.getStartedAt())
                .finishedAt(e.getFinishedAt())
                .durationMs(e.getDurationMs())
                .responseStatusCode(e.getResponseStatusCode())
                .responseBody(e.getResponseBody())
                .errorMessage(e.getErrorMessage())
                .createdAt(e.getCreatedAt())
                .pipelineRunId(e.getPipelineRunId())
                .stageName(e.getStageName())
                .stageIndex(e.getStageIndex())
                .callbackDeadline(e.getCallbackDeadline())
                .logLines(e.getLogLines())
                .paramsSnapshot(e.getParamsSnapshot())
                .build();
    }
}
