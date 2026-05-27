package com.uniovi.rag.infrastructure.classifier;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.RagExecutionContext;
import com.uniovi.rag.domain.runtime.RagExecutionContextHolder;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/** Response DTO for classifier-service POST /classify (camelCase). */
record ClassifyResponseDto(String queryType) {}

/**
 * Query classifier that calls the classifier-service API over HTTP.
 * Uses the default pre-trained model (tag "default") so RAG classification is consistent.
 * Request/response use camelCase for interoperability.
 * POST {baseUrl}/classify with body {"query": "...", "modelId": "default"}, expects {"queryType": "COUNT_DOCUMENTS"} etc.
 * On transport or HTTP failures throws a recoverable exception so the runtime trace records UNAVAILABLE
 * (instead of silently returning INVALID_OUTPUT).
 */
public class ClassifierServiceClient implements QueryClassifier {

    private static final String DEFAULT_MODEL_ID = "default";

    private final String baseUrl;
    private final String modelId;
    private final RestTemplate restTemplate;

    public ClassifierServiceClient(String baseUrl) {
        this(baseUrl, DEFAULT_MODEL_ID, 5000);
    }

    public ClassifierServiceClient(String baseUrl, int timeoutMs) {
        this(baseUrl, DEFAULT_MODEL_ID, timeoutMs);
    }

    /** Main constructor: base URL of classifier-service, model tag (use "default" for pre-trained), timeout. */
    public ClassifierServiceClient(String baseUrl, String modelId, int timeoutMs) {
        this(baseUrl, modelId, timeoutMs, createDefaultRestTemplate(timeoutMs));
    }

    /**
     * Constructor for Spring wiring and tests: inject a {@link RestTemplate} built with
     * {@link org.springframework.boot.web.client.RestTemplateBuilder} so outbound calls propagate W3C trace context.
     */
    public ClassifierServiceClient(String baseUrl, String modelId, int timeoutMs, RestTemplate restTemplate) {
        this.baseUrl = baseUrl != null ? stripTrailingSlashes(baseUrl) : "";
        this.modelId = (modelId != null && !modelId.isBlank()) ? modelId : DEFAULT_MODEL_ID;
        int effectiveTimeout = timeoutMs > 0 ? timeoutMs : 5000;
        this.restTemplate = restTemplate != null ? restTemplate : createDefaultRestTemplate(effectiveTimeout);
    }

    @Override
    public QueryType classify(String query) {
        String raw = classifyWithText(query);
        return parseQueryType(raw);
    }

    @Override
    public QueryType classify(String query, String modelId) {
        String raw = classifyWithText(query, modelId);
        return parseQueryType(raw);
    }

    private QueryType parseQueryType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            QueryType qt = QueryType.valueOf(raw.trim());
            log().info("[CLASSIFIER] Query type: {}", qt);
            return qt;
        } catch (IllegalArgumentException e) {
            log().warn("[CLASSIFIER] Unknown queryType: '{}', returning null", raw);
            return null;
        }
    }

    @Override
    public String classifyWithText(String query) {
        return classifyWithText(query, null);
    }

    @Override
    public String classifyWithText(String query, String modelIdOverride) {
        if (baseUrl.isEmpty()) {
            log().debug("[CLASSIFIER] Classifier-service URL not configured, returning null");
            return null;
        }
        if (query == null) {
            return null;
        }
        String url = baseUrl + "/classify";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String effectiveModelId = resolveEffectiveModelId(modelIdOverride);
        Map<String, String> body = Map.of("query", query, "modelId", effectiveModelId);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<ClassifyResponseDto> response =
                    restTemplate.exchange(url, HttpMethod.POST, request, ClassifyResponseDto.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                ClassifyResponseDto responseBody = response.getBody();
                return (responseBody != null && responseBody.queryType() != null) ? responseBody.queryType() : null;
            }
            throw new IllegalStateException("Classifier service returned non-2xx status=" + response.getStatusCode().value());
        } catch (HttpStatusCodeException e) {
            String detail =
                    "Classifier HTTP error status="
                            + e.getStatusCode().value()
                            + " url="
                            + url
                            + " body="
                            + safeBodyPreview(e.getResponseBodyAsString());
            log().warn("[CLASSIFIER] {}", detail);
            throw new IllegalStateException(detail, e);
        } catch (RestClientException e) {
            String detail = "Classifier transport error url=" + url + " detail=" + e.getMessage();
            log().warn("[CLASSIFIER] {}", detail);
            throw new IllegalStateException(detail, e);
        }
    }

    private static String safeBodyPreview(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        if (t.length() <= 500) {
            return t;
        }
        return t.substring(0, 500) + "…";
    }

    private String resolveEffectiveModelId(String explicitModelId) {
        if (explicitModelId != null && !explicitModelId.isBlank()) {
            return explicitModelId.trim();
        }
        RagExecutionContext ctx = RagExecutionContextHolder.get();
        if (ctx != null
                && ctx.resolvedConfig() != null
                && ctx.resolvedConfig().classifierModelId() != null
                && !ctx.resolvedConfig().classifierModelId().isBlank()) {
            return ctx.resolvedConfig().classifierModelId();
        }
        return modelId;
    }

    private static RestTemplate createDefaultRestTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        RestTemplate rt = new RestTemplate();
        rt.setRequestFactory(factory);
        return rt;
    }

    private static String stripTrailingSlashes(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }
}
