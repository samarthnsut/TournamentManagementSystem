package com.acme.tms.document.api;

import com.acme.tms.document.dto.DocumentResponse;
import com.acme.tms.document.dto.InitUploadRequest;
import com.acme.tms.document.dto.InitUploadResponse;
import com.acme.tms.document.service.DocumentService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 08 §14. No {@code @RequiresPermission} here on purpose: the scope of a document is the scope of
 * whatever it is attached to, which is only known once a polymorphic entityType/entityId pair has
 * been resolved. {@code DocumentService} runs that check against the owning unit — never against
 * anything the caller supplied.
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload-init")
    public InitUploadResponse initUpload(@Valid @RequestBody InitUploadRequest request) {
        return documentService.initUpload(request);
    }

    @PostMapping("/{uploadId}/attach")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse attach(@PathVariable UUID uploadId) {
        return documentService.attach(uploadId);
    }

    @GetMapping
    public List<DocumentResponse> list(
        @RequestParam String entityType,
        @RequestParam UUID entityId
    ) {
        return documentService.list(entityType, entityId);
    }
}
