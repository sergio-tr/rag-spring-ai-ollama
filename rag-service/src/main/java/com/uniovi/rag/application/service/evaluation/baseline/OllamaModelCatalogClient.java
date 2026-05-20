package com.uniovi.rag.application.service.evaluation.baseline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Lightweight tag lookup against Ollama {@code GET /api/tags}. Used for baseline runs to mark
 * {@link com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome#MODEL_NOT_AVAILABLE} without aborting the job.
 */
@Component
public class OllamaModelCatalogClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public OllamaModelCatalogClient(
            ObjectMapper objectMapper, @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl) {
        this.objectMapper = objectMapper;
        String trimmed = baseUrl != null ? baseUrl.trim().replaceAll("/+$", "") : "";
        this.baseUrl = trimmed.isEmpty() ? "http://localhost:11434" : trimmed;
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    /**
     * @return {@code false} when the daemon is unreachable, JSON is invalid, or the exact model tag is not listed.
     */
    public boolean isModelAvailable(String modelTag) {
        if (modelTag == null || modelTag.isBlank()) {
            return false;
        }
        try {
            Set<String> names = fetchModelNames();
            String want = modelTag.trim();
            if (names.contains(want)) {
                return true;
            }
            String lower = want.toLowerCase(Locale.ROOT);
            for (String n : names) {
                if (n != null && n.toLowerCase(Locale.ROOT).equals(lower)) {
                    return true;
                }
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private Set<String> fetchModelNames() throws IOException, InterruptedException {
        URI uri = URI.create(baseUrl + "/api/tags");
        HttpRequest req =
                HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT).GET().build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            return Set.of();
        }
        JsonNode root = objectMapper.readTree(res.body());
        JsonNode models = root.get("models");
        Set<String> out = new HashSet<>();
        if (models == null || !models.isArray()) {
            return out;
        }
        for (JsonNode m : models) {
            JsonNode name = m.get("name");
            if (name != null && name.isTextual()) {
                out.add(name.asText());
            }
            JsonNode model = m.get("model");
            if (model != null && model.isTextual()) {
                out.add(model.asText());
            }
        }
        return out;
    }
}
