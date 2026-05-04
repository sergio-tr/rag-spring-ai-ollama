package com.uniovi.rag.service.async.lab;

import com.uniovi.rag.application.service.evaluation.BenchmarkDatasetResolutionException;
import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import com.uniovi.rag.application.service.evaluation.ExperimentalDatasetResolver;
import com.uniovi.rag.application.service.evaluation.TypedBenchmarkDataset;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.domain.evaluation.BenchmarkEvaluationProtocol;
import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import com.uniovi.rag.domain.evaluation.snapshot.LlmExperimentalSnapshot;
import com.uniovi.rag.domain.evaluation.snapshot.PromptProfileSnapshot;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.evaluation.workbook.DifficultyLevel;
import com.uniovi.rag.domain.evaluation.workbook.EmbeddingRetrievalDataset;
import com.uniovi.rag.domain.evaluation.workbook.EmbeddingRetrievalQuery;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import com.uniovi.rag.service.evaluation.EvaluationCanonicalPersistenceService;
import com.uniovi.rag.service.evaluation.EvaluationService;
import com.uniovi.rag.service.evaluation.baseline.BaselineRunSnapshotWriter;
import com.uniovi.rag.service.evaluation.baseline.EmbeddingRetrievalMetrics;
import com.uniovi.rag.service.evaluation.baseline.ExperimentalSnapshotFactory;
import com.uniovi.rag.service.evaluation.baseline.ModelBaselineLlmRunner;
import com.uniovi.rag.service.evaluation.baseline.OllamaModelCatalogClient;
import com.uniovi.rag.service.evaluation.baseline.PromptProfileSnapshotFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Retrieval benchmark with Phase 5 protocols: pure embedding retrieval metrics (Recall@k, MRR) and optional downstream
 * fixed-LLM answer when {@link EvaluationRunEntity#isEmbeddingDownstreamRag()} is true.
 */
@Component
class EvalEmbeddingRetrievalJobHandler implements LabJobHandler {

    private final PgVectorStore vectorStore;
    private final EvaluationService evaluationService;
    private final EvaluationCanonicalPersistenceService canonicalPersistence;
    private final ExperimentalDatasetResolver experimentalDatasetResolver;
    private final BaselineRunSnapshotWriter baselineRunSnapshotWriter;
    private final ExperimentalSnapshotFactory experimentalSnapshotFactory;
    private final ModelBaselineLlmRunner modelBaselineLlmRunner;
    private final OllamaModelCatalogClient ollamaModelCatalogClient;
    private final EvaluationRunRepository evaluationRunRepository;
    private final int topK;

    EvalEmbeddingRetrievalJobHandler(
            PgVectorStore vectorStore,
            EvaluationService evaluationService,
            EvaluationCanonicalPersistenceService canonicalPersistence,
            ExperimentalDatasetResolver experimentalDatasetResolver,
            BaselineRunSnapshotWriter baselineRunSnapshotWriter,
            ExperimentalSnapshotFactory experimentalSnapshotFactory,
            ModelBaselineLlmRunner modelBaselineLlmRunner,
            OllamaModelCatalogClient ollamaModelCatalogClient,
            EvaluationRunRepository evaluationRunRepository,
            @Value("${spring.ai.ollama.top-k:5}") int topK) {
        this.vectorStore = vectorStore;
        this.evaluationService = evaluationService;
        this.canonicalPersistence = canonicalPersistence;
        this.experimentalDatasetResolver = experimentalDatasetResolver;
        this.baselineRunSnapshotWriter = baselineRunSnapshotWriter;
        this.experimentalSnapshotFactory = experimentalSnapshotFactory;
        this.modelBaselineLlmRunner = modelBaselineLlmRunner;
        this.ollamaModelCatalogClient = ollamaModelCatalogClient;
        this.evaluationRunRepository = evaluationRunRepository;
        this.topK = Math.max(1, topK);
    }

    @Override
    public AsyncTaskType taskType() {
        return AsyncTaskType.EVAL_EMBEDDING_RETRIEVAL;
    }

    @Override
    public void run(AsyncTaskEntity task, AsyncTaskMutationService mutation) {
        UUID taskId = task.getId();
        UUID evaluationRunId = LabJobPayloads.evaluationRunId(task.getRequestPayload());
        LabEvalConcurrency.SERIAL_EVAL.lock();
        try {
            List<Map<String, Object>> rows;
            EmbeddingRetrievalDataset embeddingDs = null;
            EmbeddingBenchmarkContext ctx = EmbeddingBenchmarkContext.disabled();

            if (evaluationRunId == null) {
                throw new IllegalStateException(
                        "EVAL_EMBEDDING_RETRIEVAL jobs require evaluation_run_id on the async payload; "
                                + "enqueue via POST /lab/benchmarks/EMBEDDING_RETRIEVAL/runs with a typed evaluation_dataset.");
            }
            mutation.appendProgressLine(taskId, "Resolving typed dataset for EMBEDDING_RETRIEVAL…");
            TypedBenchmarkDataset typed = experimentalDatasetResolver.resolve(evaluationRunId);
            if (!(typed instanceof TypedBenchmarkDataset.EmbeddingQuestions emb)) {
                throw new IllegalStateException("Resolver returned unexpected payload for EMBEDDING_RETRIEVAL");
            }
            embeddingDs = emb.dataset();
            mutation.appendProgressLine(
                    taskId,
                    "Parsed dataset EMBEDDING_RETRIEVAL: " + embeddingDs.queries().size() + " queries");

            Optional<EvaluationRunEntity> runOpt = evaluationRunRepository.findById(evaluationRunId);
            EvaluationRunEntity runOrNull = runOpt.orElse(null);
            var llmSnap = experimentalSnapshotFactory.buildLlmSnapshot(runOrNull);
            var embSnap = experimentalSnapshotFactory.buildEmbeddingSnapshot(runOrNull);
            PromptProfileSnapshot prompts = PromptProfileSnapshotFactory.baselineLabProfile();
            baselineRunSnapshotWriter.writeSnapshots(evaluationRunId, llmSnap, embSnap, prompts);

            boolean downstream = runOrNull != null && runOrNull.isEmbeddingDownstreamRag();
            boolean embeddingCatalogOk = ollamaModelCatalogClient.isModelAvailable(embSnap.model());
            boolean downstreamLlmOk =
                    !downstream || ollamaModelCatalogClient.isModelAvailable(llmSnap.model());
            ctx =
                    new EmbeddingBenchmarkContext(
                            downstream, embeddingCatalogOk, downstreamLlmOk, llmSnap, prompts, embSnap.model());
            rows = runEmbeddingQueries(taskId, mutation, embeddingDs.queries(), ctx);

            int hitsAt1 = 0;
            int hitsAtK = 0;
            double mrrSum = 0.0;
            int n = 0;
            for (Map<String, Object> row : rows) {
                @SuppressWarnings("unchecked")
                Map<String, Object> metrics =
                        row.get("metrics") instanceof Map ? (Map<String, Object>) row.get("metrics") : Map.of();
                Number r1 = (Number) metrics.getOrDefault("recall_at_1", 0);
                Number rk = (Number) metrics.getOrDefault("recall_at_k", 0);
                Number mrr = (Number) metrics.getOrDefault("mrr", 0);
                if (r1.doubleValue() >= 1.0) {
                    hitsAt1++;
                }
                if (rk.doubleValue() >= 1.0) {
                    hitsAtK++;
                }
                mrrSum += mrr.doubleValue();
                n++;
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            Map<String, Object> retrieval = new LinkedHashMap<>();
            retrieval.put("mean_recall_at_1", n > 0 ? (double) hitsAt1 / n : null);
            retrieval.put("mean_recall_at_k", n > 0 ? (double) hitsAtK / n : null);
            retrieval.put("mean_mrr", n > 0 ? mrrSum / n : null);
            retrieval.put("n", n);
            retrieval.put("k", topK);
            if (embeddingDs != null) {
                retrieval.put("chunk_registry_rows", embeddingDs.chunkRegistry().size());
                retrieval.put("corpus_documents_rows", embeddingDs.corpusDocuments().size());
            }
            summary.put("retrieval", retrieval);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("results", rows);
            payload.put("evaluation_summary", summary);
            if (evaluationRunId != null) {
                canonicalPersistence.persistEmbeddingRetrievalResults(evaluationRunId, payload);
            }
            mutation.markSucceeded(taskId, payload);
        } catch (BenchmarkDatasetResolutionException e) {
            if (evaluationRunId != null) {
                canonicalPersistence.markRunFailed(evaluationRunId, e.getMessage());
            }
            throw e;
        } catch (RuntimeException e) {
            if (evaluationRunId != null) {
                canonicalPersistence.markRunFailed(evaluationRunId, e.getMessage());
            }
            throw e;
        } finally {
            LabEvalConcurrency.SERIAL_EVAL.unlock();
        }
    }

    private List<Map<String, Object>> runEmbeddingQueries(
            UUID taskId,
            AsyncTaskMutationService mutation,
            List<EmbeddingRetrievalQuery> queries,
            EmbeddingBenchmarkContext ctx) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int n = queries.size();
        int idx = 0;
        for (EmbeddingRetrievalQuery q : queries) {
            idx++;
            mutation.appendProgressLine(taskId, "Running item " + idx + "/" + n);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(BenchmarkResultRowKeys.DATASET_QUESTION_ID, q.id());
            String question = q.query();
            String expected = q.expectedAnswer() != null ? q.expectedAnswer() : "";
            row.put("question", question);
            row.put("expected_answer", expected);
            row.put("query_type", q.queryType().map(QueryType::name).orElse(null));
            row.put(BenchmarkResultRowKeys.DIFFICULTY, q.difficulty().map(DifficultyLevel::name).orElse(null));
            row.put(BenchmarkResultRowKeys.EMBEDDING_MODEL_ID, ctx.embeddingModelId());
            BenchmarkEvaluationProtocol protocol =
                    ctx.downstreamRag
                            ? BenchmarkEvaluationProtocol.EMBEDDING_DOWNSTREAM_RAG
                            : BenchmarkEvaluationProtocol.EMBEDDING_RETRIEVAL_PURE;
            row.put("benchmark_protocol", protocol.name());

            if (!ctx.embeddingModelCatalogOk) {
                Map<String, Object> metrics = baseMetrics(ctx);
                metrics.put("recall_at_1", 0.0);
                metrics.put("recall_at_3", 0.0);
                metrics.put("recall_at_5", 0.0);
                metrics.put("recall_at_k", 0.0);
                metrics.put("mrr", 0.0);
                metrics.put("first_relevant_rank", 0);
                metrics.put("retrieved_count", 0);
                metrics.put("gold_found", false);
                metrics.put("benchmark_protocol", protocol.name());
                metrics.put("reason", "embedding_model_not_listed_or_daemon_unreachable");
                row.put("metrics", metrics);
                row.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.MODEL_NOT_AVAILABLE.name());
                row.put("top_document_id", null);
                row.put(BenchmarkResultRowKeys.LATENCY_MS, null);
                rows.add(row);
                continue;
            }

            try {
                long t0 = System.nanoTime();
                SearchRequest req =
                        SearchRequest.builder().query(question).topK(topK).similarityThreshold(0.0).build();
                List<Document> docs = vectorStore.similaritySearch(req);
                long latencyMs = (System.nanoTime() - t0) / 1_000_000L;

                double r1 = EmbeddingRetrievalMetrics.recallAt1(docs, expected);
                double rk = EmbeddingRetrievalMetrics.recallAtK(docs, expected);
                double r3 = EmbeddingRetrievalMetrics.recallAtN(docs, expected, 3);
                double r5 = EmbeddingRetrievalMetrics.recallAtN(docs, expected, 5);
                double mrr = EmbeddingRetrievalMetrics.mrr(docs, expected);
                int rank = EmbeddingRetrievalMetrics.firstRelevantRank(docs, expected);
                boolean goldFound = rank > 0;

                Map<String, Object> metrics = baseMetrics(ctx);
                metrics.put("recall_at_1", r1);
                metrics.put("recall_at_3", r3);
                metrics.put("recall_at_5", r5);
                metrics.put("recall_at_k", rk);
                metrics.put("mrr", mrr);
                metrics.put("first_relevant_rank", rank);
                metrics.put("retrieved_count", docs.size());
                metrics.put("gold_found", goldFound);
                metrics.put("benchmark_protocol", protocol.name());

                row.put("top_document_id", docs.isEmpty() ? null : docs.get(0).getId());
                row.put(BenchmarkResultRowKeys.LATENCY_MS, latencyMs);
                row.put("metrics", metrics);
                row.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.EXECUTED.name());

                if (ctx.downstreamRag) {
                    if (!ctx.downstreamLlmCatalogOk) {
                        row.put("generated_answer", "");
                        row.put("llm_evaluation", "");
                        metrics.put("downstream_reason", "fixed_llm_model_not_listed_or_daemon_unreachable");
                        row.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.MODEL_NOT_AVAILABLE.name());
                    } else {
                        row.put(BenchmarkResultRowKeys.LLM_MODEL_ID, ctx.llmSnapshot().model());
                        try {
                            String answer =
                                    modelBaselineLlmRunner.generateAnswerFromRetrievedChunks(
                                            ctx.llmSnapshot(), ctx.prompts(), question, docs);
                            row.put("generated_answer", answer != null ? answer : "");
                            String judge =
                                    evaluationService.judgeQaAnswer(question, expected, answer != null ? answer : "");
                            row.put("llm_evaluation", judge != null ? judge : "");
                        } catch (RuntimeException dex) {
                            row.put("generated_answer", "");
                            row.put("llm_evaluation", "");
                            row.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.FAILED.name());
                            row.put(
                                    "error",
                                    dex.getMessage() != null && !dex.getMessage().isBlank()
                                            ? dex.getMessage()
                                            : dex.getClass().getSimpleName());
                        }
                    }
                }
            } catch (RuntimeException ex) {
                Map<String, Object> metrics = baseMetrics(ctx);
                metrics.put("recall_at_1", 0.0);
                metrics.put("recall_at_3", 0.0);
                metrics.put("recall_at_5", 0.0);
                metrics.put("recall_at_k", 0.0);
                metrics.put("mrr", 0.0);
                metrics.put("first_relevant_rank", 0);
                metrics.put("retrieved_count", 0);
                metrics.put("gold_found", false);
                metrics.put("benchmark_protocol", protocol.name());
                row.put("top_document_id", null);
                row.put(BenchmarkResultRowKeys.LATENCY_MS, null);
                row.put("metrics", metrics);
                row.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.FAILED.name());
                row.put(
                        "error",
                        ex.getMessage() != null && !ex.getMessage().isBlank()
                                ? ex.getMessage()
                                : ex.getClass().getSimpleName());
            }
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> baseMetrics(EmbeddingBenchmarkContext ctx) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("k", topK);
        metrics.put("embedding_downstream_rag", ctx.downstreamRag);
        return metrics;
    }

    private record EmbeddingBenchmarkContext(
            boolean downstreamRag,
            boolean embeddingModelCatalogOk,
            boolean downstreamLlmCatalogOk,
            LlmExperimentalSnapshot llmSnapshot,
            PromptProfileSnapshot prompts,
            String embeddingModelId) {

        static EmbeddingBenchmarkContext disabled() {
            return new EmbeddingBenchmarkContext(false, true, true, null, null, "");
        }
    }
}
