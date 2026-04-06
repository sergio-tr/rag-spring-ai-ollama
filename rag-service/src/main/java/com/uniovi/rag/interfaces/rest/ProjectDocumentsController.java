package com.uniovi.rag.interfaces.rest;

import com.uniovi.rag.application.service.PromoteDocumentApplicationService;
import com.uniovi.rag.application.service.ProjectDocumentApplicationService;
import com.uniovi.rag.interfaces.rest.dto.ProjectDocumentDto;
import com.uniovi.rag.security.RagPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("${rag.api.product-base-path}/projects/{projectId}/documents")
public class ProjectDocumentsController {

    private final ProjectDocumentApplicationService projectDocumentApplicationService;
    private final PromoteDocumentApplicationService promoteDocumentApplicationService;

    public ProjectDocumentsController(
            ProjectDocumentApplicationService projectDocumentApplicationService,
            PromoteDocumentApplicationService promoteDocumentApplicationService) {
        this.projectDocumentApplicationService = projectDocumentApplicationService;
        this.promoteDocumentApplicationService = promoteDocumentApplicationService;
    }

    @GetMapping
    public List<ProjectDocumentDto> list(
            @AuthenticationPrincipal RagPrincipal principal, @PathVariable UUID projectId) {
        return projectDocumentApplicationService.listDocuments(principal.userId(), projectId);
    }

    @PostMapping("/{documentId}/promote")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void promote(
            @AuthenticationPrincipal RagPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID documentId) throws IOException {
        promoteDocumentApplicationService.promote(principal.userId(), projectId, documentId);
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
