package com.uniovi.rag.service.query;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagRuntimeProperties;
import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.service.analyser.NERQueryEnricher;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.infrastructure.classifier.QueryClassifier;
import com.uniovi.rag.service.expand.QueryExpander;
import com.uniovi.rag.service.guard.DateExistenceGuard;
import com.uniovi.rag.service.postretrieval.PostRetrievalProcessor;
import com.uniovi.rag.service.query.pipeline.AnswerGenerationKernel;
import com.uniovi.rag.service.query.pipeline.ChatRequestSpecFactory;
import com.uniovi.rag.service.query.pipeline.QueryInputPreparer;
import com.uniovi.rag.service.query.pipeline.ResponseSynthesisPipeline;
import com.uniovi.rag.service.query.pipeline.ToolRoutingService;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.service.retriever.NaiveCorpusContextService;
import com.uniovi.rag.tool.MeetingMinutesToolsAdapter;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.lang.Nullable;

/**
 * Builds {@link QueryRuntimeComponents} for product and evaluation paths (single shared runtime).
 */
public final class QueryRuntimeComponentsFactory {

    private QueryRuntimeComponentsFactory() {
    }

    /**
     * @param questionAnswerAdvisor may be {@code null} for evaluation runs that force manual retrieval only
     */
    public static QueryRuntimeComponents create(
            RagFeatureConfiguration featureConfig,
            RagToolsConfiguration toolsConfig,
            QueryExpander expander,
            QueryAnalyser analyser,
            NERQueryEnricher nerQueryEnricher,
            QueryClassifier classifier,
            ContextRetriever retriever,
            DateExistenceGuard dateExistenceGuard,
            MeetingMinutesToolsAdapter meetingMinutesToolsAdapter,
            PostRetrievalProcessor postRetrievalProcessor,
            ResponseValidator responseValidator,
            @Nullable QuestionAnswerAdvisor questionAnswerAdvisor,
            ChatRequestSpecFactory chatRequestSpecFactory,
            @Nullable NaiveCorpusContextService naiveCorpusContextService,
            @Nullable RagRuntimeProperties runtimeProperties) {
        QueryInputPreparer preparer = new QueryInputPreparer(featureConfig, expander, analyser, classifier);
        ToolRoutingService toolRouting = new ToolRoutingService(
                featureConfig, toolsConfig, meetingMinutesToolsAdapter, responseValidator, chatRequestSpecFactory);
        boolean legacyAdvisor = runtimeProperties != null && runtimeProperties.isLegacyAdvisorWithPostRetrieval();
        AnswerGenerationKernel kernel = new AnswerGenerationKernel(
                featureConfig,
                nerQueryEnricher,
                retriever,
                postRetrievalProcessor,
                responseValidator,
                questionAnswerAdvisor,
                chatRequestSpecFactory,
                naiveCorpusContextService,
                legacyAdvisor);
        ResponseSynthesisPipeline pipeline = new ResponseSynthesisPipeline(
                featureConfig, dateExistenceGuard, toolRouting, kernel);
        return new QueryRuntimeComponents(preparer, pipeline);
    }
}
