package com.example.scheduler.model;

import com.example.scheduler.model.enums.CredentialType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A named, encrypted secret that jobs/stages reference by name (e.g.
 * "{{secrets.stripe-api-key}}") instead of embedding raw values. Values are
 * AES-256-GCM encrypted at rest (see service/CredentialService) and the
 * plaintext is never returned by the API.
 */
@Entity
@Table(name = "credentials", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Credential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CredentialType type;

    // HEADER: header to send the value under. HMAC: header for the signature.
    @Column(name = "header_name", length = 100)
    private String headerName;

    // Bearer token / API key / HMAC key / basic-auth username, by type.
    @JsonIgnore
    @Column(name = "encrypted_primary", nullable = false, length = 2000)
    private String encryptedPrimary;

    // BASIC only: the password.
    @JsonIgnore
    @Column(name = "encrypted_secondary", length = 2000)
    private String encryptedSecondary;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
