package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.ProjectDocumentApplicationService;
import com.uniovi.rag.interfaces.rest.dto.ProjectDocumentDto;
import com.uniovi.rag.security.RagPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("${rag.api.product-base-path}/projects/{projectId}/documents")
public class ProjectDocumentsController {

    private final ProjectDocumentApplicationService projectDocumentApplicationService;

    public ProjectDocumentsController(ProjectDocumentApplicationService projectDocumentApplicationService) {
        this.projectDocumentApplicationService = projectDocumentApplicationService;
    }

    @GetMapping
    public List<ProjectDocumentDto> list(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID projectId) {
        return projectDocumentApplicationService.listDocuments(principal.userId(), projectId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProjectDocumentDto> upload(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID projectId,
            @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        ProjectDocumentDto dto = projectDocumentApplicationService.uploadDocument(principal.userId(), projectId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID documentId) {
        projectDocumentApplicationService.deleteDocument(principal.userId(), projectId, documentId);
        return ResponseEntity.noContent().build();
    }
}
