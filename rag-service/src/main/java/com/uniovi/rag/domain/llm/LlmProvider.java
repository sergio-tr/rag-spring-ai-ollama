package com.uniovi.rag.domain.llm;

/**
 * Application-default LLM backend kind. Per-user selection is resolved via {@code ResolvedLlmConfigResolver}
 * and {@code LlmClientResolver}.
 */
public enum LlmProvider {
    OLLAMA_NATIVE,
    OPENAI_COMPATIBLE
}
