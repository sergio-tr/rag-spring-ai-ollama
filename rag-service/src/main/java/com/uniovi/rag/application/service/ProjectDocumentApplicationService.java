package com.uniovi.rag.application.service;

import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.interfaces.rest.dto.ProjectDocumentDto;
import com.uniovi.rag.interfaces.rest.dto.ProjectDocumentDebugDto;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.application.port.BinaryStoragePort;
import com.uniovi.rag.application.service.knowledge.DocumentIngestionHumanErrors;
import com.uniovi.rag.application.service.knowledge.KnowledgeIngestionService;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
    private final BinaryStoragePort binaryStoragePort;
    private final JdbcTemplate jdbcTemplate;

    public ProjectDocumentApplicationService(
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            KnowledgeIngestionService knowledgeIngestionService,
            ProjectAccessService projectAccessService,
            BinaryStoragePort binaryStoragePort,
            JdbcTemplate jdbcTemplate) {
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeIngestionService = knowledgeIngestionService;
        this.projectAccessService = projectAccessService;
        this.binaryStoragePort = binaryStoragePort;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ProjectDocumentDto> listDocuments(UUID userId, UUID projectId) {
        projectAccessService.requireOwnedProject(userId, projectId);
        return knowledgeDocumentRepository.findByProject_IdOrderByUploadedAtDesc(projectId).stream()
                .map(this::toDto)
                .toList();
    }

    public List<ProjectDocumentDto> listDocumentsForConversation(UUID userId, UUID projectId, UUID conversationId) {
        projectAccessService.requireConversationForUser(userId, conversationId);
        return knowledgeDocumentRepository.findByProject_IdOrderByUploadedAtDesc(projectId).stream()
                .filter(
                        d ->
                                d.getCorpusScope() == CorpusScope.PROJECT_SHARED
                                        || (d.getCorpusScope() == CorpusScope.CHAT_LOCAL
                                                && d.getConversation() != null
                                                && conversationId.equals(d.getConversation().getId())))
                .map(this::toDto)
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
        KnowledgeDocumentEntity row = doc.get();
        knowledgeIngestionService.deleteVectorChunksForDocument(documentId);
        deleteStoredBinaryQuietly(row.getStorageUri());
        knowledgeDocumentRepository.delete(row);
    }

    private void deleteStoredBinaryQuietly(String storageUri) {
        if (storageUri == null || storageUri.isBlank()) {
            return;
        }
        try {
            binaryStoragePort.delete(storageUri);
        } catch (IOException e) {
            // Best-effort; DB row removal still proceeds.
        }
    }

    public ProjectDocumentDto documentStatus(UUID userId, UUID documentId) {
        KnowledgeDocumentEntity e = projectAccessService.requireDocumentForUser(userId, documentId);
        return toDto(e);
    }

    public ProjectDocumentDebugDto documentDebug(UUID userId, UUID documentId) {
        KnowledgeDocumentEntity e = projectAccessService.requireDocumentForUser(userId, documentId);
        UUID projectId = e.getProject() != null ? e.getProject().getId() : null;
        long vectorRows = 0L;
        if (jdbcTemplate != null) {
            // Count vector_store rows by metadata's projectDocumentId (canonical key for our ingests).
            Long n =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM vector_store
                            WHERE metadata->>'projectDocumentId' = ?
                               OR metadata->>'documentId' = ?
                               OR metadata->>'document_id' = ?
                            """,
                            Long.class,
                            documentId.toString(),
                            documentId.toString(),
                            documentId.toString());
            vectorRows = n != null ? n : 0L;
        }
        return new ProjectDocumentDebugDto(
                e.getId(),
                projectId,
                e.getFileName(),
                e.getStatus() != null ? e.getStatus().name() : "UNKNOWN",
                e.getErrorMessage(),
                e.getChunkCount(),
                vectorRows,
                e.getUploadedAt(),
                e.getReindexedAt(),
                e.getCurrentIndexSnapshot() != null ? e.getCurrentIndexSnapshot().getId() : null,
                e.getCurrentIndexSnapshot() != null ? e.getCurrentIndexSnapshot().getSignatureHash() : null);
    }

    public ProjectDocumentDto reindexDocument(UUID userId, UUID documentId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        KnowledgeDocumentEntity row = projectAccessService.requireDocumentForUser(userId, documentId);
        UUID projectId = row.getProject().getId();
        row.setStatus(ProjectDocumentStatus.INGESTING);
        row.setErrorMessage(null);
        row.setReindexedAt(Instant.now());
        knowledgeDocumentRepository.save(row);

        String original =
                file.getOriginalFilename() != null ? file.getOriginalFilename() : DEFAULT_ORIGINAL_FILENAME;
        String ct = file.getContentType() != null ? file.getContentType() : MIME_APPLICATION_OCTET_STREAM;
        Path temp = Files.createTempFile("rag-reindex-", "-" + FILENAME_SANITIZE_PATTERN.matcher(original).replaceAll("_"));
        file.transferTo(temp.toFile());
        knowledgeIngestionService.ingestFromTempFileJoiningCallerTransaction(
                userId, projectId, documentId, temp, original, ct);
        return knowledgeIngestionService.loadTerminalProjectDocumentDto(documentId);
    }

    public ProjectDocumentDto retryIngestFromStoredBinary(UUID userId, UUID documentId) {
        KnowledgeDocumentEntity row = projectAccessService.requireDocumentForUser(userId, documentId);
        if (row.getStorageUri() == null || row.getStorageUri().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "STORED_BINARY_MISSING: Stored binary is missing; upload the file again to reindex.");
        }
        row.setStatus(ProjectDocumentStatus.INGESTING);
        row.setErrorMessage(null);
        row.setReindexedAt(Instant.now());
        knowledgeDocumentRepository.save(row);
        return knowledgeIngestionService.retryIngestFromStoredBinarySynchronously(
                userId, row.getProject().getId(), row.getId());
    }

    private ProjectDocumentDto toDto(KnowledgeDocumentEntity e) {
        boolean storagePresent =
                e.getStorageUri() != null
                        && !e.getStorageUri().isBlank()
                        && binaryStoragePort.isReadableNonEmpty(e.getStorageUri());
        return new ProjectDocumentDto(
                e.getId(),
                e.getFileName(),
                e.getStatus(),
                e.getChunkCount(),
                DocumentIngestionHumanErrors.humanize(e.getErrorMessage()),
                e.getUploadedAt(),
                e.getReindexedAt(),
                e.getCorpusScope(),
                e.getConversation() != null ? e.getConversation().getId() : null,
                e.getCurrentIndexSnapshot() != null ? e.getCurrentIndexSnapshot().getId() : null,
                e.getCurrentIndexSnapshot() != null ? e.getCurrentIndexSnapshot().getSignatureHash() : null,
                storagePresent);
    }
}
