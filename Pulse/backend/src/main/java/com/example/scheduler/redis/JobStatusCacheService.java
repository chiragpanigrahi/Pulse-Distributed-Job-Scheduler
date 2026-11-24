package com.example.scheduler.redis;

import com.example.scheduler.model.dto.JobExecutionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Caches the most recent execution result per job so the dashboard's list
 * view can show live status without hitting Postgres for every row.
 * Best-effort: cache failures are logged, never propagated.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobStatusCacheService {

    private static final String KEY_PREFIX = "job-status:";
    private static final Duration TTL = Duration.ofHours(6);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void cacheLatestExecution(UUID jobId, JobExecutionResponse execution) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + jobId, objectMapper.writeValueAsString(execution), TTL);
        } catch (Exception e) {
            log.warn("Failed to cache latest execution for job {}: {}", jobId, e.getMessage());
        }
    }

    public JobExecutionResponse getLatestExecution(UUID jobId) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + jobId);
            return json == null ? null : objectMapper.readValue(json, JobExecutionResponse.class);
        } catch (Exception e) {
            return null;
        }
    }

    public void evict(UUID jobId) {
        redisTemplate.delete(KEY_PREFIX + jobId);
    }
}
