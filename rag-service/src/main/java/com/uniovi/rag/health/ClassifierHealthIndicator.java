package com.uniovi.rag.health;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Verifies the Python classifier HTTP API and that the default model is loaded (required for queries).
 */
@Component("classifier")
public class ClassifierHealthIndicator implements HealthIndicator {

    private final RagHealthProperties healthProperties;
    private final String classifierBaseUrl;
    private final HttpClient httpClient;

    public ClassifierHealthIndicator(
            RagHealthProperties healthProperties,
            @Value("${rag.classifier.service.url}") String classifierBaseUrl) {
        this.healthProperties = healthProperties;
        this.classifierBaseUrl = stripTrailingSlash(classifierBaseUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(500, healthProperties.getConnectTimeoutMs())))
                .build();
    }

    @Override
    public Health health() {
        if (!healthProperties.isClassifierEnabled()) {
            return Health.up()
                    .withDetail("check", "skipped")
                    .withDetail("reason", "rag.health.classifier-enabled=false")
                    .build();
        }

        String healthUrl = this.classifierBaseUrl + "/health";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(healthUrl))
                    .GET()
                    .timeout(Duration.ofMillis(Math.max(500, healthProperties.getReadTimeoutMs())))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Health.down()
                        .withDetail("url", healthUrl)
                        .withDetail("status", response.statusCode())
                        .build();
            }
            JSONObject json = new JSONObject(response.body());
            String model = json.optString("model", "");
            if (!"loaded".equals(model)) {
                if (healthProperties.isClassifierRequireModelLoaded()) {
                    return Health.down()
                            .withDetail("url", healthUrl)
                            .withDetail("model", model)
                            .withDetail("hint", "Classifier default model is not loaded; check MODELS_DIR and classifier logs")
                            .build();
                }
                return Health.up()
                        .withDetail("url", healthUrl)
                        .withDetail("model", model)
                        .withDetail("warning", "default model not loaded; set rag.health.classifier-require-model-loaded=false in Docker dev")
                        .build();
            }
            return Health.up()
                    .withDetail("url", healthUrl)
                    .withDetail("model", model)
                    .build();
        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("url", healthUrl)
                    .build();
        }
    }

    private static String stripTrailingSlash(String url) {
        if (url == null || url.isEmpty()) {
            return "http://localhost:8000";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
