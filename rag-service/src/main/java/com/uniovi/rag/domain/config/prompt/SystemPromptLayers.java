package com.uniovi.rag.domain.config.prompt;

/**
 * Four ADR-0008 layers before composition into the effective system prompt.
 */
public record SystemPromptLayers(String base, String account, String project, String presetWorkflow) {

    public static SystemPromptLayers empty() {
        return new SystemPromptLayers("", "", "", "");
    }
}
