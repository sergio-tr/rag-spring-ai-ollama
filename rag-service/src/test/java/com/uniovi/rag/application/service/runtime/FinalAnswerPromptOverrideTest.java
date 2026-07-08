package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.config.ConfigurablePromptResolver;
import com.uniovi.rag.domain.config.prompt.PromptOverrideKeys;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.policy.AnswerGroundingPolicy;
import com.uniovi.rag.testsupport.config.TestConfigurablePromptResolver;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class FinalAnswerPromptOverrideTest {

    private static final String USER_PROMPT_MARKER_12345 = "USER_PROMPT_MARKER_12345";

    @Test
    void runtimeAnswerPromptResolver_usesConfigurableAnswerSynthesisOverride() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(
                PromptOverrideKeys.OVERRIDES_MAP_KEY,
                Map.of(
                        "answer_synthesis",
                        "SYNTH " + USER_PROMPT_MARKER_12345 + " %s <Question> %s </Question> <Context> %s </Context>",
                        "source_grounding",
                        "GROUNDING_BLOCK"));
        ConfigurablePromptResolver promptResolver = TestConfigurablePromptResolver.withOverrides(values);
        RuntimeAnswerPromptResolver resolver = new RuntimeAnswerPromptResolver(promptResolver);

        ExecutionContext ctx = Mockito.mock(ExecutionContext.class);
        when(ctx.userId()).thenReturn(UUID.randomUUID());
        when(ctx.projectId()).thenReturn(UUID.randomUUID());

        String userTurn =
                resolver.ragUserTurn(
                        ctx,
                        "What is RAG?",
                        "context block",
                        AnswerGroundingPolicy.ATTEMPT_WITH_CONTEXT,
                        true,
                        Optional.empty(),
                        "");

        assertTrue(userTurn.contains(USER_PROMPT_MARKER_12345));
        assertTrue(userTurn.contains("What is RAG?"));
        assertTrue(userTurn.contains("context block"));
    }
}
