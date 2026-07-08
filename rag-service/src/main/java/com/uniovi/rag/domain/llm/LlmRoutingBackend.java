package com.uniovi.rag.domain.llm;

/** Effective upstream backend for a chat completion request. */
public enum LlmRoutingBackend {
    /** Ollama native API or LiteLLM routing to an Ollama model. */
    OLLAMA,
    /** OpenAI-compatible HTTP API (non-Ollama upstream). */
    OPENAI_COMPATIBLE_API
}
