package com.uniovi.rag.application.service.runtime.routing.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.evaluation.preset.LabBenchmarkExecutionContext;
import com.uniovi.rag.application.service.runtime.KnowledgeRuntimeSnapshotSelector;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class IntegratedParentPresetSnapshotResolverTest {

    @AfterEach
    void tearDown() {
        LabBenchmarkExecutionContext.clear();
    }

    @Test
    void resolve_usesCampaignBindingForParentPreset() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID p7Snapshot = UUID.randomUUID();
        UUID p15Snapshot = UUID.randomUUID();

        KnowledgeRuntimeSnapshotSelector selector = mock(KnowledgeRuntimeSnapshotSelector.class);
        KnowledgeSnapshotSelection p7Selection =
                new KnowledgeSnapshotSelection(
                        List.of(p7Snapshot),
                        Optional.of(p7Snapshot),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty());
        when(selector.selectExplicit(projectId, List.of(p7Snapshot))).thenReturn(p7Selection);

        IntegratedParentPresetSnapshotResolver resolver = new IntegratedParentPresetSnapshotResolver(selector);

        try (AutoCloseable ignoredP15 =
                        LabBenchmarkExecutionContext.openLab(
                                null,
                                UUID.randomUUID(),
                                projectId,
                                List.of(p15Snapshot),
                                "HYBRID_METADATA",
                                "P15",
                                true);
                AutoCloseable ignoredP7 =
                        LabBenchmarkExecutionContext.openLab(
                                null,
                                UUID.randomUUID(),
                                projectId,
                                List.of(p7Snapshot),
                                "CHUNK_LEVEL",
                                "P7",
                                true)) {
            ExecutionContext p15Ctx = context(projectId, p15Snapshot);
            KnowledgeSnapshotSelection resolved =
                    resolver.resolve(p15Ctx, RagExperimentalPresetCode.P7);

            assertThat(resolved.orderedSnapshotIds()).containsExactly(p7Snapshot);
        }
    }

    @Test
    void resolve_fallsBackToBaseContextWhenNoCampaignBinding() {
        UUID projectId = UUID.randomUUID();
        UUID baseSnapshot = UUID.randomUUID();
        KnowledgeRuntimeSnapshotSelector selector = mock(KnowledgeRuntimeSnapshotSelector.class);
        IntegratedParentPresetSnapshotResolver resolver = new IntegratedParentPresetSnapshotResolver(selector);

        ExecutionContext ctx = context(projectId, baseSnapshot);
        KnowledgeSnapshotSelection resolved = resolver.resolve(ctx, RagExperimentalPresetCode.P7);

        assertThat(resolved.orderedSnapshotIds()).containsExactly(baseSnapshot);
    }

    private static ExecutionContext context(UUID projectId, UUID snapshotId) {
        RagConfig rag =
                new RagConfig(
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
                projectId,
                projectId,
                UUID.randomUUID(),
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
}
