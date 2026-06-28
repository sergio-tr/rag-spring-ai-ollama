package com.uniovi.rag.application.service.evaluation.preset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uniovi.rag.application.service.runtime.WorkflowNameInference;
import com.uniovi.rag.application.service.runtime.WorkflowSelector;
import com.uniovi.rag.application.service.runtime.retrieval.AdvancedRetrievalPipeline;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagRuntimeProperties;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import com.uniovi.rag.application.service.runtime.ChunkDenseMetadataWorkflow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.uniovi.rag.application.service.runtime.ChunkDenseRagWorkflow;
import com.uniovi.rag.application.service.runtime.CorpusGroundedDirectWorkflow;
import com.uniovi.rag.application.service.runtime.DirectLlmWorkflow;
import com.uniovi.rag.application.service.runtime.DocumentDenseRagWorkflow;
import com.uniovi.rag.application.service.runtime.FullCorpusWorkflow;
import com.uniovi.rag.application.service.runtime.RuntimePromptBudgeter;
import com.uniovi.rag.application.service.runtime.SnapshotCorpusAssembler;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.uniovi.rag.application.service.runtime.llm.RagLlmChatInvoker;
import com.uniovi.rag.application.service.runtime.llm.RagLlmChatInvokerTestSupport;
import org.springframework.ai.chat.client.ChatClient;

class P0P1BaselineContractTest {

    @Test
    void p0_resolvedConfig_isPureDirectLlm() {
        Map<String, Object> p0 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P0);
        assertThat(p0)
                .containsEntry("useRetrieval", false)
                .containsEntry("naiveFullCorpusInPromptEnabled", false)
                .containsEntry("corpusGroundedDirectWorkflow", false)
                .containsEntry("metadataEnabled", false)
                .containsEntry("toolsEnabled", false)
                .containsEntry("functionCallingEnabled", false)
                .containsEntry("useAdvisor", false)
                .containsEntry("postRetrievalEnabled", false)
                .containsEntry("rankerEnabled", false)
                .containsEntry("clarificationEnabled", false)
                .containsEntry("memoryEnabled", false)
                .containsEntry("adaptiveRoutingEnabled", false)
                .containsEntry("judgeEnabled", false);
        assertThat(ExperimentalPresetCanonicalCatalog.corpusRequired(RagExperimentalPresetCode.P0)).isFalse();
        assertThat(ExperimentalPresetCanonicalCatalog.needsVectorIndex(RagExperimentalPresetCode.P0)).isFalse();
        assertThat(ExperimentalPresetCanonicalCatalog.canRunWithoutCorpus(RagExperimentalPresetCode.P0)).isTrue();
    }

    @Test
    void p1_resolvedConfig_requiresCorpus_notVectorIndex() {
        Map<String, Object> p1 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P1);
        assertThat(p1)
                .containsEntry("useRetrieval", false)
                .containsEntry("naiveFullCorpusInPromptEnabled", true)
                .containsEntry("corpusGroundedDirectWorkflow", false);
        assertThat(ExperimentalPresetCanonicalCatalog.corpusRequired(RagExperimentalPresetCode.P1)).isTrue();
        assertThat(ExperimentalPresetCanonicalCatalog.needsVectorIndex(RagExperimentalPresetCode.P1)).isFalse();
        assertThat(ExperimentalPresetCanonicalCatalog.requiresSnapshotAssembledCorpusEvidence(
                        RagExperimentalPresetCode.P1))
                .isTrue();
    }

    @Test
    void p0_selectsDirectLlmWorkflow_p1_selectsFullCorpusWorkflow() {
        RagConfig p0 = effectiveRagConfig(RagExperimentalPresetCode.P0);
        RagConfig p1 = effectiveRagConfig(RagExperimentalPresetCode.P1);

        assertThat(WorkflowNameInference.inferWorkflowName(p0)).isEqualTo("DirectLlmWorkflow");
        assertThat(WorkflowNameInference.inferWorkflowName(p1)).isEqualTo("FullCorpusWorkflow");

        WorkflowSelector selector = workflowSelector();
        assertThat(selector.select(ctx(p0))).isInstanceOf(DirectLlmWorkflow.class);
        assertThat(selector.select(ctx(p1))).isInstanceOf(FullCorpusWorkflow.class);
        assertThat(selector.select(ctx(p0))).isNotInstanceOf(CorpusGroundedDirectWorkflow.class);
    }

    @Test
    void allCanRunWithoutCorpus_onlyTrueForP0OnlySelections() {
        assertThat(ExperimentalPresetCanonicalCatalog.allCanRunWithoutCorpus(List.of("P0"))).isTrue();
        assertThat(ExperimentalPresetCanonicalCatalog.allCanRunWithoutCorpus(List.of("P1"))).isFalse();
        assertThat(ExperimentalPresetCanonicalCatalog.allCanRunWithoutCorpus(List.of("P0", "P1"))).isFalse();
        assertThat(ExperimentalPresetCanonicalCatalog.allCanRunWithoutCorpus(List.of())).isFalse();
        assertThat(ExperimentalPresetCanonicalCatalog.allCanRunWithoutCorpus(null)).isFalse();
    }

    @Test
    void p0_and_p1_runPlanGroups_differ() {
        assertThat(LabPresetRunPlanService.groupKeyFor(RagExperimentalPresetCode.P0))
                .isEqualTo(LabPresetRunGroupKey.DIRECT_LLM);
        assertThat(LabPresetRunPlanService.groupKeyFor(RagExperimentalPresetCode.P1))
                .isEqualTo(LabPresetRunGroupKey.NO_INDEX);
    }

    @Test
    void p0_and_p1_terminalOverlay_metadata_differs() {
        RagPresetExperimentalOverlay.Overlay p0 =
                RagPresetExperimentalOverlay.build(new RagFeatureConfiguration(), RagExperimentalPresetCode.P0);
        RagPresetExperimentalOverlay.Overlay p1 =
                RagPresetExperimentalOverlay.build(new RagFeatureConfiguration(), RagExperimentalPresetCode.P1);

        ObjectNode p0Json = p0.terminalRuntimeJson();
        ObjectNode p1Json = p1.terminalRuntimeJson();
        assertThat(p0Json.get("naiveFullCorpusInPromptEnabled").asBoolean()).isFalse();
        assertThat(p1Json.get("naiveFullCorpusInPromptEnabled").asBoolean()).isTrue();
        assertThat(p0Json.get("corpusGroundedDirectWorkflow").asBoolean()).isFalse();
        assertThat(p1Json.get("corpusGroundedDirectWorkflow").asBoolean()).isFalse();
    }

    private static RagConfig effectiveRagConfig(RagExperimentalPresetCode preset) {
        RagFeatureConfiguration base = new RagFeatureConfiguration();
        RagPresetExperimentalOverlay.Overlay overlay = RagPresetExperimentalOverlay.build(base, preset);
        RagConfig cfg =
                RagConfig.fromFeatureConfiguration(overlay.features(), 10, 0.7, "llm", "emb", "classifier", "simple");
        return RagConfig.applyJsonOverrides(cfg, overlay.terminalRuntimeJson());
    }

    private static WorkflowSelector workflowSelector() {
        RagLlmChatInvoker llmChatInvoker = RagLlmChatInvokerTestSupport.stubContent("ANS");
        return new WorkflowSelector(
                new DirectLlmWorkflow(llmChatInvoker, null),
                new CorpusGroundedDirectWorkflow(
                        llmChatInvoker,
                        mock(SnapshotCorpusAssembler.class),
                        new RuntimePromptBudgeter(new RagRuntimeProperties()),
                        null),
                new FullCorpusWorkflow(
                        llmChatInvoker,
                        mock(SnapshotCorpusAssembler.class),
                        new RuntimePromptBudgeter(new RagRuntimeProperties()),
                        null),
                new DocumentDenseRagWorkflow(llmChatInvoker, mock(AdvancedRetrievalPipeline.class), null),
                new ChunkDenseRagWorkflow(llmChatInvoker, mock(AdvancedRetrievalPipeline.class), null),
                new ChunkDenseMetadataWorkflow(llmChatInvoker, mock(AdvancedRetrievalPipeline.class), null));
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
                Optional.empty(),
                false,
                AdaptiveRoutingOutcome.DISABLED_BY_CONFIG,
                AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                false,
                Optional.empty(),
                false,
                List.of());
    }
}
