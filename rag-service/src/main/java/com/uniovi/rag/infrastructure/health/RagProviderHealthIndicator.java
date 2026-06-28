package com.uniovi.rag.infrastructure.health;

import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import com.uniovi.rag.infrastructure.llm.openaicompat.OpenAiCompatibleEmbeddingHealthProbe;
import com.uniovi.rag.infrastructure.llm.openaicompat.OpenAiCompatibleLlmChatClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

/**
 * Unified readiness probe for the effective LLM stack: OpenAI-compatible {@code /v1/chat/completions} and
 * {@code /v1/embeddings} when configured, or Ollama {@code /api/tags} when native providers are active.
 */
@Component("ragProvider")
public class RagProviderHealthIndicator implements HealthIndicator {

    private final RagHealthProperties healthProperties;
    private final LlmProperties llmProperties;
    private final OpenAiCompatibleLlmChatClient openAiChatClient;
    private final OpenAiCompatibleEmbeddingHealthProbe openAiEmbeddingHealthProbe;
    private final OllamaHealthIndicator ollamaHealthIndicator;

    public RagProviderHealthIndicator(
            RagHealthProperties healthProperties,
            LlmProperties llmProperties,
            OpenAiCompatibleLlmChatClient openAiChatClient,
            OpenAiCompatibleEmbeddingHealthProbe openAiEmbeddingHealthProbe,
            OllamaHealthIndicator ollamaHealthIndicator) {
        this.healthProperties = healthProperties;
        this.llmProperties = llmProperties;
        this.openAiChatClient = openAiChatClient;
        this.openAiEmbeddingHealthProbe = openAiEmbeddingHealthProbe;
        this.ollamaHealthIndicator = ollamaHealthIndicator;
    }

    @Override
    public Health health() {
        LlmProvider chatProvider = llmProperties.getEffectiveDefaultChatProvider();
        LlmProvider embeddingProvider = llmProperties.getEffectiveDefaultEmbeddingProvider();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("chatProvider", chatProvider.name());
        details.put("embeddingProvider", embeddingProvider.name());

        List<String> failures = new ArrayList<>();

        if (chatProvider == LlmProvider.OPENAI_COMPATIBLE) {
            probeOpenAiChat(details, failures);
        }
        if (embeddingProvider == LlmProvider.OPENAI_COMPATIBLE) {
            probeOpenAiEmbeddings(details, failures);
        }

        boolean needsOllama =
                chatProvider == LlmProvider.OLLAMA_NATIVE || embeddingProvider == LlmProvider.OLLAMA_NATIVE;
        if (needsOllama) {
            Health ollama = ollamaHealthIndicator.health();
            details.put("ollama", ollama.getDetails());
            if (!Status.UP.equals(ollama.getStatus())) {
                failures.add("ollama: " + ollama.getStatus());
            }
        }

        if (failures.isEmpty()) {
            return Health.up().withDetails(details).build();
        }

        boolean chatOnlyDegraded =
                healthProperties.isChatOnlyMode()
                        && chatProvider == LlmProvider.OPENAI_COMPATIBLE
                        && embeddingProvider == LlmProvider.OPENAI_COMPATIBLE
                        && failures.stream().allMatch(f -> f.startsWith("embeddings:"))
                        && !failures.stream().anyMatch(f -> f.startsWith("chat:"));

        if (chatOnlyDegraded) {
            details.put("mode", "degraded-chat-only");
            details.put("embeddingCheck", "failed-but-ignored");
            return Health.up().withDetails(details).build();
        }

        return Health.down().withDetails(details).withDetail("failures", failures).build();
    }

    private void probeOpenAiChat(Map<String, Object> details, List<String> failures) {
        try {
            boolean ok = openAiChatClient.healthCheckViaChatCompletion();
            details.put("openAiChat", ok ? "up" : "down");
            if (!ok) {
                failures.add("chat: empty response from /v1/chat/completions");
            }
        } catch (Exception e) {
            details.put("openAiChat", "down");
            details.put("openAiChatError", e.getMessage());
            failures.add("chat: " + e.getMessage());
        }
    }

    private void probeOpenAiEmbeddings(Map<String, Object> details, List<String> failures) {
        try {
            boolean ok = openAiEmbeddingHealthProbe.healthCheckViaEmbeddings(healthProperties.getReadTimeoutMs());
            details.put("openAiEmbeddings", ok ? "up" : "down");
            if (!ok) {
                failures.add("embeddings: empty response from /v1/embeddings");
            }
        } catch (Exception e) {
            details.put("openAiEmbeddings", "down");
            details.put("openAiEmbeddingsError", e.getMessage());
            failures.add("embeddings: " + e.getMessage());
        }
    }
}
