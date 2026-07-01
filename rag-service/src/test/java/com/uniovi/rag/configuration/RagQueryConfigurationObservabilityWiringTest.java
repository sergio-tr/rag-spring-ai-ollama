package com.uniovi.rag.configuration;

import com.uniovi.rag.application.config.ConfigurablePromptResolver;
import com.uniovi.rag.infrastructure.observability.ObservabilitySupport;
import com.uniovi.rag.infrastructure.observability.TracedDateExistenceGuard;
import com.uniovi.rag.infrastructure.observability.TracedQueryAnalyser;
import com.uniovi.rag.infrastructure.observability.TracedQueryClassifier;
import com.uniovi.rag.infrastructure.observability.TracedQueryExpander;
import com.uniovi.rag.infrastructure.observability.TracedReasoningStrategy;
import com.uniovi.rag.infrastructure.observability.TracedResponseRanker;
import com.uniovi.rag.infrastructure.observability.TracedResponseValidator;
import com.uniovi.rag.application.service.runtime.query.guard.QueryDateExtractor;
import com.uniovi.rag.application.service.runtime.query.analyser.QueryAnalyser;
import com.uniovi.rag.application.service.llm.ProviderAwareSecondaryLlmExecutor;
import com.uniovi.rag.infrastructure.classifier.QueryClassifier;
import com.uniovi.rag.application.service.runtime.query.expand.QueryExpander;
import com.uniovi.rag.application.service.runtime.retrieval.ContextRetriever;
import com.uniovi.rag.application.service.runtime.validation.ResponseValidator;
import com.uniovi.rag.application.service.runtime.ranking.ResponseRanker;
import com.uniovi.rag.application.service.runtime.reasoning.ReasoningStrategy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.client.RestTemplate;
import com.uniovi.rag.testsupport.ClassifierClientTestSupport;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

class RagQueryConfigurationObservabilityWiringTest {

    @Test
    void whenObservabilityPresent_returnsTracedBeans() {
        RagQueryConfiguration config = new RagQueryConfiguration();
        ObservabilitySupport observability = new ObservabilitySupport(new SimpleTracer(), new SimpleMeterRegistry());

        ResponseValidator responseValidator = config.responseValidator(observability);
        assertInstanceOf(TracedResponseValidator.class, responseValidator);

        RagReasoningProperties reasoningProps = new RagReasoningProperties();
        reasoningProps.setStrategy(null); // Default: SIMPLE
        ReasoningStrategy reasoningStrategy = config.reasoningStrategy(reasoningProps, mock(ProviderAwareSecondaryLlmExecutor.class), observability);
        assertInstanceOf(TracedReasoningStrategy.class, reasoningStrategy);

        RagRankerProperties rankerProps = new RagRankerProperties();
        rankerProps.setStrategy("FAITHFULNESS");
        ResponseRanker responseRanker = config.responseRanker(rankerProps, mock(ProviderAwareSecondaryLlmExecutor.class), mock(ConfigurablePromptResolver.class), observability);
        assertInstanceOf(TracedResponseRanker.class, responseRanker);

        QueryExpander queryExpander = config.queryExpander(
                mock(ProviderAwareSecondaryLlmExecutor.class),
                mock(ConfigurablePromptResolver.class),
                "COT",
                1,
                350,
                512,
                500,
                200,
                observability
        );
        assertInstanceOf(TracedQueryExpander.class, queryExpander);

        QueryClassifier classifier =
                config.queryClassifier(
                        ClassifierClientTestSupport.defaultBaseUrl(),
                        "default",
                        5000,
                        observability,
                        new SimpleMeterRegistry(),
                        new RestTemplate());
        assertInstanceOf(TracedQueryClassifier.class, classifier);

        RagImplementationProperties implProps = new RagImplementationProperties();
        implProps.setAnalyserImpl("no-op");
        QueryAnalyser analyser = config.queryAnalyser(mock(ProviderAwareSecondaryLlmExecutor.class), implProps, observability);
        assertInstanceOf(TracedQueryAnalyser.class, analyser);

        var retriever = mock(ContextRetriever.class);
        QueryDateExtractor queryDateExtractor = new QueryDateExtractor();
        var dateExistenceGuard = config.dateExistenceGuard(retriever, queryDateExtractor, observability);
        assertInstanceOf(TracedDateExistenceGuard.class, dateExistenceGuard);
    }
}

