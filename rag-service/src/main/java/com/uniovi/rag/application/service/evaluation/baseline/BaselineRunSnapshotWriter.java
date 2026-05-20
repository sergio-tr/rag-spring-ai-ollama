package com.uniovi.rag.application.service.evaluation.baseline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.domain.evaluation.snapshot.EmbeddingExperimentalSnapshot;
import com.uniovi.rag.domain.evaluation.snapshot.LlmExperimentalSnapshot;
import com.uniovi.rag.domain.evaluation.snapshot.PromptProfileSnapshot;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/** Persists Phase 5 experimental snapshots on {@link EvaluationRunEntity} JSON columns. */
@Service
public class BaselineRunSnapshotWriter {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final EvaluationRunRepository evaluationRunRepository;
    private final ObjectMapper objectMapper;

    public BaselineRunSnapshotWriter(EvaluationRunRepository evaluationRunRepository, ObjectMapper objectMapper) {
        this.evaluationRunRepository = evaluationRunRepository;
        this.objectMapper = objectMapper;
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
        evaluationRunRepository.save(run);
    }
}
