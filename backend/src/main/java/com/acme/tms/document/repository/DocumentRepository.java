package com.acme.tms.document.repository;

import com.acme.tms.document.domain.Document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndDeletedAtIsNull(UUID id);

    List<Document> findByEntityTypeAndEntityIdAndDeletedAtIsNullOrderByCreatedAtDesc(
        String entityType, UUID entityId);

    boolean existsByFileUrlAndDeletedAtIsNull(String fileUrl);
}
