package com.uniovi.rag.application.exception.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LlmSafeOperationLoggerTest {

    @Test
    void logFailed_neverIncludesApiKeyLikeValues() {
        Logger logger = (Logger) LoggerFactory.getLogger(LlmSafeOperationLoggerTest.class);
        ListAppender<ILoggingEvent> appender = attachAppender(logger);

        LlmSafeOperationLogger.logFailed(
                logger,
                "chat",
                LlmProvider.OPENAI_COMPATIBLE,
                "gpt-4o",
                "http://litellm:4000",
                42L,
                "UNAUTHORIZED",
                "LLM credentials rejected (HTTP 401)");

        String joined = joinedMessages(appender);
        assertTrue(joined.contains("provider=OPENAI_COMPATIBLE"));
        assertTrue(joined.contains("latencyMs=42"));
        assertFalse(joined.toLowerCase().contains("bearer"));
        assertFalse(joined.toLowerCase().contains("authorization"));
        assertFalse(joined.contains("sk-secret"));
    }

    @Test
    void logResolvedConfig_includesProvidersModelsAndBaseUrlWithoutSecrets() {
        Logger logger = (Logger) LoggerFactory.getLogger(LlmSafeOperationLoggerTest.class);
        ListAppender<ILoggingEvent> appender = attachAppender(logger);

        ResolvedLlmConfig config =
                ResolvedLlmConfig.uniform(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "http://litellm:4000",
                        "gpt-oss:20b",
                        "hf.co/mixedbread-ai/mxbai-embed-large-v1:latest",
                        "OPENAI_COMPATIBLE_API_KEY",
                        "vault-secret-name",
                        0.1,
                        60_000,
                        null,
                        Map.of("Authorization", "Bearer sk-live-secret"));

        LlmSafeOperationLogger.logResolvedConfig(logger, config);

        String joined = joinedMessages(appender);
        assertTrue(joined.contains("Resolved LLM config:"));
        assertTrue(joined.contains("chatProvider=OPENAI_COMPATIBLE"));
        assertTrue(joined.contains("chatModel=gpt-oss:20b"));
        assertTrue(joined.contains("embeddingProvider=OPENAI_COMPATIBLE"));
        assertTrue(joined.contains("embeddingModel=hf.co/mixedbread-ai/mxbai-embed-large-v1:latest"));
        assertTrue(joined.contains("baseUrl=http://litellm:4000"));
        assertFalse(joined.contains("OPENAI_COMPATIBLE_API_KEY"));
        assertFalse(joined.contains("vault-secret-name"));
        assertFalse(joined.toLowerCase().contains("bearer"));
        assertFalse(joined.contains("sk-live-secret"));
    }

    @Test
    void formatResolvedConfigSummary_matchesExpectedOpenAiCompatibleLine() {
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

        assertEquals(
                "Resolved LLM config: chatProvider=OPENAI_COMPATIBLE chatModel=gpt-oss:20b "
                        + "embeddingProvider=OPENAI_COMPATIBLE "
                        + "embeddingModel=hf.co/mixedbread-ai/mxbai-embed-large-v1:latest "
                        + "baseUrl=http://litellm:4000",
                LlmSafeOperationLogger.formatResolvedConfigSummary(config));
    }

    @Test
    void sanitizeLogValue_redactsBearerAndSkPrefixedValues() {
        assertEquals("[redacted]", LlmSafeOperationLogger.sanitizeLogValue("Bearer sk-abc"));
        assertEquals("[redacted]", LlmSafeOperationLogger.sanitizeLogValue("sk-project-key"));
    }

    private static ListAppender<ILoggingEvent> attachAppender(Logger logger) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static String joinedMessages(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
    }
}
