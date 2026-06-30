package com.uniovi.rag.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.evaluation.EvaluationJudgePromptSources;
import com.uniovi.rag.application.service.runtime.RuntimeAnswerPromptResolver;
import com.uniovi.rag.application.service.runtime.query.expand.MinuteDocumentStructureExpander;
import com.uniovi.rag.domain.config.prompt.ConfigurablePromptGroup;
import com.uniovi.rag.domain.config.prompt.MetadataConfigurablePromptSources;
import com.uniovi.rag.domain.config.prompt.PromptOverrideKeys;
import static org.mockito.Mockito.when;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.testsupport.config.TestConfigurablePromptResolver;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies main RAG prompt groups resolve overrides at runtime (not catalog-only). */
class PromptRuntimeWiringTest {

    private static final UUID USER = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID PROJECT = UUID.fromString("00000000-0000-4000-8000-000000000002");

    @Test
    void answerPromptResolver_usesCustomSynthesisAndGrounding() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(
                PromptOverrideKeys.OVERRIDES_MAP_KEY,
                Map.of(
                        "answer_synthesis",
                        "SYNTH %s <Question> %s </Question> <Context> %s </Context>",
                        "source_grounding",
                        "GROUNDING_BLOCK"));
        ConfigurablePromptResolver resolver = TestConfigurablePromptResolver.withOverrides(values);
        RuntimeAnswerPromptResolver answerResolver = new RuntimeAnswerPromptResolver(resolver);

        ExecutionContext ctx = executionContext();
        String user =
                answerResolver.ragUserTurn(
                        ctx, "q?", "ctx", null, true, java.util.Optional.empty(), "plan");

        assertThat(user).contains("SYNTH");
        assertThat(user).contains("GROUNDING_BLOCK");
        assertThat(user).contains("q?");
        assertThat(user).contains("ctx");
    }

    @Test
    void judgeRetryPolicy_resolvesFromOverride() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(
                PromptOverrideKeys.OVERRIDES_MAP_KEY,
                Map.of(
                        "runtime_judge_retry",
                        "If bad, output RETRY_REQUESTED.\nIf bad, output REJECTED_NO_RETRY."));
        ConfigurablePromptResolver resolver = TestConfigurablePromptResolver.withOverrides(values);
        ExecutionContext ctx = executionContext();
        String allowed =
                ConfigurablePromptRuntimeSupport.retryPolicyLine(resolver, ctx, true);
        assertThat(allowed).contains("RETRY_REQUESTED");
    }

    @Test
    void queryExpansion_defaultMatchesCatalog() {
        assertThat(MinuteDocumentStructureExpander.defaultExpansionPrompt())
                .isEqualTo(ConfigurablePromptGroup.QUERY_EXPANSION.defaultContent());
    }

    @Test
    void evaluationJudgeTemplate_usesResolvableDefault() {
        assertThat(ConfigurablePromptGroup.EVALUATION_JUDGE.defaultContent())
                .isEqualTo(EvaluationJudgePromptSources.defaultTemplate());
    }

    @Test
    void metadataDefaults_matchConfigurableSources() {
        assertThat(ConfigurablePromptGroup.METADATA_FILTER_AND_LIST.defaultContent())
                .isEqualTo(MetadataConfigurablePromptSources.FILTER_AND_LIST);
        assertThat(ConfigurablePromptGroup.METADATA_BOOLEAN_QUERY.defaultContent())
                .isEqualTo(MetadataConfigurablePromptSources.BOOLEAN_QUERY);
    }

    @Test
    void runtimeJudgeRetry_defaultContainsPolicyMarkers() {
        assertThat(ConfigurablePromptGroup.RUNTIME_JUDGE_RETRY.defaultContent())
                .contains("RETRY_REQUESTED");
        assertThat(ConfigurablePromptGroup.RUNTIME_JUDGE_RETRY.defaultContent())
                .contains("REJECTED_NO_RETRY");
    }

    @Test
    void functionCallingUserAssembly_isCatalogReadOnly() {
        assertThat(ConfigurablePromptGroup.FUNCTION_CALLING_USER_ASSEMBLY.runtimeEditable()).isFalse();
        assertThat(ConfigurablePromptGroup.FUNCTION_CALLING_USER_ASSEMBLY.defaultContent())
                .contains("FunctionCallingPrompts");
    }

    private static ExecutionContext executionContext() {
        ExecutionContext ctx = org.mockito.Mockito.mock(ExecutionContext.class);
        when(ctx.userId()).thenReturn(USER);
        when(ctx.projectId()).thenReturn(PROJECT);
        return ctx;
    }
}
