package com.uniovi.rag.configuration;

import com.uniovi.rag.service.classifier.ClassifierServiceClient;
import com.uniovi.rag.service.classifier.QueryClassifier;
import com.uniovi.rag.service.query.ResponseValidator;
import com.uniovi.rag.service.reasoning.ReasoningStrategy;
import com.uniovi.rag.service.reasoning.SimpleReasoningStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link RagQueryConfiguration}. Bean logic is tested in isolation where possible.
 */
class RagQueryConfigurationTest {

    @Test
    void responseValidatorBean_returnsLLMValidator() {
        RagQueryConfiguration config = new RagQueryConfiguration();
        ResponseValidator validator = config.responseValidator(null);
        assertNotNull(validator);
    }

    @Test
    void reasoningStrategyBean_defaultStrategy_returnsSimpleReasoningStrategy() {
        RagQueryConfiguration config = new RagQueryConfiguration();
        RagReasoningProperties props = new RagReasoningProperties();
        props.setStrategy(null);
        ChatClient chatClient = mock(ChatClient.class);
        ReasoningStrategy strategy = config.reasoningStrategy(props, chatClient, null);
        assertNotNull(strategy);
        assertTrue(strategy instanceof SimpleReasoningStrategy);
    }

    @Test
    void queryClassifierBean_returnsClassifierServiceClient() {
        RagQueryConfiguration config = new RagQueryConfiguration();
        QueryClassifier classifier = config.queryClassifier("http://localhost:8000", "default", 5000, null);
        assertNotNull(classifier);
        assertTrue(classifier instanceof ClassifierServiceClient);
    }

    @Test
    void queryDateExtractorBean_returnsInstance() {
        RagQueryConfiguration config = new RagQueryConfiguration();
        assertNotNull(config.queryDateExtractor());
    }
}
