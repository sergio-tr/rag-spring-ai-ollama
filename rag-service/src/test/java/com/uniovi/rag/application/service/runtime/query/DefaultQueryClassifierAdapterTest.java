package com.uniovi.rag.application.service.runtime.query;

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
import com.uniovi.rag.application.service.runtime.query.QueryClassifierAdapter.ClassifierOutcome;
import com.uniovi.rag.infrastructure.classifier.QueryClassifier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultQueryClassifierAdapterTest {

    @Test
    void nullQueryType_mapsToInvalidOutput_recoverable() {
        QueryClassifier classifier = mock(QueryClassifier.class);
        when(classifier.classify("hello", "cls")).thenReturn(null);
        DefaultQueryClassifierAdapter adapter = new DefaultQueryClassifierAdapter(classifier);

        ClassifierOutcome out = adapter.classify(ctxToolsEnabled(), "hello");

        assertEquals(DefaultQueryClassifierAdapter.UNCLASSIFIED, out.classifierLabel());
        assertEquals(ClassifierStatus.INVALID_OUTPUT, out.classifierStatus());
        assertTrue(out.classifierQueryType().isEmpty());
    }

    @Test
    void classifierException_mapsToUnavailable_recoverable() {
        QueryClassifier classifier = mock(QueryClassifier.class);
        when(classifier.classify("hello", "cls")).thenThrow(new IllegalStateException("simulated classifier outage"));
        DefaultQueryClassifierAdapter adapter = new DefaultQueryClassifierAdapter(classifier);

        ClassifierOutcome out = adapter.classify(ctxToolsEnabled(), "hello");

        assertEquals(DefaultQueryClassifierAdapter.UNCLASSIFIED, out.classifierLabel());
        assertEquals(ClassifierStatus.UNAVAILABLE, out.classifierStatus());
        assertTrue(out.note().startsWith("UNAVAILABLE"));
    }

    @Test
    void okClassification_returnsOk() {
        QueryClassifier classifier = mock(QueryClassifier.class);
        when(classifier.classify("How many documents?", "cls")).thenReturn(QueryType.COUNT_DOCUMENTS);
        DefaultQueryClassifierAdapter adapter = new DefaultQueryClassifierAdapter(classifier);

        ClassifierOutcome out = adapter.classify(ctxToolsEnabled(), "How many documents?");

        assertEquals(ClassifierStatus.OK, out.classifierStatus());
        assertEquals(Optional.of(QueryType.COUNT_DOCUMENTS), out.classifierQueryType());
    }

    private static ExecutionContext ctxToolsEnabled() {
        RagConfig rag =
                new RagConfig(
                        false,
                        false,
                        true,
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
                "hello",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "corr-test",
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
