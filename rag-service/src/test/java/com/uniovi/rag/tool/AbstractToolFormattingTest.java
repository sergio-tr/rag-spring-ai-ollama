package com.uniovi.rag.tool;

import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.testsupport.ChatClientTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Exercises protected formatting helpers on {@link AbstractTool} via a minimal subclass.
 */
class AbstractToolFormattingTest {

    private TestableAbstractTool tool;

    @BeforeEach
    void setUp() {
        ChatClient chat = ChatClientTestSupport.clientWithUserPromptReturning("fallback");
        ContextRetriever retriever = mock(ContextRetriever.class);
        DocumentContentExtractor extractor = mock(DocumentContentExtractor.class);
        tool = new TestableAbstractTool(chat, retriever, extractor);
    }

    @Test
    void removeQuestionRepetition_stripsLeadingQuestion() {
        String q = "What is the budget?";
        String r = q + " The budget is 100.";
        String cleaned = tool.exposeRemoveRepetition(r, q);
        assertTrue(cleaned.contains("100"));
    }

    @Test
    void formatResponse_normalizesWhitespace() {
        String out = tool.exposeFormat("hello   world", "q");
        assertEquals("Hello world", out);
    }

    /** Minimal concrete tool to expose protected methods for testing. */
    static final class TestableAbstractTool extends AbstractTool {
        TestableAbstractTool(ChatClient c, ContextRetriever r, DocumentContentExtractor e) {
            super(c, r, e);
        }

        String exposeRemoveRepetition(String response, String query) {
            return removeQuestionRepetition(response, query);
        }

        String exposeFormat(String response, String query) {
            return formatResponse(response, query);
        }

        @Override
        public ToolResult execute(ToolExecutionContext context) {
            return ToolResult.from("ok", TestableAbstractTool.class);
        }
    }
}
