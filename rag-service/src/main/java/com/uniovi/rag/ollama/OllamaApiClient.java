package com.uniovi.rag.ollama;

import com.uniovi.rag.health.RagHealthProperties;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

/**
 * Minimal client for Ollama {@code /api/tags} and {@code /api/pull} (any reachable host).
 */
@Component
public class OllamaApiClient {

    private final String baseUrl;
    private final RagHealthProperties healthProperties;
    private final HttpClient httpClient;

    public OllamaApiClient(
            @Value("${spring.ai.ollama.base-url}") String baseUrl,
            RagHealthProperties healthProperties) {
        this.baseUrl = OllamaUrlUtils.stripTrailingSlash(baseUrl);
        this.healthProperties = healthProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(500, healthProperties.getConnectTimeoutMs())))
                .build();
    }

    public Set<String> listModelNames() throws IOException, InterruptedException {
        String tagsUrl = baseUrl + "/api/tags";
        HttpRequest request = HttpRequest.newBuilder(URI.create(tagsUrl))
                .GET()
                .timeout(Duration.ofMillis(Math.max(500, healthProperties.getReadTimeoutMs())))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GET /api/tags failed: HTTP " + response.statusCode() + " body=" + response.body());
        }
        return OllamaTagsParser.parseModelNames(response.body());
    }

    /**
     * Checks that Ollama responds at {@code baseUrl} (same endpoint as the CLI: {@code GET /api/tags}).
     */
    public boolean ping() throws IOException, InterruptedException {
        String tagsUrl = baseUrl + "/api/tags";
        HttpRequest request = HttpRequest.newBuilder(URI.create(tagsUrl))
                .GET()
                .timeout(Duration.ofMillis(Math.max(500, healthProperties.getReadTimeoutMs())))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    /**
     * Pulls a model if not present (same operation as {@code ollama pull} in the CLI).
     *
     * @param modelName exact name (e.g. {@code gemma3:4b})
     * @param pullReadTimeoutMs read timeout for the full pull response
     */
    public void pullModel(String modelName, long pullReadTimeoutMs) throws IOException, InterruptedException {
        String pullUrl = baseUrl + "/api/pull";
        JSONObject body = new JSONObject();
        body.put("name", modelName);
        body.put("stream", false);

        HttpRequest request = HttpRequest.newBuilder(URI.create(pullUrl))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(Math.max(5_000L, pullReadTimeoutMs)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("POST /api/pull failed for model '" + modelName + "': HTTP "
                    + response.statusCode() + " body=" + truncate(response.body(), 4000));
        }
        String respBody = response.body();
        if (respBody != null && !respBody.isBlank()) {
            try {
                JSONObject o = new JSONObject(respBody);
                if (o.has("error") && !o.isNull("error")) {
                    String err = o.optString("error", "");
                    if (!err.isEmpty()) {
                        throw new IOException("Ollama pull error for '" + modelName + "': " + err);
                    }
                }
            } catch (Exception e) {
                if (e instanceof IOException ioexception) {
                    throw ioexception;
                }
                // Non-JSON body; Ollama sometimes returns line-oriented output; if HTTP 200, assume OK
            }
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
