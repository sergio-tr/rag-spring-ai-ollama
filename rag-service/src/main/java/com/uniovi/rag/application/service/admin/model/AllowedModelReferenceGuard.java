package com.uniovi.rag.application.service.admin.model;

import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectIndexProfileRepository;
import org.springframework.stereotype.Component;

/**
 * Detects whether an allowlisted model id is referenced by historical evaluation runs or project profiles.
 */
@Component
public class AllowedModelReferenceGuard {

    private final EvaluationRunRepository evaluationRunRepository;
    private final ProjectIndexProfileRepository projectIndexProfileRepository;

    public AllowedModelReferenceGuard(
            EvaluationRunRepository evaluationRunRepository,
            ProjectIndexProfileRepository projectIndexProfileRepository) {
        this.evaluationRunRepository = evaluationRunRepository;
        this.projectIndexProfileRepository = projectIndexProfileRepository;
    }

    public boolean isReferenced(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }
        String trimmed = modelName.trim();
        if (evaluationRunRepository.existsByLlmModelId(trimmed)
                || evaluationRunRepository.existsByEmbeddingModelId(trimmed)) {
            return true;
        }
        if (!trimmed.contains(":")) {
            String withLatest = trimmed + ":latest";
            if (evaluationRunRepository.existsByLlmModelId(withLatest)
                    || evaluationRunRepository.existsByEmbeddingModelId(withLatest)) {
                return true;
            }
        }
        return projectIndexProfileRepository.existsByEmbeddingModelId(trimmed)
                || (!trimmed.contains(":")
                        && projectIndexProfileRepository.existsByEmbeddingModelId(trimmed + ":latest"));
    }
}
