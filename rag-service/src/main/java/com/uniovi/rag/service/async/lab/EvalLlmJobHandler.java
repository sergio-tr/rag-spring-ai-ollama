package com.uniovi.rag.service.async.lab;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import com.uniovi.rag.service.evaluation.EvaluationService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
class EvalLlmJobHandler implements LabJobHandler {

    private final EvaluationService evaluationService;
    private final RagFeatureConfiguration featureConfiguration;
    private final RagImplementationProperties implementationProperties;

    EvalLlmJobHandler(
            EvaluationService evaluationService,
            RagFeatureConfiguration featureConfiguration,
            RagImplementationProperties implementationProperties) {
        this.evaluationService = evaluationService;
        this.featureConfiguration = featureConfiguration;
        this.implementationProperties = implementationProperties;
    }

    @Override
    public AsyncTaskType taskType() {
        return AsyncTaskType.EVAL_LLM;
    }

    @Override
    public void run(AsyncTaskEntity task, AsyncTaskMutationService mutation) {
        UUID taskId = task.getId();
        LabEvalConcurrency.SERIAL_EVAL.lock();
        try {
            mutation.appendProgressLine(taskId, "Starting LLM evaluation (no retrieval)…");
            RagFeatureConfiguration cfg = copyFeatureFlags(featureConfiguration);
            cfg.setUseRetrieval(false);
            Map<String, Object> res =
                    evaluationService.evaluateWithConfiguration(cfg, implementationProperties);
            mutation.markSucceeded(taskId, res);
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
