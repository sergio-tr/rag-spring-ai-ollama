package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.application.port.BinaryStoragePort;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.IndexSignature;
import com.uniovi.rag.domain.knowledge.KnowledgeBuildProjection;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotScopeType;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.domain.knowledge.SnapshotSignatureHasher;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.vector.EmbeddingSpaceGuard;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
 * ETL stages: binary storage and checksum → snapshot creation → vector delete → per-document indexing → activation.
 * Micrometer counter {@code rag.knowledge.etl.events} tags {@code stage} / {@code outcome} when {@link MeterRegistry} is present.
 */
@Service
public class KnowledgePipelineOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(KnowledgePipelineOrchestrator.class);

    private static final String ETL_STAGE_INGEST_TEMP_FILE = "ingest_temp_file";
    private static final String ETL_STAGE_REBUILD_SCOPE = "rebuild_scope";

    private final JdbcTemplate jdbcTemplate;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final BinaryStoragePort binaryStoragePort;
    private final KnowledgeSnapshotService knowledgeSnapshotService;
    private final KnowledgeIndexingService knowledgeIndexingService;
    private final ProjectIndexProfileService projectIndexProfileService;
    private final LabIndexProfileOverrideFactory labIndexProfileOverrideFactory;
    private final EmbeddingSpaceGuard embeddingSpaceGuard;
    private final KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;

    public KnowledgePipelineOrchestrator(
            JdbcTemplate jdbcTemplate,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            BinaryStoragePort binaryStoragePort,
            KnowledgeSnapshotService knowledgeSnapshotService,
            KnowledgeIndexingService knowledgeIndexingService,
            ProjectIndexProfileService projectIndexProfileService,
            LabIndexProfileOverrideFactory labIndexProfileOverrideFactory,
            EmbeddingSpaceGuard embeddingSpaceGuard,
            KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository,
            PlatformTransactionManager transactionManager,
            @Autowired(required = false) MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.binaryStoragePort = binaryStoragePort;
        this.knowledgeSnapshotService = knowledgeSnapshotService;
        this.knowledgeIndexingService = knowledgeIndexingService;
        this.projectIndexProfileService = projectIndexProfileService;
        this.labIndexProfileOverrideFactory = labIndexProfileOverrideFactory;
        this.embeddingSpaceGuard = embeddingSpaceGuard;
        this.knowledgeIndexSnapshotRepository = knowledgeIndexSnapshotRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.meterRegistry = meterRegistry;
    }

    private void probeAndPersistSnapshotEmbeddingDimensions(
            ProjectIndexProfile profile, MaterializationStrategy strategy, KnowledgeIndexSnapshotEntity building) {
        if (strategy == MaterializationStrategy.STRUCTURED_SEARCH) {
            return;
        }
        Optional<String> emb = IndexProfileJsonSupport.readEmbeddingModelId(profile.toSnapshotJsonb());
        if (emb.isEmpty() || emb.get().isBlank()) {
            throw new IllegalStateException(
                    "embeddingModelId is required in the project index profile for dense/hybrid vector indexing");
        }
        int dims = embeddingSpaceGuard.assertFitsPhysicalVectorColumnReturning(emb.get().trim());
        building.setEmbeddingDimensions(dims);
        knowledgeIndexSnapshotRepository.save(building);
    }

    private void recordEtlEvent(String stage, String outcome) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("rag.knowledge.etl.events")
                .tag("stage", stage)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    private ProjectIndexProfile loadProfile(UUID projectId) {
        // Ensure every project has a profile row (created lazily for legacy projects).
        return projectIndexProfileService.ensureDefault(projectId);
    }

    public void ingestFromTempFile(
            UUID projectId,
            UUID projectDocumentId,
            Path tempFile,
            String originalFilename,
            String contentType,
            UUID resolvedConfigSnapshotId,
            String resolvedConfigHash) {
        try {
            recordEtlEvent(ETL_STAGE_INGEST_TEMP_FILE, "started");
            transactionTemplate.executeWithoutResult(
                    s ->
                            ingestTx(
                                    projectId,
                                    projectDocumentId,
                                    tempFile,
                                    originalFilename,
                                    contentType,
                                    resolvedConfigSnapshotId,
                                    resolvedConfigHash));
            recordEtlEvent(ETL_STAGE_INGEST_TEMP_FILE, "success");
        } catch (Exception e) {
            recordEtlEvent(ETL_STAGE_INGEST_TEMP_FILE, "failure");
            log.error("Knowledge ingest failed for project document {}: {}", projectDocumentId, e.getMessage(), e);
            transactionTemplate.executeWithoutResult(
                    s -> {
                        KnowledgeDocumentEntity rowErr =
                                knowledgeDocumentRepository.findById(projectDocumentId).orElse(null);
                        if (rowErr != null) {
                            rowErr.setStatus(ProjectDocumentStatus.ERROR);
                            rowErr.setErrorMessage(
                                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                            knowledgeDocumentRepository.save(rowErr);
                        }
                    });
        } finally {
            deleteTempQuietly(tempFile);
        }
    }

    /**
     * Retry ingest without a new upload by reusing the stored binary (storageUri).
     * This guarantees the document will transition to READY or ERROR (never remain INGESTING forever).
     */
    public void ingestFromStoredBinary(
            UUID projectId,
            UUID projectDocumentId,
            UUID resolvedConfigSnapshotId,
            String resolvedConfigHash) {
        try {
            recordEtlEvent(ETL_STAGE_INGEST_TEMP_FILE, "started");
            transactionTemplate.executeWithoutResult(
                    s -> ingestStoredTx(projectId, projectDocumentId, resolvedConfigSnapshotId, resolvedConfigHash));
            recordEtlEvent(ETL_STAGE_INGEST_TEMP_FILE, "success");
        } catch (Exception e) {
            recordEtlEvent(ETL_STAGE_INGEST_TEMP_FILE, "failure");
            log.error("Knowledge ingest failed for project document {}: {}", projectDocumentId, e.getMessage(), e);
            transactionTemplate.executeWithoutResult(
                    s -> {
                        KnowledgeDocumentEntity rowErr =
                                knowledgeDocumentRepository.findById(projectDocumentId).orElse(null);
                        if (rowErr != null) {
                            rowErr.setStatus(ProjectDocumentStatus.ERROR);
                            rowErr.setErrorMessage(
                                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                            knowledgeDocumentRepository.save(rowErr);
                        }
                    });
        }
    }

    private void ingestTx(
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
            return;
        }

        persistBinaryAndUpdateRow(projectId, projectDocumentId, tempFile, contentType, row);
        final KnowledgeDocumentEntity rowReloaded = knowledgeDocumentRepository.findById(projectDocumentId).orElseThrow();

        List<KnowledgeDocumentEntity> scopeDocs = resolveScopeDocuments(rowReloaded);
        if (scopeDocs.stream().noneMatch(d -> d.getId().equals(rowReloaded.getId()))) {
            scopeDocs = new ArrayList<>(scopeDocs);
            scopeDocs.add(rowReloaded);
            scopeDocs.sort(Comparator.comparing(KnowledgeDocumentEntity::getId));
        }
        ProjectIndexProfile profile = loadProfile(projectId);
        IndexAndSnapshotSig sig = computeSignaturePair(scopeDocs, null, profile);
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

        KnowledgeIndexSnapshotEntity building =
                knowledgeSnapshotService.createBuildingSnapshot(
                        rowReloaded.getProject(),
                        rowReloaded.getConversation(),
                        snapScope,
                        snapshotSigHex,
                        resolvedConfigSnapshotId,
                        resolvedConfigHash,
                        profile.toSnapshotJsonb(),
                        profile.profileHash());

        probeAndPersistSnapshotEmbeddingDimensions(profile, profile.materializationStrategy(), building);

        previousActive.ifPresent(p -> knowledgeSnapshotService.deleteVectorsForSnapshotId(p.getId()));
        deleteVectorsForScopeDocs(scopeDocs);

        MaterializationStrategy strategy = profile.materializationStrategy();
        int chunkMaxChars = profile.chunkMaxChars();
        for (KnowledgeDocumentEntity doc : scopeDocs) {
            try {
                knowledgeIndexingService.processDocument(
                        new KnowledgeDocumentIndexingRequest(
                                doc,
                                doc.getId().equals(projectDocumentId) ? tempFile : null,
                                originalFilename,
                                contentType,
                                building,
                                indexSigHex,
                                strategy,
                                chunkMaxChars));
            } catch (IOException e) {
                throw new IllegalStateException("Document indexing failed: " + e.getMessage(), e);
            }
        }

        knowledgeSnapshotService.activateSnapshot(building, scopeDocs, previousActive);

        KnowledgeDocumentEntity rowDone = knowledgeDocumentRepository.findById(projectDocumentId).orElseThrow();
        rowDone.setStatus(ProjectDocumentStatus.READY);
        rowDone.setChunkCount(knowledgeIndexingService.computeChunkCountForDoc(rowDone.getId()));
        rowDone.setErrorMessage(null);
        rowDone.setReindexedAt(Instant.now());
        knowledgeDocumentRepository.save(rowDone);
        log.info(
                "Knowledge pipeline completed for project document {} (snapshot {})",
                projectDocumentId,
                building.getId());
    }

    private void ingestStoredTx(
            UUID projectId,
            UUID projectDocumentId,
            UUID resolvedConfigSnapshotId,
            String resolvedConfigHash) {
        KnowledgeDocumentEntity row = knowledgeDocumentRepository.findById(projectDocumentId).orElse(null);
        if (row == null) {
            log.warn("Project document {} not found, skipping ingest", projectDocumentId);
            return;
        }
        if (row.getStorageUri() == null || row.getStorageUri().isBlank()) {
            throw new IllegalStateException("missing storage URI for stored-binary retry");
        }

        List<KnowledgeDocumentEntity> scopeDocs = resolveScopeDocuments(row);
        if (scopeDocs.stream().noneMatch(d -> d.getId().equals(row.getId()))) {
            scopeDocs = new ArrayList<>(scopeDocs);
            scopeDocs.add(row);
            scopeDocs.sort(Comparator.comparing(KnowledgeDocumentEntity::getId));
        }
        ProjectIndexProfile profile = loadProfile(projectId);
        IndexAndSnapshotSig sig = computeSignaturePair(scopeDocs, null, profile);
        String indexSigHex = sig.indexSigHex();
        String snapshotSigHex = sig.snapshotSigHex();

        KnowledgeSnapshotScopeType snapScope =
                row.getCorpusScope() == CorpusScope.PROJECT_SHARED
                        ? KnowledgeSnapshotScopeType.PROJECT
                        : KnowledgeSnapshotScopeType.CONVERSATION;

        Optional<KnowledgeIndexSnapshotEntity> previousActive =
                row.getCorpusScope() == CorpusScope.PROJECT_SHARED
                        ? knowledgeSnapshotService.findActiveProjectSnapshot(projectId)
                        : knowledgeSnapshotService.findActiveConversationSnapshot(
                                row.getConversation().getId());

        KnowledgeIndexSnapshotEntity building =
                knowledgeSnapshotService.createBuildingSnapshot(
                        row.getProject(),
                        row.getConversation(),
                        snapScope,
                        snapshotSigHex,
                        resolvedConfigSnapshotId,
                        resolvedConfigHash,
                        profile.toSnapshotJsonb(),
                        profile.profileHash());

        probeAndPersistSnapshotEmbeddingDimensions(profile, profile.materializationStrategy(), building);

        previousActive.ifPresent(p -> knowledgeSnapshotService.deleteVectorsForSnapshotId(p.getId()));
        deleteVectorsForScopeDocs(scopeDocs);

        MaterializationStrategy strategy = profile.materializationStrategy();
        int chunkMaxChars = profile.chunkMaxChars();
        String ct = row.getMimeType() != null ? row.getMimeType() : "application/octet-stream";
        for (KnowledgeDocumentEntity doc : scopeDocs) {
            try {
                knowledgeIndexingService.processDocument(
                        new KnowledgeDocumentIndexingRequest(
                                doc,
                                null,
                                doc.getFileName(),
                                ct,
                                building,
                                indexSigHex,
                                strategy,
                                chunkMaxChars));
            } catch (IOException e) {
                throw new IllegalStateException("Document indexing failed: " + e.getMessage(), e);
            }
        }

        knowledgeSnapshotService.activateSnapshot(building, scopeDocs, previousActive);

        KnowledgeDocumentEntity rowDone = knowledgeDocumentRepository.findById(projectDocumentId).orElseThrow();
        rowDone.setStatus(ProjectDocumentStatus.READY);
        rowDone.setChunkCount(knowledgeIndexingService.computeChunkCountForDoc(rowDone.getId()));
        rowDone.setErrorMessage(null);
        rowDone.setReindexedAt(Instant.now());
        knowledgeDocumentRepository.save(rowDone);
    }

    private void persistBinaryAndUpdateRow(
            UUID projectId,
            UUID projectDocumentId,
            Path tempFile,
            String contentType,
            KnowledgeDocumentEntity row) {
        try (InputStream in = Files.newInputStream(tempFile)) {
            long size = Files.size(tempFile);
            BinaryStoragePort.StoredObject stored =
                    binaryStoragePort.store(in, size, projectId + "/" + projectDocumentId + "/source.bin");
            row.setStorageUri(stored.relativeUri());
            row.setContentChecksum(stored.sha256Hex());
            row.setByteSize(size);
            row.setMimeType(contentType);
        } catch (IOException e) {
            throw new IllegalStateException("Could not persist binary for ingest: " + e.getMessage(), e);
        }
        knowledgeDocumentRepository.save(row);
    }

    private void deleteVectorsForScopeDocs(List<KnowledgeDocumentEntity> scopeDocs) {
        for (KnowledgeDocumentEntity d : scopeDocs) {
            deleteVectorChunksForDocument(d.getId());
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
        ProjectIndexProfile profile = loadProfile(projectId);
        return computeSignaturePair(scopeDocs, null, profile).snapshotSigHex();
    }

    public String previewSnapshotSignatureHex(
            UUID projectId, CorpusScope corpusScope, UUID conversationId, KnowledgeBuildProjection projection) {
        List<KnowledgeDocumentEntity> scopeDocs = loadReadyScopeDocuments(projectId, corpusScope, conversationId);
        ProjectIndexProfile profile = loadProfile(projectId);
        return computeSignaturePair(scopeDocs, projection, profile).snapshotSigHex();
    }

    private record IndexAndSnapshotSig(String indexSigHex, String snapshotSigHex) {}

    private IndexAndSnapshotSig computeSignaturePair(
            List<KnowledgeDocumentEntity> scopeDocs,
            KnowledgeBuildProjection projectionOrNull,
            ProjectIndexProfile profile) {
        String embed = projectionOrNull != null ? projectionOrNull.embeddingModelId() : profile.embeddingModelId();
        int chunk = projectionOrNull != null ? projectionOrNull.chunkMaxChars() : profile.chunkMaxChars();
        MaterializationStrategy strat =
                projectionOrNull != null ? projectionOrNull.materializationStrategy() : profile.materializationStrategy();
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
            recordEtlEvent(ETL_STAGE_REBUILD_SCOPE, "started");
            ProjectIndexProfile profile = loadProfile(projectId);
            IndexAndSnapshotSig sig = computeSignaturePair(scopeDocs, projection, profile);
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
                            projection.configHash(),
                            profile.toSnapshotJsonb(),
                            profile.profileHash());

            probeAndPersistSnapshotEmbeddingDimensions(profile, projection.materializationStrategy(), building);

            previousActive.ifPresent(p -> knowledgeSnapshotService.deleteVectorsForSnapshotId(p.getId()));
            for (KnowledgeDocumentEntity d : scopeDocs) {
                deleteVectorChunksForDocument(d.getId());
            }

            MaterializationStrategy strategy = projection.materializationStrategy();
            for (KnowledgeDocumentEntity doc : scopeDocs) {
                knowledgeIndexingService.processDocument(
                        new KnowledgeDocumentIndexingRequest(
                                doc,
                                null,
                                doc.getFileName(),
                                doc.getMimeType(),
                                building,
                                indexSigHex,
                                strategy,
                                projection.chunkMaxChars()));
            }

            knowledgeSnapshotService.activateSnapshot(building, scopeDocs, previousActive);

            Instant now = Instant.now();
            for (KnowledgeDocumentEntity d : scopeDocs) {
                d.setRequiresReindex(false);
                d.setChunkCount(knowledgeIndexingService.computeChunkCountForDoc(d.getId()));
                d.setReindexedAt(now);
                knowledgeDocumentRepository.save(d);
            }
            recordEtlEvent(ETL_STAGE_REBUILD_SCOPE, "success");
            log.info("rebuildScope completed snapshot {} for project {}", building.getId(), projectId);
            return building.getId();
        } catch (Exception e) {
            recordEtlEvent(ETL_STAGE_REBUILD_SCOPE, "failure");
            log.error("rebuildScope failed: {}", e.getMessage(), e);
            if (building != null) {
                knowledgeSnapshotService.deleteVectorsForSnapshotId(building.getId());
                knowledgeSnapshotService.failSnapshotById(building.getId());
            }
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * Lab-only internal rebuild using a derived profile override (materialization + metadata toggle).
     *
     * <p>This method intentionally does not write {@code project_index_profile}. It creates and activates a
     * new {@code knowledge_index_snapshot} for the scope using the effective profile's capabilities.
     *
     * @return new knowledge snapshot id, or {@code null} when no READY documents (no snapshot created)
     */
    @Transactional
    public UUID rebuildScopeWithProfileOverride(
            UUID projectId,
            CorpusScope corpusScope,
            UUID conversationId,
            UUID resolvedConfigSnapshotId,
            String resolvedConfigHash,
            ProjectIndexProfile effectiveProfile) {
        List<KnowledgeDocumentEntity> scopeDocs = loadReadyScopeDocuments(projectId, corpusScope, conversationId);
        if (scopeDocs.isEmpty()) {
            log.info("rebuildScopeWithProfileOverride: no READY documents in scope project={}, corpusScope={}", projectId, corpusScope);
            return null;
        }
        KnowledgeIndexSnapshotEntity building = null;
        try {
            recordEtlEvent(ETL_STAGE_REBUILD_SCOPE, "started");
            ProjectIndexProfile profile =
                    effectiveProfile != null ? effectiveProfile : loadProfile(projectId);
            IndexAndSnapshotSig sig = computeSignaturePair(scopeDocs, null, profile);
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
                            resolvedConfigHash != null ? resolvedConfigHash : "lab-auto-reindex",
                            profile.toSnapshotJsonb(),
                            profile.profileHash());

            probeAndPersistSnapshotEmbeddingDimensions(profile, profile.materializationStrategy(), building);

            previousActive.ifPresent(p -> knowledgeSnapshotService.deleteVectorsForSnapshotId(p.getId()));
            for (KnowledgeDocumentEntity d : scopeDocs) {
                deleteVectorChunksForDocument(d.getId());
            }

            MaterializationStrategy strategy = profile.materializationStrategy();
            int chunkMaxChars = profile.chunkMaxChars();
            for (KnowledgeDocumentEntity doc : scopeDocs) {
                knowledgeIndexingService.processDocument(
                        new KnowledgeDocumentIndexingRequest(
                                doc,
                                null,
                                doc.getFileName(),
                                doc.getMimeType(),
                                building,
                                indexSigHex,
                                strategy,
                                chunkMaxChars));
            }

            knowledgeSnapshotService.activateSnapshot(building, scopeDocs, previousActive);

            Instant now = Instant.now();
            for (KnowledgeDocumentEntity d : scopeDocs) {
                d.setRequiresReindex(false);
                d.setChunkCount(knowledgeIndexingService.computeChunkCountForDoc(d.getId()));
                d.setReindexedAt(now);
                knowledgeDocumentRepository.save(d);
            }
            recordEtlEvent(ETL_STAGE_REBUILD_SCOPE, "success");
            log.info("rebuildScopeWithProfileOverride completed snapshot {} for project {}", building.getId(), projectId);
            return building.getId();
        } catch (Exception e) {
            recordEtlEvent(ETL_STAGE_REBUILD_SCOPE, "failure");
            log.error("rebuildScopeWithProfileOverride failed: {}", e.getMessage(), e);
            if (building != null) {
                knowledgeSnapshotService.deleteVectorsForSnapshotId(building.getId());
                knowledgeSnapshotService.failSnapshotById(building.getId());
            }
            throw new IllegalStateException("AUTO_REINDEX_FAILED: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
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
        return (d.getId().equals(triggerDocumentId) && d.getStatus() == ProjectDocumentStatus.INGESTING)
                || (d.getStatus() == ProjectDocumentStatus.READY
                        && d.getStorageUri() != null
                        && !d.getStorageUri().isBlank());
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
