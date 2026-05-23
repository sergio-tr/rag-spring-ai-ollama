package com.uniovi.rag.application.service.admin.model;

import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectIndexProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllowedModelReferenceGuardTest {

    @Mock
    private EvaluationRunRepository evaluationRunRepository;

    @Mock
    private ProjectIndexProfileRepository projectIndexProfileRepository;

    @Test
    void isReferenced_trueWhenEvaluationRunUsesModel() {
        when(evaluationRunRepository.existsByEmbeddingModelId("bge-m3:latest")).thenReturn(true);
        AllowedModelReferenceGuard guard =
                new AllowedModelReferenceGuard(evaluationRunRepository, projectIndexProfileRepository);
        assertThat(guard.isReferenced("bge-m3:latest")).isTrue();
    }

    @Test
    void isReferenced_checksLatestSuffixForBaseName() {
        when(evaluationRunRepository.existsByLlmModelId("bge-m3")).thenReturn(false);
        when(evaluationRunRepository.existsByEmbeddingModelId("bge-m3")).thenReturn(false);
        when(evaluationRunRepository.existsByLlmModelId("bge-m3:latest")).thenReturn(true);
        AllowedModelReferenceGuard guard =
                new AllowedModelReferenceGuard(evaluationRunRepository, projectIndexProfileRepository);
        assertThat(guard.isReferenced("bge-m3")).isTrue();
    }
}
