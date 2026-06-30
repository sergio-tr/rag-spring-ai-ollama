package com.uniovi.rag.configuration;

import com.uniovi.rag.application.service.knowledge.EmbeddingIndexCompatibilityService;
import com.uniovi.rag.application.service.runtime.document.extraction.DocumentContentExtractor;
import com.uniovi.rag.application.service.runtime.retrieval.post.PostRetrievalProcessor;
import com.uniovi.rag.application.service.runtime.retrieval.ContextRetriever;
import com.uniovi.rag.application.service.runtime.retrieval.ProviderAwareContextRetriever;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void retrieverBean_defaultImpl_wrapsWithProviderAwareRetriever() {
        RagRetrievalConfiguration config = new RagRetrievalConfiguration();
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        ChatClient chatClient = mock(ChatClient.class);
        RagImplementationProperties implProps = new RagImplementationProperties();
        implProps.setRetrieverImpl(null);
        EmbeddingIndexCompatibilityService compatibilityService = mock(EmbeddingIndexCompatibilityService.class);

        ContextRetriever retriever =
                config.retriever(vectorStore, chatClient, implProps, compatibilityService, 10, 0.7, false, null);
        assertNotNull(retriever);
        assertTrue(retriever instanceof ProviderAwareContextRetriever);
    }

    @Test
    void retrieverBean_filteredImpl_wrapsWithProviderAwareRetriever() {
        RagRetrievalConfiguration config = new RagRetrievalConfiguration();
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        ChatClient chatClient = mock(ChatClient.class);
        RagImplementationProperties implProps = new RagImplementationProperties();
        implProps.setRetrieverImpl("filtered");
        EmbeddingIndexCompatibilityService compatibilityService = mock(EmbeddingIndexCompatibilityService.class);

        ContextRetriever retriever =
                config.retriever(vectorStore, chatClient, implProps, compatibilityService, 10, 0.7, false, null);
        assertNotNull(retriever);
        assertTrue(retriever instanceof ProviderAwareContextRetriever);
    }

    @Test
    void retrieverBean_minuteDocumentImpl_wrapsWithProviderAwareRetriever() {
        RagRetrievalConfiguration config = new RagRetrievalConfiguration();
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        ChatClient chatClient = mock(ChatClient.class);
        RagImplementationProperties implProps = new RagImplementationProperties();
        implProps.setRetrieverImpl("minute-document");
        EmbeddingIndexCompatibilityService compatibilityService = mock(EmbeddingIndexCompatibilityService.class);

        ContextRetriever retriever =
                config.retriever(vectorStore, chatClient, implProps, compatibilityService, 10, 0.7, false, null);
        assertNotNull(retriever);
        assertTrue(retriever instanceof ProviderAwareContextRetriever);
    }
}
