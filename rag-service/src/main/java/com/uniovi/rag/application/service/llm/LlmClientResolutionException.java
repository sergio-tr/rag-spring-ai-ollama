package com.uniovi.rag.application.service.llm;

import com.uniovi.rag.application.exception.llm.LlmConfigurationException;

/**
 * Raised when effective LLM configuration cannot be mapped to a client implementation.
 * @deprecated Prefer catching {@link LlmConfigurationException} or {@link com.uniovi.rag.application.exception.llm.LlmProviderException}.
 */
@Deprecated
public class LlmClientResolutionException extends LlmConfigurationException {

    public LlmClientResolutionException(String message) {
        super(null, "resolve", null, null, message, null, null);
    }

    public LlmClientResolutionException(String message, Throwable cause) {
        super(null, "resolve", null, null, message, null, cause);
    }
}
