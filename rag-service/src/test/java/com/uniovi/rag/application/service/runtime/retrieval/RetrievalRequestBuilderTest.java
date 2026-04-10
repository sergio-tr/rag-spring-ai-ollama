package com.uniovi.rag.application.service.runtime.retrieval;

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
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrievalRequestBuilderTest {

    private final RetrievalRequestBuilder builder = new RetrievalRequestBuilder();

    @Test
    void resolveMode_hybrid_whenMaterializationHybrid() {
        RagConfig rag = baseRag(5, MaterializationStrategy.HYBRID, true);
        assertThat(builder.resolveMode(rag)).isEqualTo(RetrievalMode.HYBRID_DENSE_SPARSE);
    }

    @Test
    void resolveMode_dense_whenDocumentLevel() {
        RagConfig rag = baseRag(5, MaterializationStrategy.DOCUMENT_LEVEL, true);
        assertThat(builder.resolveMode(rag)).isEqualTo(RetrievalMode.DENSE_ONLY);
    }

    @Test
    void build_usesRewrittenQueryText() {
        RagConfig rag = baseRag(5, MaterializationStrategy.CHUNK_LEVEL, true);
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
        UUID sid = UUID.randomUUID();
        ExecutionContext ctx =
                new ExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "u",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                new KnowledgeSnapshotSelection(
                                List.of(sid), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                Optional.empty(),
                Optional.empty(),
                "c",
                List.of("all"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "u",
                false,
                false,
                false,
                Optional.empty(),
                Optional.empty());
        QueryPlan plan = minimalPlan("raw", "rewritten");
        var req = builder.build(ctx, plan);
        assertThat(req.queryText()).isEqualTo("rewritten");
        assertThat(req.denseFetchLimit()).isEqualTo(50);
    }

    @Test
    void build_throwsWhenNoSnapshots() {
        RagConfig rag = baseRag(5, MaterializationStrategy.CHUNK_LEVEL, true);
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
        ExecutionContext ctx =
                new ExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "u",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "c",
                List.of("all"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "u",
                false,
                false,
                false,
                Optional.empty(),
                Optional.empty());
        QueryPlan plan = minimalPlan("a", "b");
        assertThatThrownBy(() -> builder.build(ctx, plan)).isInstanceOf(IllegalStateException.class);
    }

    private static RagConfig baseRag(int topK, MaterializationStrategy strat, boolean useRetrieval) {
        return new RagConfig(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                useRetrieval,
                false,
                false,
                topK,
                0.7,
                "l",
                "e",
                "c",
                "r",
                false,
                RagConfig.DEFAULT_NAIVE_FULL_CORPUS_MAX_CHARS,
                RagConfig.DEFAULT_ADVANCED_RETRIEVAL_MAX_CONTEXT_CHARS,
                strat);
    }

    private static QueryPlan minimalPlan(String raw, String rewritten) {
        EntityExtractionResult entities =
                new EntityExtractionResult(
                        List.of(), List.of(), List.of(), List.of(), List.of(),
                        Optional.empty(), Optional.empty(), Optional.empty(), List.of());
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                raw,
                raw,
                raw,
                rewritten,
                "L",
                Optional.empty(),
                com.uniovi.rag.domain.runtime.query.ClassifierStatus.DISABLED,
                com.uniovi.rag.domain.runtime.query.QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                entities,
                com.uniovi.rag.domain.runtime.query.StructuredRewriteResult.identityDisabled("r", "r"),
                com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape.UNKNOWN,
                com.uniovi.rag.domain.runtime.query.AmbiguityAssessment.sufficient(),
                "c",
                "m",
                List.of());
    }
}
