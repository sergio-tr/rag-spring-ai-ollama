package com.uniovi.rag.service.evaluation.baseline;

import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import com.uniovi.rag.application.service.evaluation.TypedBenchmarkDataset;
import com.uniovi.rag.domain.evaluation.BenchmarkEvaluationProtocol;
import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import com.uniovi.rag.domain.evaluation.snapshot.EmbeddingExperimentalSnapshot;
import com.uniovi.rag.domain.evaluation.snapshot.LlmExperimentalSnapshot;
import com.uniovi.rag.domain.evaluation.snapshot.PromptProfileSnapshot;
import com.uniovi.rag.domain.evaluation.workbook.DifficultyLevel;
import com.uniovi.rag.domain.evaluation.workbook.LlmReaderQuestion;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.service.evaluation.EvaluationService;
import com.uniovi.rag.service.async.LabJobCancelledException;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
/**
 * Phase 5 LLM model baseline: protocol-aware generation (oracle vs full document), snapshot persistence, optional model
 * catalog gate, per-item resilience.
 */
@Service
public class ModelBaselineEvaluationOrchestrator {

    private static final String JSON_KEY_CORRECT_ANSWER = "correct_answer";
    private static final String JSON_KEY_GENERATED_ANSWER = "generated_answer";
    private static final String MODEL_UNAVAILABLE = "MODEL_UNAVAILABLE";

    private final EvaluationRunRepository evaluationRunRepository;
    private final ExperimentalSnapshotFactory experimentalSnapshotFactory;
    private final BaselineRunSnapshotWriter baselineRunSnapshotWriter;
    private final ModelBaselineLlmRunner modelBaselineLlmRunner;
    private final OllamaModelCatalogClient ollamaModelCatalogClient;
    private final EvaluationService evaluationService;

    public ModelBaselineEvaluationOrchestrator(
            EvaluationRunRepository evaluationRunRepository,
            ExperimentalSnapshotFactory experimentalSnapshotFactory,
            BaselineRunSnapshotWriter baselineRunSnapshotWriter,
            ModelBaselineLlmRunner modelBaselineLlmRunner,
            OllamaModelCatalogClient ollamaModelCatalogClient,
            EvaluationService evaluationService) {
        this.evaluationRunRepository = evaluationRunRepository;
        this.experimentalSnapshotFactory = experimentalSnapshotFactory;
        this.baselineRunSnapshotWriter = baselineRunSnapshotWriter;
        this.modelBaselineLlmRunner = modelBaselineLlmRunner;
        this.ollamaModelCatalogClient = ollamaModelCatalogClient;
        this.evaluationService = evaluationService;
    }

    /**
     * Runs typed LLM baseline rows and returns the same payload shape as legacy typed evaluation (for canonical
     * persistence).
     */
    public Map<String, Object> runLlmJudgeBaseline(
            UUID evaluationRunId,
            TypedBenchmarkDataset.LlmQuestions bundle,
            BiConsumer<Integer, Integer> itemProgress) {
        return runLlmJudgeBaseline(evaluationRunId, bundle, itemProgress, null);
    }

    public Map<String, Object> runLlmJudgeBaseline(
            UUID evaluationRunId,
            TypedBenchmarkDataset.LlmQuestions bundle,
            BiConsumer<Integer, Integer> itemProgress,
            Runnable cancellationCheck) {
        EvaluationRunEntity run =
                evaluationRunId != null ? evaluationRunRepository.findById(evaluationRunId).orElse(null) : null;
        LlmExperimentalSnapshot llmSnap = experimentalSnapshotFactory.buildLlmSnapshot(run);
        EmbeddingExperimentalSnapshot embSnap = experimentalSnapshotFactory.buildEmbeddingSnapshot(run);
        PromptProfileSnapshot prompts = PromptProfileSnapshotFactory.baselineLabProfile();
        if (evaluationRunId != null) {
            baselineRunSnapshotWriter.writeSnapshots(evaluationRunId, llmSnap, embSnap, prompts);
        }

        boolean llmAvailable = ollamaModelCatalogClient.isModelAvailable(llmSnap.model());
        Map<String, String> corpusById = CorpusDocumentLookup.indexByDocumentId(bundle.corpusDocuments());

        List<Map<String, Object>> rows = new ArrayList<>();
        List<LlmReaderQuestion> questions = bundle.questions();
        int total = questions.size();
        int idx = 0;
        boolean cancelled = false;
        String cancelReason = null;
        for (LlmReaderQuestion q : questions) {
            try {
                if (cancellationCheck != null) {
                    cancellationCheck.run();
                }
            } catch (LabJobCancelledException ex) {
                cancelled = true;
                cancelReason = ex.getMessage();
                break;
            }
            idx++;
            if (itemProgress != null) {
                itemProgress.accept(idx, total);
            }
            BenchmarkEvaluationProtocol protocol = BenchmarkEvaluationProtocol.fromAnswerMode(q.answerMode());
            if (protocol != BenchmarkEvaluationProtocol.LLM_READER_ORACLE_CONTEXT
                    && protocol != BenchmarkEvaluationProtocol.LLM_FULL_DOCUMENT_CONTEXT) {
                protocol = BenchmarkEvaluationProtocol.LLM_READER_ORACLE_CONTEXT;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("question", q.question());
            row.put(JSON_KEY_CORRECT_ANSWER, q.expectedAnswer() != null ? q.expectedAnswer() : "");
            row.put(BenchmarkResultRowKeys.DATASET_QUESTION_ID, q.id());
            row.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.EXECUTED.name());
            row.put("query_type", q.queryType().map(QueryType::name).orElse(null));
            row.put(BenchmarkResultRowKeys.DIFFICULTY, q.difficulty().map(DifficultyLevel::name).orElse(null));

            Map<String, Object> baselineMetrics = new LinkedHashMap<>();
            baselineMetrics.put("benchmark_protocol", protocol.name());
            baselineMetrics.put("llm_model", llmSnap.model());
            row.put(BenchmarkResultRowKeys.LLM_MODEL_ID, llmSnap.model());
            row.put(BenchmarkResultRowKeys.EMBEDDING_MODEL_ID, embSnap.model());
            row.put("benchmark_protocol", protocol.name());

            if (!llmAvailable) {
                row.put(JSON_KEY_GENERATED_ANSWER, "");
                row.put("llm_evaluation", "");
                row.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.MODEL_NOT_AVAILABLE.name());
                row.put(BenchmarkResultRowKeys.ERROR_CODE, MODEL_UNAVAILABLE);
                row.put(BenchmarkResultRowKeys.REASON, MODEL_UNAVAILABLE);
                baselineMetrics.put("reason", "ollama_model_not_listed_or_daemon_unreachable");
                baselineMetrics.put("reasonCode", MODEL_UNAVAILABLE);
                row.put("baseline_metrics", baselineMetrics);
                row.put(BenchmarkResultRowKeys.LATENCY_MS, 0L);
                rows.add(row);
                continue;
            }

            String oracleContext = q.contextText() != null ? q.contextText() : "";
            String fullDoc =
                    CorpusDocumentLookup.findDocumentText(corpusById, q.sourceDocumentId());
            if ((fullDoc == null || fullDoc.isBlank()) && protocol == BenchmarkEvaluationProtocol.LLM_FULL_DOCUMENT_CONTEXT) {
                fullDoc = oracleContext;
                baselineMetrics.put(
                        "full_document_fallback",
                        "Used context_text because corpus_documents had no match for source_document_id.");
            }

            DocumentContextTruncator.Result truncation =
                    DocumentContextTruncator.truncate(fullDoc, llmSnap.numCtx(), 96_000);
            baselineMetrics.putAll(ModelBaselineLlmRunner.truncationMetrics(truncation));

            long t0 = System.nanoTime();
            try {
                String generated =
                        modelBaselineLlmRunner.generateAnswer(
                                protocol,
                                llmSnap,
                                prompts,
                                q.question(),
                                oracleContext,
                                fullDoc,
                                truncation);
                row.put(JSON_KEY_GENERATED_ANSWER, generated != null ? generated : "");
                try {
                    if (cancellationCheck != null) {
                        cancellationCheck.run();
                    }
                } catch (LabJobCancelledException ex) {
                    cancelled = true;
                    cancelReason = ex.getMessage();
                }
                String gold = row.get(JSON_KEY_CORRECT_ANSWER) instanceof String s ? s : "";
                String judge = evaluationService.judgeQaAnswer(q.question(), gold, generated);
                row.put("llm_evaluation", judge != null ? judge : "");
                row.put(BenchmarkResultRowKeys.LATENCY_MS, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0));
            } catch (RuntimeException ex) {
                row.put(JSON_KEY_GENERATED_ANSWER, "");
                row.put("llm_evaluation", "");
                row.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.FAILED.name());
                row.put(BenchmarkResultRowKeys.ERROR_CODE, ex.getClass().getSimpleName());
                row.put(BenchmarkResultRowKeys.LATENCY_MS, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0));
                row.put(
                        "error",
                        ex.getMessage() != null && !ex.getMessage().isBlank()
                                ? ex.getMessage()
                                : ex.getClass().getSimpleName());
            }
            row.put("baseline_metrics", baselineMetrics);
            rows.add(row);
            if (cancelled) {
                break;
            }
        }

        Map<String, Object> out = new HashMap<>();
        out.put("configuration", Map.of("baseline_phase5", true, "protocol_driver", "ModelBaselineEvaluationOrchestrator"));
        out.put("results", rows);
        Map<String, Object> summary = new LinkedHashMap<>(evaluationService.summarizeJudgeResults(rows));
        if (cancelled) {
            summary.put("cancelled", true);
            if (cancelReason != null && !cancelReason.isBlank()) {
                summary.put("cancel_reason", cancelReason);
            }
            summary.put("completed_items", rows.size());
            summary.put("total_items", total);
        }
        out.put("evaluation_summary", summary);
        return out;
    }
}
