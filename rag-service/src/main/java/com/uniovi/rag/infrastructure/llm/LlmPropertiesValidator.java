package com.uniovi.rag.infrastructure.llm;

import org.springframework.stereotype.Component;

/**
 * Fails fast on invalid {@link LlmProperties} at application startup.
 */
@Component
public class LlmPropertiesValidator {

    public LlmPropertiesValidator(LlmProperties llmProperties) {
        llmProperties.validate();
    }
}
