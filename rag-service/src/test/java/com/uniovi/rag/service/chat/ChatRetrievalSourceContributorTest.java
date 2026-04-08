package com.uniovi.rag.service.chat;

import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import com.uniovi.rag.service.config.ChatScopedRagConfigResolver;
import com.uniovi.rag.service.retriever.ContextRetriever;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRetrievalSourceContributorTest {

    private static final RagConfig SAMPLE_CONFIG =
            new RagConfig(
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    true,
                    false,
                    5,
                    0.2,
                    "llm",
                    "emb",
                    "cls",
                    "reason",
                    false,
                    RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                    RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                    MaterializationStrategy.CHUNK_LEVEL);

    @Mock
    private ChatScopedRagConfigResolver chatScopedRagConfigResolver;

    @Mock
    private ContextRetriever contextRetriever;

    @InjectMocks
    private ChatRetrievalSourceContributor contributor;

    @AfterEach
    void clearHolder() {
        RagExecutionContextHolder.clear();
    }

    @Test
    void buildSources_emptyWhenContentBlank() {
        assertThat(contributor.buildSources(UUID.randomUUID(), UUID.randomUUID(), null, List.of(), "  "))
                .isEmpty();
    }

    @Test
    void buildSources_emptyWhenProjectIdNull() {
        assertThat(contributor.buildSources(UUID.randomUUID(), null, UUID.randomUUID(), List.of(), "hello"))
                .isEmpty();
    }

    @Test
    void buildSources_invokesRetrieverWhenFilterEmpty() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        when(chatScopedRagConfigResolver.resolveForChat(eq(userId), eq(projectId), eq(convId))).thenReturn(SAMPLE_CONFIG);
        when(contextRetriever.retrieve("q")).thenReturn(List.of());

        contributor.buildSources(userId, projectId, convId, List.of(), " q ");

        verify(contextRetriever).retrieve("q");
    }

    @Test
    void buildSources_mapsSnippetAndTruncates() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(chatScopedRagConfigResolver.resolveForChat(eq(userId), eq(projectId), isNull())).thenReturn(SAMPLE_CONFIG);
        String longText = "x".repeat(300);
        Document d =
                new Document(
                        longText,
                        Map.of(
                                "filename",
                                "f.pdf",
                                "document_id",
                                UUID.randomUUID().toString(),
                                "chunk_index",
                                2,
                                "distance",
                                0.12));
        when(contextRetriever.retrieve("hi")).thenReturn(List.of(d));

        List<Map<String, Object>> sources =
                contributor.buildSources(userId, projectId, null, List.of("doc-1"), "hi");

        assertThat(sources).hasSize(1);
        assertThat((String) sources.getFirst().get("snippet")).endsWith("…");
        assertThat(sources.getFirst().get("filename")).isEqualTo("f.pdf");
    }

    @Test
    void buildSources_returnsEmptyOnRetrieverFailure() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(chatScopedRagConfigResolver.resolveForChat(eq(userId), eq(projectId), isNull())).thenReturn(SAMPLE_CONFIG);
        when(contextRetriever.retrieve(anyString())).thenThrow(new RuntimeException("boom"));

        assertThat(contributor.buildSources(userId, projectId, null, List.of(), "q")).isEmpty();
        verify(chatScopedRagConfigResolver).resolveForChat(userId, projectId, null);
    }

    @Test
    void buildSources_skipsBlankMetadataStrings() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(chatScopedRagConfigResolver.resolveForChat(eq(userId), eq(projectId), isNull())).thenReturn(SAMPLE_CONFIG);
        Document d = new Document("short", Map.of("filename", "   ", "chunk_index", 0));
        when(contextRetriever.retrieve("q")).thenReturn(List.of(d));

        List<Map<String, Object>> sources = contributor.buildSources(userId, projectId, null, List.of(), "q");

        assertThat(sources).hasSize(1);
        assertThat(sources.getFirst().containsKey("filename")).isFalse();
        assertThat(sources.getFirst().get("snippet")).isEqualTo("short");
    }
}
