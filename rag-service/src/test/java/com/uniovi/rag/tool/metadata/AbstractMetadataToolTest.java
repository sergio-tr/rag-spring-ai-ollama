package com.uniovi.rag.tool.metadata;

import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.testsupport.ChatClientTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Covers {@link AbstractMetadataTool} via concrete subclass. */
class AbstractMetadataToolTest {

    @Test
    void metadataCountDocumentsTool_isInstanceOfAbstractMetadataTool() {
        ChatClient chatClient = ChatClientTestSupport.mockForUserPromptChain();
        ChatClientTestSupport.stubUserPromptReturns(chatClient, "NONE");
        ContextRetriever retriever = mock(ContextRetriever.class);
        DocumentContentExtractor extractor = mock(DocumentContentExtractor.class);
        MetadataLlmResponseCacheService llmCache = mock(MetadataLlmResponseCacheService.class);
        MetadataCountDocumentsTool tool = new MetadataCountDocumentsTool(chatClient, retriever, extractor, llmCache);
        assertNotNull(tool);
        assertTrue(tool instanceof AbstractMetadataTool);
    }
}
