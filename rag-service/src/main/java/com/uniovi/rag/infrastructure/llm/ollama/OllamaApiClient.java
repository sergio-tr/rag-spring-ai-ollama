package com.uniovi.rag.infrastructure.llm.ollama;

import com.uniovi.rag.infrastructure.health.RagHealthProperties;
import org.json.JSONObject;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * Minimal client for Ollama {@code /api/tags} and {@code /api/pull} (any reachable host).
 */
@Component
@Profile("!test")
public class OllamaApiClient {

    private static final String JSON_FIELD_ERROR = "error";

    private final String baseUrl;
    private final RagHealthProperties healthProperties;
    private final HttpClient httpClient;

    /** Selector for {@link #noHttpStub(RagHealthProperties)}; avoids a second (String, RagHealthProperties) constructor. */
    private enum InternalNoHttpStub {
        INSTANCE
    }

    /**
     * Subclasses used only by {@link #noHttpStub(RagHealthProperties)}; {@code httpClient} stays null (overrides do not use it).
     */
    private OllamaApiClient(InternalNoHttpStub ignored, RagHealthProperties healthProperties) {
        Objects.requireNonNull(ignored, "stub discriminator");
        this.baseUrl = OllamaUrlUtils.stripTrailingSlash("http://127.0.0.1:1");
        this.healthProperties = healthProperties;
        this.httpClient = null;
    }

    @Autowired
    public OllamaApiClient(
            @Value("${spring.ai.ollama.base-url}") String baseUrl,
            RagHealthProperties healthProperties) {
        this.baseUrl = OllamaUrlUtils.stripTrailingSlash(baseUrl);
        this.healthProperties = healthProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(500, healthProperties.getConnectTimeoutMs())))
                .build();
    }

    /**
     * Stub for Spring tests (profile {@code test}): no HTTP; avoids Mockito concrete-class mocking on CI JDKs.
     */
    public static OllamaApiClient noHttpStub(RagHealthProperties healthProperties) {
        return new OllamaApiClient(InternalNoHttpStub.INSTANCE, healthProperties) {
            @Override
            public Set<String> listModelNames() {
                return Set.of();
            }

            @Override
            public boolean ping() {
                return true;
            }

            @Override
            public void pullModel(String modelName, long pullReadTimeoutMs) {
                // no-op
            }

            @Override
            public boolean probeEmbedding(String modelName, String text, long readTimeoutMs) {
                return true;
            }
        };
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
                if (o.has(JSON_FIELD_ERROR) && !o.isNull(JSON_FIELD_ERROR)) {
                    String err = o.optString(JSON_FIELD_ERROR, "");
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

    /**
     * Minimal embedding probe: POST {@code /api/embeddings} with a short prompt and checks that Ollama returns an embedding array.
     *
     * <p>Note: this does not validate model "quality", only that the endpoint accepts the model as an embedding-capable one.
     */
    public boolean probeEmbedding(String modelName, String text, long readTimeoutMs) throws IOException, InterruptedException {
        String url = baseUrl + "/api/embeddings";
        JSONObject body = new JSONObject();
        body.put("model", modelName);
        body.put("prompt", text != null ? text : "ping");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(Math.max(1_000L, readTimeoutMs)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return false;
        }
        String respBody = response.body();
        if (respBody == null || respBody.isBlank()) {
            return false;
        }
        try {
            JSONObject o = new JSONObject(respBody);
            return o.has("embedding") && o.get("embedding") instanceof JSONArray;
        } catch (Exception e) {
            return false;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
