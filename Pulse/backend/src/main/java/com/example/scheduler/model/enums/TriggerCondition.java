package com.example.scheduler.model.enums;

/**
 * Jenkins "build other projects" style downstream trigger condition, applied
 * to every job listed in Job.downstreamJobIds.
 */
public enum TriggerCondition {
    ALWAYS,
    ON_SUCCESS,
    ON_FAILURE
}
