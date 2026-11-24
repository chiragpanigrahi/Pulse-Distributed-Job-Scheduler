package com.example.scheduler.kafka;

import com.example.scheduler.model.dto.JobExecutionMessage;
import com.example.scheduler.service.JobExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobExecutionConsumer {

    private final JobExecutionService jobExecutionService;

    // Topics come from app.worker.tags (see WorkerTopics): a replica started with
    // WORKER_TAGS=heavy only consumes the "heavy" pool; WORKER_TAGS=default,heavy
    // serves both.
    @KafkaListener(topics = "#{@workerTopics.topics()}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(JobExecutionMessage message) {
        log.info("Consuming job {} (attempt {})", message.getJobId(), message.getAttemptNumber());
        try {
            jobExecutionService.execute(message);
        } catch (Exception e) {
            // execute() already handles and persists webhook-call failures;
            // this catch is a last resort so one bad message can never kill
            // the consumer thread and stall its partitions.
            log.error("Unexpected error processing job {}: {}", message.getJobId(), e.getMessage(), e);
        }
    }
}
