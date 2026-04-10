package com.uniovi.rag.application.service.runtime.tool;

import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultDeterministicToolResolverTest {

    private DefaultDeterministicToolResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DefaultDeterministicToolResolver();
    }

    @Test
    void disabledByConfig() {
        RagConfig rag = baseRag(false, false);
        QueryPlan plan = minimalPlan(QueryIntent.COUNT, ExpectedAnswerShape.SCALAR_COUNT, AmbiguityStatus.SUFFICIENT);
        var d = resolver.resolve(ctx(rag), plan, "DirectLlmWorkflow");
        assertThat(d.outcome()).isEqualTo(DeterministicToolOutcome.DISABLED_BY_CONFIG);
        assertThat(d.selected()).isFalse();
    }

    @Test
    void suppressedByAmbiguity() {
        RagConfig rag = baseRag(true, false);
        QueryPlan plan = minimalPlan(QueryIntent.COUNT, ExpectedAnswerShape.SCALAR_COUNT, AmbiguityStatus.MISSING_INFORMATION);
        var d = resolver.resolve(ctx(rag), plan, "DirectLlmWorkflow");
        assertThat(d.outcome()).isEqualTo(DeterministicToolOutcome.SUPPRESSED_BY_AMBIGUITY);
        assertThat(d.selected()).isFalse();
    }

    @Test
    void selectsCountDocuments() {
        RagConfig rag = baseRag(true, false);
        QueryPlan plan = minimalPlan(QueryIntent.COUNT, ExpectedAnswerShape.SCALAR_COUNT, AmbiguityStatus.SUFFICIENT);
        var d = resolver.resolve(ctx(rag), plan, "DirectLlmWorkflow");
        assertThat(d.selected()).isTrue();
        assertThat(d.selectedToolKind()).contains(DeterministicToolKind.COUNT_DOCUMENTS_TOOL);
    }

    @Test
    void ambiguousMatch_rejects() {
        RagConfig rag = baseRag(true, false);
        QueryPlan plan =
                planWithClassifier(
                        QueryIntent.COUNT,
                        ExpectedAnswerShape.SCALAR_COUNT,
                        AmbiguityStatus.SUFFICIENT,
                        Optional.of(QueryType.COUNT_AND_EXPLAIN));
        var d = resolver.resolve(ctx(rag), plan, "DirectLlmWorkflow");
        assertThat(d.outcome()).isEqualTo(DeterministicToolOutcome.NOT_APPLICABLE);
        assertThat(d.selected()).isFalse();
        assertThat(d.reasons().toString()).contains("tool_ambiguous_match");
    }

    @Test
    void getField_requiresSlotField() {
        RagConfig rag = baseRag(true, false);
        QueryPlan plan =
                new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                "raw",
                "raw",
                "norm",
                "rewritten",
                "lbl",
                Optional.of(QueryType.GET_FIELD),
                ClassifierStatus.OK,
                QueryIntent.EXTRACT_FIELD,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityDisabled("norm", ""),
                ExpectedAnswerShape.FIELD_VALUE,
                AmbiguityAssessment.sufficient(),
                "cid",
                "",
                List.of());
        var d = resolver.resolve(ctx(rag), plan, "DirectLlmWorkflow");
        assertThat(d.selected()).isFalse();
        assertThat(d.outcome()).isEqualTo(DeterministicToolOutcome.NOT_APPLICABLE);
    }

    private static RagConfig baseRag(boolean toolsEnabled, boolean useAdvisor) {
        return new RagConfig(
                false,
                false,
                toolsEnabled,
                false,
                false,
                false,
                false,
                false,
                false,
                useAdvisor,
                false,
                5,
                0.2,
                "l",
                "e",
                "c",
                "r",
                false,
                RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                com.uniovi.rag.domain.knowledge.MaterializationStrategy.CHUNK_LEVEL);
    }

    private static ExecutionContext ctx(RagConfig rag) {
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
                "q",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "t",
                List.of("all"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "q",
                false,
                false,
                false,
                Optional.empty(),
                Optional.empty());
    }

    private static QueryPlan minimalPlan(
            QueryIntent intent, ExpectedAnswerShape shape, AmbiguityStatus amb) {
        return planWithClassifier(intent, shape, amb, Optional.empty());
    }

    private static QueryPlan planWithClassifier(
            QueryIntent intent,
            ExpectedAnswerShape shape,
            AmbiguityStatus amb,
            Optional<QueryType> classifierQt) {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                "raw",
                "raw",
                "norm",
                "rewritten",
                "lbl",
                classifierQt,
                ClassifierStatus.OK,
                intent,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityDisabled("norm", ""),
                shape,
                new AmbiguityAssessment(amb, List.of(), List.of()),
                "cid",
                "",
                List.of());
    }
}
