package com.uniovi.rag.infrastructure.llm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.exception.llm.LlmSafeOperationLogger;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class LlmResolvedConfigStartupLoggerTest {

    @Mock private ResolvedLlmConfigResolver configResolver;

    @Test
    void startupRunnerResolvesApplicationDefaults() {
        ResolvedLlmConfig config =
                ResolvedLlmConfig.uniform(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "http://litellm:4000",
                        "gpt-oss:20b",
                        "hf.co/mixedbread-ai/mxbai-embed-large-v1:latest",
                        "OPENAI_COMPATIBLE_API_KEY",
                        null,
                        0.1,
                        60_000,
                        null,
                        Map.of());
        when(configResolver.resolve(null, null, null)).thenReturn(config);

        LlmResolvedConfigStartupLogger runner = new LlmResolvedConfigStartupLogger(configResolver);
        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(configResolver).resolve(null, null, null);
    }

    @Test
    void formatResolvedConfigSummary_isUsedForStartupLogging() {
        ResolvedLlmConfig config =
                ResolvedLlmConfig.uniform(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "http://litellm:4000",
                        "gpt-oss:20b",
                        "hf.co/mixedbread-ai/mxbai-embed-large-v1:latest",
                        "OPENAI_COMPATIBLE_API_KEY",
                        null,
                        0.1,
                        60_000,
                        null,
                        Map.of());

        String summary = LlmSafeOperationLogger.formatResolvedConfigSummary(config);

        assertTrue(summary.contains("chatProvider=OPENAI_COMPATIBLE"));
        assertFalse(summary.contains("OPENAI_COMPATIBLE_API_KEY"));
    }
}
