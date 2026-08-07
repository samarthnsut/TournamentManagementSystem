package com.acme.tms.document.service;

import com.acme.tms.common.audit.AuditSnapshotProvider;
import com.acme.tms.document.domain.DocumentUpload;
import com.acme.tms.document.repository.DocumentRepository;
import com.acme.tms.document.repository.DocumentUploadRepository;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Audit snapshots for the document module.
 *
 * <p>Both matter mainly for the owning organization unit: without it an audit row belongs to no
 * tenant and nobody but a global reader can see it. Neither provider throws, and neither reads the
 * caller — the two rules in ADR-017.
 */
public final class DocumentAuditSnapshots {

    private DocumentAuditSnapshots() {
    }

    public static final String DOCUMENT = "Document";
    public static final String DOCUMENT_UPLOAD = "DocumentUpload";

    @Component
    public static class DocumentSnapshots implements AuditSnapshotProvider {

        private final DocumentRepository documentRepository;

        public DocumentSnapshots(DocumentRepository documentRepository) {
            this.documentRepository = documentRepository;
        }

        @Override
        public String entityType() {
            return DOCUMENT;
        }

        @Override
        public Optional<Object> snapshot(UUID entityId) {
            return documentRepository.findByIdAndDeletedAtIsNull(entityId)
                .map(document -> new Snapshot(
                    document.getOrganizationUnitId(),
                    document.getEntityType(),
                    document.getEntityId(),
                    document.getFileName(),
                    document.getMimeType(),
                    document.getSizeBytes()));
        }

        public record Snapshot(
            UUID organizationUnitId,
            String entityType,
            UUID entityId,
            String fileName,
            String mimeType,
            long sizeBytes
        ) {
        }
    }

    @Component
    public static class DocumentUploadSnapshots implements AuditSnapshotProvider {

        private final DocumentUploadRepository documentUploadRepository;

        public DocumentUploadSnapshots(DocumentUploadRepository documentUploadRepository) {
            this.documentUploadRepository = documentUploadRepository;
        }

        @Override
        public String entityType() {
            return DOCUMENT_UPLOAD;
        }

        @Override
        public Optional<Object> snapshot(UUID entityId) {
            return documentUploadRepository.findById(entityId).map(this::toSnapshot);
        }

        private Object toSnapshot(DocumentUpload upload) {
            return new Snapshot(
                upload.getOrganizationUnitId(),
                upload.getEntityType(),
                upload.getEntityId(),
                upload.getFileName(),
                upload.getMimeType(),
                upload.getDeclaredSizeBytes(),
                upload.isAttached());
        }

        public record Snapshot(
            UUID organizationUnitId,
            String entityType,
            UUID entityId,
            String fileName,
            String mimeType,
            long declaredSizeBytes,
            boolean attached
        ) {
        }
    }
}
