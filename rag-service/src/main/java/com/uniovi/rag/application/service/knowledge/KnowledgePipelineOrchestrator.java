package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.application.port.BinaryStoragePort;
import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.IndexSnapshotStatus;
import com.uniovi.rag.domain.knowledge.IndexSignature;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotOwnerType;
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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final IndexingEmbeddingGuard indexingEmbeddingGuard;
    private final KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository;
    private final EmbeddingIndexCompatibilityService embeddingIndexCompatibilityService;
    private final TransactionTemplate transactionTemplate;
    /** Joins the caller's Spring transaction (lab sync ingest); do not use {@link #transactionTemplate} here. */
    private final TransactionTemplate joinCallerTransactionTemplate;
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
            IndexingEmbeddingGuard indexingEmbeddingGuard,
            KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository,
            EmbeddingIndexCompatibilityService embeddingIndexCompatibilityService,
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
        this.indexingEmbeddingGuard = indexingEmbeddingGuard;
        this.knowledgeIndexSnapshotRepository = knowledgeIndexSnapshotRepository;
        this.embeddingIndexCompatibilityService = embeddingIndexCompatibilityService;
        // Isolate ingest work so a failed inner ingest does not mark the caller transaction rollback-only.
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.joinCallerTransactionTemplate = new TransactionTemplate(transactionManager);
        this.joinCallerTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.meterRegistry = meterRegistry;
    }

    private Map<String, Object> snapshotIndexProfileJsonb(ProjectIndexProfile profile) {
        return embeddingIndexCompatibilityService.enrichIndexProfile(profile.toSnapshotJsonb());
    }

    private void probeAndPersistSnapshotEmbeddingDimensions(
            ProjectIndexProfile profile, MaterializationStrategy strategy, KnowledgeIndexSnapshotEntity building) {
        if (strategy == MaterializationStrategy.STRUCTURED_SEARCH) {
            return;
        }
        Optional<String> emb =
                IndexProfileJsonSupport.readEmbeddingModelId(
                        embeddingIndexCompatibilityService.enrichIndexProfile(profile.toSnapshotJsonb()));
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
        // Ensure every project has a profile row (created lazily for older projects).
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
     * Same as {@link #ingestFromTempFile} but runs {@link #ingestTx} in the caller's Spring transaction
     * ({@link TransactionDefinition#PROPAGATION_REQUIRED}). Use when the caller is already isolated
     * (e.g. {@code REQUIRES_NEW} lab sync ingest) so the flushed document row and {@code ingestTx}
     * commit together as {@code READY} or {@code ERROR}.
     */
    public void ingestFromTempFileInCurrentTransaction(
            UUID projectId,
            UUID projectDocumentId,
            Path tempFile,
            String originalFilename,
            String contentType,
            UUID resolvedConfigSnapshotId,
            String resolvedConfigHash) {
        try {
            runIngestWorkInCallerTransaction(
                    projectDocumentId,
                    () ->
                            ingestTx(
                                    projectId,
                                    projectDocumentId,
                                    tempFile,
                                    originalFilename,
                                    contentType,
                                    resolvedConfigSnapshotId,
                                    resolvedConfigHash));
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

    /**
     * Same as {@link #ingestFromStoredBinary} but runs {@link #ingestStoredTx} in the caller's Spring
     * transaction so {@code resolved_config_snapshot} rows persisted in the same transaction are visible
     * to {@code knowledge_index_snapshot} FK inserts.
     */
    public void ingestFromStoredBinaryInCurrentTransaction(
            UUID projectId,
            UUID projectDocumentId,
            UUID resolvedConfigSnapshotId,
            String resolvedConfigHash) {
        runIngestWorkInCallerTransaction(
                projectDocumentId,
                () ->
                        ingestStoredTx(
                                projectId,
                                projectDocumentId,
                                resolvedConfigSnapshotId,
                                resolvedConfigHash));
    }

    /**
     * Runs ingest work in the caller's transaction. Failures are persisted as {@code ERROR} in the same
     * transaction so a nested {@code REQUIRES_NEW} ingest (lab corpus upload) can commit instead of raising
     * {@code UnexpectedRollbackException} ("Transaction silently rolled back because it has been marked as
     * rollback-only").
     */
    private void runIngestWorkInCallerTransaction(UUID projectDocumentId, Runnable ingestWork) {
        recordEtlEvent(ETL_STAGE_INGEST_TEMP_FILE, "started");
        final boolean[] failed = {false};
        joinCallerTransactionTemplate.executeWithoutResult(
                status -> {
                    try {
                        ingestWork.run();
                    } catch (Exception ingestEx) {
                        failed[0] = true;
                        recordEtlEvent(ETL_STAGE_INGEST_TEMP_FILE, "failure");
                        log.error(
                                "Knowledge ingest failed for project document {}: {}",
                                projectDocumentId,
                                ingestEx.getMessage(),
                                ingestEx);
                        markDocumentIngestError(projectDocumentId, ingestEx);
                    }
                });
        if (!failed[0]) {
            recordEtlEvent(ETL_STAGE_INGEST_TEMP_FILE, "success");
        }
    }

    private void markDocumentIngestError(UUID projectDocumentId, Exception e) {
        KnowledgeDocumentEntity rowErr =
                knowledgeDocumentRepository.findById(projectDocumentId).orElse(null);
        if (rowErr != null) {
            rowErr.setStatus(ProjectDocumentStatus.ERROR);
            rowErr.setErrorMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            knowledgeDocumentRepository.save(rowErr);
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
        KnowledgeDocumentEntity row =
                knowledgeDocumentRepository
                        .findById(projectDocumentId)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Project document "
                                                        + projectDocumentId
                                                        + " not found for ingest"));

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
                        snapshotIndexProfileJsonb(profile),
                        profile.profileHash());

        try {
            probeAndPersistSnapshotEmbeddingDimensions(profile, profile.materializationStrategy(), building);

            previousActive.ifPresent(p -> knowledgeSnapshotService.deleteVectorsForSnapshotId(p.getId()));
            deleteVectorsForScopeDocs(scopeDocs);

            MaterializationStrategy strategy = profile.materializationStrategy();
            int chunkMaxChars = profile.chunkMaxChars();
            int embedMaxChars = indexingEmbeddingGuard.effectiveEmbedMaxChars(chunkMaxChars);
            log.debug(
                    "Knowledge ingest embed caps projectId={} profileChunkMax={} embedMax={}",
                    projectId,
                    chunkMaxChars,
                    embedMaxChars);
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
        } catch (Exception e) {
            knowledgeSnapshotService.deleteVectorsForSnapshotId(building.getId());
            knowledgeSnapshotService.failSnapshotById(building.getId());
            throw e;
        }
    }

    private void ingestStoredTx(
            UUID projectId,
            UUID projectDocumentId,
            UUID resolvedConfigSnapshotId,
            String resolvedConfigHash) {
        KnowledgeDocumentEntity row =
                knowledgeDocumentRepository
                        .findById(projectDocumentId)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Project document "
                                                        + projectDocumentId
                                                        + " not found for ingest"));
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
                        snapshotIndexProfileJsonb(profile),
                        profile.profileHash());

        try {
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
        } catch (Exception e) {
            knowledgeSnapshotService.deleteVectorsForSnapshotId(building.getId());
            knowledgeSnapshotService.failSnapshotById(building.getId());
            throw e;
        }
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

    /**
     * Deterministic snapshot signature for the current READY corpus using an explicit index profile (Lab reuse/staleness).
     */
    public String computeSnapshotSignatureHex(
            UUID projectId, CorpusScope corpusScope, UUID conversationId, ProjectIndexProfile profile) {
        List<KnowledgeDocumentEntity> scopeDocs = loadReadyScopeDocuments(projectId, corpusScope, conversationId);
        ProjectIndexProfile effective = profile != null ? profile : loadProfile(projectId);
        return computeSignaturePair(scopeDocs, null, effective).snapshotSigHex();
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
            ProjectIndexProfile effectiveProfile = profileForProjection(profile, projection);
            IndexAndSnapshotSig sig = computeSignaturePair(scopeDocs, null, effectiveProfile);
            String indexSigHex = sig.indexSigHex();
            String snapshotSigHex = sig.snapshotSigHex();

            KnowledgeSnapshotScopeType snapScope =
                    corpusScope == CorpusScope.PROJECT_SHARED
                            ? KnowledgeSnapshotScopeType.PROJECT
                            : KnowledgeSnapshotScopeType.CONVERSATION;

            Optional<KnowledgeIndexSnapshotEntity> previousActive =
                    corpusScope == CorpusScope.PROJECT_SHARED
                            ? knowledgeSnapshotService.findCompatibleProjectSnapshot(
                                    projectId,
                                    s ->
                                            s.getStatus() == IndexSnapshotStatus.ACTIVE
                                                    && Objects.equals(
                                                            s.getIndexProfileHash(),
                                                            effectiveProfile.profileHash()))
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
                            snapshotIndexProfileJsonb(effectiveProfile),
                            effectiveProfile.profileHash());

            probeAndPersistSnapshotEmbeddingDimensions(effectiveProfile, effectiveProfile.materializationStrategy(), building);

            previousActive.ifPresent(p -> knowledgeSnapshotService.deleteVectorsForSnapshotId(p.getId()));
            if (corpusScope != CorpusScope.PROJECT_SHARED) {
                for (KnowledgeDocumentEntity d : scopeDocs) {
                    deleteVectorChunksForDocument(d.getId());
                }
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

    private static ProjectIndexProfile profileForProjection(ProjectIndexProfile base, KnowledgeBuildProjection projection) {
        if (projection == null) {
            return base;
        }
        MaterializationStrategy strategy = projection.materializationStrategy();
        boolean metadataEnabled = projection.metadataExtractionEnabled();
        String embeddingModelId = projection.embeddingModelId();
        int chunkMaxChars = projection.chunkMaxChars();
        Integer chunkOverlap = projection.chunkOverlap();
        String hash =
                ProjectIndexProfile.computeProfileHash(
                        strategy,
                        metadataEnabled,
                        base.metadataProfile(),
                        embeddingModelId,
                        chunkMaxChars,
                        chunkOverlap);
        Instant now = Instant.now();
        return new ProjectIndexProfile(
                base.projectId(),
                strategy,
                metadataEnabled,
                base.metadataProfile(),
                embeddingModelId,
                chunkMaxChars,
                chunkOverlap,
                hash,
                now,
                now);
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
        return rebuildScopeWithProfileOverride(
                projectId,
                corpusScope,
                conversationId,
                null,
                null,
                resolvedConfigSnapshotId,
                resolvedConfigHash,
                effectiveProfile);
    }

    /**
     * Lab rebuild with optional evaluation-corpus ownership (does not mutate persisted project index profile).
     *
     * @return new knowledge snapshot id, or {@code null} when no READY documents (no snapshot created)
     */
    @Transactional
    public UUID rebuildScopeWithProfileOverride(
            UUID projectId,
            CorpusScope corpusScope,
            UUID conversationId,
            KnowledgeSnapshotOwnerType ownerType,
            UUID ownerId,
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
                    ownerType == KnowledgeSnapshotOwnerType.EVALUATION_CORPUS
                                    && ownerId != null
                            ? knowledgeSnapshotService.findCompatibleCorpusSnapshot(
                                    ownerId,
                                    s ->
                                            s.getStatus() == IndexSnapshotStatus.ACTIVE
                                                    && Objects.equals(
                                                            s.getIndexProfileHash(), profile.profileHash()))
                            : corpusScope == CorpusScope.PROJECT_SHARED
                                    ? knowledgeSnapshotService.findCompatibleProjectSnapshot(
                                            projectId,
                                            s ->
                                                    s.getStatus() == IndexSnapshotStatus.ACTIVE
                                                            && Objects.equals(
                                                                    s.getIndexProfileHash(),
                                                                    profile.profileHash()))
                                    : knowledgeSnapshotService.findActiveConversationSnapshot(conversationId);

            KnowledgeDocumentEntity first = scopeDocs.getFirst();
            building =
                    knowledgeSnapshotService.createBuildingSnapshot(
                            first.getProject(),
                            first.getConversation(),
                            snapScope,
                            ownerType,
                            ownerId,
                            snapshotSigHex,
                            resolvedConfigSnapshotId,
                            resolvedConfigHash != null ? resolvedConfigHash : "lab-auto-reindex",
                            snapshotIndexProfileJsonb(profile),
                            profile.profileHash());

            probeAndPersistSnapshotEmbeddingDimensions(profile, profile.materializationStrategy(), building);

            previousActive.ifPresent(p -> knowledgeSnapshotService.deleteVectorsForSnapshotId(p.getId()));
            // Multi-materialization projects and evaluation corpora keep one ACTIVE snapshot per index profile hash.
            // Never delete document-global vectors when building an alternate materialization profile.
            if (ownerType != KnowledgeSnapshotOwnerType.EVALUATION_CORPUS
                    && ownerType != KnowledgeSnapshotOwnerType.PROJECT) {
                for (KnowledgeDocumentEntity d : scopeDocs) {
                    deleteVectorChunksForDocument(d.getId());
                }
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
