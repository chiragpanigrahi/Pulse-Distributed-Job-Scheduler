package com.example.scheduler.model.enums;

/**
 * Outcome of a single job execution attempt (one row per attempt).
 */
public enum ExecutionStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    TIMEOUT,
    RETRYING,
    SKIPPED,            // pipeline stage whose condition didn't match
    AWAITING_APPROVAL,  // pipeline paused at a manual approval gate
    PENDING_CALLBACK,   // webhook accepted the job; waiting for the external callback
    CANCELLED           // pipeline stopped by a rejected approval gate
}
