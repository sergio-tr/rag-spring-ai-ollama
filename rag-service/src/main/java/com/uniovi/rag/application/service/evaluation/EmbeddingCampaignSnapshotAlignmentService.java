package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.port.KnowledgeIndexSnapshotLookupPort;
import com.uniovi.rag.application.service.ResolvedConfigSnapshotApplicationService;
import com.uniovi.rag.application.service.ResolvedConfigSnapshotLinkage;
import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusApplicationService;
import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
import com.uniovi.rag.application.service.evaluation.preset.LabPresetRunGroupKey;
import com.uniovi.rag.application.service.knowledge.IndexProfileJsonSupport;
import com.uniovi.rag.application.service.knowledge.IndexSnapshotEmbeddingLookup;
import com.uniovi.rag.application.service.knowledge.KnowledgePipelineOrchestrator;
import com.uniovi.rag.application.service.knowledge.LabIndexProfileOverrideFactory;
import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileService;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotOwnerType;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Aligns {@code indexSnapshotIds} with {@code embeddingModelIds} for embedding campaigns, optionally
 * building missing evaluation-corpus snapshots before the campaign is accepted.
 */
@Service
public class EmbeddingCampaignSnapshotAlignmentService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingCampaignSnapshotAlignmentService.class);

    private final KnowledgeIndexSnapshotLookupPort knowledgeIndexSnapshotLookupPort;
    private final KnowledgePipelineOrchestrator knowledgePipelineOrchestrator;
    private final ProjectIndexProfileService projectIndexProfileService;
    private final LabIndexProfileOverrideFactory labIndexProfileOverrideFactory;
    private final EvaluationCorpusApplicationService evaluationCorpusApplicationService;
    private final ResolvedConfigSnapshotApplicationService resolvedConfigSnapshotApplicationService;

    public EmbeddingCampaignSnapshotAlignmentService(
            KnowledgeIndexSnapshotLookupPort knowledgeIndexSnapshotLookupPort,
            KnowledgePipelineOrchestrator knowledgePipelineOrchestrator,
            ProjectIndexProfileService projectIndexProfileService,
            LabIndexProfileOverrideFactory labIndexProfileOverrideFactory,
            EvaluationCorpusApplicationService evaluationCorpusApplicationService,
            ResolvedConfigSnapshotApplicationService resolvedConfigSnapshotApplicationService) {
        this.knowledgeIndexSnapshotLookupPort = knowledgeIndexSnapshotLookupPort;
        this.knowledgePipelineOrchestrator = knowledgePipelineOrchestrator;
        this.projectIndexProfileService = projectIndexProfileService;
        this.labIndexProfileOverrideFactory = labIndexProfileOverrideFactory;
        this.evaluationCorpusApplicationService = evaluationCorpusApplicationService;
        this.resolvedConfigSnapshotApplicationService = resolvedConfigSnapshotApplicationService;
    }

    /**
     * Returns a list the same size as {@code modelIds}; fills null slots by rebuilding corpus-scoped snapshots when possible.
     */
    public List<UUID> ensureAligned(
            UUID userId,
            UUID alignProjectId,
            UUID corpusId,
            List<String> modelIds,
            List<UUID> initialAligned) {
        if (modelIds == null || modelIds.isEmpty()) {
            return List.of();
        }
        List<UUID> aligned = new ArrayList<>(initialAligned != null ? initialAligned : List.of());
        while (aligned.size() < modelIds.size()) {
            aligned.add(null);
        }
        if (alignProjectId == null || corpusId == null) {
            return aligned;
        }
        try {
            evaluationCorpusApplicationService.requireContext(userId, corpusId);
        } catch (RuntimeException ex) {
            log.warn("embedding_campaign_align_skip corpusId={} reason={}", corpusId, ex.getMessage());
            return aligned;
        }

        List<IndexSnapshotEmbeddingLookup> corpusSnapshots =
                knowledgeIndexSnapshotLookupPort.findCorpusSnapshots(corpusId);
        List<IndexSnapshotEmbeddingLookup> projectSnapshots =
                knowledgeIndexSnapshotLookupPort.findProjectSnapshots(alignProjectId);
        for (int i = 0; i < modelIds.size(); i++) {
            UUID current = aligned.get(i);
            if (current != null && !snapshotMatchesModel(corpusSnapshots, projectSnapshots, current, modelIds.get(i))) {
                log.warn(
                        "embedding_campaign_snapshot_mismatch corpusId={} modelId={} snapshotId={} - rebuilding",
                        corpusId,
                        modelIds.get(i),
                        current);
                aligned.set(i, null);
            }
        }

        ExperimentalPresetCanonicalCatalog.IndexRequirements requirements =
                ExperimentalPresetCanonicalCatalog.IndexRequirements.none();
        List<String> buildFailures = new ArrayList<>();
        for (int i = 0; i < modelIds.size(); i++) {
            if (aligned.get(i) != null) {
                continue;
            }
            String modelId = modelIds.get(i);
            UUID built = buildCorpusSnapshotForModel(userId, alignProjectId, corpusId, modelId, requirements);
            if (built != null) {
                aligned.set(i, built);
                log.info(
                        "embedding_campaign_snapshot_prepared corpusId={} projectId={} modelId={} snapshotId={}",
                        corpusId,
                        alignProjectId,
                        modelId,
                        built);
            } else {
                buildFailures.add(modelId);
            }
        }
        if (!buildFailures.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "EMBEDDING_CAMPAIGN_SNAPSHOT_ALIGN_FAILED: could not prepare evaluation-corpus index snapshots"
                            + " for embedding models "
                            + buildFailures
                            + ". Check corpus documents are READY and the embedding provider is reachable.");
        }
        return aligned;
    }

    private UUID buildCorpusSnapshotForModel(
            UUID userId,
            UUID alignProjectId,
            UUID corpusId,
            String embeddingModelId,
            ExperimentalPresetCanonicalCatalog.IndexRequirements requirements) {
        Objects.requireNonNull(userId, "userId");
        ProjectIndexProfile current = projectIndexProfileService.ensureDefault(alignProjectId);
        ProjectIndexProfile effective =
                labIndexProfileOverrideFactory.withEmbeddingModelId(current, embeddingModelId);
        effective =
                labIndexProfileOverrideFactory.buildEffectiveProfile(
                        effective, requirements, LabPresetRunGroupKey.CHUNK_LEVEL);

        try {
            ResolvedConfigSnapshotLinkage resolved =
                    resolvedConfigSnapshotApplicationService.persistIngestionDefaultSnapshotLinkage(
                            userId, alignProjectId, Optional.empty());
            return knowledgePipelineOrchestrator.rebuildScopeWithProfileOverride(
                    alignProjectId,
                    CorpusScope.PROJECT_SHARED,
                    null,
                    KnowledgeSnapshotOwnerType.EVALUATION_CORPUS,
                    corpusId,
                    resolved.id(),
                    resolved.configHash(),
                    effective);
        } catch (RuntimeException ex) {
            log.error(
                    "embedding_campaign_snapshot_build_failed corpusId={} projectId={} modelId={} reason={}",
                    corpusId,
                    alignProjectId,
                    embeddingModelId,
                    ex.getMessage());
            return null;
        }
    }

    /** Aligns snapshot ids from explicit payload, evaluation-corpus owner, then project history. */
    public List<UUID> alignFromCatalog(
            UUID alignProjectId, UUID corpusId, List<String> modelIds, List<UUID> provided) {
        if (modelIds == null || modelIds.isEmpty()) {
            return List.of();
        }

        List<IndexSnapshotEmbeddingLookup> corpusSnapshots =
                corpusId != null ? knowledgeIndexSnapshotLookupPort.findCorpusSnapshots(corpusId) : List.of();
        List<IndexSnapshotEmbeddingLookup> projectSnapshots =
                alignProjectId != null
                        ? knowledgeIndexSnapshotLookupPort.findProjectSnapshots(alignProjectId)
                        : List.of();

        List<UUID> aligned = new ArrayList<>();
        for (int i = 0; i < modelIds.size(); i++) {
            String modelId = modelIds.get(i);
            UUID matched = null;
            if (provided != null && i < provided.size() && provided.get(i) != null) {
                matched =
                        snapshotMatchesModel(corpusSnapshots, projectSnapshots, provided.get(i), modelId)
                                ? provided.get(i)
                                : null;
            }
            if (matched == null) {
                matched = findMatchingSnapshot(corpusSnapshots, modelId);
            }
            if (matched == null) {
                matched = findMatchingSnapshot(projectSnapshots, modelId);
            }
            aligned.add(matched);
        }
        return aligned;
    }

    private static boolean snapshotMatchesModel(
            List<IndexSnapshotEmbeddingLookup> corpusSnapshots,
            List<IndexSnapshotEmbeddingLookup> projectSnapshots,
            UUID snapshotId,
            String modelId) {
        IndexSnapshotEmbeddingLookup snap = findSnapshotById(corpusSnapshots, snapshotId);
        if (snap == null) {
            snap = findSnapshotById(projectSnapshots, snapshotId);
        }
        if (snap == null) {
            return false;
        }
        return IndexProfileJsonSupport.readEmbeddingModelId(snap.indexProfileJsonb())
                .map(
                        prof ->
                                IndexProfileJsonSupport.normalizeEmbeddingKey(prof)
                                        .equals(IndexProfileJsonSupport.normalizeEmbeddingKey(modelId)))
                .orElse(false);
    }

    private static IndexSnapshotEmbeddingLookup findSnapshotById(
            List<IndexSnapshotEmbeddingLookup> snapshots, UUID snapshotId) {
        return snapshots.stream().filter(s -> snapshotId.equals(s.id())).findFirst().orElse(null);
    }

    private static UUID findMatchingSnapshot(List<IndexSnapshotEmbeddingLookup> snapshots, String modelId) {
        return snapshots.stream()
                .filter(
                        s ->
                                IndexProfileJsonSupport.readEmbeddingModelId(s.indexProfileJsonb())
                                        .map(
                                                prof ->
                                                        IndexProfileJsonSupport.normalizeEmbeddingKey(prof)
                                                                .equals(
                                                                        IndexProfileJsonSupport.normalizeEmbeddingKey(
                                                                                modelId)))
                                        .orElse(false))
                .map(IndexSnapshotEmbeddingLookup::id)
                .findFirst()
                .orElse(null);
    }
}
