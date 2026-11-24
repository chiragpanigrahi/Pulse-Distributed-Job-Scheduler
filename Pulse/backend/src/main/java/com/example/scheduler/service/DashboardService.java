package com.example.scheduler.service;

import com.example.scheduler.model.JobExecution;
import com.example.scheduler.model.dto.DashboardStatsResponse;
import com.example.scheduler.model.dto.JobExecutionResponse;
import com.example.scheduler.model.enums.ExecutionStatus;
import com.example.scheduler.model.enums.JobStatus;
import com.example.scheduler.repository.JobExecutionRepository;
import com.example.scheduler.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final JobRepository jobRepository;
    private final JobExecutionRepository executionRepository;

    private static final DateTimeFormatter HOUR_LABEL = DateTimeFormatter.ofPattern("HH:00").withZone(ZoneOffset.UTC);

    public DashboardStatsResponse getStats() {
        Instant since24h = Instant.now().minus(1, ChronoUnit.DAYS);

        long totalJobs = jobRepository.count();
        long scheduledJobs = jobRepository.countByStatus(JobStatus.SCHEDULED);
        long pausedJobs = jobRepository.countByStatus(JobStatus.PAUSED);
        long runningExecutions = executionRepository.countByStatus(ExecutionStatus.RUNNING);
        long awaitingApprovalCount = executionRepository.countByStatus(ExecutionStatus.AWAITING_APPROVAL);
        long pendingCallbackCount = executionRepository.countByStatus(ExecutionStatus.PENDING_CALLBACK);
        long executionsLast24h = executionRepository.countByCreatedAtAfter(since24h);
        long successLast24h = executionRepository.countByStatusAndCreatedAtAfter(ExecutionStatus.SUCCESS, since24h);
        long failedLast24h = executionRepository.countByStatusAndCreatedAtAfter(ExecutionStatus.FAILED, since24h)
                + executionRepository.countByStatusAndCreatedAtAfter(ExecutionStatus.TIMEOUT, since24h);

        double successRate = executionsLast24h == 0 ? 100.0 : (successLast24h * 100.0 / executionsLast24h);

        List<JobExecutionResponse> recent = executionRepository.findTop20ByOrderByCreatedAtDesc()
                .stream().map(JobExecutionResponse::fromEntity).toList();

        Map<String, Long> byHour = buildHourlyThroughput(since24h);

        return DashboardStatsResponse.builder()
                .totalJobs(totalJobs)
                .scheduledJobs(scheduledJobs)
                .pausedJobs(pausedJobs)
                .runningExecutions(runningExecutions)
                .awaitingApprovalCount(awaitingApprovalCount)
                .pendingCallbackCount(pendingCallbackCount)
                .executionsLast24h(executionsLast24h)
                .successLast24h(successLast24h)
                .failedLast24h(failedLast24h)
                .successRatePercent(Math.round(successRate * 10) / 10.0)
                .recentExecutions(recent)
                .executionsByHour(byHour)
                .build();
    }

    private Map<String, Long> buildHourlyThroughput(Instant since) {
        List<JobExecution> executions = executionRepository.findSince(since);
        Map<String, Long> byHour = new LinkedHashMap<>();
        // Seed every hour bucket for the last 24h so the chart has a
        // continuous x-axis even when some hours had zero executions.
        for (int i = 23; i >= 0; i--) {
            String label = HOUR_LABEL.format(Instant.now().minus(i, ChronoUnit.HOURS));
            byHour.put(label, 0L);
        }
        for (JobExecution e : executions) {
            String label = HOUR_LABEL.format(e.getCreatedAt());
            byHour.merge(label, 1L, Long::sum);
        }
        return byHour;
    }
}
