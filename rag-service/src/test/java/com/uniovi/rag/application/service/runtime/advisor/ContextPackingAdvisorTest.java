package com.uniovi.rag.application.service.runtime.advisor;

import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.advisor.PackedContextSet;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.retrieval.CompressionOutcome;
import com.uniovi.rag.domain.runtime.retrieval.CuratedContextSet;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalCandidate;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalDiagnostics;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalFusionMode;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContextPackingAdvisorTest {

    private final ContextPackingAdvisor packing = new ContextPackingAdvisor();

    @Test
    void builds_deterministic_packed_set() {
        UUID snap = UUID.randomUUID();
        RetrievalCandidate c =
                new RetrievalCandidate(
                        "bid-1",
                        "hello text",
                        Map.of("documentId", "doc-1"),
                        1.0,
                        0.0,
                        1,
                        0,
                        snap,
                        1.0);
        CuratedContextSet curated =
                new CuratedContextSet(
                        List.of(c),
                        "prompt body",
                        new CompressionOutcome(10, 10, 0, List.of()),
                        List.of("n1"),
                        new RetrievalDiagnostics(
                                RetrievalMode.DENSE_ONLY,
                                Optional.of(RetrievalFusionMode.RRF_ONLY),
                                "",
                                1,
                                0,
                                1,
                                1,
                                1,
                                1),
                        List.of(),
                        List.of());
        PackedContextSet packed =
                packing.pack(minimalCtx(), minimalPlan(), curated, "ChunkDenseRagWorkflow");
        assertEquals(1, packed.totalBlockCount());
        assertEquals(1, packed.blocks().size());
        assertEquals("prompt body", packed.promptContextText());
        assertEquals(ContextPackingAdvisor.PACKING_STRATEGY_ID, packed.packingStrategyId());
        assertEquals("bid-1", packed.blocks().getFirst().blockId());
    }

    private static ExecutionContext minimalCtx() {
        RagConfig rag =
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
                true,
                false,
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
                "q",
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
                "q",
                "q",
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

    private static QueryPlan minimalPlan() {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                "raw",
                "raw",
                "norm",
                "rewritten",
                "lbl",
                Optional.empty(),
                ClassifierStatus.OK,
                QueryIntent.UNKNOWN,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityDisabled("norm", ""),
                ExpectedAnswerShape.UNKNOWN,
                new AmbiguityAssessment(AmbiguityStatus.SUFFICIENT, List.of(), List.of()),
                "cid",
                "",
                List.of());
    }
}
