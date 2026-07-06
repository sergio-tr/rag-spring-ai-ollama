package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.application.service.llm.ProviderAwareSecondaryLlmExecutor;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.NormalizedQuery;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.uniovi.rag.testsupport.config.TestConfigurablePromptResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultStructuredQueryRewriterTest {

    @Mock private ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor;

    private static ExecutionContext ctx(boolean nerEnabled) {
        RagConfig rag =
                new RagConfig(
                false,
                nerEnabled,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
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
                MaterializationStrategy.CHUNK_LEVEL);
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
                "raw",
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
                "raw",
                "raw",
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

    @Test
    void disabled_returnsIdentityDisabled() {
        DefaultStructuredQueryRewriter rewriter = new DefaultStructuredQueryRewriter(secondaryLlmExecutor, TestConfigurablePromptResolver.defaultsOnly());
        NormalizedQuery nq = new NormalizedQuery("raw", "hello", List.of());
        EntityExtractionResult entities =
                new EntityExtractionResult(List.of(), List.of(), List.of(), List.of(), List.of(),
                        Optional.empty(), Optional.empty(), Optional.empty(), List.of());

        StructuredRewriteResult r = rewriter.rewrite(ctx(false), nq, "UNCLASSIFIED", Optional.empty(), ClassifierStatus.DISABLED, entities);
        assertFalse(r.rewriteApplied());
        assertEquals("hello", r.rewrittenQueryText());
        assertTrue(r.rewriteNotes().get(0).startsWith("DISABLED"));
        verifyNoInteractions(secondaryLlmExecutor);
    }

    @Test
    void invalidJson_returnsIdentityFallback() {
        when(secondaryLlmExecutor.complete(
                        any(ExecutionContext.class),
                        eq("query-rewrite"),
                        anyString(),
                        anyString(),
                        eq(ProviderAwareSecondaryLlmExecutor.SECONDARY_TASK_DEFAULT_TEMPERATURE)))
                .thenReturn("not json");
        DefaultStructuredQueryRewriter rewriter = new DefaultStructuredQueryRewriter(secondaryLlmExecutor, TestConfigurablePromptResolver.defaultsOnly());
        NormalizedQuery nq = new NormalizedQuery("raw", "hello", List.of());
        EntityExtractionResult entities =
                new EntityExtractionResult(List.of(), List.of(), List.of(), List.of(), List.of(),
                        Optional.empty(), Optional.empty(), Optional.empty(), List.of());

        StructuredRewriteResult r = rewriter.rewrite(ctx(true), nq, "UNCLASSIFIED", Optional.empty(), ClassifierStatus.OK, entities);
        assertFalse(r.rewriteApplied());
        assertEquals("hello", r.rewrittenQueryText());
        assertTrue(r.rewriteNotes().get(0).startsWith("FALLBACK"));
    }

    @Test
    void droppedDate_isRejectedToFallback() {
        String rewriteJson = """
                {"rewrittenQueryText":"tell me about the meeting","targetEntities":[],"targetAttributes":[],"targetAction":"SUMMARIZE","slotFilling":{},"constraints":[]}
                """;
        when(secondaryLlmExecutor.complete(
                        any(ExecutionContext.class),
                        eq("query-rewrite"),
                        anyString(),
                        anyString(),
                        eq(ProviderAwareSecondaryLlmExecutor.SECONDARY_TASK_DEFAULT_TEMPERATURE)))
                .thenReturn(rewriteJson);
        DefaultStructuredQueryRewriter rewriter = new DefaultStructuredQueryRewriter(secondaryLlmExecutor, TestConfigurablePromptResolver.defaultsOnly());

        NormalizedQuery nq = new NormalizedQuery("raw", "meeting on 2026-02-25", List.of());
        EntityExtractionResult entities =
                new EntityExtractionResult(
                        List.of(),
                        List.of("2026-02-25"),
                        List.of(),
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of());

        StructuredRewriteResult r = rewriter.rewrite(ctx(true), nq, "UNCLASSIFIED", Optional.empty(), ClassifierStatus.OK, entities);
        assertFalse(r.rewriteApplied());
        assertEquals("meeting on 2026-02-25", r.rewrittenQueryText());
        assertTrue(r.rewriteNotes().get(0).startsWith("FALLBACK"));
    }

    @Test
    void carriedAttendeeCount_preservesQueryWithoutDurationRewrite() {
        DefaultStructuredQueryRewriter rewriter = new DefaultStructuredQueryRewriter(secondaryLlmExecutor, TestConfigurablePromptResolver.defaultsOnly());

        NormalizedQuery nq =
                new NormalizedQuery(
                        "raw",
                        "¿Cuántos asistentes tiene el acta del 25 de agosto del 2025?",
                        List.of());
        EntityExtractionResult entities =
                new EntityExtractionResult(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of());

        StructuredRewriteResult r =
                rewriter.rewrite(ctx(true), nq, "GET_FIELD", Optional.of(QueryType.GET_FIELD), ClassifierStatus.OK, entities);
        assertFalse(r.rewriteApplied());
        assertEquals("¿Cuántos asistentes tiene el acta del 25 de agosto del 2025?", r.rewrittenQueryText());
        assertFalse(r.rewrittenQueryText().toLowerCase().contains("duración"));
        verifyNoInteractions(secondaryLlmExecutor);
    }
}
