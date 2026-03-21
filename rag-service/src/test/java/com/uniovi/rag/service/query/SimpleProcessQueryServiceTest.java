package com.uniovi.rag.service.query;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.model.QueryType;
import com.uniovi.rag.model.QueryResponse;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.service.classifier.QueryClassifier;
import com.uniovi.rag.service.expand.QueryExpander;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.testsupport.ChatClientTestSupport;
import com.uniovi.rag.tool.Tool;
import com.uniovi.rag.tool.ToolResult;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SimpleProcessQueryServiceTest {

    private RagFeatureConfiguration featureConfig;
    private RagToolsConfiguration toolsConfig;
    private QueryExpander expander;
    private QueryAnalyser analyser;
    private QueryClassifier classifier;
    private ContextRetriever retriever;
    private ChatClient chatClient;
    private SimpleProcessQueryService service;

    @BeforeEach
    void setUp() {
        featureConfig = new RagFeatureConfiguration();
        featureConfig.setToolsEnabled(false);
        featureConfig.setExpansionEnabled(false);
        featureConfig.setNerEnabled(false);
        featureConfig.setUseRetrieval(true);
        toolsConfig = mock(RagToolsConfiguration.class);
        expander = mock(QueryExpander.class);
        analyser = mock(QueryAnalyser.class);
        classifier = mock(QueryClassifier.class);
        retriever = mock(ContextRetriever.class);
        chatClient = ChatClientTestSupport.mockForUserPromptChain();
        service = new SimpleProcessQueryService(featureConfig, toolsConfig, expander, analyser, classifier, retriever, chatClient);
    }

    @Test
    void generateResponse_toolsDisabled_usesLlmPath() {
        when(expander.expand(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(retriever.retrieve(anyString())).thenReturn(List.of(new Document("ctx", java.util.Map.of())));
        when(retriever.createContext(anyList(), anyString(), any())).thenReturn("context");
        ChatClientTestSupport.stubUserPromptReturns(chatClient, "LLM answer");

        QueryResponse response = service.generateResponse("question");

        assertNotNull(response);
        assertTrue(response.getAnswer().contains("LLM answer"));
    }

    @Test
    void generateResponse_toolsEnabled_classifierReturnsNull_usesLlmPath() {
        featureConfig.setToolsEnabled(true);
        when(expander.expand(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(classifier.classify(anyString())).thenReturn(null);
        when(retriever.retrieve(anyString())).thenReturn(List.of());
        when(retriever.createContext(anyList(), anyString(), any())).thenReturn("");
        ChatClientTestSupport.stubUserPromptReturns(chatClient, "No context answer");

        QueryResponse response = service.generateResponse("q");

        assertNotNull(response);
    }
}
