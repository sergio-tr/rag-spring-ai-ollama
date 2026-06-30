package com.uniovi.rag.application.exception.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
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
        ListAppender<ILoggingEvent> warnAppender = attachAppender(logger, ch.qos.logback.classic.Level.WARN);
        ListAppender<ILoggingEvent> debugAppender = attachAppender(logger, ch.qos.logback.classic.Level.DEBUG);

        LlmSafeOperationLogger.logFailed(
                logger,
                "chat",
                LlmProvider.OPENAI_COMPATIBLE,
                "gpt-4o",
                "http://litellm:4000",
                42L,
                "UNAUTHORIZED",
                "LLM credentials rejected (HTTP 401)");

        String warnJoined = joinedMessages(warnAppender, Level.WARN);
        String debugJoined = joinedMessages(debugAppender, Level.DEBUG);
        assertTrue(warnJoined.contains("provider=OPENAI_COMPATIBLE"));
        assertTrue(warnJoined.contains("latencyMs=42"));
        assertFalse(warnJoined.contains("baseUrl"));
        assertFalse(warnJoined.toLowerCase().contains("bearer"));
        assertFalse(warnJoined.contains("sk-secret"));
        assertTrue(debugJoined.contains("baseUrl=http://litellm:4000"));
    }

    @Test
    void baseUrlIsNotLoggedAtInfo() {
        Logger logger = (Logger) LoggerFactory.getLogger(LlmSafeOperationLoggerTest.class);
        ListAppender<ILoggingEvent> infoAppender = attachAppender(logger, ch.qos.logback.classic.Level.INFO);
        ListAppender<ILoggingEvent> debugAppender = attachAppender(logger, ch.qos.logback.classic.Level.DEBUG);

        LlmSafeOperationLogger.logStarted(
                logger, "ner", LlmProvider.OPENAI_COMPATIBLE, "gpt-oss:20b", "http://litellm:4000");

        assertFalse(joinedMessages(infoAppender, Level.INFO).contains("baseUrl"));
        assertTrue(joinedMessages(debugAppender, Level.DEBUG).contains("baseUrl=http://litellm:4000"));
    }

    @Test
    void logResolvedConfig_includesProvidersModelsAndBaseUrlWithoutSecrets() {
        Logger logger = (Logger) LoggerFactory.getLogger(LlmSafeOperationLoggerTest.class);
        ListAppender<ILoggingEvent> infoAppender = attachAppender(logger, ch.qos.logback.classic.Level.INFO);
        ListAppender<ILoggingEvent> debugAppender = attachAppender(logger, ch.qos.logback.classic.Level.DEBUG);

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

        String infoJoined = joinedMessages(infoAppender, Level.INFO);
        String debugJoined = joinedMessages(debugAppender, Level.DEBUG);
        assertTrue(infoJoined.contains("Resolved LLM config:"));
        assertTrue(infoJoined.contains("chatProvider=OPENAI_COMPATIBLE"));
        assertTrue(infoJoined.contains("chatModel=gpt-oss:20b"));
        assertTrue(infoJoined.contains("embeddingProvider=OPENAI_COMPATIBLE"));
        assertTrue(infoJoined.contains("embeddingModel=hf.co/mixedbread-ai/mxbai-embed-large-v1:latest"));
        assertFalse(infoJoined.contains("baseUrl"));
        assertFalse(infoJoined.contains("OPENAI_COMPATIBLE_API_KEY"));
        assertFalse(infoJoined.contains("vault-secret-name"));
        assertTrue(debugJoined.contains("baseUrl=http://litellm:4000"));
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
                        + "embeddingModel=hf.co/mixedbread-ai/mxbai-embed-large-v1:latest",
                LlmSafeOperationLogger.formatResolvedConfigSummary(config));
    }

    @Test
    void sanitizeLogValue_redactsBearerAndSkPrefixedValues() {
        assertEquals("[redacted]", LlmSafeOperationLogger.sanitizeLogValue("Bearer sk-abc"));
        assertEquals("[redacted]", LlmSafeOperationLogger.sanitizeLogValue("sk-project-key"));
    }

    private static ListAppender<ILoggingEvent> attachAppender(Logger logger) {
        return attachAppender(logger, ch.qos.logback.classic.Level.TRACE);
    }

    private static ListAppender<ILoggingEvent> attachAppender(Logger logger, ch.qos.logback.classic.Level level) {
        logger.setLevel(level);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static String joinedMessages(ListAppender<ILoggingEvent> appender) {
        return joinedMessages(appender, null);
    }

    private static String joinedMessages(ListAppender<ILoggingEvent> appender, Level level) {
        return appender.list.stream()
                .filter(e -> level == null || e.getLevel().equals(level))
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", String::concat);
    }
}
