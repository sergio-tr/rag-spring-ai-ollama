package com.uniovi.rag.service.query;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagRuntimeProperties;
import com.uniovi.rag.infrastructure.classifier.QueryClassifier;
import com.uniovi.rag.service.analyser.NERQueryEnricher;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.service.expand.QueryExpander;
import com.uniovi.rag.service.guard.DateExistenceGuard;
import com.uniovi.rag.service.postretrieval.PostRetrievalProcessor;
import com.uniovi.rag.service.query.pipeline.ChatRequestSpecFactory;
import com.uniovi.rag.service.retriever.ContextRetriever;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class QueryRuntimeComponentsFactoryTest {

    @Test
    void create_whenRuntimePropertiesNull_setsLegacyAdvisorFalse() {
        QueryRuntimeComponents c =
                QueryRuntimeComponentsFactory.create(
                        new RagFeatureConfiguration(),
                        mock(QueryExpander.class),
                        mock(QueryAnalyser.class),
                        mock(NERQueryEnricher.class),
                        mock(QueryClassifier.class),
                        mock(ContextRetriever.class),
                        mock(DateExistenceGuard.class),
                        mock(PostRetrievalProcessor.class),
                        mock(ResponseValidator.class),
                        mock(QuestionAnswerAdvisor.class),
                        mock(ChatRequestSpecFactory.class),
                        null,
                        null);

        assertThat(c.queryInputPreparer()).isNotNull();
        assertThat(c.responseSynthesisPipeline()).isNotNull();
    }

    @Test
    void create_whenLegacyAdvisorEnabled_setsLegacyAdvisorTrue() {
        RagRuntimeProperties props = new RagRuntimeProperties();
        props.setLegacyAdvisorWithPostRetrieval(true);

        QueryRuntimeComponents c =
                QueryRuntimeComponentsFactory.create(
                        new RagFeatureConfiguration(),
                        mock(QueryExpander.class),
                        mock(QueryAnalyser.class),
                        mock(NERQueryEnricher.class),
                        mock(QueryClassifier.class),
                        mock(ContextRetriever.class),
                        mock(DateExistenceGuard.class),
                        mock(PostRetrievalProcessor.class),
                        mock(ResponseValidator.class),
                        mock(QuestionAnswerAdvisor.class),
                        mock(ChatRequestSpecFactory.class),
                        null,
                        props);

        assertThat(c.queryInputPreparer()).isNotNull();
        assertThat(c.responseSynthesisPipeline()).isNotNull();
    }
}

