package com.example.scheduler.model.dto;

import lombok.Data;

/** Body an external system POSTs to /api/callbacks/{executionId}?token=... */
@Data
public class CallbackRequest {
    /** "SUCCESS" or "FAILED" (case-insensitive); anything else counts as FAILED. */
    private String status;
    private String message;
    private Object output;
}
