package com.uniovi.rag.application.port;

/**
 * Resolves whether an Ollama model name appears on {@code GET /api/tags} for the configured host.
 */
public interface OllamaModelAvailabilityPort {

    /**
     * @param modelName exact tag name such as {@code gemma3:4b}; blank returns {@code false}
     */
    boolean isModelPresent(String modelName);
}
