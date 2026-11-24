package com.example.scheduler.controller;

import com.example.scheduler.model.dto.CallbackRequest;
import com.example.scheduler.service.JobExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Public endpoint for async/long-running stages: after a stage with
 * awaitCallback=true fires its webhook, the external system POSTs here when the
 * real work is done. Authorization is the one-shot token issued for that
 * execution (exposed to the stage as {{callback.url}} / {{callback.token}}),
 * not a user JWT - see SecurityConfig.
 */
@RestController
@RequestMapping("/api/callbacks")
@RequiredArgsConstructor
public class CallbackController {

    private final JobExecutionService jobExecutionService;

    @PostMapping("/{executionId}")
    public ResponseEntity<Void> complete(@PathVariable UUID executionId,
                                          @RequestParam String token,
                                          @RequestBody CallbackRequest request) {
        boolean success = request.getStatus() != null && request.getStatus().equalsIgnoreCase("SUCCESS");
        jobExecutionService.completeCallback(executionId, token, success, request.getMessage(), request.getOutput());
        return ResponseEntity.noContent().build();
    }
}
