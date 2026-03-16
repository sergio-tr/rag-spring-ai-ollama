package com.uniovi.rag.service.query;

import com.uniovi.rag.model.QueryResponse;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import com.uniovi.rag.service.expand.QueryExpander;
import com.uniovi.rag.service.retriever.ContextRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SimpleQueryServiceTest {

    private QueryExpander expander;
    private QueryAnalyser analyser;
    private ContextRetriever retriever;
    private ChatClient chatClient;
    private SimpleQueryService service;

    @BeforeEach
    void setUp() {
        expander = mock(QueryExpander.class);
        analyser = mock(QueryAnalyser.class);
        retriever = mock(ContextRetriever.class);
        chatClient = mock(ChatClient.class);
        service = new SimpleQueryService(expander, analyser, retriever, chatClient);
    }

    @Test
    void generateResponse_nullOrEmptyQuestion_throws() {
        when(expander.expand(anyString())).thenReturn("q");
        assertThrows(IllegalArgumentException.class, () -> service.generateResponse(null));
        assertThrows(IllegalArgumentException.class, () -> service.generateResponse(""));
        assertThrows(IllegalArgumentException.class, () -> service.generateResponse("   "));
    }

    @Test
    void generateResponse_emptyContext_returnsNoInfoMessage() {
        when(expander.expand("pregunta")).thenReturn("pregunta");
        when(analyser.analyse("pregunta")).thenReturn(null);
        when(retriever.retrieve("pregunta")).thenReturn(List.of());
        when(retriever.createContext(anyList(), anyString(), any())).thenReturn("");

        QueryResponse response = service.generateResponse("pregunta");

        assertNotNull(response);
        assertTrue(response.getAnswer().contains("No se encontró") || response.getAnswer().contains("información"));
    }

    @Test
    void generateResponse_withContext_returnsLlmAnswer() {
        when(expander.expand("p")).thenReturn("p");
        when(analyser.analyse("p")).thenReturn(null);
        when(retriever.retrieve("p")).thenReturn(List.of(new Document("doc", java.util.Map.of())));
        when(retriever.createContext(anyList(), anyString(), any())).thenReturn("context");
        var callSpec = mock(org.springframework.ai.chat.client.CallResponseSpec.class);
        var promptSpec = mock(org.springframework.ai.chat.client.PromptSpec.class);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(callSpec);
        when(callSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Answer from LLM");

        QueryResponse response = service.generateResponse("p");

        assertNotNull(response);
        assertEquals("Answer from LLM", response.getAnswer());
    }
}
