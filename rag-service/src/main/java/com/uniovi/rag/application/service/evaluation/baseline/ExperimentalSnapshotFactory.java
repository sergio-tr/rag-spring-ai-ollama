package com.uniovi.rag.application.service.evaluation.baseline;

import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.evaluation.LabBenchmarkDefaultModelResolver;
import com.uniovi.rag.application.service.llm.catalog.LlmCatalogApiService;
import com.uniovi.rag.domain.evaluation.snapshot.EmbeddingExperimentalSnapshot;
import com.uniovi.rag.domain.evaluation.snapshot.ExperimentalSnapshotFieldSource;
import com.uniovi.rag.domain.evaluation.snapshot.LlmExperimentalSnapshot;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Builds baseline snapshots from resolved LLM config plus optional {@link EvaluationRunEntity} overrides. */
@Component
public class ExperimentalSnapshotFactory {

    private final LabBenchmarkDefaultModelResolver defaultModelResolver;
    private final ResolvedLlmConfigResolver configResolver;
    private final int defaultTopK;
    private final int defaultNumCtx;

    public ExperimentalSnapshotFactory(
            LabBenchmarkDefaultModelResolver defaultModelResolver,
            ResolvedLlmConfigResolver configResolver,
            @Value("${spring.ai.ollama.top-k:10}") int defaultTopK,
            @Value("${spring.ai.ollama.options.num-ctx:8192}") int defaultNumCtx) {
        this.defaultModelResolver = defaultModelResolver;
        this.configResolver = configResolver;
        this.defaultTopK = Math.max(1, defaultTopK);
        this.defaultNumCtx = Math.max(512, defaultNumCtx);
    }

    public LlmExperimentalSnapshot buildLlmSnapshot(EvaluationRunEntity run) {
        UUID userId = run != null && run.getUser() != null ? run.getUser().getId() : null;
        UUID projectId = run != null && run.getProject() != null ? run.getProject().getId() : null;
        ResolvedLlmConfig config = configResolver.resolve(userId, projectId, null);
        String runOverride = run != null ? run.getLlmModelId() : null;
        String model = defaultModelResolver.resolveLlmModelId(userId, runOverride);
        ExperimentalSnapshotFieldSource modelSource =
                runOverride != null && !runOverride.isBlank()
                        ? ExperimentalSnapshotFieldSource.RUN_OVERRIDE
                        : ExperimentalSnapshotFieldSource.RESOLVED_CONFIG;
        return ResolvedLlmExperimentalSnapshotMapper.toLlmSnapshot(
                config, model, modelSource, defaultTopK, defaultNumCtx);
    }

    public EmbeddingExperimentalSnapshot buildEmbeddingSnapshot(EvaluationRunEntity run) {
        UUID userId = run != null && run.getUser() != null ? run.getUser().getId() : null;
        UUID projectId = run != null && run.getProject() != null ? run.getProject().getId() : null;
        ResolvedLlmConfig config = configResolver.resolve(userId, projectId, null);

        Map<String, String> fieldSources = new LinkedHashMap<>();
        String runOverride = run != null ? run.getEmbeddingModelId() : null;
        String model = defaultModelResolver.resolveEmbeddingModelId(userId, runOverride);
        fieldSources.put(
                "model",
                runOverride != null && !runOverride.isBlank()
                        ? ExperimentalSnapshotFieldSource.RUN_OVERRIDE.name()
                        : ExperimentalSnapshotFieldSource.RESOLVED_CONFIG.name());
        fieldSources.put("chatProvider", ExperimentalSnapshotFieldSource.RESOLVED_CONFIG.name());
        fieldSources.put("embeddingProvider", ExperimentalSnapshotFieldSource.RESOLVED_CONFIG.name());

        Integer dim = run != null && run.getEmbeddingDimensions() != null ? run.getEmbeddingDimensions() : null;
        if (dim != null) {
            fieldSources.put("dimension", ExperimentalSnapshotFieldSource.RUN_ENTITY.name());
        } else if (model != null) {
            var resolvedDim = LlmCatalogApiService.resolveEmbeddingDimensions(model);
            if (resolvedDim.isPresent()) {
                dim = resolvedDim.getAsInt();
                fieldSources.put("dimension", ExperimentalSnapshotFieldSource.CATALOG_HEURISTIC.name());
            } else {
                fieldSources.put("dimension", ExperimentalSnapshotFieldSource.UNKNOWN.name());
            }
        } else {
            fieldSources.put("dimension", ExperimentalSnapshotFieldSource.UNKNOWN.name());
        }

        List<String> unsupported = new ArrayList<>();
        unsupported.add("normalize");
        unsupported.add("queryPrefix");
        unsupported.add("passagePrefix");
        unsupported.add("batchSize");
        unsupported.add("truncateStrategy");
        fieldSources.put("truncateStrategy", ExperimentalSnapshotFieldSource.NOT_APPLIED.name());
        fieldSources.put("normalize", ExperimentalSnapshotFieldSource.UNSUPPORTED.name());
        fieldSources.put("queryPrefix", ExperimentalSnapshotFieldSource.UNSUPPORTED.name());
        fieldSources.put("passagePrefix", ExperimentalSnapshotFieldSource.UNSUPPORTED.name());
        fieldSources.put("batchSize", ExperimentalSnapshotFieldSource.UNSUPPORTED.name());

        return new EmbeddingExperimentalSnapshot(
                model,
                dim,
                null,
                null,
                null,
                null,
                null,
                config.chatProvider().name(),
                config.embeddingProvider().name(),
                Map.copyOf(fieldSources),
                List.copyOf(unsupported));
    }
}
