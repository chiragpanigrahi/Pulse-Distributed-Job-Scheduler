package com.example.scheduler.service;

import com.example.scheduler.exception.InvalidJobConfigException;
import com.example.scheduler.model.Job;
import com.example.scheduler.model.dto.JobExecutionMessage;
import com.example.scheduler.model.dto.JobParameterDefinition;
import com.example.scheduler.model.enums.ExecutionMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the Kafka dispatch message for a job, resolving "trigger with
 * parameters" values against the job's parameter definitions. Shared by
 * JobPollingService (scheduled dispatch, parameter defaults) and
 * JobService.triggerNow (manual dispatch, caller-supplied values).
 */
@Component
@RequiredArgsConstructor
public class JobExecutionMessageFactory {

    private final InstanceIdProvider instanceIdProvider;

    public JobExecutionMessage build(Job job, Map<String, String> providedParams, int attemptNumber) {
        Map<String, String> resolvedParams = resolveParams(job, providedParams);

        JobExecutionMessage.JobExecutionMessageBuilder builder = JobExecutionMessage.builder()
                .jobId(job.getId())
                .jobName(job.getName())
                .timeoutSeconds(job.getTimeoutSeconds())
                .maxRetries(job.getMaxRetries())
                .attemptNumber(attemptNumber)
                .dispatchedBy(instanceIdProvider.get())
                .workerTag(job.getWorkerTag() == null || job.getWorkerTag().isBlank() ? "default" : job.getWorkerTag())
                .params(resolvedParams)
                .dispatchDepth(0);

        if (job.getExecutionMode() == ExecutionMode.PIPELINE) {
            builder.stages(job.getStages())
                    .pipelineRunId(UUID.randomUUID())
                    .currentStageIndex(0);
        } else {
            builder.webhookUrl(job.getWebhookUrl())
                    .httpMethod(job.getHttpMethod())
                    .headers(job.getHeaders())
                    .payload(job.getPayload())
                    .credentialName(job.getCredentialName());
        }

        return builder.build();
    }

    /** Merges caller-supplied values with each definition's default, enforcing required ones. */
    public Map<String, String> resolveParams(Job job, Map<String, String> provided) {
        Map<String, String> resolved = new LinkedHashMap<>();
        if (job.getParameters() == null) {
            return resolved;
        }
        for (JobParameterDefinition def : job.getParameters()) {
            String value = (provided != null && provided.get(def.getName()) != null)
                    ? provided.get(def.getName())
                    : def.getDefaultValue();
            if (def.isRequired() && (value == null || value.isBlank())) {
                throw new InvalidJobConfigException("Missing required parameter: " + def.getName());
            }
            if (value != null) {
                resolved.put(def.getName(), value);
            }
        }
        return resolved;
    }
}
