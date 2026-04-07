package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.application.port.BinaryStoragePort;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.IndexSignature;
import com.uniovi.rag.domain.knowledge.KnowledgeBuildProjection;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotScopeType;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.knowledge.SnapshotSignatureHasher;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.service.document.ProjectDocumentIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sole write path for corpus {@code document_artifact} rows and {@code vector_store} inserts.
 */
@Service
public class KnowledgePipelineOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(KnowledgePipelineOrchestrator.class);

    private final JdbcTemplate jdbcTemplate;
    private final ProjectDocumentIngestionService projectDocumentIngestionService;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final BinaryStoragePort binaryStoragePort;
    private final KnowledgeSnapshotService knowledgeSnapshotService;
    private final KnowledgeIndexingService knowledgeIndexingService;
    private final int chunkMaxChars;
    private final String embeddingModelId;
    private final MaterializationStrategy materializationStrategy;

    public KnowledgePipelineOrchestrator(
            JdbcTemplate jdbcTemplate,
            @Lazy ProjectDocumentIngestionService projectDocumentIngestionService,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            BinaryStoragePort binaryStoragePort,
            KnowledgeSnapshotService knowledgeSnapshotService,
            KnowledgeIndexingService knowledgeIndexingService,
            @Value("${rag.chunk.max-chars:400}") int chunkMaxChars,
            @Value("${spring.ai.ollama.embedding.model:mxbai-embed-large}") String embeddingModelId,
            @Value("${rag.knowledge.materialization-strategy:CHUNK_LEVEL}") String materializationStrategyRaw) {
        this.jdbcTemplate = jdbcTemplate;
        this.projectDocumentIngestionService = projectDocumentIngestionService;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.binaryStoragePort = binaryStoragePort;
        this.knowledgeSnapshotService = knowledgeSnapshotService;
        this.knowledgeIndexingService = knowledgeIndexingService;
        this.chunkMaxChars = chunkMaxChars > 0 ? chunkMaxChars : 400;
        this.embeddingModelId = embeddingModelId;
        this.materializationStrategy = parseMaterializationStrategy(materializationStrategyRaw);
    }

    private static MaterializationStrategy parseMaterializationStrategy(String raw) {
        if (raw == null || raw.isBlank()) {
            return MaterializationStrategy.CHUNK_LEVEL;
        }
        try {
            return MaterializationStrategy.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return MaterializationStrategy.CHUNK_LEVEL;
        }
    }

    @Transactional
    public void ingestFromTempFile(
            UUID projectId,
            UUID projectDocumentId,
            Path tempFile,
            String originalFilename,
            String contentType,
            UUID resolvedConfigSnapshotId,
            String resolvedConfigHash) {
        KnowledgeDocumentEntity row = knowledgeDocumentRepository.findById(projectDocumentId).orElse(null);
        if (row == null) {
            log.warn("Project document {} not found, skipping ingest", projectDocumentId);
            deleteTempQuietly(tempFile);
            return;
        }
        KnowledgeIndexSnapshotEntity building = null;
        try {
            try (InputStream in = Files.newInputStream(tempFile)) {
                long size = Files.size(tempFile);
                BinaryStoragePort.StoredObject stored =
                        binaryStoragePort.store(in, size, projectId + "/" + projectDocumentId + "/source.bin");
                row.setStorageUri(stored.relativeUri());
                row.setContentChecksum(stored.sha256Hex());
                row.setByteSize(size);
                row.setMimeType(contentType);
            }
            knowledgeDocumentRepository.save(row);
            final KnowledgeDocumentEntity rowReloaded =
                    knowledgeDocumentRepository.findById(projectDocumentId).orElseThrow();

            List<KnowledgeDocumentEntity> scopeDocs = resolveScopeDocuments(rowReloaded);
            if (scopeDocs.stream().noneMatch(d -> d.getId().equals(rowReloaded.getId()))) {
                scopeDocs = new ArrayList<>(scopeDocs);
                scopeDocs.add(rowReloaded);
                scopeDocs.sort(Comparator.comparing(KnowledgeDocumentEntity::getId));
            }
            IndexAndSnapshotSig sig = computeSignaturePair(scopeDocs, null);
            String indexSigHex = sig.indexSigHex();
            String snapshotSigHex = sig.snapshotSigHex();

            KnowledgeSnapshotScopeType snapScope =
                    rowReloaded.getCorpusScope() == CorpusScope.PROJECT_SHARED
                            ? KnowledgeSnapshotScopeType.PROJECT
                            : KnowledgeSnapshotScopeType.CONVERSATION;

            Optional<KnowledgeIndexSnapshotEntity> previousActive =
                    rowReloaded.getCorpusScope() == CorpusScope.PROJECT_SHARED
                            ? knowledgeSnapshotService.findActiveProjectSnapshot(projectId)
                            : knowledgeSnapshotService.findActiveConversationSnapshot(
                                    rowReloaded.getConversation().getId());

            building =
                    knowledgeSnapshotService.createBuildingSnapshot(
                            rowReloaded.getProject(),
                            rowReloaded.getConversation(),
                            snapScope,
                            snapshotSigHex,
                            resolvedConfigSnapshotId,
                            resolvedConfigHash);

            previousActive.ifPresent(p -> knowledgeSnapshotService.deleteVectorsForSnapshotId(p.getId()));
            for (KnowledgeDocumentEntity d : scopeDocs) {
                deleteVectorChunksForDocument(d.getId());
            }

            MaterializationStrategy strategy = materializationStrategy;
            for (KnowledgeDocumentEntity doc : scopeDocs) {
                knowledgeIndexingService.processDocument(
                        doc,
                        doc.getId().equals(projectDocumentId) ? tempFile : null,
                        originalFilename,
                        contentType,
                        building,
                        indexSigHex,
                        strategy,
                        chunkMaxChars);
            }

            knowledgeSnapshotService.activateSnapshot(building, scopeDocs, previousActive);

            KnowledgeDocumentEntity rowDone =
                    knowledgeDocumentRepository.findById(projectDocumentId).orElseThrow();
            rowDone.setStatus(ProjectDocumentStatus.READY);
            rowDone.setChunkCount(knowledgeIndexingService.computeChunkCountForDoc(rowDone.getId()));
            rowDone.setErrorMessage(null);
            rowDone.setReindexedAt(Instant.now());
            knowledgeDocumentRepository.save(rowDone);
            log.info("Knowledge pipeline completed for project document {} (snapshot {})", projectDocumentId, building.getId());
        } catch (Exception e) {
            log.error("Knowledge ingest failed for project document {}: {}", projectDocumentId, e.getMessage(), e);
            if (building != null) {
                knowledgeSnapshotService.deleteVectorsForSnapshotId(building.getId());
                knowledgeSnapshotService.failSnapshotById(building.getId());
            }
            KnowledgeDocumentEntity rowErr =
                    knowledgeDocumentRepository.findById(projectDocumentId).orElse(null);
            if (rowErr != null) {
                rowErr.setStatus(ProjectDocumentStatus.ERROR);
                rowErr.setErrorMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                knowledgeDocumentRepository.save(rowErr);
            }
        } finally {
            deleteTempQuietly(tempFile);
        }
    }

    /**
     * True when at least one READY document in scope is marked {@code requires_reindex} (SOFT gate per §11a).
     */
    public boolean scopeHasRequiresReindex(UUID projectId, CorpusScope corpusScope, UUID conversationId) {
        return loadReadyScopeDocuments(projectId, corpusScope, conversationId).stream()
                .anyMatch(KnowledgeDocumentEntity::isRequiresReindex);
    }

    public boolean hasReadyDocumentsInScope(UUID projectId, CorpusScope corpusScope, UUID conversationId) {
        return !loadReadyScopeDocuments(projectId, corpusScope, conversationId).isEmpty();
    }

    /**
     * Deterministic snapshot signature for the current READY corpus in scope (used for {@code reindex_event}).
     */
    public String previewSnapshotSignatureHex(UUID projectId, CorpusScope corpusScope, UUID conversationId) {
        List<KnowledgeDocumentEntity> scopeDocs = loadReadyScopeDocuments(projectId, corpusScope, conversationId);
        return computeSignaturePair(scopeDocs, null).snapshotSigHex();
    }

    public String previewSnapshotSignatureHex(
            UUID projectId, CorpusScope corpusScope, UUID conversationId, KnowledgeBuildProjection projection) {
        List<KnowledgeDocumentEntity> scopeDocs = loadReadyScopeDocuments(projectId, corpusScope, conversationId);
        return computeSignaturePair(scopeDocs, projection).snapshotSigHex();
    }

    private record IndexAndSnapshotSig(String indexSigHex, String snapshotSigHex) {}

    private IndexAndSnapshotSig computeSignaturePair(
            List<KnowledgeDocumentEntity> scopeDocs, KnowledgeBuildProjection projectionOrNull) {
        String embed = projectionOrNull != null ? projectionOrNull.embeddingModelId() : embeddingModelId;
        int chunk = projectionOrNull != null ? projectionOrNull.chunkMaxChars() : chunkMaxChars;
        MaterializationStrategy strat =
                projectionOrNull != null ? projectionOrNull.materializationStrategy() : materializationStrategy;
        IndexSignature indexSignature = IndexSignature.forStrategy(embed, chunk, strat);
        String indexSigHex = indexSignature.toHashHex();
        List<UUID> docIds = scopeDocs.stream().map(KnowledgeDocumentEntity::getId).sorted().toList();
        List<String> checksums =
                scopeDocs.stream().map(d -> d.getContentChecksum() != null ? d.getContentChecksum() : "").toList();
        String snapshotSigHex = SnapshotSignatureHasher.computeSnapshotSignatureHex(indexSigHex, docIds, checksums);
        return new IndexAndSnapshotSig(indexSigHex, snapshotSigHex);
    }

    /**
     * Full-scope rebuild using {@link KnowledgeBuildProjection} (config-aware path).
     *
     * @return new knowledge snapshot id, or {@code null} when no READY documents (no snapshot created)
     */
    @Transactional
    public UUID rebuildScope(
            UUID projectId,
            CorpusScope corpusScope,
            UUID conversationId,
            KnowledgeBuildProjection projection,
            UUID resolvedConfigSnapshotId) {
        List<KnowledgeDocumentEntity> scopeDocs = loadReadyScopeDocuments(projectId, corpusScope, conversationId);
        if (scopeDocs.isEmpty()) {
            log.info("rebuildScope: no READY documents in scope project={}, corpusScope={}", projectId, corpusScope);
            return null;
        }
        KnowledgeIndexSnapshotEntity building = null;
        try {
            IndexAndSnapshotSig sig = computeSignaturePair(scopeDocs, projection);
            String indexSigHex = sig.indexSigHex();
            String snapshotSigHex = sig.snapshotSigHex();

            KnowledgeSnapshotScopeType snapScope =
                    corpusScope == CorpusScope.PROJECT_SHARED
                            ? KnowledgeSnapshotScopeType.PROJECT
                            : KnowledgeSnapshotScopeType.CONVERSATION;

            Optional<KnowledgeIndexSnapshotEntity> previousActive =
                    corpusScope == CorpusScope.PROJECT_SHARED
                            ? knowledgeSnapshotService.findActiveProjectSnapshot(projectId)
                            : knowledgeSnapshotService.findActiveConversationSnapshot(conversationId);

            KnowledgeDocumentEntity first = scopeDocs.getFirst();
            building =
                    knowledgeSnapshotService.createBuildingSnapshot(
                            first.getProject(),
                            first.getConversation(),
                            snapScope,
                            snapshotSigHex,
                            resolvedConfigSnapshotId,
                            projection.configHash());

            previousActive.ifPresent(p -> knowledgeSnapshotService.deleteVectorsForSnapshotId(p.getId()));
            for (KnowledgeDocumentEntity d : scopeDocs) {
                deleteVectorChunksForDocument(d.getId());
            }

            MaterializationStrategy strategy = projection.materializationStrategy();
            for (KnowledgeDocumentEntity doc : scopeDocs) {
                knowledgeIndexingService.processDocument(
                        doc,
                        null,
                        doc.getFileName(),
                        doc.getMimeType(),
                        building,
                        indexSigHex,
                        strategy,
                        projection.chunkMaxChars());
            }

            knowledgeSnapshotService.activateSnapshot(building, scopeDocs, previousActive);

            Instant now = Instant.now();
            for (KnowledgeDocumentEntity d : scopeDocs) {
                d.setRequiresReindex(false);
                d.setChunkCount(knowledgeIndexingService.computeChunkCountForDoc(d.getId()));
                d.setReindexedAt(now);
                knowledgeDocumentRepository.save(d);
            }
            log.info("rebuildScope completed snapshot {} for project {}", building.getId(), projectId);
            return building.getId();
        } catch (Exception e) {
            log.error("rebuildScope failed: {}", e.getMessage(), e);
            if (building != null) {
                knowledgeSnapshotService.deleteVectorsForSnapshotId(building.getId());
                knowledgeSnapshotService.failSnapshotById(building.getId());
            }
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private List<KnowledgeDocumentEntity> loadReadyScopeDocuments(
            UUID projectId, CorpusScope corpusScope, UUID conversationId) {
        if (corpusScope == CorpusScope.PROJECT_SHARED) {
            return knowledgeDocumentRepository
                    .findByProject_IdAndCorpusScopeOrderByIdAsc(projectId, CorpusScope.PROJECT_SHARED)
                    .stream()
                    .filter(
                            d ->
                                    d.getStatus() == ProjectDocumentStatus.READY
                                            && d.getStorageUri() != null
                                            && !d.getStorageUri().isBlank())
                    .collect(Collectors.toList());
        }
        if (conversationId == null) {
            return List.of();
        }
        return knowledgeDocumentRepository
                .findByConversation_IdAndCorpusScope(conversationId, CorpusScope.CHAT_LOCAL)
                .stream()
                .filter(
                        d ->
                                d.getStatus() == ProjectDocumentStatus.READY
                                        && d.getStorageUri() != null
                                        && !d.getStorageUri().isBlank())
                .sorted(Comparator.comparing(KnowledgeDocumentEntity::getId))
                .collect(Collectors.toList());
    }

    private List<KnowledgeDocumentEntity> resolveScopeDocuments(KnowledgeDocumentEntity trigger) {
        if (trigger.getCorpusScope() == CorpusScope.PROJECT_SHARED) {
            UUID pid = trigger.getProject().getId();
            return knowledgeDocumentRepository.findByProject_IdAndCorpusScopeOrderByIdAsc(pid, CorpusScope.PROJECT_SHARED)
                    .stream()
                    .filter(d -> eligibleForRebuild(d, trigger.getId()))
                    .collect(Collectors.toList());
        }
        UUID convId = trigger.getConversation().getId();
        return knowledgeDocumentRepository.findByConversation_IdAndCorpusScope(convId, CorpusScope.CHAT_LOCAL).stream()
                .filter(d -> eligibleForRebuild(d, trigger.getId()))
                .sorted(Comparator.comparing(KnowledgeDocumentEntity::getId))
                .collect(Collectors.toList());
    }

    private boolean eligibleForRebuild(KnowledgeDocumentEntity d, UUID triggerDocumentId) {
        if (d.getId().equals(triggerDocumentId) && d.getStatus() == ProjectDocumentStatus.INGESTING) {
            return true;
        }
        if (d.getStatus() == ProjectDocumentStatus.READY
                && d.getStorageUri() != null
                && !d.getStorageUri().isBlank()) {
            return true;
        }
        return false;
    }

    public void deleteVectorChunksForDocument(UUID projectDocumentId) {
        jdbcTemplate.update(
                """
                        DELETE FROM vector_store
                        WHERE metadata->>'projectDocumentId' = ?
                           OR metadata->>'documentId' = ?
                        """,
                projectDocumentId.toString(),
                projectDocumentId.toString());
    }

    private static void deleteTempQuietly(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {
            // intentionally quiet
        }
    }
}
