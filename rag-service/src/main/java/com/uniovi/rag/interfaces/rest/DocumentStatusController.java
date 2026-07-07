package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.ProjectDocumentApplicationService;
import com.uniovi.rag.interfaces.rest.dto.ProjectDocumentDebugDto;
import com.uniovi.rag.interfaces.rest.dto.ProjectDocumentDto;
import com.uniovi.rag.security.RagPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("${rag.api.product-base-path}/documents")
public class DocumentStatusController {

    private final ProjectDocumentApplicationService projectDocumentApplicationService;

    public DocumentStatusController(ProjectDocumentApplicationService projectDocumentApplicationService) {
        this.projectDocumentApplicationService = projectDocumentApplicationService;
    }

    @GetMapping("/{documentId}/status")
    public ProjectDocumentDto status(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID documentId) {
        return projectDocumentApplicationService.documentStatus(principal.userId(), documentId);
    }

    @GetMapping("/{documentId}/debug")
    public ProjectDocumentDebugDto debug(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID documentId) {
        return projectDocumentApplicationService.documentDebug(principal.userId(), documentId);
    }

    /**
     * Re-ingest: requires a new file body (MVP - source files are not retained server-side).
     */
    @PostMapping(value = "/{documentId}/reindex", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProjectDocumentDto> reindex(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID documentId,
            @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        ProjectDocumentDto dto = projectDocumentApplicationService.reindexDocument(principal.userId(), documentId, file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(dto);
    }

    /**
     * Retry ingest using the stored binary already persisted for this document.
     */
    @PostMapping("/{documentId}/retry-ingest")
    public ResponseEntity<ProjectDocumentDto> retryIngest(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID documentId) {
        ProjectDocumentDto dto = projectDocumentApplicationService.retryIngestFromStoredBinary(principal.userId(), documentId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(dto);
    }
}
