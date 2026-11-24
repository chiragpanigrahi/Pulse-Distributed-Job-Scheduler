package com.example.scheduler.service;

import com.example.scheduler.exception.InvalidJobConfigException;
import com.example.scheduler.exception.ResourceNotFoundException;
import com.example.scheduler.kafka.JobExecutionProducer;
import com.example.scheduler.model.Job;
import com.example.scheduler.model.dto.JobExecutionMessage;
import com.example.scheduler.model.dto.JobParameterDefinition;
import com.example.scheduler.model.dto.JobRequest;
import com.example.scheduler.model.dto.JobResponse;
import com.example.scheduler.model.dto.JobStage;
import com.example.scheduler.model.enums.ExecutionMode;
import com.example.scheduler.model.enums.JobStatus;
import com.example.scheduler.model.enums.JobType;
import com.example.scheduler.model.enums.TriggerCondition;
import com.example.scheduler.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final JobExecutionProducer jobExecutionProducer;
    private final JobExecutionMessageFactory messageFactory;

    @Transactional
    public JobResponse createJob(JobRequest request) {
        validate(request, null);

        Job job = Job.builder()
                .name(request.getName())
                .description(request.getDescription())
                .type(request.getType())
                .cronExpression(request.getCronExpression())
                .runAt(request.getRunAt())
                .executionMode(request.getExecutionMode())
                .webhookUrl(request.getWebhookUrl() == null ? "" : request.getWebhookUrl())
                .httpMethod(request.getHttpMethod())
                .headers(request.getHeaders())
                .payload(request.getPayload())
                .credentialName(request.getCredentialName())
                .timeoutSeconds(request.getTimeoutSeconds())
                .maxRetries(request.getMaxRetries())
                .stages(request.getStages())
                .parameters(request.getParameters())
                .workerTag(blankToDefault(request.getWorkerTag()))
                .downstreamJobIds(request.getDownstreamJobIds())
                .downstreamTriggerCondition(request.getDownstreamTriggerCondition())
                .status(JobStatus.SCHEDULED)
                .createdBy(currentUsername())
                .build();

        job.setNextRunAt(computeInitialNextRun(request));

        return JobResponse.fromEntity(jobRepository.save(job));
    }

    @Transactional
    public JobResponse updateJob(UUID id, JobRequest request) {
        validate(request, id);
        Job job = getEntity(id);

        job.setName(request.getName());
        job.setDescription(request.getDescription());
        job.setType(request.getType());
        job.setCronExpression(request.getCronExpression());
        job.setRunAt(request.getRunAt());
        job.setExecutionMode(request.getExecutionMode());
        // "" not null: databases created by V1 still carry a NOT NULL constraint that ddl-auto=update never drops
        job.setWebhookUrl(request.getWebhookUrl() == null ? "" : request.getWebhookUrl());
        job.setHttpMethod(request.getHttpMethod());
        job.setHeaders(request.getHeaders());
        job.setPayload(request.getPayload());
        job.setCredentialName(request.getCredentialName());
        job.setTimeoutSeconds(request.getTimeoutSeconds());
        job.setMaxRetries(request.getMaxRetries());
        job.setStages(request.getStages());
        job.setParameters(request.getParameters());
        job.setWorkerTag(blankToDefault(request.getWorkerTag()));
        job.setDownstreamJobIds(request.getDownstreamJobIds());
        job.setDownstreamTriggerCondition(request.getDownstreamTriggerCondition());

        // Only recompute the schedule if the job hasn't been paused
        if (job.getStatus() != JobStatus.PAUSED) {
            job.setNextRunAt(computeInitialNextRun(request));
            job.setStatus(JobStatus.SCHEDULED);
        }

        return JobResponse.fromEntity(jobRepository.save(job));
    }

    public JobResponse getJob(UUID id) {
        return JobResponse.fromEntity(getEntity(id));
    }

    public Page<JobResponse> listJobs(String search, JobStatus status, Pageable pageable) {
        Page<Job> page;
        if (status != null) {
            page = jobRepository.findByStatus(status, pageable);
        } else if (search != null && !search.isBlank()) {
            page = jobRepository.findByNameContainingIgnoreCase(search, pageable);
        } else {
            page = jobRepository.findAll(pageable);
        }
        return page.map(JobResponse::fromEntity);
    }

    @Transactional
    public void deleteJob(UUID id) {
        Job job = getEntity(id);
        jobRepository.delete(job);
    }

    @Transactional
    public JobResponse pauseJob(UUID id) {
        Job job = getEntity(id);
        job.setStatus(JobStatus.PAUSED);
        return JobResponse.fromEntity(jobRepository.save(job));
    }

    @Transactional
    public JobResponse resumeJob(UUID id) {
        Job job = getEntity(id);
        job.setStatus(JobStatus.SCHEDULED);
        if (job.getNextRunAt() == null || job.getNextRunAt().isBefore(Instant.now())) {
            job.setNextRunAt(computeNextRunFromNow(job));
        }
        return JobResponse.fromEntity(jobRepository.save(job));
    }

    /**
     * "Run now" / "Build with parameters": dispatches an execution
     * immediately via Kafka, bypassing the poller, so it neither waits for the
     * next poll tick nor disturbs the job's regular schedule. Caller-supplied
     * params are merged with each parameter's default; a missing required
     * param fails fast here rather than inside a worker. A paused job stays
     * paused (a manual run doesn't re-enable its schedule).
     */
    @Transactional
    public JobResponse triggerNow(UUID id, Map<String, String> params) {
        Job job = getEntity(id);
        JobExecutionMessage message = messageFactory.build(job, params, 1);
        if (job.getStatus() != JobStatus.PAUSED) {
            job.setStatus(JobStatus.RUNNING);
        }
        job.setLastRunAt(Instant.now());
        if (job.getType() == JobType.ONE_TIME) {
            // Already run manually - don't let the poller fire it again at its scheduled time.
            job.setNextRunAt(null);
        }
        Job saved = jobRepository.save(job);
        jobExecutionProducer.dispatchAfterCommit(message);
        return JobResponse.fromEntity(saved);
    }

    Job getEntity(UUID id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + id));
    }

    private void validate(JobRequest request, UUID selfId) {
        // Clients may send explicit nulls, which override the DTO's field defaults.
        if (request.getExecutionMode() == null) request.setExecutionMode(ExecutionMode.SINGLE);
        if (request.getHttpMethod() == null || request.getHttpMethod().isBlank()) request.setHttpMethod("POST");
        if (request.getTimeoutSeconds() == null) request.setTimeoutSeconds(30);
        if (request.getMaxRetries() == null) request.setMaxRetries(3);
        if (request.getDownstreamTriggerCondition() == null) request.setDownstreamTriggerCondition(TriggerCondition.ON_SUCCESS);
        if (request.getType() == JobType.RECURRING) {
            if (request.getCronExpression() == null || request.getCronExpression().isBlank()) {
                throw new InvalidJobConfigException("cronExpression is required for RECURRING jobs");
            }
            if (!CronExpression.isValidExpression(request.getCronExpression())) {
                throw new InvalidJobConfigException("Invalid cron expression: " + request.getCronExpression());
            }
        } else if (request.getType() == JobType.ONE_TIME) {
            if (request.getRunAt() == null) {
                throw new InvalidJobConfigException("runAt is required for ONE_TIME jobs");
            }
            if (request.getRunAt().isBefore(Instant.now())) {
                throw new InvalidJobConfigException("runAt must be in the future");
            }
        }

        if (request.getExecutionMode() == ExecutionMode.PIPELINE) {
            validatePipeline(request.getStages());
        } else {
            if (request.getWebhookUrl() == null || request.getWebhookUrl().isBlank()) {
                throw new InvalidJobConfigException("webhookUrl is required for SINGLE-mode jobs");
            }
            if (request.getHttpMethod() == null || request.getHttpMethod().isBlank()) {
                throw new InvalidJobConfigException("httpMethod is required for SINGLE-mode jobs");
            }
        }

        validateParameters(request.getParameters());
        validateDownstream(selfId, request.getDownstreamJobIds());
    }

    private void validatePipeline(List<JobStage> stages) {
        if (stages == null || stages.isEmpty()) {
            throw new InvalidJobConfigException("A PIPELINE job needs at least one stage");
        }
        Set<String> names = new HashSet<>();
        for (JobStage stage : stages) {
            if (stage.getName() == null || stage.getName().isBlank()) {
                throw new InvalidJobConfigException("Every stage needs a name");
            }
            if (!stage.getName().matches("[A-Za-z0-9_\\-]+")) {
                throw new InvalidJobConfigException("Stage name '" + stage.getName()
                        + "' may only contain letters, digits, '_' and '-' (it is referenced as {{stages.name...}})");
            }
            if (!names.add(stage.getName())) {
                throw new InvalidJobConfigException("Duplicate stage name: " + stage.getName());
            }
            if (stage.getWebhookUrl() == null || stage.getWebhookUrl().isBlank()) {
                throw new InvalidJobConfigException("Stage '" + stage.getName() + "' needs a webhookUrl");
            }
            if (stage.isAwaitCallback() && (stage.getCallbackTimeoutSeconds() == null || stage.getCallbackTimeoutSeconds() <= 0)) {
                throw new InvalidJobConfigException(
                        "Stage '" + stage.getName() + "' waits for a callback but has no positive callbackTimeoutSeconds");
            }
        }
    }

    private void validateParameters(List<JobParameterDefinition> parameters) {
        if (parameters == null) return;
        Set<String> names = new HashSet<>();
        for (JobParameterDefinition p : parameters) {
            if (p.getName() == null || p.getName().isBlank()) {
                throw new InvalidJobConfigException("Every parameter needs a name");
            }
            if (!names.add(p.getName())) {
                throw new InvalidJobConfigException("Duplicate parameter name: " + p.getName());
            }
        }
    }

    /**
     * Rejects a downstream link to a job that doesn't exist, a job triggering
     * itself, or a chain that would form a cycle (A -> B -> A). The runtime
     * dispatch-depth cap is only a backstop.
     */
    private void validateDownstream(UUID selfId, List<UUID> downstreamIds) {
        if (downstreamIds == null || downstreamIds.isEmpty()) return;

        if (selfId != null && downstreamIds.contains(selfId)) {
            throw new InvalidJobConfigException("A job cannot trigger itself");
        }
        for (UUID downstreamId : downstreamIds) {
            if (!jobRepository.existsById(downstreamId)) {
                throw new InvalidJobConfigException("Downstream job not found: " + downstreamId);
            }
        }
        if (selfId == null) {
            return; // new job: no existing job can point at it yet, so no cycle is possible
        }

        // BFS over the downstream graph as it WOULD look with this edge set -
        // reaching selfId again means saving this would create a cycle.
        Deque<UUID> queue = new ArrayDeque<>(downstreamIds);
        Set<UUID> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            if (current.equals(selfId)) {
                throw new InvalidJobConfigException("This downstream configuration would create a trigger cycle");
            }
            if (!visited.add(current)) continue;
            jobRepository.findById(current).ifPresent(j -> {
                if (j.getDownstreamJobIds() != null) {
                    queue.addAll(j.getDownstreamJobIds());
                }
            });
        }
    }

    private Instant computeInitialNextRun(JobRequest request) {
        if (request.getType() == JobType.ONE_TIME) {
            return request.getRunAt();
        }
        CronExpression cron = CronExpression.parse(request.getCronExpression());
        ZonedDateTime next = cron.next(ZonedDateTime.now(ZoneOffset.UTC));
        return next == null ? null : next.toInstant();
    }

    private Instant computeNextRunFromNow(Job job) {
        if (job.getType() == JobType.ONE_TIME) {
            return Instant.now();
        }
        CronExpression cron = CronExpression.parse(job.getCronExpression());
        ZonedDateTime next = cron.next(ZonedDateTime.now(ZoneOffset.UTC));
        return next == null ? null : next.toInstant();
    }

    private String blankToDefault(String workerTag) {
        return workerTag == null || workerTag.isBlank() ? "default" : workerTag;
    }

    private String currentUsername() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            return "system";
        }
    }
}
