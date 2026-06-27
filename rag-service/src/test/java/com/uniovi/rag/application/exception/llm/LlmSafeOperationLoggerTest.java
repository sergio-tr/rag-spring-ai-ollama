package com.uniovi.rag.application.exception.llm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.uniovi.rag.domain.llm.LlmProvider;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LlmSafeOperationLoggerTest {

    @Test
    void logFailed_neverIncludesApiKeyLikeValues() {
        Logger logger = (Logger) LoggerFactory.getLogger(LlmSafeOperationLoggerTest.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        LlmSafeOperationLogger.logFailed(
                logger,
                "chat",
                LlmProvider.OPENAI_COMPATIBLE,
                "gpt-4o",
                "http://litellm:4000",
                42L,
                "UNAUTHORIZED",
                "LLM credentials rejected (HTTP 401)");

        String joined =
                appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
        assertTrue(joined.contains("provider=OPENAI_COMPATIBLE"));
        assertTrue(joined.contains("latencyMs=42"));
        assertFalse(joined.toLowerCase().contains("bearer"));
        assertFalse(joined.toLowerCase().contains("authorization"));
        assertFalse(joined.contains("sk-secret"));
    }
}
