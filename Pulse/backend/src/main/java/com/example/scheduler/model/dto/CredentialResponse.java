package com.example.scheduler.model.dto;

import com.example.scheduler.model.Credential;
import com.example.scheduler.model.enums.CredentialType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Metadata only - encrypted values never leave the server once stored. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredentialResponse {
    private UUID id;
    private String name;
    private String description;
    private CredentialType type;
    private String headerName;
    private String createdBy;
    private Instant createdAt;

    public static CredentialResponse fromEntity(Credential c) {
        return CredentialResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .type(c.getType())
                .headerName(c.getHeaderName())
                .createdBy(c.getCreatedBy())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
