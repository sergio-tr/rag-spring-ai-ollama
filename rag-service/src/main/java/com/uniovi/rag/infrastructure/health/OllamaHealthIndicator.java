package com.uniovi.rag.infrastructure.health;

import com.uniovi.rag.infrastructure.llm.ollama.OllamaTagsParser;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaUrlUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * Verifies reachability of the configured Ollama API and (optionally) that chat + embedding models exist.
 */
@Component("ollama")
public class OllamaHealthIndicator implements HealthIndicator {

    private final RagHealthProperties healthProperties;
    private final String baseUrl;
    private final String chatModel;
    private final String embeddingModel;
    private final HttpClient httpClient;

    public OllamaHealthIndicator(
            RagHealthProperties healthProperties,
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${spring.ai.ollama.chat.model:gemma3:4b}") String chatModel,
            @Value("${spring.ai.ollama.embedding.model:mxbai-embed-large:latest}") String embeddingModel) {
        this.healthProperties = healthProperties;
        this.baseUrl = OllamaUrlUtils.stripTrailingSlash(baseUrl);
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(500, healthProperties.getConnectTimeoutMs())))
                .build();
    }

    @Override
    public Health health() {
        if (!healthProperties.isOllamaEnabled()) {
            return Health.up()
                    .withDetail("check", "skipped")
                    .withDetail("reason", "rag.health.ollama-enabled=false")
                    .build();
        }

        String tagsUrl = this.baseUrl + "/api/tags";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(tagsUrl))
                    .GET()
                    .timeout(Duration.ofMillis(Math.max(500, healthProperties.getReadTimeoutMs())))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Health.down()
                        .withDetail("url", tagsUrl)
                        .withDetail("status", response.statusCode())
                        .build();
            }
            if (!healthProperties.isOllamaVerifyModels()) {
                return Health.up()
                        .withDetail("url", tagsUrl)
                        .withDetail("modelsVerified", false)
                        .build();
            }
            Set<String> names = OllamaTagsParser.parseModelNames(response.body());
            Set<String> missing = new HashSet<>();
            if (!names.contains(chatModel)) {
                missing.add(chatModel);
            }
            if (!names.contains(embeddingModel)) {
                missing.add(embeddingModel);
            }
            if (!missing.isEmpty()) {
                return Health.down()
                        .withDetail("url", tagsUrl)
                        .withDetail("missingModels", missing)
                        .withDetail("hint", "The backend can pull them with rag.ollama.auto-pull-enabled=true, or: docker exec -it ollama ollama pull <model>")
                        .build();
            }
            return Health.up()
                    .withDetail("url", tagsUrl)
                    .withDetail("chatModel", chatModel)
                    .withDetail("embeddingModel", embeddingModel)
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Health.down(e)
                    .withDetail("url", tagsUrl)
                    .build();
        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("url", tagsUrl)
                    .build();
        }
    }

}
