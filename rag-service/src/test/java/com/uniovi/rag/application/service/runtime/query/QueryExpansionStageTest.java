package com.uniovi.rag.application.service.runtime.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.runtime.query.expand.QueryExpander;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.query.QueryExpansionResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QueryExpansionStageTest {

    @Test
    void skipsWhenExpansionDisabled() {
        QueryExpander expander = mock(QueryExpander.class);
        QueryExpansionStage stage = new QueryExpansionStage(expander);
        QueryExpansionResult result = stage.expand(ctx(false), "hello");
        assertThat(result.applied()).isFalse();
        assertThat(result.expandedQuery()).isEqualTo("hello");
    }

    @Test
    void appliesExpandedQueryWhenEnabled() {
        QueryExpander expander = mock(QueryExpander.class);
        when(expander.expand("hello")).thenReturn("hello expanded");
        QueryExpansionStage stage = new QueryExpansionStage(expander);
        QueryExpansionResult result = stage.expand(ctx(true), "hello");
        assertThat(result.applied()).isTrue();
        assertThat(result.expandedQuery()).isEqualTo("hello expanded");
    }

    @Test
    void fallsBackToOriginalOnFailure() {
        QueryExpander expander = mock(QueryExpander.class);
        when(expander.expand(anyString())).thenThrow(new RuntimeException("llm down"));
        QueryExpansionStage stage = new QueryExpansionStage(expander);
        QueryExpansionResult result = stage.expand(ctx(true), "hello");
        assertThat(result.applied()).isFalse();
        assertThat(result.strategy()).isEqualTo("FAILED");
        assertThat(result.expandedQuery()).isEqualTo("hello");
    }

    private static ExecutionContext ctx(boolean expansionEnabled) {
        RagConfig rag =
                new RagConfig(
                        expansionEnabled,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
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
                        MaterializationStrategy.STRUCTURED_SEARCH);
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        rag,
                        CapabilitySet.fromRagConfig(rag),
                        CompatibilityResult.ok(),
                        ReindexImpact.none(),
                        new SystemPromptLayers("", "", "", ""),
                        "sys",
                        new ConfigProvenance(null, null, null, List.of(), null, null),
                        rag);
        return new ExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "hello",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "corr",
                List.of("all"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "hello",
                "hello",
                Optional.empty(),
                ConversationMemoryOutcome.DISABLED_BY_CONFIG,
                List.of(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                Optional.empty(),
                Optional.empty());
    }
}
