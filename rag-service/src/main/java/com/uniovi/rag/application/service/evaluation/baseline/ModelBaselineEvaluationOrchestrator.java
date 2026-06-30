package com.uniovi.rag.application.service.evaluation.baseline;

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
import com.uniovi.rag.application.result.evaluation.EvaluationSummary;
import com.uniovi.rag.application.result.evaluation.LlmJudgeEvaluationBatchResult;
import com.uniovi.rag.application.result.evaluation.LlmJudgeItemResult;
import com.uniovi.rag.application.service.evaluation.EvaluationService;
import com.uniovi.rag.application.service.evaluation.judge.EvaluationJudgeException;
import com.uniovi.rag.application.service.evaluation.judge.EvaluationJudgeExecutionScope;
import com.uniovi.rag.application.service.async.LabJobCancelledException;
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
    private final EvaluationModelAvailabilityGate modelAvailabilityGate;
    private final EvaluationService evaluationService;

    public ModelBaselineEvaluationOrchestrator(
            EvaluationRunRepository evaluationRunRepository,
            ExperimentalSnapshotFactory experimentalSnapshotFactory,
            BaselineRunSnapshotWriter baselineRunSnapshotWriter,
            ModelBaselineLlmRunner modelBaselineLlmRunner,
            EvaluationModelAvailabilityGate modelAvailabilityGate,
            EvaluationService evaluationService) {
        this.evaluationRunRepository = evaluationRunRepository;
        this.experimentalSnapshotFactory = experimentalSnapshotFactory;
        this.baselineRunSnapshotWriter = baselineRunSnapshotWriter;
        this.modelBaselineLlmRunner = modelBaselineLlmRunner;
        this.modelAvailabilityGate = modelAvailabilityGate;
        this.evaluationService = evaluationService;
    }

    /** Runs typed LLM baseline rows with judge scoring for canonical persistence. */
    public LlmJudgeEvaluationBatchResult runLlmJudgeBaseline(
            UUID evaluationRunId,
            TypedBenchmarkDataset.LlmQuestions bundle,
            BiConsumer<Integer, Integer> itemProgress) {
        return runLlmJudgeBaseline(evaluationRunId, bundle, itemProgress, null);
    }

    public LlmJudgeEvaluationBatchResult runLlmJudgeBaseline(
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

        UUID runUserId = run != null && run.getUser() != null ? run.getUser().getId() : null;
        boolean llmAvailable = modelAvailabilityGate.isChatModelAvailable(runUserId, llmSnap.model());
        Map<String, String> corpusById = CorpusDocumentLookup.indexByDocumentId(bundle.corpusDocuments());

        List<LlmJudgeItemResult> rows = new ArrayList<>();
        List<LlmReaderQuestion> questions = bundle.questions();
        int total = questions.size();
        int idx = 0;
        boolean cancelled = false;
        String cancelReason = null;
        UUID judgeUserId = run != null && run.getUser() != null ? run.getUser().getId() : null;
        AutoCloseable judgeScope = EvaluationJudgeExecutionScope.open(judgeUserId, null);
        try {
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

            Map<String, Object> baselineMetrics = new LinkedHashMap<>();
            baselineMetrics.put("benchmark_protocol", protocol.name());
            baselineMetrics.put("llm_model", llmSnap.model());

            if (!llmAvailable) {
                baselineMetrics.put("reason", "model_not_available_for_effective_provider");
                baselineMetrics.put("reasonCode", MODEL_UNAVAILABLE);
                rows.add(
                        LlmJudgeItemResult.builder()
                                .question(q.question())
                                .correctAnswer(q.expectedAnswer() != null ? q.expectedAnswer() : "")
                                .datasetQuestionId(q.id())
                                .itemOutcome(BenchmarkItemOutcome.MODEL_NOT_AVAILABLE)
                                .queryType(q.queryType().map(QueryType::name).orElse(null))
                                .difficulty(q.difficulty().map(DifficultyLevel::name).orElse(null))
                                .evaluationProtocol(protocol.name())
                                .llmModelId(llmSnap.model())
                                .embeddingModelId(embSnap.model())
                                .errorCode(MODEL_UNAVAILABLE)
                                .errorMessage(MODEL_UNAVAILABLE)
                                .baselineMetrics(baselineMetrics)
                                .latencyMs(0L)
                                .build());
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
            LlmJudgeItemResult.Builder rowBuilder =
                    LlmJudgeItemResult.builder()
                            .question(q.question())
                            .correctAnswer(q.expectedAnswer() != null ? q.expectedAnswer() : "")
                            .datasetQuestionId(q.id())
                            .itemOutcome(BenchmarkItemOutcome.EXECUTED)
                            .queryType(q.queryType().map(QueryType::name).orElse(null))
                            .difficulty(q.difficulty().map(DifficultyLevel::name).orElse(null))
                            .evaluationProtocol(protocol.name())
                            .llmModelId(llmSnap.model())
                            .embeddingModelId(embSnap.model())
                            .baselineMetrics(baselineMetrics);
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
                generated = generated != null ? generated : "";
                rowBuilder.generatedAnswer(generated);
                try {
                    if (cancellationCheck != null) {
                        cancellationCheck.run();
                    }
                } catch (LabJobCancelledException ex) {
                    cancelled = true;
                    cancelReason = ex.getMessage();
                }
                String gold = q.expectedAnswer() != null ? q.expectedAnswer() : "";
                String judge = evaluationService.judgeQaAnswer(q.question(), gold, generated);
                rowBuilder.llmEvaluation(judge != null ? judge : "");
                rowBuilder.latencyMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0));
            } catch (EvaluationJudgeException ex) {
                rowBuilder
                        .llmEvaluation("")
                        .itemOutcome(BenchmarkItemOutcome.FAILED)
                        .errorCode(ex.errorCode())
                        .errorMessage(ex.getMessage())
                        .latencyMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0));
            } catch (RuntimeException ex) {
                rowBuilder
                        .generatedAnswer("")
                        .llmEvaluation("")
                        .itemOutcome(BenchmarkItemOutcome.FAILED)
                        .errorCode(ex.getClass().getSimpleName())
                        .errorMessage(
                                ex.getMessage() != null && !ex.getMessage().isBlank()
                                        ? ex.getMessage()
                                        : ex.getClass().getSimpleName())
                        .latencyMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0));
            }
            rows.add(rowBuilder.build());
            if (cancelled) {
                break;
            }
        }
        } finally {
            closeQuietly(judgeScope);
        }

        EvaluationSummary summary = evaluationService.summarizeJudgeResults(rows);
        if (cancelled) {
            summary =
                    summary.withCancellation(
                            true,
                            cancelReason,
                            rows.size(),
                            total);
        }
        return new LlmJudgeEvaluationBatchResult(
                Map.of("baseline_phase5", true, "protocol_driver", "ModelBaselineEvaluationOrchestrator"),
                rows,
                summary);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // scope cleanup must not mask benchmark results
        }
    }
}
