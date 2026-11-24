package com.example.scheduler.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Declares one caller-suppliable value for a job, available as
 * {{params.name}} - the equivalent of a Jenkins parameterized build.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobParameterDefinition {
    private String name;
    private String label;
    private String defaultValue;
    @Builder.Default
    private boolean required = false;
}
