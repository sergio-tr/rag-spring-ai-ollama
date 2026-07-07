package com.uniovi.rag.application.service.evaluation.baseline;

import com.uniovi.rag.application.service.evaluation.BenchmarkRuntimeParametersSupport;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.ResolvedConfigSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ResolvedConfigSnapshotEntity;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Resolves retrieval knobs for {@code EMBEDDING_RETRIEVAL} from a bound resolved-config snapshot or app defaults. */
@Component
public class EmbeddingRetrievalRunConfigResolver {

    public record Params(
            int topK,
            double similarityThreshold,
            boolean rankerEnabled,
            boolean metadataEnabled,
            boolean expansionEnabled) {}

    private final EvaluationRunRepository evaluationRunRepository;
    private final ResolvedConfigSnapshotRepository resolvedConfigSnapshotRepository;
    private final int defaultTopK;
    private final double defaultSimilarityThreshold;

    public EmbeddingRetrievalRunConfigResolver(
            EvaluationRunRepository evaluationRunRepository,
            ResolvedConfigSnapshotRepository resolvedConfigSnapshotRepository,
            @Value("${spring.ai.ollama.top-k:8}") int defaultTopK,
            @Value("${spring.ai.ollama.similarity-threshold:0.25}") double defaultSimilarityThreshold) {
        this.evaluationRunRepository = evaluationRunRepository;
        this.resolvedConfigSnapshotRepository = resolvedConfigSnapshotRepository;
        this.defaultTopK = Math.max(1, defaultTopK);
        this.defaultSimilarityThreshold = defaultSimilarityThreshold;
    }

    @Transactional(readOnly = true)
    public Params resolveForRun(UUID evaluationRunId) {
        if (evaluationRunId == null) {
            return defaultsWithoutSnapshot();
        }
        EvaluationRunEntity run = evaluationRunRepository.findById(evaluationRunId).orElse(null);
        if (run == null) {
            return defaultsWithoutSnapshot();
        }
        Map<String, Object> runtime = BenchmarkRuntimeParametersSupport.readFromRun(run);
        Params base =
                run.getResolvedConfigSnapshot() == null
                        ? defaultsWithoutSnapshot()
                        : fromSnapshotEntity(run.getResolvedConfigSnapshot());
        return new Params(
                BenchmarkRuntimeParametersSupport.overlayTopK(base.topK(), runtime),
                BenchmarkRuntimeParametersSupport.overlaySimilarityThreshold(base.similarityThreshold(), runtime),
                base.rankerEnabled(),
                base.metadataEnabled(),
                base.expansionEnabled());
    }

    public Params defaultsWithoutSnapshot() {
        return new Params(defaultTopK, 0.0, false, false, false);
    }

    private Params fromSnapshotEntity(ResolvedConfigSnapshotEntity snap) {
        if (snap == null) {
            return defaultsWithoutSnapshot();
        }
        Map<String, Object> payload = snap.getPayloadJsonb();
        if (payload == null || payload.isEmpty()) {
            return resolvedConfigSnapshotRepository
                    .findById(snap.getId())
                    .map(this::payloadFromEntity)
                    .orElseGet(this::defaultsWithoutSnapshot);
        }
        return fromPayload(payload);
    }

    private Params payloadFromEntity(ResolvedConfigSnapshotEntity snap) {
        Map<String, Object> payload = snap.getPayloadJsonb();
        return payload == null || payload.isEmpty() ? defaultsWithoutSnapshot() : fromPayload(payload);
    }

    private Params fromPayload(Map<String, Object> payload) {
        int topK = Math.max(1, readInt(payload, "topK", defaultTopK));
        double threshold = readDouble(payload, "similarityThreshold", 0.0);
        return new Params(
                topK,
                threshold,
                readBool(payload, "rankerEnabled", false),
                readBool(payload, "metadataEnabled", false),
                readBool(payload, "expansionEnabled", false));
    }

    private static int readInt(Map<String, Object> payload, String key, int fallback) {
        Object v = payload.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return fallback;
    }

    private static double readDouble(Map<String, Object> payload, String key, double fallback) {
        Object v = payload.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return fallback;
    }

    private static boolean readBool(Map<String, Object> payload, String key, boolean fallback) {
        Object v = payload.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        return fallback;
    }
}
