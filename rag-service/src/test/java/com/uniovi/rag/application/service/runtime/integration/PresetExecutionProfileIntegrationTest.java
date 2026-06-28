package com.uniovi.rag.application.service.runtime.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.runtime.routing.DeterministicToolRoutingPolicy;
import com.uniovi.rag.application.service.runtime.tool.DefaultDeterministicToolResolver;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
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
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Preset feature profile → resolved {@link RagConfig} → deterministic routing/tool selection.
 * Mirrors Demo_Best acceptance configuration without live DB or harness scripts.
 */
class PresetExecutionProfileIntegrationTest {

    private static final String STAGE_A_COUNT_QUERY = "¿Cuántas actas mencionan el ascensor?";

    @Test
    void demoBestLikePresetProfile_enablesDeterministicToolRouteAndCountDocumentsTool() {
        RagConfig rag = demoBestLikeRagFromFeatureProfile();
        QueryPlan plan = countDocumentsPlan(STAGE_A_COUNT_QUERY);
        ExecutionContext ctx = executionContext(rag, STAGE_A_COUNT_QUERY);

        assertThat(rag.toolsEnabled()).isTrue();
        assertThat(rag.metadataEnabled()).isTrue();
        assertThat(rag.deterministicToolRoutingEnabled()).isTrue();

        var routing = new DeterministicToolRoutingPolicy().resolve(rag, plan);
        assertThat(routing.primaryRouteKind()).isEqualTo(AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE);

        var toolDecision = new DefaultDeterministicToolResolver().resolve(ctx, plan);
        assertThat(toolDecision.outcome()).isEqualTo(DeterministicToolOutcome.SELECTED);
        assertThat(toolDecision.selectedToolKind()).contains(DeterministicToolKind.COUNT_DOCUMENTS_TOOL);
    }

    private static RagConfig demoBestLikeRagFromFeatureProfile() {
        RagFeatureConfiguration features = new RagFeatureConfiguration();
        features.setToolsEnabled(true);
        features.setMetadataEnabled(true);
        features.setUseRetrieval(true);
        features.setPostRetrievalEnabled(true);
        features.setRankerEnabled(true);
        features.setClarificationEnabled(true);
        features.setDeterministicToolRoutingEnabled(true);
        return RagConfig.fromFeatureConfiguration(features, 5, 0.2, "llm", "emb", "cls", "reason");
    }

    private static QueryPlan countDocumentsPlan(String query) {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                query,
                query,
                query,
                query,
                QueryType.COUNT_DOCUMENTS.name(),
                Optional.of(QueryType.COUNT_DOCUMENTS),
                ClassifierStatus.OK,
                QueryIntent.COUNT,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityFallback(query, null),
                ExpectedAnswerShape.SCALAR_COUNT,
                AmbiguityAssessment.sufficient(),
                "corr",
                "default",
                List.of());
    }

    private static ExecutionContext executionContext(RagConfig rag, String query) {
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
                query,
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
                Optional.empty(),
                query,
                query,
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
