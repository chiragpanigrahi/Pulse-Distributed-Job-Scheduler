package com.example.scheduler.model.enums;

/**
 * Orthogonal to JobType (which controls WHEN a job runs). ExecutionMode
 * controls WHAT it does when it runs: a single webhook call, or an ordered
 * pipeline of stages - Jenkins-style, but each stage is an HTTP call rather
 * than a shell step.
 */
public enum ExecutionMode {
    SINGLE,
    PIPELINE
}
