package com.uniovi.rag.application.service.evaluation.async;

import com.uniovi.rag.infrastructure.persistence.EvaluationCorpusRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Loads scalar RAG job context inside short read-only transactions (no detached JPA graphs). */
@Component
public class EvaluationRunRagJobContextLoader {

    private static final String AGG_CORPUS_BOOTSTRAP_POLICY = "corpusBootstrapPolicy";
    private static final String AGG_AUTO_REINDEX_POLICY = "autoReindexPolicy";
    private static final String AGG_REQUESTED_PRESET_CODES = "requested_preset_codes";

    private final EvaluationRunRepository evaluationRunRepository;
    private final EvaluationCorpusRepository evaluationCorpusRepository;

    public EvaluationRunRagJobContextLoader(
            EvaluationRunRepository evaluationRunRepository,
            EvaluationCorpusRepository evaluationCorpusRepository) {
        this.evaluationRunRepository = evaluationRunRepository;
        this.evaluationCorpusRepository = evaluationCorpusRepository;
    }

    @Transactional(readOnly = true)
    public Optional<EvaluationRunRagJobContext> loadContext(UUID evaluationRunId) {
        if (evaluationRunId == null) {
            return Optional.empty();
        }
        Optional<UUID> userId = evaluationRunRepository.findUserIdByRunId(evaluationRunId);
        if (userId.isEmpty()) {
            return Optional.empty();
        }
        UUID datasetId = evaluationRunRepository.findDatasetIdByRunId(evaluationRunId).orElse(null);
        String datasetKind =
                evaluationRunRepository.findDatasetExperimentalKindByRunId(evaluationRunId).orElse(null);
        UUID corpusId = evaluationRunRepository.findCorpusIdByRunId(evaluationRunId).orElse(null);
        UUID projectId = evaluationRunRepository.findEffectiveProjectIdByRunId(evaluationRunId).orElse(null);
        Map<String, Object> aggregates =
                evaluationRunRepository
                        .findAggregatesJsonByRunId(evaluationRunId)
                        .map(EvaluationRunRagJobContextLoader::copyAggregates)
                        .orElseGet(LinkedHashMap::new);
        String knowledgeBaseName =
                corpusId != null
                        ? evaluationCorpusRepository.findNameById(corpusId).orElse("")
                        : "";
        return Optional.of(
                new EvaluationRunRagJobContext(
                        evaluationRunId,
                        userId.get(),
                        datasetId,
                        datasetKind,
                        corpusId,
                        knowledgeBaseName,
                        projectId,
                        corpusBootstrapEnabled(aggregates),
                        autoReindexEnabled(aggregates),
                        Map.copyOf(aggregates),
                        requestedPresetCodes(aggregates)));
    }

    @Transactional
    public void markAutoReindexLockAcquired(UUID evaluationRunId) {
        mergeAggregatesJson(evaluationRunId, Map.of("autoReindexLockAcquired", Boolean.TRUE));
    }

    @Transactional
    public void mergeAggregatesJson(UUID evaluationRunId, Map<String, Object> patch) {
        if (evaluationRunId == null || patch == null || patch.isEmpty()) {
            return;
        }
        Map<String, Object> agg =
                evaluationRunRepository
                        .findAggregatesJsonByRunId(evaluationRunId)
                        .map(EvaluationRunRagJobContextLoader::copyAggregates)
                        .orElseGet(LinkedHashMap::new);
        Map<String, Object> merged = new LinkedHashMap<>(agg);
        merged.putAll(patch);
        evaluationRunRepository.updateAggregatesJson(evaluationRunId, Map.copyOf(merged));
    }

    private static Map<String, Object> copyAggregates(Map<String, Object> source) {
        return source != null ? new LinkedHashMap<>(source) : new LinkedHashMap<>();
    }

    private static boolean corpusBootstrapEnabled(Map<String, Object> aggregates) {
        Object policyObj = aggregates.get(AGG_CORPUS_BOOTSTRAP_POLICY);
        if (!(policyObj instanceof Map<?, ?> policy)) {
            return false;
        }
        return Boolean.TRUE.equals(policy.get("enabled"));
    }

    private static boolean autoReindexEnabled(Map<String, Object> aggregates) {
        Object policyObj = aggregates.get(AGG_AUTO_REINDEX_POLICY);
        if (!(policyObj instanceof Map<?, ?> policy)) {
            return false;
        }
        return Boolean.TRUE.equals(policy.get("enabled"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> requestedPresetCodes(Map<String, Object> aggregates) {
        Object raw = aggregates.get(AGG_REQUESTED_PRESET_CODES);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object row : list) {
            if (row != null) {
                out.add(String.valueOf(row));
            }
        }
        return List.copyOf(out);
    }
}
