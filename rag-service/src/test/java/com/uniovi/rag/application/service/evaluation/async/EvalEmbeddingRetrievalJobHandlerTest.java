package com.uniovi.rag.application.service.evaluation.async;

import com.uniovi.rag.application.service.evaluation.BenchmarkDatasetResolutionException;
import com.uniovi.rag.application.service.evaluation.ExperimentalDatasetResolver;
import com.uniovi.rag.application.service.evaluation.TypedBenchmarkDataset;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.domain.evaluation.snapshot.EmbeddingExperimentalSnapshot;
import com.uniovi.rag.domain.evaluation.snapshot.LlmExperimentalSnapshot;
import com.uniovi.rag.domain.evaluation.workbook.EmbeddingRetrievalDataset;
import com.uniovi.rag.domain.evaluation.workbook.EmbeddingRetrievalQuery;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.vector.EmbeddingSpaceGuard;
import com.uniovi.rag.infrastructure.vector.PgVectorStoreRegistry;
import com.uniovi.rag.application.service.async.AsyncTaskCancellationService;
import com.uniovi.rag.application.service.async.LabJobCancelledException;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import com.uniovi.rag.application.service.evaluation.EvaluationCanonicalPersistenceService;
import com.uniovi.rag.application.service.evaluation.EvaluationService;
import com.uniovi.rag.application.service.evaluation.baseline.BaselineRunSnapshotWriter;
import com.uniovi.rag.application.service.evaluation.baseline.ExperimentalSnapshotFactory;
import com.uniovi.rag.application.service.evaluation.baseline.ModelBaselineLlmRunner;
import com.uniovi.rag.application.service.evaluation.baseline.OllamaModelCatalogClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvalEmbeddingRetrievalJobHandlerTest {

    private static final LlmExperimentalSnapshot SAMPLE_LLM =
            new LlmExperimentalSnapshot(
                    "gemma:test",
                    0.2,
                    0.9,
                    5,
                    null,
                    1.05,
                    8192,
                    512,
                    null,
                    42,
                    List.of(),
                    null,
                    false,
                    List.of());

    private static final EmbeddingExperimentalSnapshot SAMPLE_EMB =
            new EmbeddingExperimentalSnapshot("emb:test", null, null, null, null, null, "MODEL_DEFAULT", List.of());

    @Mock
    private PgVectorStore vectorStore;

    @Mock
    private PgVectorStoreRegistry vectorStoreRegistry;

    @Mock
    private EmbeddingSpaceGuard embeddingSpaceGuard;

    @Mock
    private EvaluationService evaluationService;

    @Mock
    private EvaluationCanonicalPersistenceService canonicalPersistence;

    @Mock
    private ExperimentalDatasetResolver experimentalDatasetResolver;

    @Mock
    private BaselineRunSnapshotWriter baselineRunSnapshotWriter;

    @Mock
    private ExperimentalSnapshotFactory experimentalSnapshotFactory;

    @Mock
    private ModelBaselineLlmRunner modelBaselineLlmRunner;

    @Mock
    private OllamaModelCatalogClient ollamaModelCatalogClient;

    @Mock
    private EvaluationRunRepository evaluationRunRepository;

    @Mock
    private AsyncTaskMutationService mutation;

    @Mock
    private AsyncTaskCancellationService cancellationService;

    private EvalEmbeddingRetrievalJobHandler handler(int topK) {
        return new EvalEmbeddingRetrievalJobHandler(
                vectorStoreRegistry,
                embeddingSpaceGuard,
                evaluationService,
                canonicalPersistence,
                experimentalDatasetResolver,
                baselineRunSnapshotWriter,
                experimentalSnapshotFactory,
                modelBaselineLlmRunner,
                ollamaModelCatalogClient,
                evaluationRunRepository,
                cancellationService,
                topK);
    }

    private void stubBaselineForCanonicalRun(UUID runId) {
        when(experimentalSnapshotFactory.buildLlmSnapshot(any())).thenReturn(SAMPLE_LLM);
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(any())).thenReturn(SAMPLE_EMB);
        when(ollamaModelCatalogClient.isModelAvailable(anyString())).thenReturn(true);
        when(embeddingSpaceGuard.assertFitsPhysicalVectorColumnReturning(anyString())).thenReturn(1024);
        when(vectorStoreRegistry.forEmbeddingModelId(anyString())).thenReturn(vectorStore);
        when(evaluationRunRepository.findById(runId)).thenReturn(Optional.empty());
    }

    @Test
    void taskType_isEvalEmbeddingRetrieval() {
        assertThat(handler(3).taskType()).isEqualTo(AsyncTaskType.EVAL_EMBEDDING_RETRIEVAL);
    }

    @Test
    void cancellation_stopsBeforeNextItem_andPersistsPartialAsCancelled() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        stubBaselineForCanonicalRun(runId);

        List<EmbeddingRetrievalQuery> queries =
                List.of(
                        new EmbeddingRetrievalQuery(
                                "q1",
                                "Q1",
                                "",
                                Optional.empty(),
                                Optional.empty(),
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                ""),
                        new EmbeddingRetrievalQuery(
                                "q2",
                                "Q2",
                                "",
                                Optional.empty(),
                                Optional.empty(),
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                ""),
                        new EmbeddingRetrievalQuery(
                                "q3",
                                "Q3",
                                "",
                                Optional.empty(),
                                Optional.empty(),
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                ""));
        EmbeddingRetrievalDataset ds = new EmbeddingRetrievalDataset(queries, List.of(), List.of());
        when(experimentalDatasetResolver.resolve(runId)).thenReturn(new TypedBenchmarkDataset.EmbeddingQuestions(ds));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(new Document("d1")));

        // Allow first item, then request cancellation before second.
        Mockito.doNothing()
                .doThrow(new LabJobCancelledException("Cancellation requested by user"))
                .when(cancellationService)
                .throwIfCancellationRequested(eq(taskId));

        AsyncTaskEntity task = Mockito.mock(AsyncTaskEntity.class);
        when(task.getId()).thenReturn(taskId);
        when(task.getRequestPayload()).thenReturn(Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        assertThatThrownBy(() -> handler(2).run(task, mutation)).isInstanceOf(LabJobCancelledException.class);
        verify(canonicalPersistence).persistEmbeddingRetrievalResults(eq(runId), any());
        verify(mutation).appendProgressLine(eq(taskId), ArgumentMatchers.contains("Cancellation requested"));
    }

    @Test
    void constructor_clampsTopKToAtLeastOne() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        stubBaselineForCanonicalRun(runId);
        EmbeddingRetrievalQuery q =
                new EmbeddingRetrievalQuery(
                        "eq1",
                        "needle query",
                        "",
                        Optional.empty(),
                        Optional.empty(),
                        "needle",
                        List.of(),
                        List.of(),
                        "",
                        "",
                        "");
        when(experimentalDatasetResolver.resolve(runId))
                .thenReturn(
                        new TypedBenchmarkDataset.EmbeddingQuestions(
                                new EmbeddingRetrievalDataset(List.of(q), List.of(), List.of())));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("1", "NEEDLE in body", Map.of())));
        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        handler(0).run(task, mutation);

        ArgumentCaptor<SearchRequest> req = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(req.capture());
        assertThat(req.getValue().getTopK()).isEqualTo(1);
    }

    @Test
    void run_withRunId_resolvesTypedDataset_neverLegacyQaMap() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        stubBaselineForCanonicalRun(runId);
        EmbeddingRetrievalQuery q =
                new EmbeddingRetrievalQuery(
                        "eq1",
                        "q1",
                        "",
                        Optional.empty(),
                        Optional.empty(),
                        "alpha",
                        List.of(),
                        List.of(),
                        "",
                        "",
                        "");
        when(experimentalDatasetResolver.resolve(runId))
                .thenReturn(
                        new TypedBenchmarkDataset.EmbeddingQuestions(
                                new EmbeddingRetrievalDataset(List.of(q), List.of(), List.of())));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("d1", "contains ALPHA here", Map.of())));
        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        handler(2).run(task, mutation);

        ArgumentCaptor<Map<String, Object>> result = ArgumentCaptor.forClass(Map.class);
        verify(mutation).markSucceeded(eq(taskId), result.capture());
        assertThat(result.getValue()).containsKeys("results", "evaluation_summary");
        @SuppressWarnings("unchecked")
        Map<String, Object> retr =
                (Map<String, Object>)
                        ((Map<String, Object>) result.getValue().get("evaluation_summary")).get("retrieval");
        assertThat(retr.get("mean_mrr")).isNotNull();
        verify(canonicalPersistence).persistEmbeddingRetrievalResults(eq(runId), eq(result.getValue()));
    }

    @Test
    void run_withoutRunId_throws() {
        UUID taskId = UUID.randomUUID();
        AsyncTaskEntity task = task(taskId, Map.of());

        assertThatThrownBy(() -> handler(5).run(task, mutation)).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(vectorStoreRegistry);
        verifyNoInteractions(experimentalDatasetResolver);
    }

    @Test
    void run_perQueryFailure_recordsFailedOutcome_withoutAbortingWholeJob() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        stubBaselineForCanonicalRun(runId);
        EmbeddingRetrievalQuery q =
                new EmbeddingRetrievalQuery(
                        "eq1",
                        "q",
                        "",
                        Optional.empty(),
                        Optional.empty(),
                        "x",
                        List.of(),
                        List.of(),
                        "",
                        "",
                        "");
        when(experimentalDatasetResolver.resolve(runId))
                .thenReturn(
                        new TypedBenchmarkDataset.EmbeddingQuestions(
                                new EmbeddingRetrievalDataset(List.of(q), List.of(), List.of())));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenThrow(new RuntimeException("vs"));
        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        handler(2).run(task, mutation);

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(canonicalPersistence).persistEmbeddingRetrievalResults(eq(runId), payload.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) payload.getValue().get("results");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("item_outcome")).isEqualTo("FAILED");
        verify(mutation).markSucceeded(eq(taskId), ArgumentMatchers.any());
    }

    @Test
    void run_dimensionMismatch_recordsUnsupportedRows_withoutQueryingVectorStore() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        when(experimentalSnapshotFactory.buildLlmSnapshot(any())).thenReturn(SAMPLE_LLM);
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(any()))
                .thenReturn(
                        new EmbeddingExperimentalSnapshot(
                                "nomic-embed-text", null, null, null, null, null, "MODEL_DEFAULT", List.of()));
        when(evaluationRunRepository.findById(runId)).thenReturn(Optional.empty());
        when(embeddingSpaceGuard.assertFitsPhysicalVectorColumnReturning("nomic-embed-text"))
                .thenThrow(
                        new ResponseStatusException(
                                HttpStatus.UNPROCESSABLE_ENTITY,
                                "EMBEDDING_DIMENSION_MISMATCH: model 'nomic-embed-text' outputs 768 dimensions but this deployment's vector_store.embedding column is fixed to 1024"));
        when(ollamaModelCatalogClient.isModelAvailable(anyString())).thenReturn(true);
        EmbeddingRetrievalQuery q =
                new EmbeddingRetrievalQuery(
                        "eq1",
                        "q",
                        "",
                        Optional.empty(),
                        Optional.empty(),
                        "x",
                        List.of("doc-1"),
                        List.of(),
                        "",
                        "",
                        "");
        when(experimentalDatasetResolver.resolve(runId))
                .thenReturn(
                        new TypedBenchmarkDataset.EmbeddingQuestions(
                                new EmbeddingRetrievalDataset(List.of(q), List.of(), List.of())));
        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        handler(2).run(task, mutation);

        verifyNoInteractions(vectorStoreRegistry);
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(canonicalPersistence).persistEmbeddingRetrievalResults(eq(runId), payload.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) payload.getValue().get("results");
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("item_outcome")).isEqualTo("NOT_SUPPORTED");
        assertThat(rows.getFirst().get("error_code")).isEqualTo("EMBEDDING_DIMENSION_MISMATCH");
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) rows.getFirst().get("metrics");
        assertThat(metrics)
                .containsEntry("embedding_model_id", "nomic-embed-text")
                .containsEntry("embedding_compatibility_status", "INCOMPATIBLE")
                .containsEntry("embedding_compatibility_error_code", "EMBEDDING_DIMENSION_MISMATCH");
    }

    @Test
    void run_marksRunFailed_whenResolverFails() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        when(experimentalDatasetResolver.resolve(runId))
                .thenThrow(new BenchmarkDatasetResolutionException("bad dataset"));
        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        assertThatThrownBy(() -> handler(2).run(task, mutation)).isInstanceOf(BenchmarkDatasetResolutionException.class);

        verify(canonicalPersistence).markRunFailed(runId, "bad dataset");
    }

    private static AsyncTaskEntity task(UUID id, Map<String, Object> payload) {
        AsyncTaskEntity t = Mockito.mock(AsyncTaskEntity.class);
        Mockito.when(t.getId()).thenReturn(id);
        Mockito.when(t.getRequestPayload()).thenReturn(payload);
        return t;
    }
}
