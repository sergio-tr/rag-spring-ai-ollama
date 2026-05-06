package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
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
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.service.analyser.QueryAnalyser;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultNamedEntityExtractionAdapterDateNormalizationTest {

    @Test
    void extract_whenAnalyserReturnsMonthOnlyToken_prefersExplicitDateFromText() {
        QueryAnalyser analyser =
                new QueryAnalyser() {
                    @Override
                    public JSONObject analyse(String query) {
                        return new JSONObject()
                                .put("date", List.of("february"))
                                .put("attendees", List.of())
                                .put("president", List.of())
                                .put("secretary", List.of());
                    }
                };
        DefaultNamedEntityExtractionAdapter adapter = new DefaultNamedEntityExtractionAdapter(analyser);

        ExecutionContext ctx = minimalCtxWithNerEnabled();
        EntityExtractionResult out = adapter.extract(ctx, "hazme un resumen del acta del 25 de febrero de 2025");

        assertThat(out.dates()).contains("25 de febrero de 2025");
        assertThat(out.dates()).doesNotContain("february");
    }

    private static ExecutionContext minimalCtxWithNerEnabled() {
        RagFeatureConfiguration fc = new RagFeatureConfiguration();
        fc.setNerEnabled(true);
        RagConfig cfg = RagConfig.fromFeatureConfiguration(fc, 10, 0.7, "llm", "emb", "clf", "SIMPLE");
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        cfg,
                        CapabilitySet.fromRagConfig(cfg),
                        CompatibilityResult.ok(),
                        ReindexImpact.none(),
                        SystemPromptLayers.empty(),
                        "SYS",
                        new ConfigProvenance(null, null, null, List.of(), null, null),
                        null);

        QueryPlan plan =
                new QueryPlan(
                        QueryPlan.VERSION_P12_MEMORY_CONVERSATIONAL_FLOW_V1,
                        "raw",
                        "planIn",
                        "norm",
                        "rew",
                        "label",
                        Optional.empty(),
                        ClassifierStatus.DISABLED,
                        QueryIntent.UNKNOWN,
                        Map.of(),
                        List.of(),
                        List.of(),
                        EntityExtractionResult.emptyWithNote("n"),
                        StructuredRewriteResult.identityDisabled("norm", null),
                        ExpectedAnswerShape.UNKNOWN,
                        AmbiguityAssessment.sufficient(),
                        "corr",
                        "m",
                        List.of());

        return new ExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "q",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "SYS",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "corr",
                List.of(),
                Optional.empty(),
                Optional.of(plan),
                Optional.empty(),
                "",
                "",
                Optional.empty(),
                null,
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

