package com.uniovi.rag.service.async.lab;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import com.uniovi.rag.service.evaluation.EvaluationCanonicalPersistenceService;
import com.uniovi.rag.service.evaluation.EvaluationService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
class EvalLlmJobHandler implements LabJobHandler {

    private final EvaluationService evaluationService;
    private final RagFeatureConfiguration featureConfiguration;
    private final RagImplementationProperties implementationProperties;
    private final EvaluationCanonicalPersistenceService canonicalPersistence;

    EvalLlmJobHandler(
            EvaluationService evaluationService,
            RagFeatureConfiguration featureConfiguration,
            RagImplementationProperties implementationProperties,
            EvaluationCanonicalPersistenceService canonicalPersistence) {
        this.evaluationService = evaluationService;
        this.featureConfiguration = featureConfiguration;
        this.implementationProperties = implementationProperties;
        this.canonicalPersistence = canonicalPersistence;
    }

    @Override
    public AsyncTaskType taskType() {
        return AsyncTaskType.EVAL_LLM;
    }

    @Override
    public void run(AsyncTaskEntity task, AsyncTaskMutationService mutation) {
        UUID taskId = task.getId();
        UUID evaluationRunId = LabJobPayloads.evaluationRunId(task.getRequestPayload());
        LabEvalConcurrency.SERIAL_EVAL.lock();
        try {
            mutation.appendProgressLine(taskId, "Starting LLM evaluation (no retrieval)…");
            RagFeatureConfiguration cfg = copyFeatureFlags(featureConfiguration);
            cfg.setUseRetrieval(false);
            Map<String, Object> res =
                    evaluationService.evaluateWithConfiguration(cfg, implementationProperties);
            if (evaluationRunId != null) {
                canonicalPersistence.persistLlmJudgeFromEvaluationMap(
                        evaluationRunId, res, BenchmarkKind.LLM_JUDGE_QA);
            }
            mutation.markSucceeded(taskId, res);
        } catch (RuntimeException e) {
            if (evaluationRunId != null) {
                canonicalPersistence.markRunFailed(evaluationRunId, e.getMessage());
            }
            throw e;
        } finally {
            LabEvalConcurrency.SERIAL_EVAL.unlock();
        }
    }

    private static RagFeatureConfiguration copyFeatureFlags(RagFeatureConfiguration src) {
        RagFeatureConfiguration c = new RagFeatureConfiguration();
        c.setExpansionEnabled(src.isExpansionEnabled());
        c.setNerEnabled(src.isNerEnabled());
        c.setToolsEnabled(src.isToolsEnabled());
        c.setMetadataEnabled(src.isMetadataEnabled());
        c.setReasoningEnabled(src.isReasoningEnabled());
        c.setRankerEnabled(src.isRankerEnabled());
        c.setPostRetrievalEnabled(src.isPostRetrievalEnabled());
        c.setFunctionCallingEnabled(src.isFunctionCallingEnabled());
        c.setUseRetrieval(src.isUseRetrieval());
        c.setUseAdvisor(src.isUseAdvisor());
        return c;
    }
}
