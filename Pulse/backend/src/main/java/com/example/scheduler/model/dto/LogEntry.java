package com.example.scheduler.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One line of an execution's console output (see JobExecution.logLines). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEntry {
    private Instant timestamp;
    private String message;
}
