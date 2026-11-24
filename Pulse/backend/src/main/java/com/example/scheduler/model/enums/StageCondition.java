package com.example.scheduler.model.enums;

/**
 * Evaluated per stage against the immediately preceding executed stage's
 * outcome: ALWAYS runs regardless, ON_SUCCESS only if the previous executed
 * stage succeeded (the common case), ON_FAILURE only if it failed (e.g. a
 * "notify on failure" or rollback stage).
 */
public enum StageCondition {
    ALWAYS,
    ON_SUCCESS,
    ON_FAILURE
}
