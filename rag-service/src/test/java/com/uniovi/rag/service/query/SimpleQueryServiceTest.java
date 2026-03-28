package com.uniovi.rag.service.query;

import com.uniovi.rag.api.OllamaConnectivityChecker;
import com.uniovi.rag.model.QueryResponse;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.service.expand.QueryExpander;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.testsupport.ChatClientTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.*;

class SimpleQueryServiceTest {

    private QueryExpander expander;
    private QueryAnalyser analyser;
    private ContextRetriever retriever;
    private ChatClient chatClient;
    private OllamaConnectivityChecker ollamaConnectivityChecker;
    private SimpleQueryService service;

    @BeforeEach
    void setUp() {
        expander = mock(QueryExpander.class);
        analyser = mock(QueryAnalyser.class);
        retriever = mock(ContextRetriever.class);
        chatClient = ChatClientTestSupport.mockForUserPromptChain();
        ollamaConnectivityChecker = mock(OllamaConnectivityChecker.class);
        doNothing().when(ollamaConnectivityChecker).prepareForQuery(any());
        service = new SimpleQueryService(expander, analyser, retriever, chatClient, ollamaConnectivityChecker);
    }

    @Test
    void generateResponse_nullOrEmptyQuestion_throws() {
        when(expander.expand(anyString())).thenReturn("q");
        assertThrows(IllegalArgumentException.class, () -> service.generateResponse(null));
        assertThrows(IllegalArgumentException.class, () -> service.generateResponse(""));
        assertThrows(IllegalArgumentException.class, () -> service.generateResponse("   "));
    }

    @Test
    void generateResponse_emptyContext_usesDirectLlmAnswer() {
        when(expander.expand("pregunta")).thenReturn("pregunta");
        when(analyser.analyse("pregunta")).thenReturn(null);
        when(retriever.retrieve("pregunta")).thenReturn(List.of());
        when(retriever.createContext(anyList(), anyString(), any())).thenReturn("");
        ChatClientTestSupport.stubUserPromptReturns(chatClient, "joke answer");

        QueryResponse response = service.generateResponse("pregunta");

        assertNotNull(response);
        assertEquals("joke answer", response.getAnswer());
    }

    @Test
    void generateResponse_withContext_returnsLlmAnswer() {
        when(expander.expand("p")).thenReturn("p");
        when(analyser.analyse("p")).thenReturn(null);
        when(retriever.retrieve("p")).thenReturn(List.of(new Document("doc", java.util.Map.of())));
        when(retriever.createContext(anyList(), anyString(), any())).thenReturn("context");
        ChatClientTestSupport.stubUserPromptReturns(chatClient, "Answer from LLM");

        QueryResponse response = service.generateResponse("p");

        assertNotNull(response);
        assertEquals("Answer from LLM", response.getAnswer());
    }

    @Test
    void generateResponse_withNer_usesRetrieveWithMetadataFilters() {
        JSONObject ner = new JSONObject();
        ner.put("any", "1");
        when(expander.expand("p")).thenReturn("p");
        when(analyser.analyse("p")).thenReturn(ner);
        when(retriever.retrieveWithMetadataFilters(eq("p"), eq(ner))).thenReturn(List.of(new Document("d", java.util.Map.of())));
        when(retriever.createContext(anyList(), anyString(), eq(ner))).thenReturn("ctx");
        ChatClientTestSupport.stubUserPromptReturns(chatClient, "out");

        QueryResponse response = service.generateResponse("p");

        assertEquals("out", response.getAnswer());
        verify(retriever).retrieveWithMetadataFilters("p", ner);
    }

    @Test
    void askQueryToLlama_whenChatThrows_returnsNull() {
        ChatClientTestSupport.stubUserPromptThrows(chatClient, new RuntimeException("down"));
        when(expander.expand("p")).thenReturn("p");
        when(analyser.analyse("p")).thenReturn(null);
        when(retriever.retrieve("p")).thenReturn(List.of(new Document("d", java.util.Map.of())));
        when(retriever.createContext(anyList(), anyString(), any())).thenReturn("context");

        QueryResponse response = service.generateResponse("p");

        assertNull(response.getAnswer());
    }

    @Test
    void countTokens_returnsMinusOne() {
        assertEquals(-1, SimpleQueryService.countTokens("anything"));
    }
}
