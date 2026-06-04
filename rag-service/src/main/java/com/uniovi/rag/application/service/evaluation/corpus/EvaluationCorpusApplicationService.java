package com.uniovi.rag.application.service.evaluation.corpus;

import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.evaluation.EvaluationCorpusSourceType;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.infrastructure.persistence.EvaluationCorpusDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationCorpusRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCorpusDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCorpusEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCorpusEntityFactory;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntityFactory;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.application.port.BinaryStoragePort;
import com.uniovi.rag.application.service.knowledge.DocumentIngestionHumanErrors;
import com.uniovi.rag.application.service.knowledge.KnowledgeIngestionService;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntityFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import com.uniovi.rag.interfaces.rest.dto.ProjectDocumentDto;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusAttachFromProjectRequest;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusCreateRequest;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusDocumentUploadItemDto;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusDocumentsUploadResponseDto;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusSummaryDto;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lab-scoped evaluation corpus lifecycle: create, attach documents, resolve index project for benchmarks.
 */
@Service
public class EvaluationCorpusApplicationService {

    public static final String NO_CORPUS_SELECTED = "NO_CORPUS_SELECTED";
    /** @deprecated Prefer {@link #KB_EMPTY}. */
    public static final String NO_DOCUMENTS = "KB_EMPTY";
    public static final String KB_NOT_FOUND = "KB_NOT_FOUND";
    public static final String KB_EMPTY = "KB_EMPTY";
    public static final String NO_READY_DOCUMENTS = "NO_READY_DOCUMENTS";
    public static final String DOCUMENT_PROCESSING_FAILED = "DOCUMENT_PROCESSING_FAILED";
    public static final String DUPLICATE_FILE = "DUPLICATE_FILE";
    /** Per-file upload status when content or name+size already exists in the corpus. */
    public static final String UPLOAD_STATUS_DUPLICATE = "DUPLICATE";

    private final EvaluationCorpusRepository evaluationCorpusRepository;
    private final EvaluationCorpusDocumentRepository evaluationCorpusDocumentRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectAccessService projectAccessService;
    private final KnowledgeIngestionService knowledgeIngestionService;
    private final BinaryStoragePort binaryStoragePort;

    public EvaluationCorpusApplicationService(
            EvaluationCorpusRepository evaluationCorpusRepository,
            EvaluationCorpusDocumentRepository evaluationCorpusDocumentRepository,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            ProjectAccessService projectAccessService,
            KnowledgeIngestionService knowledgeIngestionService,
            BinaryStoragePort binaryStoragePort) {
        this.evaluationCorpusRepository = evaluationCorpusRepository;
        this.evaluationCorpusDocumentRepository = evaluationCorpusDocumentRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.projectAccessService = projectAccessService;
        this.knowledgeIngestionService = knowledgeIngestionService;
        this.binaryStoragePort = binaryStoragePort;
    }

    @Transactional
    public EvaluationCorpusSummaryDto create(UUID userId, EvaluationCorpusCreateRequest request) {
        UserEntity owner = userRepository.findById(userId).orElseThrow();
        String name =
                request != null && request.name() != null && !request.name().isBlank()
                        ? request.name().trim()
                        : "Lab knowledge base";
        ProjectEntity indexProject = createIndexSandboxProject(owner, name);
        EvaluationCorpusEntity corpus =
                EvaluationCorpusEntityFactory.newCorpus(owner, name, EvaluationCorpusSourceType.UPLOADED, indexProject);
        corpus = evaluationCorpusRepository.save(corpus);
        return toSummary(corpus);
    }

    @Transactional(readOnly = true)
    public EvaluationCorpusSummaryDto getSummary(UUID userId, UUID corpusId) {
        EvaluationCorpusEntity corpus = requireOwnedCorpus(userId, corpusId);
        return toSummary(corpus);
    }

    @Transactional(readOnly = true)
    public EvaluationCorpusContext requireContext(UUID userId, UUID corpusId) {
        EvaluationCorpusEntity corpus = requireOwnedCorpus(userId, corpusId);
        List<KnowledgeDocumentEntity> docs = evaluationCorpusDocumentRepository.findDocumentsByCorpusId(corpusId);
        UUID indexProjectId =
                corpus.getIndexProject() != null ? corpus.getIndexProject().getId() : null;
        List<UUID> documentIds = docs.stream().map(KnowledgeDocumentEntity::getId).toList();
        return new EvaluationCorpusContext(corpusId, indexProjectId, documentIds, docs);
    }

    /**
     * Validates knowledge base exists, has documents, and at least one READY document for benchmark execution.
     */
    @Transactional(readOnly = true)
    public EvaluationCorpusContext requireReadyContext(UUID userId, UUID corpusId) {
        EvaluationCorpusContext context = requireContext(userId, corpusId);
        List<KnowledgeDocumentEntity> docs = context.documents();
        if (docs == null || docs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, KB_EMPTY);
        }
        if (!context.readyDocumentIds().isEmpty()) {
            return context;
        }
        boolean anyProcessing =
                docs.stream()
                        .anyMatch(d -> d != null && d.getStatus() == ProjectDocumentStatus.INGESTING);
        if (anyProcessing) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, NO_READY_DOCUMENTS);
        }
        boolean anyFailed =
                docs.stream().anyMatch(d -> d != null && d.getStatus() == ProjectDocumentStatus.ERROR);
        if (anyFailed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, DOCUMENT_PROCESSING_FAILED);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, NO_READY_DOCUMENTS);
    }

    @Transactional
    public EvaluationCorpusSummaryDto uploadDocument(UUID userId, UUID corpusId, MultipartFile file) throws IOException {
        return uploadDocuments(userId, corpusId, List.of(file)).corpus();
    }

    @Transactional
    public EvaluationCorpusDocumentsUploadResponseDto uploadDocuments(
            UUID userId, UUID corpusId, List<MultipartFile> files) throws IOException {
        EvaluationCorpusEntity corpus = requireOwnedCorpus(userId, corpusId);
        List<MultipartFile> normalized = normalizeUploadFiles(files);
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one file is required");
        }
        UUID indexProjectId = corpus.getIndexProject().getId();
        List<EvaluationCorpusDocumentUploadItemDto> uploads = new ArrayList<>();
        for (MultipartFile file : normalized) {
            uploads.add(uploadOneFile(userId, corpus, indexProjectId, file));
        }
        touchCorpus(corpus, EvaluationCorpusSourceType.UPLOADED, false);
        return new EvaluationCorpusDocumentsUploadResponseDto(toSummary(corpus), List.copyOf(uploads));
    }

    private EvaluationCorpusDocumentUploadItemDto uploadOneFile(
            UUID userId, EvaluationCorpusEntity corpus, UUID indexProjectId, MultipartFile file) {
        String fileName = resolveUploadFileName(file);
        if (file == null || file.isEmpty()) {
            return new EvaluationCorpusDocumentUploadItemDto(null, fileName, "FAILED", "File is empty");
        }
        try {
            byte[] bytes = file.getBytes();
            long byteSize = bytes.length;
            String contentChecksum = sha256Hex(bytes);
            Optional<KnowledgeDocumentEntity> duplicate =
                    findDuplicateInCorpus(corpus.getId(), fileName, byteSize, contentChecksum);
            if (duplicate.isPresent()) {
                KnowledgeDocumentEntity existing = duplicate.get();
                return new EvaluationCorpusDocumentUploadItemDto(
                        existing.getId(),
                        existing.getFileName() != null ? existing.getFileName() : fileName,
                        UPLOAD_STATUS_DUPLICATE,
                        DUPLICATE_FILE);
            }
            // Synchronous ingest: async uploadProjectDocument can run before the document row is visible.
            ProjectDocumentDto uploaded =
                    knowledgeIngestionService.ingestProjectSharedDocumentSynchronouslyFromBytes(
                            userId,
                            indexProjectId,
                            bytes,
                            fileName,
                            file.getContentType());
            linkDocument(corpus, uploaded.id());
            return new EvaluationCorpusDocumentUploadItemDto(
                    uploaded.id(),
                    uploaded.fileName() != null ? uploaded.fileName() : fileName,
                    toUploadStatus(uploaded.status()),
                    uploaded.errorMessage());
        } catch (ResponseStatusException ex) {
            return new EvaluationCorpusDocumentUploadItemDto(
                    null, fileName, "FAILED", ex.getReason() != null ? ex.getReason() : ex.getMessage());
        } catch (IOException ex) {
            return new EvaluationCorpusDocumentUploadItemDto(null, fileName, "FAILED", ex.getMessage());
        } catch (RuntimeException ex) {
            return new EvaluationCorpusDocumentUploadItemDto(
                    null, fileName, "FAILED", ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }
    }

    private static List<MultipartFile> normalizeUploadFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<MultipartFile> out = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                out.add(file);
            }
        }
        return List.copyOf(out);
    }

    private static String resolveUploadFileName(MultipartFile file) {
        if (file == null) {
            return "unknown";
        }
        String original = file.getOriginalFilename();
        return original != null && !original.isBlank() ? original : "unknown";
    }

    /** Lab upload API status labels (maps persisted {@link ProjectDocumentStatus}). */
    static String toUploadStatus(ProjectDocumentStatus status) {
        if (status == null) {
            return "PROCESSING";
        }
        return switch (status) {
            case READY -> "READY";
            case ERROR -> "FAILED";
            default -> "PROCESSING";
        };
    }

    @Transactional
    public EvaluationCorpusSummaryDto removeDocument(UUID userId, UUID corpusId, UUID documentId) {
        EvaluationCorpusEntity corpus = requireOwnedCorpus(userId, corpusId);
        if (documentId == null
                || !evaluationCorpusDocumentRepository.existsByCorpusIdAndDocumentId(corpusId, documentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found in knowledge base");
        }
        removeDocumentFromCorpus(corpus, corpusId, documentId);
        touchCorpus(corpus, corpus.getSourceType(), false);
        return toSummary(corpus);
    }

    @Transactional
    public EvaluationCorpusSummaryDto removeAllDocuments(UUID userId, UUID corpusId) {
        EvaluationCorpusEntity corpus = requireOwnedCorpus(userId, corpusId);
        List<KnowledgeDocumentEntity> docs =
                List.copyOf(evaluationCorpusDocumentRepository.findDocumentsByCorpusId(corpusId));
        for (KnowledgeDocumentEntity doc : docs) {
            if (doc != null && doc.getId() != null) {
                removeDocumentFromCorpus(corpus, corpusId, doc.getId());
            }
        }
        touchCorpus(corpus, corpus.getSourceType(), false);
        return toSummary(corpus);
    }

    @Transactional
    public EvaluationCorpusSummaryDto retryDocumentIngestion(UUID userId, UUID corpusId, UUID documentId) {
        EvaluationCorpusEntity corpus = requireOwnedCorpus(userId, corpusId);
        if (documentId == null
                || !evaluationCorpusDocumentRepository.existsByCorpusIdAndDocumentId(corpusId, documentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found in knowledge base");
        }
        UUID indexProjectId = corpus.getIndexProject().getId();
        KnowledgeDocumentEntity row =
                knowledgeDocumentRepository
                        .findByIdAndProject_Id(documentId, indexProjectId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "document not found in knowledge base"));
        if (row.getStatus() != ProjectDocumentStatus.ERROR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "document is not in failed state");
        }
        if (row.getStorageUri() == null || row.getStorageUri().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "document has no stored binary; re-upload the file");
        }
        knowledgeIngestionService.retryIngestFromStoredBinarySynchronously(userId, indexProjectId, documentId);
        touchCorpus(corpus, corpus.getSourceType(), false);
        return toSummary(corpus);
    }

    private void removeDocumentFromCorpus(
            EvaluationCorpusEntity corpus, UUID corpusId, UUID documentId) {
        evaluationCorpusDocumentRepository.deleteById(new EvaluationCorpusDocumentEntity.Key(corpusId, documentId));
        UUID indexProjectId = corpus.getIndexProject().getId();
        knowledgeIngestionService.deleteVectorChunksForDocument(documentId);
        knowledgeDocumentRepository
                .findByIdAndProject_Id(documentId, indexProjectId)
                .ifPresent(knowledgeDocumentRepository::delete);
    }

    @Transactional
    public EvaluationCorpusSummaryDto attachFromProject(
            UUID userId, UUID corpusId, EvaluationCorpusAttachFromProjectRequest request) {
        EvaluationCorpusEntity corpus = requireOwnedCorpus(userId, corpusId);
        if (request == null || request.documentIds() == null || request.documentIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documentIds is required");
        }
        UUID sourceProjectId = request.projectId();
        projectAccessService.requireOwnedProject(userId, sourceProjectId);
        UUID indexProjectId = corpus.getIndexProject().getId();
        boolean crossProject = !indexProjectId.equals(sourceProjectId);
        for (UUID sourceDocId : request.documentIds()) {
            if (sourceDocId == null) {
                continue;
            }
            KnowledgeDocumentEntity source =
                    knowledgeDocumentRepository
                            .findByIdAndProject_Id(sourceDocId, sourceProjectId)
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.BAD_REQUEST,
                                                    LabCorpusReasonCodes.DOCUMENT_IMPORT_NOT_FOUND));
            if (source.getCorpusScope() != CorpusScope.PROJECT_SHARED) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, LabCorpusReasonCodes.DOCUMENT_SCOPE_NOT_SHARED);
            }
            UUID linkedDocId;
            if (crossProject) {
                linkedDocId = copyDocumentIntoIndexProject(userId, corpus, source);
            } else {
                linkedDocId = source.getId();
            }
            linkDocument(corpus, linkedDocId);
        }
        EvaluationCorpusSourceType nextType =
                crossProject
                        ? EvaluationCorpusSourceType.MIXED
                        : EvaluationCorpusSourceType.FROM_PROJECT;
        touchCorpus(corpus, nextType, crossProject);
        return toSummary(corpus);
    }

    public UUID resolveIndexProjectId(EvaluationCorpusEntity corpus) {
        if (corpus == null || corpus.getIndexProject() == null) {
            return null;
        }
        return corpus.getIndexProject().getId();
    }

    private UUID copyDocumentIntoIndexProject(
            UUID userId, EvaluationCorpusEntity corpus, KnowledgeDocumentEntity source) {
        UUID indexProjectId = corpus.getIndexProject().getId();
        if (evaluationCorpusDocumentRepository.existsByCorpusIdAndDocumentId(corpus.getId(), source.getId())
                && indexProjectId.equals(source.getProject().getId())) {
            return source.getId();
        }
        if (source.getStorageUri() == null || source.getStorageUri().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, LabCorpusReasonCodes.DOCUMENT_BINARY_MISSING);
        }
        ProjectEntity indexProject = corpus.getIndexProject();
        KnowledgeDocumentEntity target = KnowledgeDocumentEntityFactory.newIngesting(indexProject, source.getFileName());
        target = knowledgeDocumentRepository.save(target);
        try {
            BinaryStoragePort.StoredObject copied =
                    binaryStoragePort.linkOrCopy(
                            source.getStorageUri(),
                            indexProjectId + "/" + target.getId() + "/lab-corpus.bin");
            target.setStorageUri(copied.relativeUri());
            target.setContentChecksum(copied.sha256Hex());
            target.setMimeType(source.getMimeType());
            target.setByteSize(source.getByteSize());
            target.setStatus(ProjectDocumentStatus.INGESTING);
            knowledgeDocumentRepository.save(target);

            Path temp = Files.createTempFile("rag-lab-corpus-copy-", ".bin");
            try (InputStream in = binaryStoragePort.openStream(copied.relativeUri())) {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            knowledgeIngestionService.ingestFromTempFileJoiningCallerTransaction(
                    userId,
                    indexProjectId,
                    target.getId(),
                    temp,
                    source.getFileName(),
                    source.getMimeType() != null ? source.getMimeType() : "application/octet-stream");
            return target.getId();
        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Failed to copy document into evaluation corpus: " + ex.getMessage());
        }
    }

    private Optional<KnowledgeDocumentEntity> findDuplicateInCorpus(
            UUID corpusId, String fileName, long byteSize, String contentChecksum) {
        String normalizedName = normalizeFileName(fileName);
        for (KnowledgeDocumentEntity doc : evaluationCorpusDocumentRepository.findDocumentsByCorpusId(corpusId)) {
            if (doc == null) {
                continue;
            }
            if (contentChecksum != null
                    && !contentChecksum.isBlank()
                    && contentChecksum.equals(doc.getContentChecksum())) {
                return Optional.of(doc);
            }
            if (!normalizedName.isEmpty() && normalizedName.equals(normalizeFileName(doc.getFileName()))) {
                Long existingSize = doc.getByteSize();
                if (existingSize != null && existingSize == byteSize) {
                    return Optional.of(doc);
                }
            }
        }
        return Optional.empty();
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data != null ? data : new byte[0]));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String normalizeFileName(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName.trim().toLowerCase(Locale.ROOT);
    }

    private void linkDocument(EvaluationCorpusEntity corpus, UUID documentId) {
        if (documentId == null || evaluationCorpusDocumentRepository.existsByCorpusIdAndDocumentId(corpus.getId(), documentId)) {
            return;
        }
        evaluationCorpusDocumentRepository.save(EvaluationCorpusDocumentEntity.link(corpus.getId(), documentId, Instant.now()));
    }

    private void touchCorpus(EvaluationCorpusEntity corpus, EvaluationCorpusSourceType incoming, boolean mixed) {
        if (mixed) {
            corpus.setSourceType(EvaluationCorpusSourceType.MIXED);
        } else if (corpus.getSourceType() == EvaluationCorpusSourceType.UPLOADED
                && incoming == EvaluationCorpusSourceType.FROM_PROJECT) {
            corpus.setSourceType(EvaluationCorpusSourceType.FROM_PROJECT);
        } else if (corpus.getSourceType() == EvaluationCorpusSourceType.FROM_PROJECT
                && incoming == EvaluationCorpusSourceType.UPLOADED) {
            corpus.setSourceType(EvaluationCorpusSourceType.MIXED);
        }
        corpus.setUpdatedAt(Instant.now());
        evaluationCorpusRepository.save(corpus);
    }

    private EvaluationCorpusEntity requireOwnedCorpus(UUID userId, UUID corpusId) {
        if (corpusId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, NO_CORPUS_SELECTED);
        }
        return evaluationCorpusRepository
                .findByIdAndOwner_Id(corpusId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, KB_NOT_FOUND));
    }

    private ProjectEntity createIndexSandboxProject(UserEntity owner, String corpusName) {
        String projectName = "Lab knowledge base · " + truncate(corpusName, 48);
        ProjectEntity project =
                ProjectEntityFactory.newOwnedProject(
                        owner, projectName, "Internal index scope for Lab knowledge base");
        return projectRepository.save(project);
    }

    private EvaluationCorpusSummaryDto toSummary(EvaluationCorpusEntity corpus) {
        List<KnowledgeDocumentEntity> docs =
                evaluationCorpusDocumentRepository.findDocumentsByCorpusId(corpus.getId());
        int ready = 0;
        int failed = 0;
        for (KnowledgeDocumentEntity doc : docs) {
            if (doc.getStatus() == ProjectDocumentStatus.READY) {
                ready++;
            } else if (doc.getStatus() == ProjectDocumentStatus.ERROR) {
                failed++;
            }
        }
        List<ProjectDocumentDto> documentDtos = docs.stream().map(EvaluationCorpusApplicationService::toDocumentDto).toList();
        return new EvaluationCorpusSummaryDto(
                corpus.getId(),
                corpus.getName(),
                corpus.getSourceType().name(),
                docs.size(),
                ready,
                failed,
                documentDtos,
                corpus.getCreatedAt(),
                corpus.getUpdatedAt());
    }

    private static ProjectDocumentDto toDocumentDto(KnowledgeDocumentEntity e) {
        String humanError =
                e.getStatus() == ProjectDocumentStatus.ERROR
                        ? DocumentIngestionHumanErrors.humanize(e.getErrorMessage())
                        : null;
        return new ProjectDocumentDto(
                e.getId(),
                e.getFileName(),
                e.getStatus(),
                e.getChunkCount(),
                humanError,
                e.getUploadedAt(),
                e.getReindexedAt(),
                e.getCorpusScope(),
                e.getConversation() != null ? e.getConversation().getId() : null,
                e.getCurrentIndexSnapshot() != null ? e.getCurrentIndexSnapshot().getId() : null,
                e.getCurrentIndexSnapshot() != null ? e.getCurrentIndexSnapshot().getSignatureHash() : null,
                e.getStorageUri() != null && !e.getStorageUri().isBlank());
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }

    public record EvaluationCorpusContext(
            UUID corpusId, UUID indexProjectId, List<UUID> documentIds, List<KnowledgeDocumentEntity> documents) {

        public List<UUID> readyDocumentIds() {
            List<UUID> ids = new ArrayList<>();
            if (documents == null) {
                return ids;
            }
            for (KnowledgeDocumentEntity doc : documents) {
                if (doc != null
                        && doc.getStatus() == ProjectDocumentStatus.READY
                        && doc.getStorageUri() != null
                        && !doc.getStorageUri().isBlank()) {
                    ids.add(doc.getId());
                }
            }
            return List.copyOf(new LinkedHashSet<>(ids));
        }
    }
}
