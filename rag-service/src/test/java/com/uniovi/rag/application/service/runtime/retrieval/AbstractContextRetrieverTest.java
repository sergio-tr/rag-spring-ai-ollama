package com.uniovi.rag.application.service.runtime.retrieval;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Covers {@link AbstractContextRetriever} via concrete subclass. */
class AbstractContextRetrieverTest {

    @Test
    void basicContextRetriever_isInstanceOfAbstractContextRetriever() {
        PgVectorStore vectorStore = mock(PgVectorStore.class);
        ChatClient chatClient = mock(ChatClient.class);
        BasicContextRetriever retriever = new BasicContextRetriever(vectorStore, chatClient, 10, 0.7);
        assertNotNull(retriever);
        assertTrue(retriever instanceof AbstractContextRetriever);
    }
}
