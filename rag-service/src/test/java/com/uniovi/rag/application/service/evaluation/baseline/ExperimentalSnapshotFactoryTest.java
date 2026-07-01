package com.uniovi.rag.application.service.evaluation.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.evaluation.LabBenchmarkDefaultModelResolver;
import com.uniovi.rag.domain.evaluation.snapshot.EmbeddingExperimentalSnapshot;
import com.uniovi.rag.domain.evaluation.snapshot.ExperimentalSnapshotFieldSource;
import com.uniovi.rag.domain.evaluation.snapshot.LlmExperimentalSnapshot;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExperimentalSnapshotFactoryTest {

    private static final Map<String, Object> OPENAI_PARAMS =
            Map.of(
                    "topP", 0.85,
                    "topK", 7,
                    "seed", 99,
                    "numCtx", 4096,
                    "customFlag", true);

    @Test
    void buildLlmSnapshot_usesResolvedConfigProvidersModelsAndParameters() {
        ResolvedLlmConfigResolver configResolver = mock(ResolvedLlmConfigResolver.class);
        when(configResolver.resolve(isNull(), isNull(), isNull()))
                .thenReturn(openAiConfig("gpt-oss:20b", "text-embedding-3-small"));

        LabBenchmarkDefaultModelResolver defaultModelResolver = new LabBenchmarkDefaultModelResolver(configResolver);
        ExperimentalSnapshotFactory factory = new ExperimentalSnapshotFactory(defaultModelResolver, configResolver, 10, 8192);

        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setLlmModelId("override-model");

        LlmExperimentalSnapshot snap = factory.buildLlmSnapshot(run);

        assertThat(snap.chatProvider()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(snap.embeddingProvider()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(snap.model()).isEqualTo("override-model");
        assertThat(snap.temperature()).isEqualTo(0.35);
        assertThat(snap.timeoutMs()).isEqualTo(30_000);
        assertThat(snap.topP()).isEqualTo(0.85);
        assertThat(snap.topK()).isEqualTo(7);
        assertThat(snap.seed()).isEqualTo(99);
        assertThat(snap.numCtx()).isEqualTo(4096);
        assertThat(snap.additionalParameters()).containsEntry("customFlag", true);
        assertThat(snap.fieldSources())
                .containsEntry("chatProvider", ExperimentalSnapshotFieldSource.RESOLVED_CONFIG.name())
                .containsEntry("embeddingProvider", ExperimentalSnapshotFieldSource.RESOLVED_CONFIG.name())
                .containsEntry("model", ExperimentalSnapshotFieldSource.RUN_OVERRIDE.name())
                .containsEntry("temperature", ExperimentalSnapshotFieldSource.RESOLVED_CONFIG.name());
        assertThat(snap.unsupportedFields()).contains("topK", "numCtx");
        assertThat(snap.repeatPenalty()).isNull();
        assertThat(snap.maxTokens()).isNull();
        assertThat(snap.numPredict()).isNull();
        assertThat(snap.fieldSources().get("repeatPenalty")).isEqualTo(ExperimentalSnapshotFieldSource.UNKNOWN.name());
    }

    @Test
    void buildLlmSnapshot_withoutRunOverride_usesResolvedChatModel() {
        ResolvedLlmConfigResolver configResolver = mock(ResolvedLlmConfigResolver.class);
        when(configResolver.resolve(isNull(), isNull(), isNull()))
                .thenReturn(openAiConfig("gpt-oss:20b", "text-embedding-3-small"));

        LabBenchmarkDefaultModelResolver defaultModelResolver = new LabBenchmarkDefaultModelResolver(configResolver);
        ExperimentalSnapshotFactory factory = new ExperimentalSnapshotFactory(defaultModelResolver, configResolver, 10, 8192);

        LlmExperimentalSnapshot snap = factory.buildLlmSnapshot(null);

        assertThat(snap.model()).isEqualTo("gpt-oss:20b");
        assertThat(snap.fieldSources().get("model")).isEqualTo(ExperimentalSnapshotFieldSource.RESOLVED_CONFIG.name());
    }

    @Test
    void buildLlmSnapshot_applicationDefaultsMarkedWhenParamsAbsent() {
        ResolvedLlmConfigResolver configResolver = mock(ResolvedLlmConfigResolver.class);
        when(configResolver.resolve(isNull(), isNull(), isNull()))
                .thenReturn(
                        ResolvedLlmConfig.uniform(
                                LlmProvider.OLLAMA_NATIVE,
                                "http://localhost:11434",
                                "gemma3:4b",
                                "mxbai-embed-large",
                                null,
                                null,
                                0.2,
                                null,
                                null,
                                Map.of()));

        LabBenchmarkDefaultModelResolver defaultModelResolver = new LabBenchmarkDefaultModelResolver(configResolver);
        ExperimentalSnapshotFactory factory = new ExperimentalSnapshotFactory(defaultModelResolver, configResolver, 42, 2048);

        LlmExperimentalSnapshot snap = factory.buildLlmSnapshot(null);

        assertThat(snap.topK()).isEqualTo(42);
        assertThat(snap.numCtx()).isEqualTo(2048);
        assertThat(snap.fieldSources().get("topK")).isEqualTo(ExperimentalSnapshotFieldSource.APPLICATION_DEFAULT.name());
        assertThat(snap.fieldSources().get("numCtx")).isEqualTo(ExperimentalSnapshotFieldSource.APPLICATION_DEFAULT.name());
        assertThat(snap.temperature()).isEqualTo(0.2);
    }

    @Test
    void buildEmbeddingSnapshot_usesResolvedProvidersAndModel() {
        ResolvedLlmConfigResolver configResolver = mock(ResolvedLlmConfigResolver.class);
        when(configResolver.resolve(isNull(), isNull(), isNull()))
                .thenReturn(openAiConfig("gpt-oss:20b", "mxbai-embed-large:latest"));

        LabBenchmarkDefaultModelResolver defaultModelResolver = new LabBenchmarkDefaultModelResolver(configResolver);
        ExperimentalSnapshotFactory factory = new ExperimentalSnapshotFactory(defaultModelResolver, configResolver, 10, 8192);

        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setEmbeddingModelId("emb-override");
        run.setEmbeddingDimensions(1024);

        EmbeddingExperimentalSnapshot snap = factory.buildEmbeddingSnapshot(run);

        assertThat(snap.chatProvider()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(snap.embeddingProvider()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(snap.model()).isEqualTo("emb-override");
        assertThat(snap.dimension()).isEqualTo(1024);
        assertThat(snap.truncateStrategy()).isNull();
        assertThat(snap.fieldSources())
                .containsEntry("model", ExperimentalSnapshotFieldSource.RUN_OVERRIDE.name())
                .containsEntry("dimension", ExperimentalSnapshotFieldSource.RUN_ENTITY.name())
                .containsEntry("truncateStrategy", ExperimentalSnapshotFieldSource.NOT_APPLIED.name());
        assertThat(snap.unsupportedFields()).contains("normalize", "truncateStrategy");
    }

    @Test
    void buildEmbeddingSnapshot_runEntityModelIds_matchOrchestratorDefaults() {
        ResolvedLlmConfigResolver configResolver = mock(ResolvedLlmConfigResolver.class);
        when(configResolver.resolve(isNull(), isNull(), isNull()))
                .thenReturn(openAiConfig("gpt-oss:20b", "mxbai-embed-large:latest"));

        LabBenchmarkDefaultModelResolver defaultModelResolver = new LabBenchmarkDefaultModelResolver(configResolver);
        ExperimentalSnapshotFactory factory = new ExperimentalSnapshotFactory(defaultModelResolver, configResolver, 10, 8192);

        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setLlmModelId(defaultModelResolver.resolveLlmModelId(null, null));
        run.setEmbeddingModelId(defaultModelResolver.resolveEmbeddingModelId(null, null));

        assertThat(factory.buildLlmSnapshot(run).model()).isEqualTo("gpt-oss:20b");
        assertThat(factory.buildEmbeddingSnapshot(run).model()).isEqualTo("mxbai-embed-large:latest");
    }

    private static ResolvedLlmConfig openAiConfig(String chatModel, String embeddingModel) {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OPENAI_COMPATIBLE,
                "http://litellm:4000",
                chatModel,
                embeddingModel,
                "LITELLM_API_KEY",
                null,
                0.35,
                30_000,
                null,
                OPENAI_PARAMS);
    }
}
