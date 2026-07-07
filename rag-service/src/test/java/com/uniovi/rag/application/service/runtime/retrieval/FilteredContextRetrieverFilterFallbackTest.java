package com.uniovi.rag.application.service.runtime.retrieval;

import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** T-M5-BE-filter-fallback - empty LLM filter must not drop non-empty acta content. */
class FilteredContextRetrieverFilterFallbackTest {

    @Test
    void T_M5_BE_filterFallback_whenLlmReturnsEmpty_keepsOriginalContent() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn("");

        FilteredContextRetriever retriever =
                new FilteredContextRetriever(mock(PgVectorStore.class), chatClient, 5, 0.5);

        Document doc = new Document("doc-1", "Presidente: Juan Pérez García. Fecha: 24 de febrero de 2025.", Map.of());
        String out = retriever.filterDocumentContent(doc, "¿Quién fue el presidente?", new JSONObject());

        assertThat(out).contains("Juan Pérez García");
    }
}
