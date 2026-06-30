package com.uniovi.rag.application.service.evaluation.provenance;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.evaluation.snapshot.PromptProfileSnapshot;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvaluationProvenanceSupportTest {

    @Test
    void build_includesProvidersPromptHashAndBuildMetadata() {
        ResolvedLlmConfig config =
                ResolvedLlmConfig.uniform(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "http://litellm:4000",
                        "gpt-oss:20b",
                        "text-embedding-3-small",
                        "KEY",
                        null,
                        0.2,
                        null,
                        null,
                        Map.of());
        PromptProfileSnapshot prompts =
                new PromptProfileSnapshot(
                        "baseline-v1",
                        "base",
                        "project",
                        "chat",
                        "retrieval",
                        "format",
                        "effective",
                        "abc123hash");
        EvaluationBuildMetadata buildMetadata = EvaluationBuildMetadata.of("deadbeef", "ci-99");

        Map<String, Object> provenance = EvaluationProvenanceSupport.build(config, prompts, buildMetadata);

        assertThat(provenance)
                .containsEntry(EvaluationProvenanceKeys.CHAT_PROVIDER, "OPENAI_COMPATIBLE")
                .containsEntry(EvaluationProvenanceKeys.EMBEDDING_PROVIDER, "OPENAI_COMPATIBLE")
                .containsEntry(EvaluationProvenanceKeys.PROMPT_PROFILE_VERSION, "baseline-v1")
                .containsEntry(EvaluationProvenanceKeys.EFFECTIVE_SYSTEM_PROMPT_SHA256, "abc123hash")
                .containsEntry(EvaluationProvenanceKeys.GIT_SHA, "deadbeef")
                .containsEntry(EvaluationProvenanceKeys.BUILD_ID, "ci-99")
                .containsEntry(EvaluationProvenanceKeys.ENVIRONMENT_LABEL, EvaluationBuildMetadata.UNKNOWN)
                .containsKey(EvaluationProvenanceKeys.PROMPT_BUNDLE_SHA256)
                .containsKey(EvaluationProvenanceKeys.PROMPT_BUNDLE_INCLUDED_GROUPS);
        assertThat(provenance.toString()).doesNotContain("Act as an expert evaluator");
    }

    @Test
    void withExportDefaults_fillsUnknownWhenMissing() {
        Map<String, Object> enriched =
                EvaluationProvenanceSupport.withExportDefaults(Map.of(EvaluationProvenanceKeys.GIT_SHA, "abc"));
        assertThat(enriched)
                .containsEntry(EvaluationProvenanceKeys.GIT_SHA, "abc")
                .containsEntry(EvaluationProvenanceKeys.BUILD_ID, EvaluationBuildMetadata.UNKNOWN)
                .containsEntry(EvaluationProvenanceKeys.ENVIRONMENT_LABEL, EvaluationBuildMetadata.UNKNOWN);
    }

    @Test
    void enrichMetricsFromRun_addsProvidersWhenMissingOnItem() {
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setAggregatesJson(
                Map.of(
                        EvaluationProvenanceKeys.AGGREGATES_KEY,
                        Map.of(
                                EvaluationProvenanceKeys.CHAT_PROVIDER,
                                "OPENAI_COMPATIBLE",
                                EvaluationProvenanceKeys.EMBEDDING_PROVIDER,
                                "OPENAI_COMPATIBLE")));

        Map<String, Object> metrics = new LinkedHashMap<>();
        EvaluationProvenanceSupport.enrichMetricsFromRun(metrics, run);

        assertThat(metrics)
                .containsEntry(EvaluationProvenanceKeys.CHAT_PROVIDER, "OPENAI_COMPATIBLE")
                .containsEntry(EvaluationProvenanceKeys.EMBEDDING_PROVIDER, "OPENAI_COMPATIBLE");
    }

    @Test
    void mergeLabMetrics_and_providerMetricsFromConfig() {
        assertThat(EvaluationProvenanceSupport.providerMetricsFromConfig(null)).isEmpty();
        assertThat(
                        EvaluationProvenanceSupport.mergeLabMetrics(Map.of("a", 1), Map.of("chatProvider", "X")))
                .containsEntry("a", 1)
                .containsEntry("chatProvider", "X");
    }
}
