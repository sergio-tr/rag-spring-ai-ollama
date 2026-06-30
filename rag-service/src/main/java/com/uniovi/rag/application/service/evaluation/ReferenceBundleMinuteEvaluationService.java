package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.application.service.knowledge.document.DocumentService;
import com.uniovi.rag.application.service.runtime.execution.QueryExecutionService;
import com.uniovi.rag.application.service.evaluation.judge.EvaluationJudgeLlmExecutor;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;

/**
 * Product {@link EvaluationService} implementation for Lab benchmarks: supports typed benchmark runners and
 * judge helpers only — no classpath Map Q/A dataset.
 */
public final class ReferenceBundleMinuteEvaluationService extends AbstractMinuteEvaluationService {

    public ReferenceBundleMinuteEvaluationService(
            RagFeatureConfiguration featureConfig,
            RagImplementationProperties implementationProperties,
            ChatClient chatClient,
            DocumentService documentService,
            QueryExecutionService queryService,
            boolean cleanBeforeLoad,
            EvaluationJudgeLlmExecutor evaluationJudgeLlmExecutor) {
        super(
                featureConfig,
                implementationProperties,
                chatClient,
                documentService,
                queryService,
                cleanBeforeLoad,
                evaluationJudgeLlmExecutor);
    }

}
