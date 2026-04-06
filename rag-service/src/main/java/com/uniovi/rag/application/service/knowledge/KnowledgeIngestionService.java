package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.application.service.ResolvedConfigSnapshotApplicationService;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntityFactory;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.interfaces.rest.dto.ProjectDocumentDto;
import com.uniovi.rag.service.document.ProjectDocumentIngestionService;
import com.uniovi.rag.service.project.ProjectAccessService;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * Binary persist, async pipeline trigger, and vector delete helpers; sole ingestion API for knowledge controllers.
 */
@Service
public class KnowledgeIngestionService {

    private static final String DEFAULT_ORIGINAL_FILENAME = "upload";

    private final KnowledgePipelineOrchestrator knowledgePipelineOrchestrator;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final ProjectDocumentIngestionService projectDocumentIngestionService;
    private final ProjectAccessService projectAccessService;
    private final ResolvedConfigSnapshotApplicationService resolvedConfigSnapshotApplicationService;

    public KnowledgeIngestionService(
            KnowledgePipelineOrchestrator knowledgePipelineOrchestrator,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            @Lazy ProjectDocumentIngestionService projectDocumentIngestionService,
            ProjectAccessService projectAccessService,
            ResolvedConfigSnapshotApplicationService resolvedConfigSnapshotApplicationService) {
        this.knowledgePipelineOrchestrator = knowledgePipelineOrchestrator;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.projectDocumentIngestionService = projectDocumentIngestionService;
        this.projectAccessService = projectAccessService;
        this.resolvedConfigSnapshotApplicationService = resolvedConfigSnapshotApplicationService;
    }

    @Transactional
    public void ingestFromTempFile(
            UUID userId,
            UUID projectId,
            UUID projectDocumentId,
            Path tempFile,
            String originalFilename,
            String contentType) {
        KnowledgeDocumentEntity row = knowledgeDocumentRepository.findById(projectDocumentId).orElse(null);
        if (row == null) {
            return;
        }
        Optional<UUID> conversationId =
                row.getCorpusScope() == CorpusScope.CHAT_LOCAL && row.getConversation() != null
                        ? Optional.of(row.getConversation().getId())
                        : Optional.empty();
        var snap =
                resolvedConfigSnapshotApplicationService.persistIngestionDefaultSnapshot(
                        userId, projectId, conversationId);
        knowledgePipelineOrchestrator.ingestFromTempFile(
                projectId,
                projectDocumentId,
                tempFile,
                originalFilename,
                contentType,
                snap.getId(),
                snap.getConfigHash());
    }

    public void deleteVectorChunksForDocument(UUID projectDocumentId) {
        knowledgePipelineOrchestrator.deleteVectorChunksForDocument(projectDocumentId);
    }

    public ProjectDocumentDto uploadProjectDocument(UUID userId, UUID projectId, MultipartFile file) throws IOException {
        ProjectEntity project = projectAccessService.requireOwnedProject(userId, projectId);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        String original =
                file.getOriginalFilename() != null ? file.getOriginalFilename() : DEFAULT_ORIGINAL_FILENAME;
        String ct = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        KnowledgeDocumentEntity row = KnowledgeDocumentEntityFactory.newIngesting(project, original);
        row = knowledgeDocumentRepository.save(row);

        Path temp = Files.createTempFile("rag-doc-", "-" + original.replaceAll("[^a-zA-Z0-9._-]", "_"));
        file.transferTo(temp.toFile());

        projectDocumentIngestionService.ingestFromTempFile(userId, projectId, row.getId(), temp, original, ct);
        return toDto(row);
    }

    public ProjectDocumentDto uploadConversationOverlay(
            UUID userId, UUID projectId, UUID conversationId, MultipartFile file) throws IOException {
        projectAccessService.requireOwnedProject(userId, projectId);
        ConversationEntity conv = projectAccessService.requireConversationForUser(userId, conversationId);
        if (!conv.getProject().getId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        String original =
                file.getOriginalFilename() != null ? file.getOriginalFilename() : DEFAULT_ORIGINAL_FILENAME;
        String ct = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        KnowledgeDocumentEntity row = KnowledgeDocumentEntityFactory.newChatLocalIngesting(conv.getProject(), conv, original);
        row = knowledgeDocumentRepository.save(row);
        Path temp = Files.createTempFile("rag-doc-overlay-", "-" + original.replaceAll("[^a-zA-Z0-9._-]", "_"));
        file.transferTo(temp.toFile());
        projectDocumentIngestionService.ingestFromTempFile(userId, projectId, row.getId(), temp, original, ct);
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
