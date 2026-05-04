package com.uniovi.rag.service.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.service.document.DocumentService;
import com.uniovi.rag.service.query.QueryService;
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
            QueryService queryService,
            boolean cleanBeforeLoad) {
        super(featureConfig, implementationProperties, chatClient, documentService, queryService, cleanBeforeLoad);
    }

    /**
     * Legacy Map projection removed from production; benchmarks resolve typed rows via persistence + resolver.
     *
     * @deprecated kept on the interface for transitional compilation; do not call from product code.
     */
    @Deprecated
    @Override
    public Map<String, String> getQuestionsAndAnswers() {
        throw new UnsupportedOperationException(
                "Map-based evaluation dataset was removed. "
                        + "Use POST {product}/lab/benchmarks/{kind}/runs with a typed evaluation_dataset; "
                        + "async jobs must carry evaluation_run_id.");
    }
}
