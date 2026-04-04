package com.uniovi.rag.application.service;

import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.interfaces.rest.dto.ProjectDocumentDto;
import com.uniovi.rag.infrastructure.persistence.ProjectDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectDocumentEntityFactory;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.service.document.ProjectDocumentIngestionService;
import com.uniovi.rag.service.project.ProjectAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Project document listing, upload, delete, and reindex for the product REST API.
 */
@Service
public class ProjectDocumentApplicationService {

    private final ProjectDocumentRepository projectDocumentRepository;
    private final ProjectDocumentIngestionService ingestionService;
    private final ProjectAccessService projectAccessService;

    public ProjectDocumentApplicationService(
            ProjectDocumentRepository projectDocumentRepository,
            ProjectDocumentIngestionService ingestionService,
            ProjectAccessService projectAccessService) {
        this.projectDocumentRepository = projectDocumentRepository;
        this.ingestionService = ingestionService;
        this.projectAccessService = projectAccessService;
    }

    public List<ProjectDocumentDto> listDocuments(UUID userId, UUID projectId) {
        projectAccessService.requireOwnedProject(userId, projectId);
        return projectDocumentRepository.findByProject_IdOrderByUploadedAtDesc(projectId).stream()
                .map(ProjectDocumentApplicationService::toDto)
                .toList();
    }

    public ProjectDocumentDto uploadDocument(UUID userId, UUID projectId, MultipartFile file) throws IOException {
        ProjectEntity project = projectAccessService.requireOwnedProject(userId, projectId);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        String ct = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        ProjectDocumentEntity row = ProjectDocumentEntityFactory.newIngesting(project, original);
        row = projectDocumentRepository.save(row);

        Path temp = Files.createTempFile("rag-doc-", "-" + original.replaceAll("[^a-zA-Z0-9._-]", "_"));
        file.transferTo(temp.toFile());

        ingestionService.ingestFromTempFile(projectId, row.getId(), temp, original, ct);
        return toDto(row);
    }

    public void deleteDocument(UUID userId, UUID projectId, UUID documentId) {
        projectAccessService.requireOwnedProject(userId, projectId);
        var doc = projectDocumentRepository.findByIdAndProject_Id(documentId, projectId);
        if (doc.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        ingestionService.deleteVectorChunksForProjectDocument(documentId);
        projectDocumentRepository.delete(doc.get());
    }

    public ProjectDocumentDto documentStatus(UUID userId, UUID documentId) {
        ProjectDocumentEntity e = projectAccessService.requireDocumentForUser(userId, documentId);
        return toDto(e);
    }

    public ProjectDocumentDto reindexDocument(UUID userId, UUID documentId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        ProjectDocumentEntity row = projectAccessService.requireDocumentForUser(userId, documentId);
        UUID projectId = row.getProject().getId();
        row.setStatus(ProjectDocumentStatus.INGESTING);
        row.setErrorMessage(null);
        projectDocumentRepository.save(row);

        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        String ct = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        Path temp = Files.createTempFile("rag-reindex-", "-" + original.replaceAll("[^a-zA-Z0-9._-]", "_"));
        file.transferTo(temp.toFile());
        ingestionService.ingestFromTempFile(projectId, documentId, temp, original, ct);
        return toDto(row);
    }

    private static ProjectDocumentDto toDto(ProjectDocumentEntity e) {
        return new ProjectDocumentDto(
                e.getId(),
                e.getFileName(),
                e.getStatus(),
                e.getChunkCount(),
                e.getErrorMessage(),
                e.getUploadedAt(),
                e.getReindexedAt());
    }
}
