package com.uniovi.rag.application.service;

import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.interfaces.rest.dto.ProjectDocumentDto;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.application.service.knowledge.KnowledgeIngestionService;
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
import java.util.regex.Pattern;

/**
 * Project document listing, upload, delete, and reindex for the product REST API.
 */
@Service
public class ProjectDocumentApplicationService {

    private static final String DEFAULT_ORIGINAL_FILENAME = "upload";

    private static final String MIME_APPLICATION_OCTET_STREAM = "application/octet-stream";

    private static final Pattern FILENAME_SANITIZE_PATTERN = Pattern.compile("[^a-zA-Z0-9._-]");

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeIngestionService knowledgeIngestionService;
    private final ProjectAccessService projectAccessService;

    public ProjectDocumentApplicationService(
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            KnowledgeIngestionService knowledgeIngestionService,
            ProjectAccessService projectAccessService) {
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeIngestionService = knowledgeIngestionService;
        this.projectAccessService = projectAccessService;
    }

    public List<ProjectDocumentDto> listDocuments(UUID userId, UUID projectId) {
        projectAccessService.requireOwnedProject(userId, projectId);
        return knowledgeDocumentRepository.findByProject_IdOrderByUploadedAtDesc(projectId).stream()
                .map(ProjectDocumentApplicationService::toDto)
                .toList();
    }

    public ProjectDocumentDto uploadConversationOverlay(
            UUID userId, UUID projectId, UUID conversationId, MultipartFile file) throws IOException {
        return knowledgeIngestionService.uploadConversationOverlay(userId, projectId, conversationId, file);
    }

    public ProjectDocumentDto uploadDocument(UUID userId, UUID projectId, MultipartFile file) throws IOException {
        return knowledgeIngestionService.uploadProjectDocument(userId, projectId, file);
    }

    public void deleteDocument(UUID userId, UUID projectId, UUID documentId) {
        projectAccessService.requireOwnedProject(userId, projectId);
        var doc = knowledgeDocumentRepository.findByIdAndProject_Id(documentId, projectId);
        if (doc.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        knowledgeIngestionService.deleteVectorChunksForDocument(documentId);
        knowledgeDocumentRepository.delete(doc.get());
    }

    public ProjectDocumentDto documentStatus(UUID userId, UUID documentId) {
        KnowledgeDocumentEntity e = projectAccessService.requireDocumentForUser(userId, documentId);
        return toDto(e);
    }

    public ProjectDocumentDto reindexDocument(UUID userId, UUID documentId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        KnowledgeDocumentEntity row = projectAccessService.requireDocumentForUser(userId, documentId);
        UUID projectId = row.getProject().getId();
        row.setStatus(ProjectDocumentStatus.INGESTING);
        row.setErrorMessage(null);
        knowledgeDocumentRepository.save(row);

        String original =
                file.getOriginalFilename() != null ? file.getOriginalFilename() : DEFAULT_ORIGINAL_FILENAME;
        String ct = file.getContentType() != null ? file.getContentType() : MIME_APPLICATION_OCTET_STREAM;
        Path temp = Files.createTempFile("rag-reindex-", "-" + FILENAME_SANITIZE_PATTERN.matcher(original).replaceAll("_"));
        file.transferTo(temp.toFile());
        knowledgeIngestionService.ingestFromTempFile(userId, projectId, documentId, temp, original, ct);
        return toDto(row);
    }

    private static ProjectDocumentDto toDto(KnowledgeDocumentEntity e) {
        return new ProjectDocumentDto(
                e.getId(),
                e.getFileName(),
                e.getStatus(),
                e.getChunkCount(),
                e.getErrorMessage(),
                e.getUploadedAt(),
                e.getReindexedAt(),
                e.getCorpusScope(),
                e.getConversation() != null ? e.getConversation().getId() : null,
                e.getCurrentIndexSnapshot() != null ? e.getCurrentIndexSnapshot().getId() : null,
                e.getCurrentIndexSnapshot() != null ? e.getCurrentIndexSnapshot().getSignatureHash() : null,
                e.getStorageUri() != null && !e.getStorageUri().isBlank());
    }
}
