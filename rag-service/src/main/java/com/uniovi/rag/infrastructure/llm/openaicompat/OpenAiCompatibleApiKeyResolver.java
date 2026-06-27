package com.uniovi.rag.infrastructure.llm.openaicompat;

import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Resolves OpenAI-compatible API keys from environment variable names configured in {@code rag.llm.openai-compatible.*}.
 * Never logs secret values.
 */
@Component
public class OpenAiCompatibleApiKeyResolver {

    private final Function<String, String> envReader;

    public OpenAiCompatibleApiKeyResolver() {
        this(System::getenv);
    }

    OpenAiCompatibleApiKeyResolver(Function<String, String> envReader) {
        this.envReader = envReader != null ? envReader : System::getenv;
    }

    /**
     * @param envVarName non-blank environment variable name (e.g. {@code OPENAI_COMPATIBLE_API_KEY})
     */
    public String resolve(String envVarName) {
        if (envVarName == null || envVarName.isBlank()) {
            throw OpenAiCompatibleLlmException.missingConfiguration("default-api-key-env is blank");
        }
        String trimmedName = envVarName.trim();
        String value = envReader.apply(trimmedName);
        if (value == null || value.isBlank()) {
            throw OpenAiCompatibleLlmException.missingApiKey(trimmedName);
        }
        return value.trim();
    }
}
