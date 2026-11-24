package com.example.scheduler.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {
    private long totalJobs;
    private long scheduledJobs;
    private long pausedJobs;
    private long runningExecutions;
    private long awaitingApprovalCount;
    private long pendingCallbackCount;
    private long executionsLast24h;
    private long successLast24h;
    private long failedLast24h;
    private double successRatePercent;
    private List<JobExecutionResponse> recentExecutions;
    private Map<String, Long> executionsByHour; // for the throughput chart
}
