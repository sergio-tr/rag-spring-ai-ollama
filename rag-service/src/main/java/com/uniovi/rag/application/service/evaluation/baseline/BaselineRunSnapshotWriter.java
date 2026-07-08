package com.uniovi.rag.application.service.evaluation.baseline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.evaluation.provenance.EvaluationBuildMetadataProvider;
import com.uniovi.rag.application.service.evaluation.provenance.EvaluationProvenanceSupport;
import com.uniovi.rag.domain.evaluation.snapshot.EmbeddingExperimentalSnapshot;
import com.uniovi.rag.domain.evaluation.snapshot.LlmExperimentalSnapshot;
import com.uniovi.rag.domain.evaluation.snapshot.PromptProfileSnapshot;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists Phase 5 experimental snapshots and evaluation provenance on {@link EvaluationRunEntity}. */
@Service
public class BaselineRunSnapshotWriter {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final EvaluationRunRepository evaluationRunRepository;
    private final ObjectMapper objectMapper;
    private final ResolvedLlmConfigResolver configResolver;
    private final EvaluationBuildMetadataProvider buildMetadataProvider;

    public BaselineRunSnapshotWriter(
            EvaluationRunRepository evaluationRunRepository,
            ObjectMapper objectMapper,
            ResolvedLlmConfigResolver configResolver,
            EvaluationBuildMetadataProvider buildMetadataProvider) {
        this.evaluationRunRepository = evaluationRunRepository;
        this.objectMapper = objectMapper;
        this.configResolver = configResolver;
        this.buildMetadataProvider = buildMetadataProvider;
    }

    @Transactional
    public void writeSnapshots(
            UUID runId,
            LlmExperimentalSnapshot llm,
            EmbeddingExperimentalSnapshot embedding,
            PromptProfileSnapshot prompts) {
        if (runId == null) {
            return;
        }
        EvaluationRunEntity run = evaluationRunRepository.findById(runId).orElse(null);
        if (run == null) {
            return;
        }
        if (llm != null) {
            run.setLlmExperimentalSnapshot(objectMapper.convertValue(llm, MAP_TYPE));
        }
        if (embedding != null) {
            run.setEmbeddingExperimentalSnapshot(objectMapper.convertValue(embedding, MAP_TYPE));
        }
        if (prompts != null) {
            run.setPromptProfileSnapshot(objectMapper.convertValue(prompts, MAP_TYPE));
        }
        UUID userId = run.getUser() != null ? run.getUser().getId() : null;
        UUID projectId = run.getProject() != null ? run.getProject().getId() : null;
        ResolvedLlmConfig config = configResolver.resolve(userId, projectId, null);
        Map<String, Object> provenance =
                EvaluationProvenanceSupport.build(config, prompts, buildMetadataProvider.metadata());
        run.setAggregatesJson(EvaluationProvenanceSupport.mergeIntoAggregates(run.getAggregatesJson(), provenance));
        evaluationRunRepository.save(run);
    }
}
