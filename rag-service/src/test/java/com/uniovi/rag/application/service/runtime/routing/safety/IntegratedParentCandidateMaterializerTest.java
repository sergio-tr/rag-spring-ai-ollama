package com.uniovi.rag.application.service.runtime.routing.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IntegratedParentCandidateMaterializerTest {

    @Test
    void materializeP7_disablesIntegratedRoutingAndFunctionCalling() {
        ExecutionContext p15 = context(ragP15());
        ExecutionContext p7 = IntegratedParentCandidateMaterializer.materialize(p15, RagExperimentalPresetCode.P7);
        RagConfig cfg = p7.resolved().toRagConfig();
        assertThat(cfg.adaptiveRoutingEnabled()).isFalse();
        assertThat(cfg.functionCallingEnabled()).isFalse();
        assertThat(cfg.deterministicToolRoutingEnabled()).isTrue();
        assertThat(cfg.materializationStrategy()).isEqualTo(MaterializationStrategy.CHUNK_LEVEL);
    }

    @Test
    void materializeP6_enablesReasoningRetrievalParent() {
        ExecutionContext p15 = context(ragP15());
        ExecutionContext p6 =
                IntegratedParentCandidateMaterializer.materialize(p15, RagExperimentalPresetCode.P6);
        RagConfig cfg = p6.resolved().toRagConfig();
        assertThat(cfg.adaptiveRoutingEnabled()).isFalse();
        assertThat(cfg.functionCallingEnabled()).isFalse();
        assertThat(cfg.reasoningEnabled()).isTrue();
        assertThat(cfg.useRetrieval()).isTrue();
    }

    private static ExecutionContext context(RagConfig rag) {
        UUID id = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
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
        KnowledgeSnapshotSelection snapshots =
                new KnowledgeSnapshotSelection(
                        List.of(snapshotId),
                        Optional.of(snapshotId),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty());
        return new ExecutionContext(
                id,
                id,
                id,
                "q",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                snapshots,
                Optional.empty(),
                Optional.empty(),
                "corr",
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

    private static RagConfig ragP15() {
        return new RagConfig(
                false,
                false,
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                true,
                false,
                false,
                12,
                0.6,
                "llm",
                "emb",
                "cls",
                "SIMPLE",
                false,
                32_000,
                24_000,
                false,
                MaterializationStrategy.HYBRID);
    }
}
