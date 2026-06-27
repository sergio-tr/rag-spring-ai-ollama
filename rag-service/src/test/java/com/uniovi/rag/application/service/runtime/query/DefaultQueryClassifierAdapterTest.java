package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.configuration.RagClassifierProperties;
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
import com.uniovi.rag.infrastructure.classifier.ClassifierCallException;
import com.uniovi.rag.infrastructure.classifier.ClassifierInferenceResponse;
import com.uniovi.rag.infrastructure.classifier.QueryClassifier;
import com.uniovi.rag.infrastructure.observability.RuntimeObservability;
import io.micrometer.tracing.Tracer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultQueryClassifierAdapterTest {

    @Test
    void nullQueryType_mapsToInvalidOutput_recoverable() {
        QueryClassifier classifier = mock(QueryClassifier.class);
        when(classifier.classifyInference("hello", "cls")).thenReturn(null);
        DefaultQueryClassifierAdapter adapter = adapter(classifier);

        ClassifierOutcome out = adapter.classify(ctxToolsEnabled(), "hello");

        assertEquals(DefaultQueryClassifierAdapter.UNCLASSIFIED, out.classifierLabel());
        assertEquals(ClassifierStatus.INVALID_OUTPUT, out.classifierStatus());
        assertTrue(out.classifierQueryType().isEmpty());
    }

    @Test
    void unknownLabel_mapsToInvalidOutput_recoverable() {
        QueryClassifier classifier = mock(QueryClassifier.class);
        when(classifier.classifyInference("hello", "cls"))
                .thenReturn(new ClassifierInferenceResponse("NOT_A_REAL_TYPE", 0.9, "hash1", List.of()));
        DefaultQueryClassifierAdapter adapter = adapter(classifier);

        ClassifierOutcome out = adapter.classify(ctxToolsEnabled(), "hello");

        assertEquals(ClassifierStatus.INVALID_OUTPUT, out.classifierStatus());
        assertTrue(out.classifierQueryType().isEmpty());
    }

    @Test
    void classifierException_mapsToUnavailable_recoverable() {
        QueryClassifier classifier = mock(QueryClassifier.class);
        when(classifier.classifyInference("hello", "cls"))
                .thenThrow(new ClassifierCallException(ClassifierCallException.Kind.UNAVAILABLE, "Classifier HTTP protocol error"));
        DefaultQueryClassifierAdapter adapter = adapter(classifier);

        ClassifierOutcome out = adapter.classify(ctxToolsEnabled(), "hello");

        assertEquals(DefaultQueryClassifierAdapter.UNCLASSIFIED, out.classifierLabel());
        assertEquals(ClassifierStatus.UNAVAILABLE, out.classifierStatus());
        assertEquals("UNAVAILABLE: classifier call failed", out.note());
    }

    @Test
    void classifierTimeout_mapsToTimeout_recoverable() {
        QueryClassifier classifier = mock(QueryClassifier.class);
        when(classifier.classifyInference("hello", "cls"))
                .thenThrow(new ClassifierCallException(ClassifierCallException.Kind.TIMEOUT, "Classifier timeout"));
        DefaultQueryClassifierAdapter adapter = adapter(classifier);

        ClassifierOutcome out = adapter.classify(ctxToolsEnabled(), "hello");

        assertEquals(ClassifierStatus.TIMEOUT, out.classifierStatus());
        assertEquals("TIMEOUT: classifier call failed", out.note());
    }

    @Test
    void classifierInvalidRequest_mapsToInvalidRequest_recoverable() {
        QueryClassifier classifier = mock(QueryClassifier.class);
        when(classifier.classifyInference("hello", "cls"))
                .thenThrow(new ClassifierCallException(ClassifierCallException.Kind.INVALID_REQUEST, "bad request"));
        DefaultQueryClassifierAdapter adapter = adapter(classifier);

        ClassifierOutcome out = adapter.classify(ctxToolsEnabled(), "hello");

        assertEquals(ClassifierStatus.INVALID_REQUEST, out.classifierStatus());
    }

    @Test
    void classifierException_mapsToUnavailable_recoverable_legacy() {
        QueryClassifier classifier = mock(QueryClassifier.class);
        when(classifier.classifyInference("hello", "cls")).thenThrow(new IllegalStateException("simulated classifier outage"));
        DefaultQueryClassifierAdapter adapter = adapter(classifier);

        ClassifierOutcome out = adapter.classify(ctxToolsEnabled(), "hello");

        assertEquals(DefaultQueryClassifierAdapter.UNCLASSIFIED, out.classifierLabel());
        assertEquals(ClassifierStatus.UNAVAILABLE, out.classifierStatus());
        assertTrue(out.note().startsWith("UNAVAILABLE"));
    }

    @Test
    void spanishParticipantsRuleOverride_bypassesLowConfidence() {
        QueryClassifier classifier = mock(QueryClassifier.class);
        String q = "dime los participantes del acta del 25 de febrero de 2026";
        when(classifier.classifyInference(q, "cls"))
                .thenReturn(new ClassifierInferenceResponse("EXTRACT_ENTITIES", 0.16, "hash1", List.of()));
        DefaultQueryClassifierAdapter adapter = adapter(classifier);

        ClassifierOutcome out = adapter.classify(ctxToolsEnabled(), q);

        assertEquals(ClassifierStatus.OK, out.classifierStatus());
        assertEquals(QueryType.GET_FIELD, out.classifierQueryType().orElseThrow());
        assertTrue(
                "RULE_OVERRIDE".equals(out.note()) || "DETERMINISTIC_PATTERN".equals(out.note()),
                "note=" + out.note());
    }

    @Test
    void spanishCountRuleOverride_bypassesLowConfidence() {
        QueryClassifier classifier = mock(QueryClassifier.class);
        String q = "¿Cuántas actas mencionan el ascensor?";
        when(classifier.classifyInference(q, "cls"))
                .thenReturn(new ClassifierInferenceResponse("SUMMARIZE_MEETING", 0.18, "hash1", List.of()));
        DefaultQueryClassifierAdapter adapter = adapter(classifier);

        ClassifierOutcome out = adapter.classify(ctxToolsEnabled(), q);

        assertEquals(ClassifierStatus.OK, out.classifierStatus());
        assertEquals(QueryType.COUNT_DOCUMENTS, out.classifierQueryType().orElseThrow());
        assertTrue(
                "RULE_OVERRIDE".equals(out.note()) || "DETERMINISTIC_PATTERN".equals(out.note()),
                "note=" + out.note());
    }

    @Test
    void spanishDurationRuleOverride_bypassesLowConfidence() {
        QueryClassifier classifier = mock(QueryClassifier.class);
        String q = "Duración de la reunión del 25 de febrero de 2026.";
        when(classifier.classifyInference(q, "cls"))
                .thenReturn(new ClassifierInferenceResponse("GET_FIELD", 0.12, "hash1", List.of()));
        DefaultQueryClassifierAdapter adapter = adapter(classifier);

        ClassifierOutcome out = adapter.classify(ctxToolsEnabled(), q);

        assertEquals(ClassifierStatus.OK, out.classifierStatus());
        assertEquals(QueryType.GET_DURATION, out.classifierQueryType().orElseThrow());
    }

    @Test
    void okClassification_returnsOk() {
        QueryClassifier classifier = mock(QueryClassifier.class);
        when(classifier.classifyInference("How many documents?", "cls"))
                .thenReturn(new ClassifierInferenceResponse("COUNT_DOCUMENTS", 0.92, "abc123", List.of()));
        DefaultQueryClassifierAdapter adapter = adapter(classifier);

        ClassifierOutcome out = adapter.classify(ctxToolsEnabled(), "How many documents?");

        assertEquals(ClassifierStatus.OK, out.classifierStatus());
        assertEquals(Optional.of(QueryType.COUNT_DOCUMENTS), out.classifierQueryType());
        assertEquals(Optional.of(0.92), out.classifierConfidence());
        assertEquals(Optional.of("abc123"), out.classifierLabelSetHash());
    }

    @Test
    void lowConfidence_spanishMatrixQuestion_usesDeterministicPattern() {
        QueryClassifier classifier = mock(QueryClassifier.class);
        String q = "¿Qué actas tienen hora de inicio a las 19:00?";
        when(classifier.classifyInference(q, "cls"))
                .thenReturn(new ClassifierInferenceResponse("SUMMARIZE_MEETING", 0.1, "abc123", List.of()));
        DefaultQueryClassifierAdapter adapter = adapter(classifier);

        ClassifierOutcome out = adapter.classify(ctxToolsEnabled(), q);

        assertEquals(ClassifierStatus.OK, out.classifierStatus());
        assertEquals(Optional.of(QueryType.FILTER_AND_LIST), out.classifierQueryType());
        assertEquals("DETERMINISTIC_PATTERN", out.note());
    }

    @Test
    void lowConfidence_acceptsApplicableMlLabel() {
        QueryClassifier classifier = mock(QueryClassifier.class);
        when(classifier.classifyInference("How many documents?", "cls"))
                .thenReturn(new ClassifierInferenceResponse("COUNT_DOCUMENTS", 0.2, "abc123", List.of()));
        DefaultQueryClassifierAdapter adapter = adapter(classifier);

        ClassifierOutcome out = adapter.classify(ctxToolsEnabled(), "How many documents?");

        assertEquals(ClassifierStatus.OK, out.classifierStatus());
        assertEquals(Optional.of(QueryType.COUNT_DOCUMENTS), out.classifierQueryType());
        assertEquals("APPLICABLE_LOW_CONFIDENCE", out.note());
    }

    @Test
    void lowConfidence_mapsToLowConfidence_whenLabelNotApplicableAndNoRule() {
        QueryClassifier classifier = mock(QueryClassifier.class);
        when(classifier.classifyInference("random unrelated text", "cls"))
                .thenReturn(new ClassifierInferenceResponse("EXTRACT_ENTITIES", 0.2, "abc123", List.of()));
        DefaultQueryClassifierAdapter adapter = adapter(classifier);

        ClassifierOutcome out = adapter.classify(ctxToolsEnabled(), "random unrelated text");

        assertEquals(ClassifierStatus.LOW_CONFIDENCE, out.classifierStatus());
        assertTrue(out.classifierQueryType().isEmpty());
    }

    @Test
    void missingConfidence_treatedAsValidWhenLabelParses() {
        QueryClassifier classifier = mock(QueryClassifier.class);
        when(classifier.classifyInference("How many documents?", "cls"))
                .thenReturn(new ClassifierInferenceResponse("COUNT_DOCUMENTS", null, null, List.of()));
        DefaultQueryClassifierAdapter adapter = adapter(classifier);

        ClassifierOutcome out = adapter.classify(ctxToolsEnabled(), "How many documents?");

        assertEquals(ClassifierStatus.OK, out.classifierStatus());
        assertEquals(Optional.of(QueryType.COUNT_DOCUMENTS), out.classifierQueryType());
        assertTrue(out.classifierConfidence().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static DefaultQueryClassifierAdapter adapter(QueryClassifier classifier) {
        ObjectProvider<RuntimeObservability> obs = mock(ObjectProvider.class);
        ObjectProvider<Tracer> tracer = mock(ObjectProvider.class);
        RagClassifierProperties props = new RagClassifierProperties();
        props.setConfidenceThreshold(0.55);
        return new DefaultQueryClassifierAdapter(classifier, props, obs, tracer);
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
