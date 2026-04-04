package com.uniovi.rag.service.query;

import com.uniovi.rag.interfaces.rest.support.OllamaConnectivityChecker;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagToolsConfiguration;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.application.model.QueryResponse;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.infrastructure.classifier.QueryClassifier;
import com.uniovi.rag.service.expand.QueryExpander;
import com.uniovi.rag.service.retriever.AbstractContextRetriever;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.*;

class SimpleProcessQueryServiceTest {

    private RagFeatureConfiguration featureConfig;
    private RagToolsConfiguration toolsConfig;
    private QueryExpander expander;
    private QueryAnalyser analyser;
    private QueryClassifier classifier;
    private ContextRetriever retriever;
    private ChatClient chatClient;
    private OllamaConnectivityChecker ollamaConnectivityChecker;
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
        ollamaConnectivityChecker = mock(OllamaConnectivityChecker.class);
        doNothing().when(ollamaConnectivityChecker).prepareForQuery(any());
        service = new SimpleProcessQueryService(featureConfig, toolsConfig, expander, analyser, classifier, retriever, chatClient, ollamaConnectivityChecker);
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

    @Test
    void generateResponse_expansionEnabled_callsExpander() {
        featureConfig.setExpansionEnabled(true);
        when(expander.expand("orig")).thenReturn("expanded");
        when(retriever.retrieve("expanded")).thenReturn(List.of(new Document("c", java.util.Map.of())));
        when(retriever.createContext(anyList(), anyString(), any())).thenReturn("ctx");
        ChatClientTestSupport.stubUserPromptReturns(chatClient, "out");

        QueryResponse response = service.generateResponse("orig");

        assertNotNull(response);
        verify(expander).expand("orig");
        verify(retriever).retrieve("expanded");
    }

    @Test
    void generateResponse_toolPath_returnsToolResult() {
        featureConfig.setToolsEnabled(true);
        Tool tool = mock(Tool.class);
        when(toolsConfig.getTool(QueryType.COUNT_DOCUMENTS)).thenReturn(tool);
        when(classifier.classify(anyString())).thenReturn(QueryType.COUNT_DOCUMENTS);
        when(tool.execute(any())).thenReturn(new ToolResult("tool answer", "CountDocumentsTool"));

        QueryResponse response = service.generateResponse("how many");

        assertNotNull(response);
        assertTrue(response.isUsedTool());
        assertEquals("tool answer", response.getAnswer());
    }

    @Test
    void generateResponse_toolThrows_fallsBackToLlm() {
        featureConfig.setToolsEnabled(true);
        Tool tool = mock(Tool.class);
        when(toolsConfig.getTool(QueryType.COUNT_DOCUMENTS)).thenReturn(tool);
        when(classifier.classify(anyString())).thenReturn(QueryType.COUNT_DOCUMENTS);
        when(tool.execute(any())).thenThrow(new RuntimeException("tool fail"));
        when(retriever.retrieve(anyString())).thenReturn(List.of(new Document("c", java.util.Map.of())));
        when(retriever.createContext(anyList(), anyString(), any())).thenReturn("ctx");
        ChatClientTestSupport.stubUserPromptReturns(chatClient, "after tool");

        QueryResponse response = service.generateResponse("q");

        assertEquals("after tool", response.getAnswer());
    }

    @Test
    void generateResponse_toolReturnsNullResult_usesLlm() {
        featureConfig.setToolsEnabled(true);
        Tool tool = mock(Tool.class);
        when(toolsConfig.getTool(QueryType.COUNT_DOCUMENTS)).thenReturn(tool);
        when(classifier.classify(anyString())).thenReturn(QueryType.COUNT_DOCUMENTS);
        when(tool.execute(any())).thenReturn(new ToolResult(null, "x"));
        when(retriever.retrieve(anyString())).thenReturn(List.of(new Document("c", java.util.Map.of())));
        when(retriever.createContext(anyList(), anyString(), any())).thenReturn("ctx");
        ChatClientTestSupport.stubUserPromptReturns(chatClient, "llm");

        QueryResponse response = service.generateResponse("q");

        assertEquals("llm", response.getAnswer());
    }

    @Test
    void generateResponse_useRetrievalFalse_skipsRetriever() {
        featureConfig.setUseRetrieval(false);
        ChatClientTestSupport.stubUserPromptReturns(chatClient, "direct");

        QueryResponse response = service.generateResponse("plain");

        assertEquals("direct", response.getAnswer());
        verify(retriever, never()).retrieve(anyString());
    }

    @Test
    void generateResponse_useRetrievalFalse_llmThrows_usesNoContextFallback() {
        featureConfig.setUseRetrieval(false);
        ChatClientTestSupport.stubUserPromptThrows(chatClient, new RuntimeException("down"));

        QueryResponse response = service.generateResponse("plain");

        assertNotNull(response.getAnswer());
        assertTrue(response.getAnswer().contains("I could not generate a response"));
    }

    @Test
    void generateResponse_abstractRetrieverWithNer_usesMetadataRetrieval() {
        featureConfig.setNerEnabled(true);
        AbstractContextRetriever ar = mock(AbstractContextRetriever.class);
        JSONObject ner = new JSONObject("{\"k\":\"v\"}");
        when(analyser.analyse(anyString())).thenReturn(ner);
        when(ar.retrieveWithMetadataFilters(anyString(), any())).thenReturn(List.of(new Document("c", java.util.Map.of())));
        when(ar.createContext(anyList(), anyString(), any())).thenReturn("ctx");
        ChatClientTestSupport.stubUserPromptReturns(chatClient, "meta");
        SimpleProcessQueryService svc = new SimpleProcessQueryService(
                featureConfig, toolsConfig, expander, analyser, classifier, ar, chatClient, ollamaConnectivityChecker);

        QueryResponse response = svc.generateResponse("q");

        assertEquals("meta", response.getAnswer());
        verify(ar).retrieveWithMetadataFilters(anyString(), eq(ner));
    }

    @Test
    void generateResponse_emptyContext_noContextFallbackThrows_usesStaticFallback() {
        when(retriever.retrieve(anyString())).thenReturn(List.of());
        when(retriever.createContext(anyList(), anyString(), any())).thenReturn("");
        ChatClientTestSupport.stubUserPromptThrows(chatClient, new RuntimeException("unavailable"));

        QueryResponse response = service.generateResponse("q");

        assertEquals("I could not generate a response. Please try again.",
                response.getAnswer());
    }
}
