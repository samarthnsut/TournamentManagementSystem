package com.acme.tms.document.service;

import com.acme.tms.common.audit.Audited;
import com.acme.tms.common.document.AttachableEntityResolver;
import com.acme.tms.common.exception.ConflictException;
import com.acme.tms.common.exception.ResourceNotFoundException;
import com.acme.tms.common.exception.ScopeAccessDeniedException;
import com.acme.tms.common.exception.ValidationException;
import com.acme.tms.common.security.CurrentUser;
import com.acme.tms.common.security.ScopeEvaluator;
import com.acme.tms.common.security.ScopeTarget;
import com.acme.tms.common.util.UuidV7Generator;
import com.acme.tms.document.domain.Document;
import com.acme.tms.document.domain.DocumentUpload;
import com.acme.tms.document.dto.DocumentResponse;
import com.acme.tms.document.dto.InitUploadRequest;
import com.acme.tms.document.dto.InitUploadResponse;
import com.acme.tms.document.repository.DocumentRepository;
import com.acme.tms.document.repository.DocumentUploadRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The two-phase upload from 08 §14.
 *
 * <p>Permission is checked against the *owning entity's* organization unit rather than against
 * anything the caller sent, and the scope check is done here rather than by
 * {@code @RequiresPermission} because the unit is only known after resolving a polymorphic
 * entityType/entityId pair.
 */
@Service
public class DocumentService {

    private static final String UPLOAD_PERMISSION = "document:upload";
    private static final String READ_PERMISSION = "document:read";

    private final DocumentRepository documentRepository;
    private final DocumentUploadRepository documentUploadRepository;
    private final ObjectStorage objectStorage;
    private final StorageProperties properties;
    private final ScopeEvaluator scopeEvaluator;
    private final CurrentUser currentUser;
    private final Map<String, AttachableEntityResolver> resolvers;

    public DocumentService(
        DocumentRepository documentRepository,
        DocumentUploadRepository documentUploadRepository,
        ObjectStorage objectStorage,
        StorageProperties properties,
        ScopeEvaluator scopeEvaluator,
        CurrentUser currentUser,
        List<AttachableEntityResolver> attachableEntityResolvers
    ) {
        this.documentRepository = documentRepository;
        this.documentUploadRepository = documentUploadRepository;
        this.objectStorage = objectStorage;
        this.properties = properties;
        this.scopeEvaluator = scopeEvaluator;
        this.currentUser = currentUser;

        Map<String, AttachableEntityResolver> byType = new HashMap<>();
        for (AttachableEntityResolver resolver : attachableEntityResolvers) {
            AttachableEntityResolver clash = byType.put(resolver.entityType(), resolver);
            if (clash != null) {
                throw new IllegalStateException(
                    "Duplicate AttachableEntityResolver for " + resolver.entityType() + ": "
                        + clash.getClass().getName() + " and " + resolver.getClass().getName());
            }
        }
        this.resolvers = Map.copyOf(byType);
    }

    @Transactional
    @Audited(value = "document:upload-init", entityType = "DocumentUpload")
    public InitUploadResponse initUpload(InitUploadRequest request) {
        String mimeType = normalize(request.mimeType());
        if (!properties.allowedMimeTypes().contains(mimeType)) {
            throw new ValidationException(
                "MIME_TYPE_NOT_ALLOWED",
                mimeType + " is not an accepted document type; allowed: "
                    + String.join(", ", properties.allowedMimeTypes()));
        }
        if (request.sizeBytes() <= 0 || request.sizeBytes() > properties.maxSizeBytes()) {
            throw new ValidationException(
                "FILE_TOO_LARGE",
                "Documents must be between 1 byte and " + properties.maxSizeBytes() + " bytes.");
        }

        UUID organizationUnitId = requireAttachTarget(request.entityType(), request.entityId());
        requirePermission(UPLOAD_PERMISSION, organizationUnitId);

        // The key is the server's to choose. A client-supplied key is a path traversal waiting to
        // happen, and would let one tenant overwrite another's object.
        UUID uploadId = UuidV7Generator.generate();
        String objectKey = organizationUnitId + "/" + request.entityType().toLowerCase(Locale.ROOT)
            + "/" + request.entityId() + "/" + uploadId + "/" + safeFileName(request.fileName());

        DocumentUpload upload = new DocumentUpload();
        upload.setOrganizationUnitId(organizationUnitId);
        upload.setEntityType(request.entityType());
        upload.setEntityId(request.entityId());
        upload.setFileName(request.fileName());
        upload.setObjectKey(objectKey);
        upload.setMimeType(mimeType);
        upload.setDeclaredSizeBytes(request.sizeBytes());
        upload.setRequestedBy(currentUser.requireUserId());
        upload.setExpiresAt(Instant.now().plusSeconds(properties.uploadUrlTtlSeconds()));
        documentUploadRepository.save(upload);

        return new InitUploadResponse(
            upload.getId(),
            objectStorage.presignUpload(objectKey, mimeType),
            upload.getExpiresAt());
    }

    /**
     * Confirms the upload landed and records the document.
     *
     * <p>The object is re-inspected rather than trusted. A presigned PUT constrains the content
     * type but not the length, so a caller who declared 400 KB at init can still push far more;
     * checking what actually arrived is the only place that can be caught.
     */
    @Transactional
    @Audited(value = "document:attach", entityType = "Document")
    public DocumentResponse attach(UUID uploadId) {
        DocumentUpload upload = documentUploadRepository.findById(uploadId)
            .orElseThrow(() -> new ResourceNotFoundException("UPLOAD_NOT_FOUND", "Upload not found."));

        if (upload.isAttached() || documentRepository.existsByFileUrlAndDeletedAtIsNull(upload.getObjectKey())) {
            throw new ConflictException("ALREADY_ATTACHED", "This upload has already been attached.");
        }

        // Re-check now, not just at init: authority can be revoked between the two calls.
        requirePermission(UPLOAD_PERMISSION, upload.getOrganizationUnitId());

        ObjectStorage.StoredObject stored = objectStorage.describe(upload.getObjectKey())
            .orElseThrow(() -> new ConflictException(
                "UPLOAD_NOT_COMPLETED", "No uploaded object was found for this upload."));

        if (stored.sizeBytes() > properties.maxSizeBytes()) {
            objectStorage.delete(upload.getObjectKey());
            throw new ValidationException(
                "FILE_TOO_LARGE",
                "The uploaded file is " + stored.sizeBytes() + " bytes, over the "
                    + properties.maxSizeBytes() + " byte limit.");
        }

        String actualMimeType = stored.mimeType() == null ? upload.getMimeType() : normalize(stored.mimeType());
        if (!properties.allowedMimeTypes().contains(actualMimeType)) {
            objectStorage.delete(upload.getObjectKey());
            throw new ValidationException(
                "MIME_TYPE_NOT_ALLOWED", actualMimeType + " is not an accepted document type.");
        }

        Document document = new Document();
        document.setOrganizationUnitId(upload.getOrganizationUnitId());
        document.setEntityType(upload.getEntityType());
        document.setEntityId(upload.getEntityId());
        document.setFileName(upload.getFileName());
        document.setFileUrl(upload.getObjectKey());
        document.setMimeType(actualMimeType);
        document.setSizeBytes(stored.sizeBytes());
        document.setUploadedBy(upload.getRequestedBy());
        documentRepository.save(document);

        upload.setAttachedAt(Instant.now());

        return toResponse(document, true);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(String entityType, UUID entityId) {
        UUID organizationUnitId = requireAttachTarget(entityType, entityId);
        requirePermission(READ_PERMISSION, organizationUnitId);

        return documentRepository
            .findByEntityTypeAndEntityIdAndDeletedAtIsNullOrderByCreatedAtDesc(entityType, entityId)
            .stream()
            .map(document -> toResponse(document, true))
            .toList();
    }

    /** Also the allow-list of attachable types: no resolver deployed means no such entity type. */
    private UUID requireAttachTarget(String entityType, UUID entityId) {
        AttachableEntityResolver resolver = resolvers.get(entityType);
        if (resolver == null) {
            throw new ValidationException(
                "ENTITY_TYPE_NOT_ATTACHABLE",
                "Documents cannot be attached to " + entityType + "; attachable types: "
                    + String.join(", ", resolvers.keySet()));
        }

        return resolver.organizationUnitOf(entityId)
            .orElseThrow(() -> new ResourceNotFoundException("ENTITY_NOT_FOUND", "Entity not found."));
    }

    private void requirePermission(String permission, UUID organizationUnitId) {
        if (!scopeEvaluator.hasPermission(
            currentUser.requireUserId(), permission, ScopeTarget.organization(organizationUnitId))) {
            throw new ScopeAccessDeniedException(
                "SCOPE_FORBIDDEN", "Missing permission " + permission + " at the requested scope.");
        }
    }

    private DocumentResponse toResponse(Document document, boolean withDownloadUrl) {
        return new DocumentResponse(
            document.getId(),
            document.getOrganizationUnitId(),
            document.getEntityType(),
            document.getEntityId(),
            document.getFileName(),
            document.getFileUrl(),
            document.getMimeType(),
            document.getSizeBytes(),
            document.getUploadedBy(),
            document.getCreatedAt(),
            withDownloadUrl
                ? objectStorage.presignDownload(document.getFileUrl(), document.getFileName())
                : null);
    }

    private String normalize(String mimeType) {
        // "application/pdf; charset=binary" is the same type as "application/pdf".
        String base = mimeType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        return base;
    }

    /**
     * The stored name is the client's; the *key* segment must not be.
     *
     * <p>Separators become underscores, which already defeats traversal — and the key is namespaced
     * under the organization unit besides. Runs of dots are then collapsed so no {@code ..} survives
     * into the key at all: S3 treats keys as opaque, but CDNs and S3-compatible gateways in front of
     * it do normalize paths, and a key that means one thing to the store and another to the proxy is
     * not worth the two lines it costs to avoid.
     */
    private String safeFileName(String fileName) {
        String cleaned = fileName
            .replaceAll("[^A-Za-z0-9._-]", "_")
            .replaceAll("\\.{2,}", ".");
        return cleaned.length() <= 120 ? cleaned : cleaned.substring(cleaned.length() - 120);
    }
}
