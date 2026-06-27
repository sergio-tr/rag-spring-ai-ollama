package com.uniovi.rag.domain.llm;

/** JSON keys for per-layer LLM configuration stored in {@code rag_configuration.values}, presets, and runtime overrides. */
public final class LlmConfigurationKeys {

    public static final String PROVIDER = "llmProvider";
    public static final String BASE_URL = "llmBaseUrl";
    /** Aligned with existing {@code RagConfig#llmModel()} key. */
    public static final String CHAT_MODEL = "llmModel";
    /** Aligned with existing {@code RagConfig#embeddingModel()} key. */
    public static final String EMBEDDING_MODEL = "embeddingModel";
    public static final String API_KEY_ENV = "llmApiKeyEnv";
    public static final String SECRET_NAME = "llmSecretName";
    public static final String TEMPERATURE = "llmTemperature";
    public static final String TIMEOUT_MS = "llmTimeoutMs";
    public static final String SYSTEM_PROMPT = "llmSystemPrompt";
    public static final String ADDITIONAL_PARAMETERS = "llmAdditionalParameters";

    private LlmConfigurationKeys() {}
}
