package com.example.scheduler.model.dto;

import com.example.scheduler.model.enums.CredentialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CredentialRequest {

    @NotBlank(message = "Credential name is required")
    @Size(max = 150)
    @Pattern(regexp = "[A-Za-z0-9_.\\-]+", message = "Name may only contain letters, digits, '_', '-' and '.' (it is referenced as {{secrets.name}})")
    private String name;

    @Size(max = 500)
    private String description;

    @NotNull(message = "Credential type is required")
    private CredentialType type;

    // Required for HEADER (header to send the value under) and HMAC (header
    // the computed signature is attached to).
    private String headerName;

    // BEARER: the token. BASIC: the username. HEADER: the header value.
    // HMAC: the signing secret.
    @NotBlank(message = "Credential value is required")
    private String primaryValue;

    // Only used for BASIC (the password).
    private String secondaryValue;
}
