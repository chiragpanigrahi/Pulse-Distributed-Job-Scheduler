package com.example.scheduler.service;

import com.example.scheduler.redis.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Fails stages stuck in PENDING_CALLBACK past their deadline (the external
 * system never called back), then lets the pipeline continue so ON_FAILURE /
 * ALWAYS stages still run. Every replica ticks, but a Redis lock makes only one
 * of them sweep per interval - and each row is additionally locked and
 * re-checked inside JobExecutionService.timeoutCallback, so a pipeline can
 * never be resumed twice.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CallbackSweeperService {

    private static final String LOCK_KEY = "job-scheduler:callback-sweeper-lock";

    private final JobExecutionService jobExecutionService;
    private final DistributedLockService lockService;
    private final InstanceIdProvider instanceIdProvider;

    @Scheduled(fixedDelayString = "${app.callback.sweep-interval-ms:30000}")
    public void sweep() {
        String owner = instanceIdProvider.get() + ":" + UUID.randomUUID();
        if (!lockService.tryLock(LOCK_KEY, owner, Duration.ofSeconds(25))) {
            return; // another replica is sweeping this interval
        }
        try {
            for (UUID id : jobExecutionService.overdueCallbackIds()) {
                try {
                    jobExecutionService.timeoutCallback(id);
                } catch (Exception e) {
                    log.error("Failed to time out callback execution {}", id, e);
                }
            }
        } finally {
            lockService.unlock(LOCK_KEY, owner);
        }
    }
}
