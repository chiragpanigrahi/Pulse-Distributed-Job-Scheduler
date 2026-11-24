package com.example.scheduler.controller;

import com.example.scheduler.model.dto.ExecutionExchangeResponse;
import com.example.scheduler.model.dto.JobExecutionResponse;
import com.example.scheduler.model.enums.ExecutionStatus;
import com.example.scheduler.service.JobExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
public class ExecutionController {

    private final JobExecutionService jobExecutionService;

    /** Newest-first runs across every job. Page size is capped at 100. */
    @GetMapping
    public ResponseEntity<Page<JobExecutionResponse>> list(
            @RequestParam(required = false) ExecutionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return ResponseEntity.ok(jobExecutionService.listExecutions(status, pageable));
    }

    /** What Pulse sent and what came back for one run, with secrets masked. */
    @GetMapping("/{id}/exchange")
    public ResponseEntity<ExecutionExchangeResponse> exchange(@PathVariable UUID id) {
        return ResponseEntity.ok(jobExecutionService.getExchange(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobExecutionResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(jobExecutionService.getExecution(id));
    }

    /** Every stage execution sharing one pipelineRunId, in stage order - how the UI groups a "run". */
    @GetMapping("/pipeline-runs/{pipelineRunId}")
    public ResponseEntity<List<JobExecutionResponse>> pipelineRun(@PathVariable UUID pipelineRunId) {
        return ResponseEntity.ok(jobExecutionService.listPipelineRun(pipelineRunId));
    }

    /** Every pipeline, across every job, currently paused at a manual approval gate. */
    @GetMapping("/pending-approvals")
    public ResponseEntity<List<JobExecutionResponse>> pendingApprovals() {
        return ResponseEntity.ok(jobExecutionService.listAwaitingApproval());
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable UUID id, Authentication authentication) {
        jobExecutionService.resumeAfterApproval(id, true, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID id, Authentication authentication) {
        jobExecutionService.resumeAfterApproval(id, false, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
