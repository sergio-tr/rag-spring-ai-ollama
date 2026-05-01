package com.uniovi.rag.tool;

import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests protected behaviour of AbstractTool via a minimal concrete subclass.
 */
class AbstractToolTest {

    private ChatClient chatClient;
    private ContextRetriever retriever;
    private DocumentContentExtractor extractor;
    private TestableTool tool;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        retriever = mock(ContextRetriever.class);
        extractor = mock(DocumentContentExtractor.class);
        tool = new TestableTool(chatClient, retriever, extractor);
    }

    @Test
    void removeQuestionRepetition_nullInput_returnsResponse() {
        assertNull(tool.removeQuestionRepetition(null, "q"));
        assertEquals("text", tool.removeQuestionRepetition("text", null));
    }

    @Test
    void removeQuestionRepetition_responseStartsWithQuestion_removesIt() {
        String response = "¿Cuántos documentos hay? Hay 5 documentos.";
        String query = "¿Cuántos documentos hay?";
        String result = tool.removeQuestionRepetition(response, query);
        assertNotNull(result);
        assertTrue(result.contains("5") && !result.trim().toLowerCase().startsWith("¿cuántos"));
    }

    @Test
    void formatResponse_nullOrEmpty_returnsAsIs() {
        assertNull(tool.formatResponse(null, "q"));
        assertEquals("", tool.formatResponse("", "q"));
    }

    @Test
    void formatResponse_normalizesWhitespace() {
        String result = tool.formatResponse("  multiple   spaces  ", "q");
        assertNotNull(result);
        assertFalse(result.contains("  "));
    }

    /** Concrete subclass that exposes protected methods for testing. */
    private static final class TestableTool extends AbstractTool {
        TestableTool(ChatClient chatClient, ContextRetriever retriever, DocumentContentExtractor extractor) {
            super(chatClient, retriever, extractor);
        }

        @Override
        public ToolResult execute(ToolExecutionContext context) {
            return null;
        }

        @Override
        protected String removeQuestionRepetition(String response, String query) {
            return super.removeQuestionRepetition(response, query);
        }

        @Override
        protected String formatResponse(String response, String query) {
            return super.formatResponse(response, query);
        }
    }
}
