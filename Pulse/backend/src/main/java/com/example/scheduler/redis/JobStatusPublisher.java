package com.example.scheduler.redis;

import com.example.scheduler.model.dto.JobExecutionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Called by whichever backend instance's Kafka consumer just finished
 * executing a job. Publishing through Redis (rather than pushing directly
 * to WebSocket sessions) means every other backend instance - and therefore
 * every browser connected to any of them - hears about the update too.
 *
 * Best-effort: a Redis hiccup must never fail the job execution itself.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobStatusPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void publish(JobExecutionResponse execution) {
        try {
            redisTemplate.convertAndSend(RedisConfig.JOB_STATUS_CHANNEL, objectMapper.writeValueAsString(execution));
        } catch (Exception e) {
            log.warn("Failed to publish execution {} status: {}", execution.getId(), e.getMessage());
        }
    }
}
