package com.example.scheduler.controller;

import com.example.scheduler.model.dto.CredentialRequest;
import com.example.scheduler.model.dto.CredentialResponse;
import com.example.scheduler.service.CredentialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/credentials")
@RequiredArgsConstructor
public class CredentialController {

    private final CredentialService credentialService;

    @PostMapping
    public ResponseEntity<CredentialResponse> create(@Valid @RequestBody CredentialRequest request) {
        return ResponseEntity.ok(credentialService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<CredentialResponse>> list() {
        return ResponseEntity.ok(credentialService.list());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        credentialService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
