package com.example.scheduler.service;

import com.example.scheduler.kafka.JobExecutionProducer;
import com.example.scheduler.model.Job;
import com.example.scheduler.model.dto.JobExecutionMessage;
import com.example.scheduler.model.enums.JobStatus;
import com.example.scheduler.model.enums.JobType;
import com.example.scheduler.redis.DistributedLockService;
import com.example.scheduler.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Ticks on a fixed delay on EVERY backend replica, but wraps each tick in a
 * Redis distributed lock so only one replica ever dispatches a given batch
 * of due jobs. This is what makes horizontal scaling of this service safe:
 * add more instances for more dashboard/API throughput without risking a
 * job firing twice.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobPollingService {

    private final JobRepository jobRepository;
    private final JobExecutionProducer producer;
    private final DistributedLockService lockService;
    private final InstanceIdProvider instanceIdProvider;
    private final JobExecutionMessageFactory messageFactory;

    @Value("${app.scheduler.lock-key}")
    private String lockKey;

    @Value("${app.scheduler.lock-ttl-ms}")
    private long lockTtlMs;

    // Deliberately NOT @Transactional: each save commits on its own, so the Redis lock is still held
    // while rows commit and the Kafka dispatch always follows its job's committed RUNNING state.
    @Scheduled(fixedDelayString = "${app.scheduler.poll-interval-ms}")
    public void pollAndDispatch() {
        String ownerToken = instanceIdProvider.get() + ":" + UUID.randomUUID();
        boolean acquired = lockService.tryLock(lockKey, ownerToken, Duration.ofMillis(lockTtlMs));
        if (!acquired) {
            // Another replica currently holds the dispatch lock - nothing to do here.
            return;
        }
        try {
            dispatchDueJobs();
        } finally {
            lockService.unlock(lockKey, ownerToken);
        }
    }

    private void dispatchDueJobs() {
        List<Job> dueJobs = jobRepository.findDueJobs(Instant.now());
        if (dueJobs.isEmpty()) {
            return;
        }
        log.info("Dispatching {} due job(s) from instance {}", dueJobs.size(), instanceIdProvider.get());

        for (Job job : dueJobs) {
            // Scheduled dispatches have no caller to supply parameter values, so
            // every parameter falls back to its defaultValue.
            JobExecutionMessage message = messageFactory.build(job, null, 1);

            job.setStatus(JobStatus.RUNNING);
            job.setLastRunAt(Instant.now());
            job.setNextRunAt(job.getType() == JobType.RECURRING ? computeNextCronRun(job) : null);
            jobRepository.save(job);

            // After commit: otherwise a fast worker can finish the job before RUNNING is
            // committed (leaving it stuck RUNNING), and a failed commit would re-dispatch it.
            producer.dispatchAfterCommit(message);
        }
    }

    private Instant computeNextCronRun(Job job) {
        CronExpression cron = CronExpression.parse(job.getCronExpression());
        ZonedDateTime next = cron.next(ZonedDateTime.now(ZoneOffset.UTC));
        return next == null ? null : next.toInstant();
    }
}
