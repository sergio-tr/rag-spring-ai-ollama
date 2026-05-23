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
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusSummaryDto;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
    public static final String NO_DOCUMENTS = "NO_DOCUMENTS";

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
                        : "Lab evaluation corpus";
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

    @Transactional
    public EvaluationCorpusSummaryDto uploadDocument(UUID userId, UUID corpusId, MultipartFile file) throws IOException {
        EvaluationCorpusEntity corpus = requireOwnedCorpus(userId, corpusId);
        UUID indexProjectId = corpus.getIndexProject().getId();
        ProjectDocumentDto uploaded = knowledgeIngestionService.uploadProjectDocument(userId, indexProjectId, file);
        linkDocument(corpus, uploaded.id());
        touchCorpus(corpus, EvaluationCorpusSourceType.UPLOADED, false);
        return toSummary(corpus);
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
                    knowledgeDocumentRepository.findByIdAndProject_Id(sourceDocId, sourceProjectId).orElseThrow(
                            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found in project"));
            if (source.getCorpusScope() != CorpusScope.PROJECT_SHARED) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Only PROJECT_SHARED documents can be attached to an evaluation corpus");
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
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Document "
                            + source.getFileName()
                            + " has no stored binary; re-upload it in the source project before attaching.");
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
            knowledgeIngestionService.ingestFromTempFile(
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evaluation corpus not found"));
    }

    private ProjectEntity createIndexSandboxProject(UserEntity owner, String corpusName) {
        String projectName = "Lab corpus · " + truncate(corpusName, 48);
        ProjectEntity project = ProjectEntityFactory.newOwnedProject(owner, projectName, "Internal index scope for Lab evaluation corpus");
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
