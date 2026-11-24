package com.example.scheduler.service;

import com.example.scheduler.exception.InvalidJobConfigException;
import com.example.scheduler.exception.ResourceNotFoundException;
import com.example.scheduler.model.Credential;
import com.example.scheduler.model.dto.CredentialRequest;
import com.example.scheduler.model.dto.CredentialResponse;
import com.example.scheduler.repository.CredentialRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Encrypted-at-rest secret store. Values are AES-256-GCM encrypted with the
 * key from app.security.credentials-key and only decrypted server-side,
 * immediately before being attached to an outgoing request. The API never
 * returns a decrypted value.
 */
@Service
public class CredentialService {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final CredentialRepository credentialRepository;
    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public CredentialService(CredentialRepository credentialRepository,
                              @Value("${app.security.credentials-key}") String base64Key) {
        this.credentialRepository = credentialRepository;
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "app.security.credentials-key must decode to exactly 32 bytes (AES-256); got " + keyBytes.length);
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public CredentialResponse create(CredentialRequest request) {
        if (credentialRepository.existsByName(request.getName())) {
            throw new InvalidJobConfigException("A credential named '" + request.getName() + "' already exists");
        }
        validate(request);

        Credential credential = Credential.builder()
                .name(request.getName())
                .description(request.getDescription())
                .type(request.getType())
                .headerName(request.getHeaderName())
                .encryptedPrimary(encrypt(request.getPrimaryValue()))
                .encryptedSecondary(request.getSecondaryValue() != null ? encrypt(request.getSecondaryValue()) : null)
                .createdBy(currentUsername())
                .build();

        return CredentialResponse.fromEntity(credentialRepository.save(credential));
    }

    public List<CredentialResponse> list() {
        return credentialRepository.findAll().stream().map(CredentialResponse::fromEntity).toList();
    }

    public void delete(UUID id) {
        if (!credentialRepository.existsById(id)) {
            throw new ResourceNotFoundException("Credential not found: " + id);
        }
        credentialRepository.deleteById(id);
    }

    /** Decrypted primary secret - internal use only, never exposed via the API. */
    public String decryptPrimary(Credential credential) {
        return decrypt(credential.getEncryptedPrimary());
    }

    public String decryptSecondary(Credential credential) {
        return credential.getEncryptedSecondary() == null ? null : decrypt(credential.getEncryptedSecondary());
    }

    /**
     * Every decrypted secret value (4+ chars), used only to mask them out of the
     * request/response captures stored for the run inspector.
     */
    public java.util.Set<String> allSecretValues() {
        java.util.Set<String> values = new java.util.LinkedHashSet<>();
        for (Credential credential : credentialRepository.findAll()) {
            try {
                addIfUsable(values, decryptPrimary(credential));
                addIfUsable(values, decryptSecondary(credential));
            } catch (Exception ignored) {
                // an unreadable credential cannot leak anything
            }
        }
        return values;
    }

    private void addIfUsable(java.util.Set<String> values, String value) {
        if (value != null && value.length() >= 4) {
            values.add(value);
        }
    }

    public Credential getByNameOrThrow(String name) {
        return credentialRepository.findByName(name)
                .orElseThrow(() -> new InvalidJobConfigException("Unknown credential: '" + name + "'"));
    }

    private void validate(CredentialRequest request) {
        if (request.getPrimaryValue() == null || request.getPrimaryValue().isBlank()) {
            throw new InvalidJobConfigException("Credential value is required");
        }
        switch (request.getType()) {
            case BASIC -> {
                if (request.getSecondaryValue() == null || request.getSecondaryValue().isBlank()) {
                    throw new InvalidJobConfigException("BASIC credentials require both a username (value) and password (secondaryValue)");
                }
            }
            case HEADER, HMAC -> {
                if (request.getHeaderName() == null || request.getHeaderName().isBlank()) {
                    throw new InvalidJobConfigException(request.getType() + " credentials require a headerName");
                }
            }
            default -> { /* BEARER needs nothing further */ }
        }
    }

    private String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt credential value", e);
        }
    }

    private String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt credential value", e);
        }
    }

    private String currentUsername() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            return "system";
        }
    }
}
