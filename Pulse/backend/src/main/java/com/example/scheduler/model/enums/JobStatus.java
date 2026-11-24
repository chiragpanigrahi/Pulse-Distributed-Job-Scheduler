package com.example.scheduler.model.enums;

/**
 * Lifecycle states of a scheduled job definition (not a single execution).
 */
public enum JobStatus {
    SCHEDULED,  // active, eligible to be picked up by the poller when due
    RUNNING,    // currently dispatched / being executed by a worker
    PAUSED,     // disabled by the user, will not be dispatched
    COMPLETED,  // one-time job that has already run successfully
    FAILED      // exhausted all retries on its most recent execution
}
