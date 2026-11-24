package com.example.scheduler.model.dto;

import com.example.scheduler.model.JobExecution;
import com.example.scheduler.model.enums.ExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * What Pulse sent and what came back for one run, for the run inspector.
 * Secrets are masked when the exchange is captured, so nothing here needs
 * further filtering.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionExchangeResponse {
    private UUID executionId;
    private UUID jobId;
    private String jobName;
    private UUID pipelineRunId;
    private String stageName;
    private Integer stageIndex;
    private Integer attemptNumber;
    private ExecutionStatus status;
    private Instant startedAt;
    private Long durationMs;
    private String errorMessage;
    private Map<String, String> params;
    private Request request;
    private Response response;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        private String method;
        private String url;
        private Map<String, String> headers;
        private String body;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Integer statusCode;
        private Map<String, String> headers;
        private String body;
    }

    public static ExecutionExchangeResponse fromEntity(JobExecution e) {
        Request request = e.getRequestUrl() == null ? null : Request.builder()
                .method(e.getRequestMethod())
                .url(e.getRequestUrl())
                .headers(e.getRequestHeaders())
                .body(e.getRequestBody())
                .build();
        Response response = (e.getResponseStatusCode() == null && e.getResponseBody() == null
                && e.getResponseHeaders() == null) ? null : Response.builder()
                .statusCode(e.getResponseStatusCode())
                .headers(e.getResponseHeaders())
                .body(e.getResponseBody())
                .build();
        return ExecutionExchangeResponse.builder()
                .executionId(e.getId())
                .jobId(e.getJobId())
                .jobName(e.getJobName())
                .pipelineRunId(e.getPipelineRunId())
                .stageName(e.getStageName())
                .stageIndex(e.getStageIndex())
                .attemptNumber(e.getAttemptNumber())
                .status(e.getStatus())
                .startedAt(e.getStartedAt())
                .durationMs(e.getDurationMs())
                .errorMessage(e.getErrorMessage())
                .params(e.getParamsSnapshot())
                .request(request)
                .response(response)
                .build();
    }
}
