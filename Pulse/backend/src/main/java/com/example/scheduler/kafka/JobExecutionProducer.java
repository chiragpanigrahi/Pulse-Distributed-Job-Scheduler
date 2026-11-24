package com.example.scheduler.kafka;

import com.example.scheduler.model.dto.JobExecutionMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobExecutionProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic.job-execution}")
    private String jobExecutionTopic;

    /**
     * Keying by jobId keeps every attempt for the same job on the same
     * partition, so retries for one job are always processed in order even
     * though different jobs run fully in parallel across partitions.
     */
    /**
     * Publishes only once the surrounding DB transaction has committed (or immediately
     * when there is none). Consumers rebuild pipeline state from persisted rows, so a
     * message that overtakes its own commit would resume against stale data - and a
     * rolled-back transaction must never leave a dispatched message behind.
     */
    public void dispatchAfterCommit(JobExecutionMessage message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(message);
                }
            });
        } else {
            dispatch(message);
        }
    }

    public void dispatch(JobExecutionMessage message) {
        // The job's workerTag picks WHICH topic (see WorkerTagUtil); only replicas
        // configured with that tag (app.worker.tags) consume it.
        String topic = WorkerTagUtil.topicFor(jobExecutionTopic, message.getWorkerTag());
        kafkaTemplate.send(topic, message.getJobId().toString(), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish job {} to Kafka topic {}: {}", message.getJobId(), topic, ex.getMessage());
                    } else {
                        log.debug("Dispatched job {} (attempt {}) to {} partition {}",
                                message.getJobId(), message.getAttemptNumber(),
                                topic, result.getRecordMetadata().partition());
                    }
                });
    }
}
