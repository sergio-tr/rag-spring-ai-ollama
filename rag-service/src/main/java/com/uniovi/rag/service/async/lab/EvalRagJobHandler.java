package com.uniovi.rag.service.async.lab;

import com.uniovi.rag.application.service.evaluation.BenchmarkDatasetResolutionException;
import com.uniovi.rag.application.service.evaluation.ExperimentalDatasetResolver;
import com.uniovi.rag.application.service.evaluation.TypedBenchmarkDataset;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import com.uniovi.rag.service.evaluation.EvaluationCanonicalPersistenceService;
import com.uniovi.rag.service.evaluation.preset.TypedRagPresetBenchmarkOrchestrator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
class EvalRagJobHandler implements LabJobHandler {

    private final RagFeatureConfiguration featureConfiguration;
    private final RagImplementationProperties implementationProperties;
    private final EvaluationCanonicalPersistenceService canonicalPersistence;
    private final ExperimentalDatasetResolver experimentalDatasetResolver;
    private final TypedRagPresetBenchmarkOrchestrator typedRagPresetBenchmarkOrchestrator;

    EvalRagJobHandler(
            RagFeatureConfiguration featureConfiguration,
            RagImplementationProperties implementationProperties,
            EvaluationCanonicalPersistenceService canonicalPersistence,
            ExperimentalDatasetResolver experimentalDatasetResolver,
            TypedRagPresetBenchmarkOrchestrator typedRagPresetBenchmarkOrchestrator) {
        this.featureConfiguration = featureConfiguration;
        this.implementationProperties = implementationProperties;
        this.canonicalPersistence = canonicalPersistence;
        this.experimentalDatasetResolver = experimentalDatasetResolver;
        this.typedRagPresetBenchmarkOrchestrator = typedRagPresetBenchmarkOrchestrator;
    }

    @Override
    public AsyncTaskType taskType() {
        return AsyncTaskType.EVAL_RAG;
    }

    @Override
    public void run(AsyncTaskEntity task, AsyncTaskMutationService mutation) {
        UUID taskId = task.getId();
        UUID evaluationRunId = LabJobPayloads.evaluationRunId(task.getRequestPayload());
        LabEvalConcurrency.SERIAL_EVAL.lock();
        try {
            if (evaluationRunId == null) {
                throw new IllegalStateException(
                        "EVAL_RAG jobs require evaluation_run_id on the async payload; "
                                + "enqueue via POST /lab/benchmarks/RAG_PRESET_END_TO_END/runs with a typed evaluation_dataset.");
            }
            mutation.appendProgressLine(taskId, "Resolving typed dataset for RAG_PRESET_END_TO_END…");
            TypedBenchmarkDataset typed = experimentalDatasetResolver.resolve(evaluationRunId);
            if (!(typed instanceof TypedBenchmarkDataset.RagPresetQuestions rag)) {
                throw new IllegalStateException("Resolver returned unexpected payload for RAG_PRESET_END_TO_END");
            }
            mutation.appendProgressLine(
                    taskId,
                    "Parsed dataset RAG_PRESET_END_TO_END: " + rag.questions().size() + " questions");
            Map<String, Object> res =
                    typedRagPresetBenchmarkOrchestrator.runPresetBenchmark(
                            evaluationRunId,
                            rag,
                            featureConfiguration,
                            implementationProperties,
                            (i, n) ->
                                    mutation.appendProgressLine(taskId, "Running item " + i + "/" + n));
            canonicalPersistence.persistLlmJudgeFromEvaluationMap(
                    evaluationRunId, res, BenchmarkKind.RAG_PRESET_END_TO_END);
            mutation.markSucceeded(taskId, res);
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
}
