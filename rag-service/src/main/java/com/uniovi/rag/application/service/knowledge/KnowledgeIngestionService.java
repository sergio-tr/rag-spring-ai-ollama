package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.application.port.BinaryStoragePort;
import com.uniovi.rag.application.service.ResolvedConfigSnapshotApplicationService;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.llm.ModelPreflightService;
import com.uniovi.rag.application.service.runtime.llm.OrchestrationLlmConfigScope;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntityFactory;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.interfaces.rest.dto.ProjectDocumentDto;
import com.uniovi.rag.application.service.knowledge.document.ProjectDocumentIngestionService;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;


import java.io.IOException;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import java.util.UUID;

/**
 * Binary persist, async pipeline trigger, and vector delete helpers; sole ingestion API for knowledge controllers.
 */
@Service
public class KnowledgeIngestionService {

    private static final String DEFAULT_ORIGINAL_FILENAME = "upload";

    private static final String MIME_APPLICATION_OCTET_STREAM = "application/octet-stream";

    private final KnowledgePipelineOrchestrator knowledgePipelineOrchestrator;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final ProjectDocumentIngestionService projectDocumentIngestionService;
    private final ProjectAccessService projectAccessService;
    private final ResolvedConfigSnapshotApplicationService resolvedConfigSnapshotApplicationService;
    private final ResolvedLlmConfigResolver resolvedLlmConfigResolver;
    private final EntityManager entityManager;
    private final BinaryStoragePort binaryStoragePort;
    private final ModelPreflightService modelPreflightService;

    public KnowledgeIngestionService(
            KnowledgePipelineOrchestrator knowledgePipelineOrchestrator,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            @Lazy ProjectDocumentIngestionService projectDocumentIngestionService,
            ProjectAccessService projectAccessService,
            ResolvedConfigSnapshotApplicationService resolvedConfigSnapshotApplicationService,
            ResolvedLlmConfigResolver resolvedLlmConfigResolver,
            EntityManager entityManager,
            BinaryStoragePort binaryStoragePort,
            ModelPreflightService modelPreflightService) {
        this.knowledgePipelineOrchestrator = knowledgePipelineOrchestrator;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.projectDocumentIngestionService = projectDocumentIngestionService;
        this.projectAccessService = projectAccessService;
        this.resolvedConfigSnapshotApplicationService = resolvedConfigSnapshotApplicationService;
        this.resolvedLlmConfigResolver = resolvedLlmConfigResolver;
        this.entityManager = entityManager;
        this.binaryStoragePort = binaryStoragePort;
        this.modelPreflightService = modelPreflightService;
    }

    /**
     * Synchronous PROJECT_SHARED ingest from bytes (same pipeline as HTTP upload). Caller handles deduplication.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProjectDocumentDto ingestProjectSharedDocumentSynchronouslyFromBytes(
            UUID userId,
            UUID projectId,
            byte[] bytes,
            String originalFilename,
            String contentType) throws IOException {
        ProjectEntity project = projectAccessService.requireOwnedProject(userId, projectId);
        if (bytes == null || bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty_bytes");
        }
        String original =
                originalFilename != null && !originalFilename.isBlank() ? originalFilename : DEFAULT_ORIGINAL_FILENAME;
        String ct = contentType != null ? contentType : MIME_APPLICATION_OCTET_STREAM;

        KnowledgeDocumentEntity row = KnowledgeDocumentEntityFactory.newIngesting(project, original);
        row = knowledgeDocumentRepository.save(row);
        entityManager.flush();

        Path temp = createPrivateTempFile("rag-lab-bootstrap-", original);
        Files.write(temp, bytes);

        ingestFromTempFileJoiningCallerTransaction(userId, projectId, row.getId(), temp, original, ct);

        return toDto(requireTerminalDocumentAfterSyncIngest(row.getId()));
    }

    /**
     * Re-ingest an existing project document from fresh bytes (same document id). Used by Lab classpath bootstrap retries.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProjectDocumentDto reingestProjectSharedDocumentSynchronouslyFromBytes(
            UUID userId,
            UUID projectId,
            UUID projectDocumentId,
            byte[] bytes,
            String originalFilename,
            String contentType) throws IOException {
        projectAccessService.requireOwnedProject(userId, projectId);
        KnowledgeDocumentEntity row =
                knowledgeDocumentRepository.findByIdAndProject_Id(projectDocumentId, projectId).orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "project_document"));
        if (bytes == null || bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty_bytes");
        }
        String original =
                originalFilename != null && !originalFilename.isBlank()
                        ? originalFilename
                        : (row.getFileName() != null ? row.getFileName() : DEFAULT_ORIGINAL_FILENAME);
        String ct = contentType != null ? contentType : MIME_APPLICATION_OCTET_STREAM;

        Path temp = createPrivateTempFile("rag-lab-bootstrap-retry-", original);
        Files.write(temp, bytes);

        entityManager.flush();
        ingestFromTempFileJoiningCallerTransaction(userId, projectId, row.getId(), temp, original, ct);

        return toDto(requireTerminalDocumentAfterSyncIngest(row.getId()));
    }

    /**
     * Ingest from a temp file in the caller's Spring transaction (no extra orchestrator {@code REQUIRES_NEW}).
     * Used by lab corpus synchronous upload paths that already run in an isolated transaction.
     */
    public void ingestFromTempFileJoiningCallerTransaction(
            UUID userId,
            UUID projectId,
            UUID projectDocumentId,
            Path tempFile,
            String originalFilename,
            String contentType) {
        runIngestFromTempFile(
                userId, projectId, projectDocumentId, tempFile, originalFilename, contentType, false);
    }

    public void ingestFromTempFile(
            UUID userId,
            UUID projectId,
            UUID projectDocumentId,
            Path tempFile,
            String originalFilename,
            String contentType) {
        runIngestFromTempFile(
                userId, projectId, projectDocumentId, tempFile, originalFilename, contentType, true);
    }

    private void runIngestFromTempFile(
            UUID userId,
            UUID projectId,
            UUID projectDocumentId,
            Path tempFile,
            String originalFilename,
            String contentType,
            boolean isolateOrchestratorTransaction) {
        if (!isolateOrchestratorTransaction) {
            entityManager.flush();
        }
        KnowledgeDocumentEntity row = knowledgeDocumentRepository.findById(projectDocumentId).orElse(null);
        if (row == null) {
            return;
        }
        Optional<UUID> conversationId =
                row.getCorpusScope() == CorpusScope.CHAT_LOCAL && row.getConversation() != null
                        ? Optional.of(row.getConversation().getId())
                        : Optional.empty();
        try {
            var snap =
                    resolvedConfigSnapshotApplicationService.persistIngestionDefaultSnapshot(
                            userId, projectId, conversationId);
            runWithIngestLlmScope(
                    userId,
                    projectId,
                    () -> {
                        if (isolateOrchestratorTransaction) {
                            knowledgePipelineOrchestrator.ingestFromTempFile(
                                    projectId,
                                    projectDocumentId,
                                    tempFile,
                                    originalFilename,
                                    contentType,
                                    snap.getId(),
                                    snap.getConfigHash());
                        } else {
                            knowledgePipelineOrchestrator.ingestFromTempFileInCurrentTransaction(
                                    projectId,
                                    projectDocumentId,
                                    tempFile,
                                    originalFilename,
                                    contentType,
                                    snap.getId(),
                                    snap.getConfigHash());
                        }
                    });
        } catch (Exception e) {
            KnowledgeDocumentEntity rowErr = knowledgeDocumentRepository.findById(projectDocumentId).orElse(null);
            if (rowErr != null) {
                rowErr.setStatus(ProjectDocumentStatus.ERROR);
                rowErr.setErrorMessage(DocumentIngestionFailureCodes.classify(e));
                knowledgeDocumentRepository.save(rowErr);
            }
            // Do not rethrow: orchestrator already persisted ERROR for ingest failures; callers expect
            // a terminal document row and a 200 upload response with per-file FAILED status when needed.
        }
    }

    public void ingestFromStoredBinary(
            UUID userId,
            UUID projectId,
            UUID projectDocumentId,
            UUID resolvedConfigSnapshotId,
            String resolvedConfigHash) {
        try {
            runWithIngestLlmScope(
                    userId,
                    projectId,
                    () ->
                            knowledgePipelineOrchestrator.ingestFromStoredBinary(
                                    projectId,
                                    projectDocumentId,
                                    resolvedConfigSnapshotId,
                                    resolvedConfigHash));
        } catch (Exception e) {
            KnowledgeDocumentEntity rowErr = knowledgeDocumentRepository.findById(projectDocumentId).orElse(null);
            if (rowErr != null) {
                rowErr.setStatus(ProjectDocumentStatus.ERROR);
                rowErr.setErrorMessage(DocumentIngestionFailureCodes.classify(e));
                knowledgeDocumentRepository.save(rowErr);
            }
            throw e;
        }
    }

    public void deleteVectorChunksForDocument(UUID projectDocumentId) {
        knowledgePipelineOrchestrator.deleteVectorChunksForDocument(projectDocumentId);
    }

    public void retryIngestFromStoredBinary(UUID userId, UUID projectId, UUID projectDocumentId) {
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
        projectDocumentIngestionService.ingestFromStoredBinary(
                userId, projectId, projectDocumentId, snap.getId(), snap.getConfigHash());
    }

    /**
     * Synchronous retry from stored binary; returns terminal row (READY or ERROR).
     */
    @Transactional
    public ProjectDocumentDto retryIngestFromStoredBinarySynchronously(
            UUID userId, UUID projectId, UUID projectDocumentId) {
        KnowledgeDocumentEntity row = knowledgeDocumentRepository.findById(projectDocumentId).orElse(null);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "project_document");
        }
        if (row.getStorageUri() == null || row.getStorageUri().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    DocumentIngestionFailureCodes.format(
                            DocumentIngestionFailureCodes.FAILED_GENERIC,
                            "Stored binary is missing; upload the file again."));
        }
        row.setStatus(ProjectDocumentStatus.INGESTING);
        row.setErrorMessage(null);
        row.setReindexedAt(Instant.now());
        knowledgeDocumentRepository.save(row);
        entityManager.flush();

        Optional<UUID> conversationId =
                row.getCorpusScope() == CorpusScope.CHAT_LOCAL && row.getConversation() != null
                        ? Optional.of(row.getConversation().getId())
                        : Optional.empty();
        var snap =
                resolvedConfigSnapshotApplicationService.persistIngestionDefaultSnapshot(
                        userId, projectId, conversationId);
        entityManager.flush();
        try {
            knowledgePipelineOrchestrator.ingestFromStoredBinaryInCurrentTransaction(
                    projectId, projectDocumentId, snap.getId(), snap.getConfigHash());
        } catch (Exception e) {
            KnowledgeDocumentEntity rowErr = knowledgeDocumentRepository.findById(projectDocumentId).orElse(null);
            if (rowErr != null && rowErr.getStatus() == ProjectDocumentStatus.INGESTING) {
                rowErr.setStatus(ProjectDocumentStatus.ERROR);
                rowErr.setErrorMessage(DocumentIngestionFailureCodes.classify(e));
                knowledgeDocumentRepository.save(rowErr);
            }
        }
        return loadTerminalProjectDocumentDto(projectDocumentId);
    }

    @Transactional
    public ProjectDocumentDto uploadProjectDocument(UUID userId, UUID projectId, MultipartFile file) throws IOException {
        ProjectEntity project = projectAccessService.requireOwnedProject(userId, projectId);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        String original =
                file.getOriginalFilename() != null ? file.getOriginalFilename() : DEFAULT_ORIGINAL_FILENAME;
        String ct = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        Optional<KnowledgeDocumentEntity> existing =
                knowledgeDocumentRepository.findFirstByProject_IdAndFileNameAndCorpusScopeAndConversationIsNull(
                        projectId, original, CorpusScope.PROJECT_SHARED);
        if (existing.isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A document with this filename already exists in the project.");
        }

        modelPreflightService.requireProjectEmbeddingForIndexing(userId, projectId);

        KnowledgeDocumentEntity row = KnowledgeDocumentEntityFactory.newIngesting(project, original);
        row = knowledgeDocumentRepository.save(row);
        entityManager.flush();

        Path temp = createPrivateTempFile("rag-doc-", original);
        file.transferTo(temp.toFile());

        ingestFromTempFileJoiningCallerTransaction(userId, projectId, row.getId(), temp, original, ct);
        return toDto(requireTerminalDocumentAfterSyncIngest(row.getId()));
    }

    @Transactional
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

        Optional<KnowledgeDocumentEntity> existing =
                knowledgeDocumentRepository.findFirstByProject_IdAndFileNameAndCorpusScopeAndConversation_Id(
                        projectId, original, CorpusScope.CHAT_LOCAL, conversationId);
        if (existing.isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A document with this filename already exists in the conversation.");
        }

        modelPreflightService.requireProjectEmbeddingForIndexing(userId, projectId);

        KnowledgeDocumentEntity row = KnowledgeDocumentEntityFactory.newChatLocalIngesting(conv.getProject(), conv, original);
        row = knowledgeDocumentRepository.save(row);
        entityManager.flush();
        Path temp = createPrivateTempFile("rag-doc-overlay-", original);
        file.transferTo(temp.toFile());
        ingestFromTempFileJoiningCallerTransaction(userId, projectId, row.getId(), temp, original, ct);
        return toDto(requireTerminalDocumentAfterSyncIngest(row.getId()));
    }

    private static Path createPrivateTempFile(String prefix, String originalFilename) throws IOException {
        String original =
                originalFilename != null && !originalFilename.isBlank() ? originalFilename : DEFAULT_ORIGINAL_FILENAME;
        String safeSuffix = "-" + original.replaceAll("[^a-zA-Z0-9._-]", "_");

        // Create a private temp directory (0700 on POSIX) and put the file inside it to avoid any
        // ambiguity around publicly writable shared temp directories.
        Path dir = createPrivateTempDir(prefix);
        Path p = Files.createTempFile(dir, prefix, safeSuffix, posix0600IfSupported());
        p.toFile().deleteOnExit();
        return p;
    }

    private static Path createPrivateTempDir(String prefix) throws IOException {
        Path dir = Files.createTempDirectory(prefix, posix0700IfSupported());
        dir.toFile().deleteOnExit();
        return dir;
    }

    private static FileAttribute<?>[] posix0700IfSupported() {
        try {
            return new FileAttribute<?>[] {
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rwx------"))
            };
        } catch (UnsupportedOperationException ex) {
            return new FileAttribute<?>[0];
        }
    }

    private static FileAttribute<?>[] posix0600IfSupported() {
        try {
            return new FileAttribute<?>[] {
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------"))
            };
        } catch (UnsupportedOperationException ex) {
            return new FileAttribute<?>[0];
        }
    }

    /**
     * Reload row after synchronous pipeline ingest in the caller transaction.
     *
     * <p>Flushes pending READY/ERROR updates, then {@link EntityManager#clear()} so the reload reflects
     * pipeline work completed in the same transaction (lab corpus sync upload). Without flush, a
     * post-ingest clear would reload the pre-ingest {@link ProjectDocumentStatus#INGESTING} row from the
     * database and falsely mark the document as stale.
     */
    private KnowledgeDocumentEntity reloadProjectDocumentAfterIngest(UUID projectDocumentId) {
        entityManager.flush();
        entityManager.clear();
        return knowledgeDocumentRepository
                .findById(projectDocumentId)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "project_document"));
    }

    private KnowledgeDocumentEntity requireTerminalDocumentAfterSyncIngest(UUID projectDocumentId) {
        KnowledgeDocumentEntity row = reloadProjectDocumentAfterIngest(projectDocumentId);
        if (row.getStatus() == ProjectDocumentStatus.INGESTING) {
            row.setStatus(ProjectDocumentStatus.ERROR);
            row.setErrorMessage(
                    DocumentIngestionFailureCodes.format(
                            DocumentIngestionFailureCodes.FAILED_STALE_INGESTION,
                            "Ingestion did not reach a terminal state before the request completed."));
            knowledgeDocumentRepository.save(row);
        }
        return row;
    }

    /** Reload row after synchronous ingest (READY or ERROR). */
    public ProjectDocumentDto loadTerminalProjectDocumentDto(UUID projectDocumentId) {
        return toDto(requireTerminalDocumentAfterSyncIngest(projectDocumentId));
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

    /**
     * Binds {@link OrchestrationLlmConfigScope} for async/sync ingest so embedding resolution uses the project
     * provider catalog (not bare application defaults).
     */
    private void runWithIngestLlmScope(UUID userId, UUID projectId, Runnable ingestWork) {
        ResolvedLlmConfig llm = resolvedLlmConfigResolver.resolve(userId, projectId, null);
        OrchestrationLlmConfigScope.bind(llm);
        try {
            ingestWork.run();
        } finally {
            OrchestrationLlmConfigScope.clear();
        }
    }
}
