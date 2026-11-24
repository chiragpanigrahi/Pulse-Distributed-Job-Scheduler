package com.example.scheduler.controller;

import com.example.scheduler.model.dto.JobExecutionResponse;
import com.example.scheduler.model.dto.JobRequest;
import com.example.scheduler.model.dto.JobResponse;
import com.example.scheduler.model.enums.JobStatus;
import com.example.scheduler.repository.JobExecutionRepository;
import com.example.scheduler.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;
    private final JobExecutionRepository executionRepository;

    @PostMapping
    public ResponseEntity<JobResponse> create(@Valid @RequestBody JobRequest request) {
        return ResponseEntity.ok(jobService.createJob(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<JobResponse> update(@PathVariable UUID id, @Valid @RequestBody JobRequest request) {
        return ResponseEntity.ok(jobService.updateJob(id, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(jobService.getJob(id));
    }

    @GetMapping
    public ResponseEntity<Page<JobResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) JobStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(jobService.listJobs(search, status, pageable));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        jobService.deleteJob(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<JobResponse> pause(@PathVariable UUID id) {
        return ResponseEntity.ok(jobService.pauseJob(id));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<JobResponse> resume(@PathVariable UUID id) {
        return ResponseEntity.ok(jobService.resumeJob(id));
    }

    // Body is optional: omit it for a job without parameters ("Build Now") or send
    // {"paramName": "value"} for a parameterized run ("Build with Parameters").
    @PostMapping("/{id}/trigger")
    public ResponseEntity<JobResponse> trigger(@PathVariable UUID id,
                                                @RequestBody(required = false) Map<String, String> params) {
        return ResponseEntity.ok(jobService.triggerNow(id, params));
    }

    @GetMapping("/{id}/executions")
    public ResponseEntity<Page<JobExecutionResponse>> executions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<JobExecutionResponse> result = executionRepository
                .findByJobIdOrderByCreatedAtDesc(id, pageable)
                .map(JobExecutionResponse::fromEntity);
        return ResponseEntity.ok(result);
    }
}
