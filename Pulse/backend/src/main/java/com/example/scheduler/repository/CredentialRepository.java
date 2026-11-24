package com.example.scheduler.repository;

import com.example.scheduler.model.Credential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CredentialRepository extends JpaRepository<Credential, UUID> {
    Optional<Credential> findByName(String name);
    boolean existsByName(String name);
}
