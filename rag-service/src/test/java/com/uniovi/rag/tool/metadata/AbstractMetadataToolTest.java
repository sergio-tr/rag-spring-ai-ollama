package com.uniovi.rag.tool.metadata;

import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Covers {@link AbstractMetadataTool} via concrete subclass. */
class AbstractMetadataToolTest {

    @Test
    void metadataCountDocumentsTool_isInstanceOfAbstractMetadataTool() {
        ChatClient chatClient = mock(ChatClient.class);
        ContextRetriever retriever = mock(ContextRetriever.class);
        DocumentContentExtractor extractor = mock(DocumentContentExtractor.class);
        MetadataCountDocumentsTool tool = new MetadataCountDocumentsTool(chatClient, retriever, extractor);
        assertNotNull(tool);
        assertTrue(tool instanceof AbstractMetadataTool);
    }
}
