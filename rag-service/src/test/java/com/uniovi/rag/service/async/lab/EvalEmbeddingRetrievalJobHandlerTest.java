package com.uniovi.rag.service.async.lab;

import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import com.uniovi.rag.service.evaluation.EvaluationCanonicalPersistenceService;
import com.uniovi.rag.service.evaluation.EvaluationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvalEmbeddingRetrievalJobHandlerTest {

    @Mock
    private PgVectorStore vectorStore;

    @Mock
    private EvaluationService evaluationService;

    @Mock
    private EvaluationCanonicalPersistenceService canonicalPersistence;

    @Mock
    private AsyncTaskMutationService mutation;

    @Test
    void taskType_isEvalEmbeddingRetrieval() {
        EvalEmbeddingRetrievalJobHandler h =
                new EvalEmbeddingRetrievalJobHandler(vectorStore, evaluationService, canonicalPersistence, 3);
        assertThat(h.taskType()).isEqualTo(AsyncTaskType.EVAL_EMBEDDING_RETRIEVAL);
    }

    @Test
    void constructor_clampsTopKToAtLeastOne() {
        EvalEmbeddingRetrievalJobHandler h =
                new EvalEmbeddingRetrievalJobHandler(vectorStore, evaluationService, canonicalPersistence, 0);
        UUID taskId = UUID.randomUUID();
        when(evaluationService.getQuestionsAndAnswers()).thenReturn(Map.of("q", "needle"));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("1", "NEEDLE in body", Map.of())));
        AsyncTaskEntity task = task(taskId, Map.of());

        h.run(task, mutation);

        ArgumentCaptor<SearchRequest> req = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(req.capture());
        assertThat(req.getValue().getTopK()).isEqualTo(1);
    }

    @Test
    void run_buildsSummary_andPersistsWhenRunIdPresent() {
        EvalEmbeddingRetrievalJobHandler h =
                new EvalEmbeddingRetrievalJobHandler(vectorStore, evaluationService, canonicalPersistence, 2);
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Map<String, String> qa = new LinkedHashMap<>();
        qa.put("q1", "alpha");
        when(evaluationService.getQuestionsAndAnswers()).thenReturn(qa);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("d1", "contains ALPHA here", Map.of())));
        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        h.run(task, mutation);

        ArgumentCaptor<Map<String, Object>> result = ArgumentCaptor.forClass(Map.class);
        verify(mutation).markSucceeded(eq(taskId), result.capture());
        assertThat(result.getValue()).containsKeys("results", "evaluation_summary");
        verify(canonicalPersistence).persistEmbeddingRetrievalResults(eq(runId), eq(result.getValue()));
    }

    @Test
    void run_emptyQuestions_stillSucceeds() {
        EvalEmbeddingRetrievalJobHandler h =
                new EvalEmbeddingRetrievalJobHandler(vectorStore, evaluationService, canonicalPersistence, 5);
        UUID taskId = UUID.randomUUID();
        when(evaluationService.getQuestionsAndAnswers()).thenReturn(Map.of());
        AsyncTaskEntity task = task(taskId, Map.of());

        h.run(task, mutation);

        verify(mutation).markSucceeded(eq(taskId), any());
        verifyNoInteractions(vectorStore);
    }

    @Test
    void run_marksRunFailed_onFailureWhenRunIdPresent() {
        EvalEmbeddingRetrievalJobHandler h =
                new EvalEmbeddingRetrievalJobHandler(vectorStore, evaluationService, canonicalPersistence, 2);
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        when(evaluationService.getQuestionsAndAnswers()).thenReturn(Map.of("q", "x"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenThrow(new RuntimeException("vs"));
        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        assertThatThrownBy(() -> h.run(task, mutation)).isInstanceOf(RuntimeException.class).hasMessage("vs");

        verify(canonicalPersistence).markRunFailed(runId, "vs");
    }

    private static AsyncTaskEntity task(UUID id, Map<String, Object> payload) {
        AsyncTaskEntity t = org.mockito.Mockito.mock(AsyncTaskEntity.class);
        org.mockito.Mockito.when(t.getId()).thenReturn(id);
        org.mockito.Mockito.when(t.getRequestPayload()).thenReturn(payload);
        return t;
    }
}
