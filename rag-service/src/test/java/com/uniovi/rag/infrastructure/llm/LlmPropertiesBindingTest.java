package com.uniovi.rag.infrastructure.llm;

import com.uniovi.rag.domain.llm.LlmProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class LlmPropertiesBindingTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(LlmPropertiesBindingTest.TestConfig.class);

    @Test
    void bindsOllamaNativeFromProperties() {
        runner.withPropertyValues(
                        "rag.llm.default-provider=OLLAMA_NATIVE",
                        "rag.llm.ollama.default-base-url=http://localhost:11434",
                        "rag.llm.ollama.default-chat-model=llama3.1:8b",
                        "rag.llm.ollama.default-embedding-model=hf.co/mixedbread-ai/mxbai-embed-large-v1:latest",
                        "rag.llm.ollama.default-timeout-ms=45000",
                        "rag.llm.ollama.default-temperature=0.2",
                        "rag.llm.openai-compatible.default-api-key-env=OPENAI_COMPATIBLE_API_KEY")
                .run(context -> {
                    assertThat(context).hasSingleBean(LlmProperties.class);
                    LlmProperties properties = context.getBean(LlmProperties.class);
                    assertThat(properties.getDefaultProvider()).isEqualTo(LlmProvider.OLLAMA_NATIVE);
                    assertThat(properties.getOllama().getDefaultBaseUrl()).isEqualTo("http://localhost:11434");
                    assertThat(properties.getOllama().getDefaultChatModel()).isEqualTo("llama3.1:8b");
                    assertThat(properties.getOllama().getDefaultEmbeddingModel())
                            .isEqualTo("hf.co/mixedbread-ai/mxbai-embed-large-v1:latest");
                    assertThat(properties.getOllama().getDefaultTimeoutMs()).isEqualTo(45_000L);
                    assertThat(properties.getOllama().getDefaultTemperature()).isEqualTo(0.2);
                });
    }

    @Test
    void bindsOpenAiCompatibleKebabCasePrefix() {
        runner.withPropertyValues(
                        "rag.llm.default-provider=OPENAI_COMPATIBLE",
                        "rag.llm.ollama.default-base-url=http://localhost:11434",
                        "rag.llm.ollama.default-chat-model=gemma3:4b",
                        "rag.llm.ollama.default-embedding-model=mxbai-embed-large:latest",
                        "rag.llm.openai-compatible.default-base-url=http://156.35.160.78:4000",
                        "rag.llm.openai-compatible.default-api-key-env=LITELLM_API_KEY",
                        "rag.llm.openai-compatible.default-chat-model=gpt-oss:20b",
                        "rag.llm.openai-compatible.default-embedding-model=qwen3-embedding:8b",
                        "rag.llm.openai-compatible.default-timeout-ms=30000",
                        "rag.llm.openai-compatible.default-temperature=0.15")
                .run(context -> {
                    LlmProperties properties = context.getBean(LlmProperties.class);
                    assertThat(properties.getDefaultProvider()).isEqualTo(LlmProvider.OPENAI_COMPATIBLE);
                    assertThat(properties.getOpenAiCompatible().getDefaultBaseUrl())
                            .isEqualTo("http://156.35.160.78:4000");
                    assertThat(properties.getOpenAiCompatible().getDefaultApiKeyEnv()).isEqualTo("LITELLM_API_KEY");
                    assertThat(properties.getOpenAiCompatible().getDefaultChatModel()).isEqualTo("gpt-oss:20b");
                    assertThat(properties.getOpenAiCompatible().getDefaultEmbeddingModel())
                            .isEqualTo("qwen3-embedding:8b");
                    assertThat(properties.getOpenAiCompatible().getDefaultTimeoutMs()).isEqualTo(30_000L);
                    assertThat(properties.getOpenAiCompatible().getDefaultTemperature()).isEqualTo(0.15);
                });
    }

    @Test
    void bindsOllamaAvailableChatModelsFromProperties() {
        runner.withPropertyValues(
                        "rag.llm.default-provider=OLLAMA_NATIVE",
                        "rag.llm.ollama.default-base-url=http://localhost:11434",
                        "rag.llm.ollama.default-chat-model=fixture-chat",
                        "rag.llm.ollama.default-embedding-model=fixture-embed",
                        "rag.llm.ollama.available-chat-models=fixture-chat-a,fixture-chat-b",
                        "rag.llm.openai-compatible.default-api-key-env=OPENAI_COMPATIBLE_API_KEY")
                .run(context -> {
                    LlmProperties properties = context.getBean(LlmProperties.class);
                    assertThat(properties.getOllama().getAvailableChatModels())
                            .containsExactly("fixture-chat-a", "fixture-chat-b");
                });
    }

    @Test
    void bindsOllamaAvailableEmbeddingModelsFromProperties() {
        runner.withPropertyValues(
                        "rag.llm.default-provider=OLLAMA_NATIVE",
                        "rag.llm.ollama.default-base-url=http://localhost:11434",
                        "rag.llm.ollama.default-chat-model=fixture-chat",
                        "rag.llm.ollama.default-embedding-model=fixture-embed-a",
                        "rag.llm.ollama.available-embedding-models=fixture-embed-a,fixture-embed-b",
                        "rag.llm.openai-compatible.default-api-key-env=OPENAI_COMPATIBLE_API_KEY")
                .run(context -> {
                    LlmProperties properties = context.getBean(LlmProperties.class);
                    assertThat(properties.getOllama().getAvailableEmbeddingModels())
                            .containsExactly("fixture-embed-a", "fixture-embed-b");
                });
    }

    @Test
    void bindsOpenAiCompatibleAvailableChatModelsFromProperties() {
        runner.withPropertyValues(
                        "rag.llm.default-provider=OPENAI_COMPATIBLE",
                        "rag.llm.ollama.default-base-url=http://localhost:11434",
                        "rag.llm.ollama.default-chat-model=fixture-chat",
                        "rag.llm.ollama.default-embedding-model=fixture-embed",
                        "rag.llm.openai-compatible.default-base-url=http://litellm:4000",
                        "rag.llm.openai-compatible.default-api-key-env=OPENAI_COMPATIBLE_API_KEY",
                        "rag.llm.openai-compatible.default-chat-model=litellm-chat-a",
                        "rag.llm.openai-compatible.available-chat-models=litellm-chat-a,litellm-chat-b")
                .run(context -> {
                    LlmProperties properties = context.getBean(LlmProperties.class);
                    assertThat(properties.getOpenAiCompatible().getAvailableChatModels())
                            .containsExactly("litellm-chat-a", "litellm-chat-b");
                });
    }

    @Test
    void bindsOpenAiCompatibleAvailableEmbeddingModelsFromProperties() {
        runner.withPropertyValues(
                        "rag.llm.default-provider=OPENAI_COMPATIBLE",
                        "rag.llm.ollama.default-base-url=http://localhost:11434",
                        "rag.llm.ollama.default-chat-model=fixture-chat",
                        "rag.llm.ollama.default-embedding-model=fixture-embed",
                        "rag.llm.openai-compatible.default-base-url=http://litellm:4000",
                        "rag.llm.openai-compatible.default-api-key-env=OPENAI_COMPATIBLE_API_KEY",
                        "rag.llm.openai-compatible.default-embedding-model=litellm-embed-a",
                        "rag.llm.openai-compatible.available-embedding-models=litellm-embed-a,litellm-embed-b")
                .run(context -> {
                    LlmProperties properties = context.getBean(LlmProperties.class);
                    assertThat(properties.getOpenAiCompatible().getAvailableEmbeddingModels())
                            .containsExactly("litellm-embed-a", "litellm-embed-b");
                });
    }

    @Test
    void preservesModelNamesWithColonSlashAndDots() {
        runner.withPropertyValues(
                        "rag.llm.default-provider=OLLAMA_NATIVE",
                        "rag.llm.ollama.default-base-url=http://localhost:11434",
                        "rag.llm.ollama.default-chat-model=llama3.1:8b",
                        "rag.llm.ollama.default-embedding-model=hf.co/org/model-v1:latest",
                        "rag.llm.ollama.available-chat-models=llama3.1:8b",
                        "rag.llm.ollama.available-embedding-models=hf.co/org/model-v1:latest",
                        "rag.llm.openai-compatible.default-api-key-env=OPENAI_COMPATIBLE_API_KEY")
                .run(context -> {
                    LlmProperties properties = context.getBean(LlmProperties.class);
                    assertThat(properties.getOllama().getAvailableChatModels()).containsExactly("llama3.1:8b");
                    assertThat(properties.getOllama().getAvailableEmbeddingModels())
                            .containsExactly("hf.co/org/model-v1:latest");
                });
    }

    @Test
    void emptyConfiguredModelListDoesNotCreateHardcodedFallbackModels() {
        runner.withPropertyValues(
                        "rag.llm.default-provider=OLLAMA_NATIVE",
                        "rag.llm.ollama.default-base-url=http://localhost:11434",
                        "rag.llm.ollama.default-chat-model=",
                        "rag.llm.ollama.default-embedding-model=",
                        "rag.llm.ollama.available-chat-models=",
                        "rag.llm.ollama.available-embedding-models=",
                        "rag.llm.openai-compatible.default-api-key-env=OPENAI_COMPATIBLE_API_KEY")
                .run(context -> {
                    LlmProperties properties = context.getBean(LlmProperties.class);
                    assertThat(properties.getOllama().getAvailableChatModels()).isEmpty();
                    assertThat(properties.getOllama().getAvailableEmbeddingModels()).isEmpty();
                });
    }

    @Test
    void bindsAvailableModelListsFromCsv() {
        runner.withPropertyValues(
                        "rag.llm.default-provider=OLLAMA_NATIVE",
                        "rag.llm.ollama.default-base-url=http://localhost:11434",
                        "rag.llm.ollama.default-chat-model=gemma3:4b",
                        "rag.llm.ollama.default-embedding-model=mxbai-embed-large:latest",
                        "rag.llm.ollama.available-chat-models=gemma3:4b,llama3.1:8b",
                        "rag.llm.ollama.available-embedding-models=hf.co/mixedbread-ai/mxbai-embed-large-v1:latest",
                        "rag.llm.openai-compatible.default-api-key-env=OPENAI_COMPATIBLE_API_KEY")
                .run(context -> {
                    LlmProperties properties = context.getBean(LlmProperties.class);
                    assertThat(properties.getOllama().getAvailableChatModels())
                            .containsExactly("gemma3:4b", "llama3.1:8b");
                    assertThat(properties.getOllama().getAvailableEmbeddingModels())
                            .containsExactly("hf.co/mixedbread-ai/mxbai-embed-large-v1:latest");
                });
    }

    @Configuration
    @EnableConfigurationProperties(LlmProperties.class)
    static class TestConfig {}
}
