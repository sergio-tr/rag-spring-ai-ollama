package com.uniovi.rag.application.service.evaluation.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.infrastructure.persistence.EvaluationCorpusRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvaluationRunRagJobContextLoaderTest {

    @Mock
    private EvaluationRunRepository evaluationRunRepository;

    @Mock
    private EvaluationCorpusRepository evaluationCorpusRepository;

    @Test
    void loadContext_returnsDtoWithoutEntity() {
        UUID runId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Map<String, Object> aggregates = Map.of("autoReindexPolicy", Map.of("enabled", true));

        when(evaluationRunRepository.findUserIdByRunId(runId)).thenReturn(Optional.of(userId));
        when(evaluationRunRepository.findDatasetIdByRunId(runId)).thenReturn(Optional.of(datasetId));
        when(evaluationRunRepository.findDatasetExperimentalKindByRunId(runId)).thenReturn(Optional.of("RAG_PRESET_END_TO_END"));
        when(evaluationRunRepository.findCorpusIdByRunId(runId)).thenReturn(Optional.of(corpusId));
        when(evaluationRunRepository.findEffectiveProjectIdByRunId(runId)).thenReturn(Optional.of(projectId));
        when(evaluationRunRepository.findAggregatesJsonByRunId(runId)).thenReturn(Optional.of(aggregates));
        when(evaluationCorpusRepository.findNameById(corpusId)).thenReturn(Optional.of("Lab KB"));

        EvaluationRunRagJobContextLoader loader =
                new EvaluationRunRagJobContextLoader(evaluationRunRepository, evaluationCorpusRepository);

        Optional<EvaluationRunRagJobContext> out = loader.loadContext(runId);

        assertThat(out).isPresent();
        EvaluationRunRagJobContext ctx = out.get();
        assertThat(ctx.runId()).isEqualTo(runId);
        assertThat(ctx.userId()).isEqualTo(userId);
        assertThat(ctx.corpusId()).isEqualTo(corpusId);
        assertThat(ctx.projectId()).isEqualTo(projectId);
        assertThat(ctx.autoReindexEnabled()).isTrue();
        assertThat(ctx.knowledgeBaseName()).isEqualTo("Lab KB");
    }

    @Test
    void mergeAggregatesJson_updatesWithoutLoadingRunEntity() {
        UUID runId = UUID.randomUUID();
        when(evaluationRunRepository.findAggregatesJsonByRunId(runId))
                .thenReturn(Optional.of(new LinkedHashMap<>(Map.of("a", 1))));

        EvaluationRunRagJobContextLoader loader =
                new EvaluationRunRagJobContextLoader(evaluationRunRepository, evaluationCorpusRepository);

        loader.mergeAggregatesJson(runId, Map.of("b", 2));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(evaluationRunRepository).updateAggregatesJson(eq(runId), captor.capture());
        assertThat(captor.getValue()).containsEntry("a", 1).containsEntry("b", 2);
    }
}
