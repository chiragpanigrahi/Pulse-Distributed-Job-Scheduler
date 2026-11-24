package com.example.scheduler.service;

import com.example.scheduler.exception.InvalidCallbackTokenException;
import com.example.scheduler.exception.InvalidJobConfigException;
import com.example.scheduler.exception.ResourceNotFoundException;
import com.example.scheduler.kafka.JobExecutionProducer;
import com.example.scheduler.model.Credential;
import com.example.scheduler.model.Job;
import com.example.scheduler.model.JobExecution;
import com.example.scheduler.model.dto.ExecutionExchangeResponse;
import com.example.scheduler.model.dto.JobExecutionMessage;
import com.example.scheduler.model.dto.JobExecutionResponse;
import com.example.scheduler.model.dto.JobStage;
import com.example.scheduler.model.dto.LogEntry;
import com.example.scheduler.model.enums.ExecutionStatus;
import com.example.scheduler.model.enums.JobStatus;
import com.example.scheduler.model.enums.JobType;
import com.example.scheduler.model.enums.StageCondition;
import com.example.scheduler.model.enums.TriggerCondition;
import com.example.scheduler.redis.JobStatusCacheService;
import com.example.scheduler.redis.JobStatusPublisher;
import com.example.scheduler.repository.JobExecutionRepository;
import com.example.scheduler.repository.JobRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Runs inside a Kafka consumer thread (see kafka/JobExecutionConsumer). Two
 * execution shapes share this class:
 *
 *  - SINGLE: one webhook call, retried with backoff via Kafka on failure.
 *
 *  - PIPELINE: an ordered walk through message.getStages(). Each stage can
 *    reference earlier stages' responses, pause for manual approval, or pause
 *    waiting for an external callback. A consumer thread must never block on a
 *    human or a long-running system, so both pause points END this method; the
 *    approve/reject API, the callback API, or the timeout sweeper later
 *    republishes a continuation message to Kafka that resumes the pipeline.
 *
 * Every attempt gets its own persisted JobExecution row (the "console output"),
 * cached in Redis and broadcast over Redis Pub/Sub so every replica's
 * WebSocket clients see it regardless of which replica ran it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobExecutionService {

    private final JobRepository jobRepository;
    private final JobExecutionRepository executionRepository;
    private final JobExecutionProducer producer;
    private final JobExecutionMessageFactory messageFactory;
    private final JobStatusCacheService cacheService;
    private final JobStatusPublisher publisher;
    private final InstanceIdProvider instanceIdProvider;
    private final WebhookCallService webhookCallService;
    private final TemplateResolver templateResolver;
    private final CredentialService credentialService;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Where external systems reach THIS service to complete an async stage.
    @Value("${app.callback.base-url:http://localhost:8080}")
    private String callbackBaseUrl;

    private static final long MAX_BACKOFF_MS = 30_000;
    private static final int MAX_DISPATCH_DEPTH = 10;
    private static final int MAX_RESPONSE_BODY_CHARS = 16000;
    private static final int DEFAULT_CALLBACK_TIMEOUT_SECONDS = 3600;

    public void execute(JobExecutionMessage message) {
        if (message.getStages() != null && !message.getStages().isEmpty()) {
            executePipeline(message);
        } else {
            executeSingle(message);
        }
    }

    // ============================================================
    // SINGLE mode
    // ============================================================

    private void executeSingle(JobExecutionMessage message) {
        int attempt = attemptOf(message);
        JobExecution execution = startExecutionRow(message, null, null, null, attempt);

        TemplateResolver.Context ctx = buildContext(message.getParams(), Map.of(), Map.of());
        WebhookCallService.Exchange exchange = new WebhookCallService.Exchange();

        try {
            String url = templateResolver.resolveString(message.getWebhookUrl(), ctx);
            Map<String, String> headers = templateResolver.resolveHeaders(message.getHeaders(), ctx);
            Map<String, Object> payload = templateResolver.resolvePayload(message.getPayload(), ctx);

            WebhookCallService.Result result = webhookCallService.call(new WebhookCallService.Call(
                    url, message.getHttpMethod(), headers, payload,
                    message.getTimeoutSeconds() == null ? 30 : message.getTimeoutSeconds(),
                    message.getCredentialName(), exchange));

            finishExecutionRow(execution, result);
            captureExchange(execution, exchange);

            if (isSuccessStatus(result.statusCode())) {
                execution.setStatus(ExecutionStatus.SUCCESS);
                executionRepository.save(execution);
                broadcast(execution);
                onJobFinished(message, true);
            } else {
                execution.setStatus(ExecutionStatus.FAILED);
                execution.setErrorMessage("Webhook responded with HTTP " + result.statusCode());
                executionRepository.save(execution);
                broadcast(execution);
                handleSingleFailure(message);
            }
        } catch (java.net.http.HttpTimeoutException te) {
            captureExchange(execution, exchange);
            failExecutionRow(execution, ExecutionStatus.TIMEOUT,
                    "Webhook call timed out after " + message.getTimeoutSeconds() + "s");
            handleSingleFailure(message);
        } catch (Exception e) {
            captureExchange(execution, exchange);
            failExecutionRow(execution, ExecutionStatus.FAILED, describeError(e));
            handleSingleFailure(message);
        }
    }

    /**
     * Retries with capped exponential backoff by briefly blocking this consumer
     * thread and republishing to the topic. Fine at this scale; a
     * high-throughput deployment would route retries through a delay topic.
     */
    private void handleSingleFailure(JobExecutionMessage message) {
        int nextAttempt = attemptOf(message) + 1;
        int maxRetries = message.getMaxRetries() == null ? 3 : message.getMaxRetries();

        if (nextAttempt <= maxRetries + 1) {
            log.warn("Job {} attempt {} failed, retrying (attempt {}/{})",
                    message.getJobId(), attemptOf(message), nextAttempt, maxRetries + 1);
            sleepBackoff(attemptOf(message));
            JobExecutionMessage retry = message.toBuilder()
                    .attemptNumber(nextAttempt)
                    .dispatchedBy(instanceIdProvider.get())
                    .build();
            producer.dispatch(retry);
        } else {
            log.error("Job {} exhausted all {} retries", message.getJobId(), maxRetries);
            onJobFinished(message, false);
        }
    }

    private void onJobFinished(JobExecutionMessage message, boolean success) {
        Job job = jobRepository.findById(message.getJobId()).orElse(null);
        if (job == null) {
            return; // job was deleted while its execution was in flight
        }
        finishJob(job, success, message.getDispatchDepth() == null ? 0 : message.getDispatchDepth());
    }

    // ============================================================
    // PIPELINE mode
    // ============================================================

    private void executePipeline(JobExecutionMessage message) {
        Job job = jobRepository.findById(message.getJobId()).orElse(null);
        if (job == null) {
            return; // job was deleted while this pipeline was in flight
        }

        List<JobStage> stages = message.getStages();
        int startIndex = message.getCurrentStageIndex() == null ? 0 : message.getCurrentStageIndex();
        UUID pipelineRunId = message.getPipelineRunId();
        Map<String, String> params = message.getParams() == null ? Map.of() : message.getParams();
        int depth = message.getDispatchDepth() == null ? 0 : message.getDispatchDepth();

        Map<String, StageResult> stageResults = new LinkedHashMap<>();
        boolean lastExecutedSuccess = true;
        boolean anyMainlineFailure = false;

        // A resumed pipeline (after an approval gate or callback) runs in a fresh
        // consumer invocation with no in-memory history, so rebuild just enough of
        // it - prior stage outcomes - from what's persisted.
        if (startIndex > 0) {
            PriorState prior = reconstructPriorState(pipelineRunId, stages, startIndex);
            stageResults.putAll(prior.results());
            lastExecutedSuccess = prior.lastExecutedSuccess();
            anyMainlineFailure = prior.anyMainlineFailure();
        }

        for (int i = startIndex; i < stages.size(); i++) {
            JobStage stage = stages.get(i);
            StageCondition condition = stage.getCondition() == null ? StageCondition.ON_SUCCESS : stage.getCondition();

            boolean shouldRun = switch (condition) {
                case ALWAYS -> true;
                case ON_SUCCESS -> lastExecutedSuccess;
                case ON_FAILURE -> !lastExecutedSuccess;
            };

            if (!shouldRun) {
                JobExecution skipped = startExecutionRow(message, pipelineRunId, stage.getName(), i, 1);
                skipped.setStatus(ExecutionStatus.SKIPPED);
                skipped.setFinishedAt(Instant.now());
                skipped.setDurationMs(0L);
                appendLog(skipped, "Skipped - condition " + condition + " not met for the previous stage's outcome");
                executionRepository.save(skipped);
                broadcast(skipped);
                continue; // a skipped stage doesn't change lastExecutedSuccess
            }

            boolean alreadyApproved = message.getApprovedStageIndex() != null && message.getApprovedStageIndex() == i;
            if (stage.isRequiresApproval() && !alreadyApproved) {
                // attempt 0 marks this row as the gate itself, never one of the stage's call attempts
                JobExecution gate = startExecutionRow(message, pipelineRunId, stage.getName(), i, 0);
                gate.setStatus(ExecutionStatus.AWAITING_APPROVAL);
                appendLog(gate, "Waiting for manual approval before running this stage");
                executionRepository.save(gate);
                broadcast(gate);

                if (job.getStatus() != JobStatus.PAUSED) {
                    job.setStatus(JobStatus.RUNNING);
                    jobRepository.save(job);
                }
                return; // consumer thread is free; /api/executions/{id}/approve resumes at this same stage
            }

            StageOutcome outcome = runStageWithRetries(message, pipelineRunId, stage, i, params, stageResults);

            if (outcome.pausedForCallback()) {
                return; // waiting on an external callback (or another actor already took over)
            }

            stageResults.put(stage.getName(), outcome.result());
            lastExecutedSuccess = outcome.result().success();
            if (!outcome.result().success() && condition != StageCondition.ON_FAILURE) {
                anyMainlineFailure = true;
            }
        }

        finishJob(job, !anyMainlineFailure, depth);
    }

    private record StageOutcome(StageResult result, boolean pausedForCallback) {
    }

    private record PriorState(Map<String, StageResult> results, boolean lastExecutedSuccess, boolean anyMainlineFailure) {
    }

    private StageOutcome runStageWithRetries(JobExecutionMessage message, UUID pipelineRunId, JobStage stage,
                                              int stageIndex, Map<String, String> params,
                                              Map<String, StageResult> priorResults) {
        int maxRetries = stage.getMaxRetries() == null ? 3 : stage.getMaxRetries();
        int timeoutSeconds = stage.getTimeoutSeconds() == null ? 30 : stage.getTimeoutSeconds();

        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            JobExecution row = startExecutionRow(message, pipelineRunId, stage.getName(), stageIndex, attempt);
            Map<String, String> callbackVars = Map.of();

            if (stage.isAwaitCallback()) {
                // The row is parked in PENDING_CALLBACK BEFORE the webhook call, and its
                // callback URL/token are exposed to the request as {{callback.*}}, so the
                // external system can both learn where to call back and call back
                // instantly without racing this thread.
                String token = UUID.randomUUID().toString();
                int callbackTimeout = stage.getCallbackTimeoutSeconds() == null || stage.getCallbackTimeoutSeconds() <= 0
                        ? DEFAULT_CALLBACK_TIMEOUT_SECONDS : stage.getCallbackTimeoutSeconds();
                row.setStatus(ExecutionStatus.PENDING_CALLBACK);
                row.setCallbackToken(token);
                row.setCallbackDeadline(Instant.now().plusSeconds(callbackTimeout));
                appendLog(row, "Callback issued - waiting up to " + callbackTimeout + "s for the external system");
                row = executionRepository.save(row);
                broadcast(row);
                callbackVars = Map.of(
                        "url", callbackUrl(row.getId(), token),
                        "token", token,
                        "executionId", row.getId().toString());
            }

            TemplateResolver.Context ctx = buildContext(params, priorResults, callbackVars);
            WebhookCallService.Exchange exchange = new WebhookCallService.Exchange();

            try {
                String url = templateResolver.resolveString(stage.getWebhookUrl(), ctx);
                Map<String, String> headers = templateResolver.resolveHeaders(stage.getHeaders(), ctx);
                Map<String, Object> payload = templateResolver.resolvePayload(stage.getPayload(), ctx);

                WebhookCallService.Result result = webhookCallService.call(new WebhookCallService.Call(
                        url, stage.getHttpMethod() == null ? "POST" : stage.getHttpMethod(),
                        headers, payload, timeoutSeconds, stage.getCredentialName(), exchange));

                if (evaluateAssertions(stage, result)) {
                    if (stage.isAwaitCallback()) {
                        recordCallbackAccepted(row.getId(), result, exchange);
                        return new StageOutcome(null, true);
                    }
                    finishExecutionRow(row, result);
                    captureExchange(row, exchange);
                    row.setStatus(ExecutionStatus.SUCCESS);
                    executionRepository.save(row);
                    broadcast(row);
                    return new StageOutcome(toStageResult(row), false);
                }

                String assertionError = "Response did not satisfy the stage's assertions (HTTP " + result.statusCode() + ")";
                if (stage.isAwaitCallback()) {
                    if (!failCallbackStageIfPending(row.getId(), ExecutionStatus.FAILED, assertionError, exchange)) {
                        return new StageOutcome(null, true);
                    }
                } else {
                    finishExecutionRow(row, result);
                    captureExchange(row, exchange);
                    row.setStatus(ExecutionStatus.FAILED);
                    row.setErrorMessage(assertionError);
                    executionRepository.save(row);
                    broadcast(row);
                }
            } catch (java.net.http.HttpTimeoutException te) {
                if (!recordStageFailure(row, stage, ExecutionStatus.TIMEOUT,
                        "Webhook call timed out after " + timeoutSeconds + "s", exchange)) {
                    return new StageOutcome(null, true);
                }
            } catch (Exception e) {
                if (!recordStageFailure(row, stage, ExecutionStatus.FAILED, describeError(e), exchange)) {
                    return new StageOutcome(null, true);
                }
            }

            if (attempt <= maxRetries) {
                log.warn("Stage '{}' of job {} attempt {} failed, retrying", stage.getName(), message.getJobId(), attempt);
                sleepBackoff(attempt);
            } else {
                JobExecution latest = executionRepository.findById(row.getId()).orElse(row);
                return new StageOutcome(toStageResult(latest), false);
            }
        }
        // Unreachable: the loop always returns on its final attempt.
        throw new IllegalStateException("Stage retry loop exited without a result");
    }

    /** Returns false if the row had already been completed by a callback (so the caller must stand down). */
    private boolean recordStageFailure(JobExecution row, JobStage stage, ExecutionStatus status, String errorMessage,
                                       WebhookCallService.Exchange exchange) {
        if (stage.isAwaitCallback()) {
            return failCallbackStageIfPending(row.getId(), status, errorMessage, exchange);
        }
        captureExchange(row, exchange);
        failExecutionRow(row, status, errorMessage);
        return true;
    }

    private boolean evaluateAssertions(JobStage stage, WebhookCallService.Result result) {
        List<Integer> expected = stage.getExpectedStatusCodes();
        boolean statusOk = (expected != null && !expected.isEmpty())
                ? expected.contains(result.statusCode())
                : isSuccessStatus(result.statusCode());
        if (!statusOk) return false;

        String contains = stage.getResponseBodyContains();
        if (contains == null || contains.isBlank()) return true;

        if (result.parsedBody() != null) {
            JsonNode node = JsonPathUtil.getAtPath(result.parsedBody(), contains);
            if (node != null && !node.isMissingNode()) {
                return true; // the path resolved to something - good enough
            }
        }
        return result.rawBody() != null && result.rawBody().contains(contains);
    }

    // --- atomic updates for callback stages (never clobber a callback that already landed) ---

    private boolean recordCallbackAccepted(UUID executionId, WebhookCallService.Result result,
                                           WebhookCallService.Exchange exchange) {
        Boolean updated = new TransactionTemplate(transactionManager).execute(tx -> {
            JobExecution fresh = executionRepository.findByIdForUpdate(executionId).orElse(null);
            if (fresh == null || fresh.getStatus() != ExecutionStatus.PENDING_CALLBACK) {
                return false; // the callback already completed (or timed out) this stage
            }
            fresh.setResponseStatusCode(result.statusCode());
            fresh.setResponseBody(result.rawBody());
            captureExchange(fresh, exchange);
            appendLog(fresh, "Webhook accepted the job (HTTP " + result.statusCode() + ") - waiting for callback");
            executionRepository.save(fresh);
            return true;
        });
        executionRepository.findById(executionId).ifPresent(this::broadcast);
        return Boolean.TRUE.equals(updated);
    }

    private boolean failCallbackStageIfPending(UUID executionId, ExecutionStatus status, String errorMessage,
                                               WebhookCallService.Exchange exchange) {
        Boolean updated = new TransactionTemplate(transactionManager).execute(tx -> {
            JobExecution fresh = executionRepository.findByIdForUpdate(executionId).orElse(null);
            if (fresh == null || fresh.getStatus() != ExecutionStatus.PENDING_CALLBACK) {
                return false;
            }
            fresh.setFinishedAt(Instant.now());
            fresh.setDurationMs(fresh.getStartedAt() == null ? null
                    : fresh.getFinishedAt().toEpochMilli() - fresh.getStartedAt().toEpochMilli());
            fresh.setStatus(status);
            fresh.setErrorMessage(truncate(errorMessage));
            fresh.setCallbackToken(null);
            captureExchange(fresh, exchange);
            appendLog(fresh, "Stage failed before any callback arrived: " + errorMessage);
            executionRepository.save(fresh);
            return true;
        });
        executionRepository.findById(executionId).ifPresent(this::broadcast);
        return Boolean.TRUE.equals(updated);
    }

    // ============================================================
    // Resuming a paused pipeline (approval gate / async callback)
    // ============================================================

    @Transactional
    public void resumeAfterApproval(UUID executionId, boolean approved, String approver) {
        JobExecution gate = executionRepository.findByIdForUpdate(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found: " + executionId));
        if (gate.getStatus() != ExecutionStatus.AWAITING_APPROVAL) {
            throw new InvalidJobConfigException("Execution " + executionId + " is not awaiting approval");
        }
        Job job = jobRepository.findById(gate.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + gate.getJobId()));

        gate.setFinishedAt(Instant.now());
        gate.setDurationMs(gate.getStartedAt() == null ? null
                : gate.getFinishedAt().toEpochMilli() - gate.getStartedAt().toEpochMilli());

        if (!approved) {
            gate.setStatus(ExecutionStatus.CANCELLED);
            appendLog(gate, "Rejected by " + approver);
            executionRepository.save(gate);
            broadcast(gate);
            finishJob(job, false, 0);
            return;
        }

        gate.setStatus(ExecutionStatus.SUCCESS);
        appendLog(gate, "Approved by " + approver);
        executionRepository.save(gate);
        broadcast(gate);

        // Resume AT the gated stage (its webhook has not run yet), flagged as already approved.
        int stageIndex = gate.getStageIndex() == null ? 0 : gate.getStageIndex();
        producer.dispatchAfterCommit(buildResumeMessage(job, gate, stageIndex, stageIndex));
    }

    @Transactional
    public void completeCallback(UUID executionId, String token, boolean success, String message, Object output) {
        JobExecution row = executionRepository.findByIdForUpdate(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found: " + executionId));
        if (row.getStatus() != ExecutionStatus.PENDING_CALLBACK || row.getCallbackToken() == null) {
            throw new InvalidJobConfigException("Execution " + executionId + " is not waiting for a callback");
        }
        if (!tokensMatch(row.getCallbackToken(), token)) {
            throw new InvalidCallbackTokenException("Invalid callback token");
        }

        Job job = jobRepository.findById(row.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + row.getJobId()));

        row.setFinishedAt(Instant.now());
        row.setDurationMs(row.getStartedAt() == null ? null
                : row.getFinishedAt().toEpochMilli() - row.getStartedAt().toEpochMilli());
        row.setStatus(success ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED);
        if (output != null) {
            try {
                // The callback's output becomes this stage's response body, so later
                // stages can use {{stages.thisStage.response.body.field}}.
                row.setResponseBody(truncate(objectMapper.writeValueAsString(output)));
            } catch (Exception ignored) {
                // keep the accept-time response body
            }
        }
        if (!success) {
            row.setErrorMessage(truncate(message));
        }
        appendLog(row, "Callback received: " + (success ? "SUCCESS" : "FAILED") + (message != null ? " - " + message : ""));
        row.setCallbackToken(null); // one-shot - replaying the same token is now rejected
        executionRepository.save(row);
        broadcast(row);

        continuePipeline(job, row);
    }

    public List<UUID> overdueCallbackIds() {
        return executionRepository
                .findByStatusAndCallbackDeadlineBefore(ExecutionStatus.PENDING_CALLBACK, Instant.now())
                .stream().map(JobExecution::getId).toList();
    }

    /** Fails one stage whose callback never arrived. Called per row by CallbackSweeperService. */
    @Transactional
    public void timeoutCallback(UUID executionId) {
        JobExecution row = executionRepository.findByIdForUpdate(executionId).orElse(null);
        if (row == null || row.getStatus() != ExecutionStatus.PENDING_CALLBACK
                || row.getCallbackDeadline() == null || row.getCallbackDeadline().isAfter(Instant.now())) {
            return; // already completed, or not actually overdue
        }
        log.warn("Execution {} (job {}) timed out waiting for its callback", row.getId(), row.getJobId());
        row.setStatus(ExecutionStatus.FAILED);
        row.setFinishedAt(Instant.now());
        row.setErrorMessage("Callback deadline passed with no response");
        row.setCallbackToken(null);
        appendLog(row, "Callback deadline passed with no response - marking failed");
        executionRepository.save(row);
        broadcast(row);

        jobRepository.findById(row.getJobId()).ifPresent(job -> continuePipeline(job, row));
    }

    /**
     * After a stage that paused (callback) finishes - successfully or not - carry
     * the pipeline on from the next stage, so ON_FAILURE / ALWAYS stages still
     * run, or finish the job if that was the last stage.
     */
    private void continuePipeline(Job job, JobExecution finishedRow) {
        if (finishedRow.getPipelineRunId() == null) {
            finishJob(job, finishedRow.getStatus() == ExecutionStatus.SUCCESS, 0);
            return;
        }
        int nextIndex = (finishedRow.getStageIndex() == null ? 0 : finishedRow.getStageIndex()) + 1;
        if (job.getStages() != null && nextIndex < job.getStages().size()) {
            producer.dispatchAfterCommit(buildResumeMessage(job, finishedRow, nextIndex, null));
        } else {
            finishJob(job, pipelineSucceededSoFar(job, finishedRow.getPipelineRunId()), 0);
        }
    }

    private JobExecutionMessage buildResumeMessage(Job job, JobExecution fromRow, int startStageIndex, Integer approvedStageIndex) {
        return JobExecutionMessage.builder()
                .jobId(job.getId())
                .jobName(job.getName())
                .stages(job.getStages())
                .pipelineRunId(fromRow.getPipelineRunId())
                .currentStageIndex(startStageIndex)
                .approvedStageIndex(approvedStageIndex)
                .params(fromRow.getParamsSnapshot())
                .timeoutSeconds(job.getTimeoutSeconds())
                .maxRetries(job.getMaxRetries())
                .attemptNumber(1)
                .dispatchedBy(instanceIdProvider.get())
                .workerTag(job.getWorkerTag())
                .dispatchDepth(0)
                .build();
    }

    // --- reconstructing pipeline state from persisted rows ---

    /**
     * The decisive row for each stage: the one with the highest attempt number
     * (retries supersede earlier failed attempts; the approval-gate row, attempt 0,
     * never outranks a real call attempt).
     */
    private List<JobExecution> finalRowsByStage(UUID pipelineRunId) {
        Map<Integer, JobExecution> best = new TreeMap<>();
        for (JobExecution row : executionRepository.findByPipelineRunIdOrderByStageIndexAsc(pipelineRunId)) {
            if (row.getStageIndex() == null) continue;
            JobExecution current = best.get(row.getStageIndex());
            if (current == null || rowAttempt(row) >= rowAttempt(current)) {
                best.put(row.getStageIndex(), row);
            }
        }
        return new ArrayList<>(best.values());
    }

    private PriorState reconstructPriorState(UUID pipelineRunId, List<JobStage> stages, int beforeIndex) {
        Map<String, StageResult> results = new LinkedHashMap<>();
        boolean lastExecutedSuccess = true;
        boolean anyMainlineFailure = false;

        for (JobExecution row : finalRowsByStage(pipelineRunId)) {
            if (row.getStageIndex() >= beforeIndex) continue;
            ExecutionStatus status = row.getStatus();
            if (status == ExecutionStatus.SKIPPED || status == ExecutionStatus.CANCELLED) continue;

            if (row.getStageName() != null) {
                results.put(row.getStageName(), toStageResult(row));
            }
            if (isTerminalExecutedStatus(status)) {
                lastExecutedSuccess = status == ExecutionStatus.SUCCESS;
                if (!lastExecutedSuccess && findCondition(stages, row.getStageName()) != StageCondition.ON_FAILURE) {
                    anyMainlineFailure = true;
                }
            }
        }
        return new PriorState(results, lastExecutedSuccess, anyMainlineFailure);
    }

    /** True unless a stage's decisive row failed (an ON_FAILURE stage failing doesn't count) or was cancelled. */
    private boolean pipelineSucceededSoFar(Job job, UUID pipelineRunId) {
        for (JobExecution row : finalRowsByStage(pipelineRunId)) {
            ExecutionStatus status = row.getStatus();
            if (status == ExecutionStatus.CANCELLED) return false;
            if ((status == ExecutionStatus.FAILED || status == ExecutionStatus.TIMEOUT)
                    && findCondition(job.getStages(), row.getStageName()) != StageCondition.ON_FAILURE) {
                return false;
            }
        }
        return true;
    }

    // ============================================================
    // Queries used by ExecutionController
    // ============================================================

    public JobExecutionResponse getExecution(UUID id) {
        return JobExecutionResponse.fromEntity(executionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found: " + id)));
    }

    /** What was sent and received for one execution, for the run inspector. */
    public ExecutionExchangeResponse getExchange(UUID id) {
        return ExecutionExchangeResponse.fromEntity(executionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found: " + id)));
    }

    /** Newest-first executions across every job, optionally filtered by status. */
    public org.springframework.data.domain.Page<JobExecutionResponse> listExecutions(
            ExecutionStatus status, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<JobExecution> page = status == null
                ? executionRepository.findAllByOrderByCreatedAtDesc(pageable)
                : executionRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return page.map(JobExecutionResponse::fromEntity);
    }

    public List<JobExecutionResponse> listAwaitingApproval() {
        return executionRepository.findByStatusOrderByCreatedAtDesc(ExecutionStatus.AWAITING_APPROVAL)
                .stream().map(JobExecutionResponse::fromEntity).toList();
    }

    public List<JobExecutionResponse> listPipelineRun(UUID pipelineRunId) {
        return executionRepository.findByPipelineRunIdOrderByStageIndexAsc(pipelineRunId)
                .stream().map(JobExecutionResponse::fromEntity).toList();
    }

    // ============================================================
    // Job completion and downstream ("build other projects") triggering
    // ============================================================

    private void finishJob(Job staleJob, boolean success, int dispatchDepth) {
        // Reload: the entity may have been held for minutes (long pipelines) while the
        // job was edited/paused, and saving a stale copy would fail the @Version check.
        Job job = jobRepository.findById(staleJob.getId()).orElse(null);
        if (job == null) {
            return; // deleted mid-run
        }
        // A paused job stays paused even if a manual run / downstream trigger finished it.
        if (job.getStatus() != JobStatus.PAUSED) {
            if (job.getType() == JobType.RECURRING) {
                job.setStatus(JobStatus.SCHEDULED);
            } else {
                job.setStatus(success ? JobStatus.COMPLETED : JobStatus.FAILED);
            }
            jobRepository.save(job);
        }
        triggerDownstream(job, success, dispatchDepth);
    }

    private void triggerDownstream(Job job, boolean success, int dispatchDepth) {
        if (job.getDownstreamJobIds() == null || job.getDownstreamJobIds().isEmpty()) {
            return;
        }
        if (dispatchDepth >= MAX_DISPATCH_DEPTH) {
            log.warn("Job {} hit the downstream dispatch depth cap ({}) - not triggering further jobs",
                    job.getId(), MAX_DISPATCH_DEPTH);
            return;
        }

        TriggerCondition condition = job.getDownstreamTriggerCondition() == null
                ? TriggerCondition.ON_SUCCESS : job.getDownstreamTriggerCondition();
        boolean shouldTrigger = switch (condition) {
            case ALWAYS -> true;
            case ON_SUCCESS -> success;
            case ON_FAILURE -> !success;
        };
        if (!shouldTrigger) {
            return;
        }

        for (UUID downstreamId : job.getDownstreamJobIds()) {
            jobRepository.findById(downstreamId).ifPresentOrElse(downstream -> {
                if (downstream.getStatus() == JobStatus.PAUSED) {
                    log.info("Downstream job {} is paused - not triggering it from {}", downstreamId, job.getId());
                    return;
                }
                JobExecutionMessage downstreamMessage = messageFactory.build(downstream, null, 1).toBuilder()
                        .dispatchDepth(dispatchDepth + 1)
                        .build();
                producer.dispatchAfterCommit(downstreamMessage);
                downstream.setStatus(JobStatus.RUNNING);
                downstream.setLastRunAt(Instant.now());
                jobRepository.save(downstream);
                log.info("Job {} triggered downstream job {} (depth {})", job.getId(), downstreamId, dispatchDepth + 1);
            }, () -> log.warn("Downstream job {} referenced by {} no longer exists", downstreamId, job.getId()));
        }
    }

    // ============================================================
    // Shared helpers
    // ============================================================

    private JobExecution startExecutionRow(JobExecutionMessage message, UUID pipelineRunId, String stageName,
                                            Integer stageIndex, int attemptNumber) {
        JobExecution execution = JobExecution.builder()
                .jobId(message.getJobId())
                .jobName(message.getJobName())
                .status(ExecutionStatus.RUNNING)
                .attemptNumber(attemptNumber)
                .workerId(instanceIdProvider.get())
                .startedAt(Instant.now())
                .pipelineRunId(pipelineRunId)
                .stageName(stageName)
                .stageIndex(stageIndex)
                .paramsSnapshot(message.getParams())
                .build();
        execution = executionRepository.save(execution);
        broadcast(execution);
        return execution;
    }

    private void finishExecutionRow(JobExecution row, WebhookCallService.Result result) {
        row.setFinishedAt(Instant.now());
        row.setDurationMs(row.getFinishedAt().toEpochMilli() - row.getStartedAt().toEpochMilli());
        row.setResponseStatusCode(result.statusCode());
        row.setResponseBody(result.rawBody());
    }

    /**
     * Copies the (already masked) request/response capture onto the row. The masked
     * response body replaces the raw one so a server that echoes a secret back
     * cannot leak it through the inspector.
     */
    private void captureExchange(JobExecution row, WebhookCallService.Exchange exchange) {
        if (exchange == null) return;
        row.setRequestMethod(exchange.method);
        row.setRequestUrl(exchange.url);
        row.setRequestHeaders(exchange.requestHeaders);
        row.setRequestBody(exchange.requestBody);
        row.setResponseHeaders(exchange.responseHeaders);
        if (exchange.responseBody != null) {
            row.setResponseBody(exchange.responseBody);
        }
    }

    private void failExecutionRow(JobExecution row, ExecutionStatus status, String errorMessage) {
        row.setFinishedAt(Instant.now());
        row.setDurationMs(row.getStartedAt() == null ? null
                : row.getFinishedAt().toEpochMilli() - row.getStartedAt().toEpochMilli());
        row.setStatus(status);
        row.setErrorMessage(truncate(errorMessage));
        executionRepository.save(row);
        broadcast(row);
    }

    private StageResult toStageResult(JobExecution row) {
        boolean success = row.getStatus() == ExecutionStatus.SUCCESS;
        return new StageResult(success, row.getResponseStatusCode(), row.getResponseBody(),
                tryParseJson(row.getResponseBody()), null);
    }

    private JsonNode tryParseJson(String body) {
        if (body == null) return null;
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }

    private StageCondition findCondition(List<JobStage> stages, String name) {
        if (stages == null || name == null) return StageCondition.ON_SUCCESS;
        return stages.stream()
                .filter(s -> name.equals(s.getName()))
                .map(JobStage::getCondition)
                .filter(c -> c != null)
                .findFirst()
                .orElse(StageCondition.ON_SUCCESS);
    }

    private boolean isTerminalExecutedStatus(ExecutionStatus status) {
        return status == ExecutionStatus.SUCCESS || status == ExecutionStatus.FAILED || status == ExecutionStatus.TIMEOUT;
    }

    private int attemptOf(JobExecutionMessage message) {
        return message.getAttemptNumber() == null ? 1 : message.getAttemptNumber();
    }

    private int rowAttempt(JobExecution row) {
        return row.getAttemptNumber() == null ? 0 : row.getAttemptNumber();
    }

    private void appendLog(JobExecution row, String message) {
        List<LogEntry> logs = row.getLogLines() == null ? new ArrayList<>() : new ArrayList<>(row.getLogLines());
        logs.add(LogEntry.builder().timestamp(Instant.now()).message(message).build());
        row.setLogLines(logs);
    }

    private TemplateResolver.Context buildContext(Map<String, String> params, Map<String, StageResult> stageResults,
                                                   Map<String, String> callbackVars) {
        return TemplateResolver.Context.of(params, stageResults, this::resolveSecret, callbackVars);
    }

    private String resolveSecret(String name) {
        try {
            Credential credential = credentialService.getByNameOrThrow(name);
            return credentialService.decryptPrimary(credential);
        } catch (Exception e) {
            log.warn("Template referenced unknown or unreadable secret '{}'", name);
            return null;
        }
    }

    private String callbackUrl(UUID executionId, String token) {
        String base = callbackBaseUrl.endsWith("/") ? callbackBaseUrl.substring(0, callbackBaseUrl.length() - 1) : callbackBaseUrl;
        return base + "/api/callbacks/" + executionId + "?token=" + token;
    }

    private boolean tokensMatch(String expected, String provided) {
        if (expected == null || provided == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }

    private void sleepBackoff(int attemptNumber) {
        long backoffMs = Math.min(MAX_BACKOFF_MS, 1000L * (1L << Math.min(attemptNumber, 5)));
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isSuccessStatus(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private String describeError(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private void broadcast(JobExecution execution) {
        JobExecutionResponse response = JobExecutionResponse.fromEntity(execution);
        cacheService.cacheLatestExecution(execution.getJobId(), response);
        publisher.publish(response);
    }

    private String truncate(String text) {
        if (text == null) return null;
        return text.length() > MAX_RESPONSE_BODY_CHARS ? text.substring(0, MAX_RESPONSE_BODY_CHARS) + "...(truncated)" : text;
    }
}
