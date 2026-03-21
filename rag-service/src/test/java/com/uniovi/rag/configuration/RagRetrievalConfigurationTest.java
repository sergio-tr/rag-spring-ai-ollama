package com.uniovi.rag.configuration;

import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.postretrieval.PostRetrievalProcessor;
import com.uniovi.rag.service.retriever.BasicContextRetriever;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.service.retriever.FilteredContextRetriever;
import com.uniovi.rag.service.retriever.MinuteDocumentContextRetriever;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link RagRetrievalConfiguration}.
 */
class RagRetrievalConfigurationTest {

    @Test
    void documentContentExtractorBean_returnsDefaultExtractor() {
        RagRetrievalConfiguration config = new RagRetrievalConfiguration();
        DocumentContentExtractor extractor = config.documentContentExtractor(null);
        assertNotNull(extractor);
    }

    @Test
    void postRetrievalProcessorBean_returnsProcessorWithTopK() {
        RagRetrievalConfiguration config = new RagRetrievalConfiguration();
        PostRetrievalProcessor processor = config.postRetrievalProcessor(10, null);
        assertNotNull(processor);
    }

    @Test
    void retrieverBean_defaultImpl_returnsBasicContextRetriever() {
        RagRetrievalConfiguration config = new RagRetrievalConfiguration();
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        ChatClient chatClient = mock(ChatClient.class);
        RagImplementationProperties implProps = new RagImplementationProperties();
        implProps.setRetrieverImpl(null);

        ContextRetriever retriever = config.retriever(vectorStore, chatClient, implProps, 10, 0.7, null);
        assertNotNull(retriever);
        assertTrue(retriever instanceof BasicContextRetriever);
    }

    @Test
    void retrieverBean_filteredImpl_returnsFilteredContextRetriever() {
        RagRetrievalConfiguration config = new RagRetrievalConfiguration();
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        ChatClient chatClient = mock(ChatClient.class);
        RagImplementationProperties implProps = new RagImplementationProperties();
        implProps.setRetrieverImpl("filtered");

        ContextRetriever retriever = config.retriever(vectorStore, chatClient, implProps, 10, 0.7, null);
        assertNotNull(retriever);
        assertTrue(retriever instanceof FilteredContextRetriever);
    }

    @Test
    void retrieverBean_minuteDocumentImpl_returnsMinuteDocumentContextRetriever() {
        RagRetrievalConfiguration config = new RagRetrievalConfiguration();
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        ChatClient chatClient = mock(ChatClient.class);
        RagImplementationProperties implProps = new RagImplementationProperties();
        implProps.setRetrieverImpl("minute-document");

        ContextRetriever retriever = config.retriever(vectorStore, chatClient, implProps, 10, 0.7, null);
        assertNotNull(retriever);
        assertTrue(retriever instanceof MinuteDocumentContextRetriever);
    }
}
