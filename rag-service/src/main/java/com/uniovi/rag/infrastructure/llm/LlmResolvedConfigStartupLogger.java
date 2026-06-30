package com.uniovi.rag.infrastructure.llm;

import com.uniovi.rag.application.exception.llm.LlmSafeOperationLogger;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Logs application-default {@link ResolvedLlmConfig} once at startup (safe: no secrets). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class LlmResolvedConfigStartupLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LlmResolvedConfigStartupLogger.class);

    private final ResolvedLlmConfigResolver configResolver;

    public LlmResolvedConfigStartupLogger(ResolvedLlmConfigResolver configResolver) {
        this.configResolver = configResolver;
    }

    @Override
    public void run(ApplicationArguments args) {
        ResolvedLlmConfig config = configResolver.resolve(null, null, null);
        LlmSafeOperationLogger.logResolvedConfig(log, config);
    }
}
