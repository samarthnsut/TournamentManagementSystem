package com.acme.tms.document.repository;

import com.acme.tms.document.domain.DocumentUpload;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentUploadRepository extends JpaRepository<DocumentUpload, UUID> {

    Optional<DocumentUpload> findById(UUID id);
}
